package dev.pleos.roadcontext.core

import kotlin.math.*

/** WGS84 geographic degrees, never local synthetic meters. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    fun isValid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** Vertex order defines travel direction. levelTag is provenance, not evidence of vehicle elevation. */
data class RoadPolyline(
    val id: String,
    val points: List<GeoPoint>,
    val crs: String = "EPSG:4326",
    val levelTag: String? = null,
)

data class ProjectionCandidate(
    val roadId: String,
    val alongMeters: Double,
    /** Positive to the left of vertex-order direction; signed distance to the great-circle line. */
    val crossMeters: Double,
    /** Distance to the bounded segment; unlike crossMeters, includes distance beyond its endpoint. */
    val distanceMeters: Double,
    val headingDeg: Double,
    val segmentIndex: Int,
    val closestPoint: GeoPoint,
    val levelTag: String?,
)

data class ProjectionRejection(val roadId: String?, val reason: String)

data class GeoProjectionResult(
    val candidates: List<ProjectionCandidate>,
    val ambiguous: Boolean,
    val rejections: List<ProjectionRejection>,
) {
    /** No fallback to the nearest road when competing roads or malformed input are present. */
    val unambiguousCandidate: ProjectionCandidate?
        get() = candidates.singleOrNull()?.takeIf { !ambiguous && rejections.isEmpty() }
}

/**
 * Projects onto bounded great-circle segments on a mean-radius Earth sphere.
 * This is a navigation-distance approximation, not an ellipsoidal survey or lane localization.
 * No heading, altitude, route name, or levelTag is used to silently resolve competing roads.
 */
class GeoProjector {
    fun project(
        location: GeoPoint,
        roads: List<RoadPolyline>,
        uncertaintyMeters: Double = 20.0,
        maxDistanceMeters: Double = 50.0,
    ): GeoProjectionResult {
        if (!location.isValid()) return rejected("INVALID_LOCATION")
        if (!uncertaintyMeters.isFinite() || uncertaintyMeters < 0 || !maxDistanceMeters.isFinite() || maxDistanceMeters <= 0) return rejected("INVALID_DISTANCE_POLICY")
        val rejections = mutableListOf<ProjectionRejection>()
        val raw = mutableListOf<ProjectionCandidate>()
        val duplicateIds = roads.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val p = location.vector()
        roads.forEach { road ->
            val error = when {
                road.id.isBlank() -> "MISSING_ROAD_ID"
                road.id in duplicateIds -> "DUPLICATE_ROAD_ID"
                road.crs != "EPSG:4326" -> "UNSUPPORTED_CRS"
                road.points.size < 2 -> "INSUFFICIENT_VERTICES"
                road.points.any { !it.isValid() } -> "INVALID_ROAD_COORDINATE"
                else -> null
            }
            if (error != null) {
                rejections += ProjectionRejection(road.id, error)
                return@forEach
            }
            val candidates = mutableListOf<ProjectionCandidate>()
            var alongBase = 0.0
            var nonzeroSegments = 0
            var invalidSegment = false
            road.points.zipWithNext().forEachIndexed { index, (start, end) ->
                val a = start.vector()
                val b = end.vector()
                val cross = a.cross(b)
                val lengthAngle = atan2(cross.norm(), a.dot(b))
                if (lengthAngle < 1e-12) return@forEachIndexed // duplicate vertex carries no geometry
                if (PI - lengthAngle < 1e-7) {
                    invalidSegment = true // antipodes do not define a unique shortest arc
                    return@forEachIndexed
                }
                nonzeroSegments++
                val normal = cross.normalized()
                val projected = p - normal * p.dot(normal)
                if (projected.norm() < 1e-12) {
                    // A pole of this great circle is equally far from every point on it.
                    // Its distance is enormous for road corridors, so no candidate is warranted.
                    alongBase += lengthAngle * EARTH_RADIUS_METERS
                    return@forEachIndexed
                }
                val foot = projected.normalized()
                val signedAngle = atan2(normal.dot(a.cross(foot)), a.dot(foot))
                val clampedAngle: Double
                val closest: Vector3
                if (signedAngle in 0.0..lengthAngle) {
                    clampedAngle = signedAngle
                    closest = foot
                } else if (angularDistance(p, a) <= angularDistance(p, b)) {
                    clampedAngle = 0.0
                    closest = a
                } else {
                    clampedAngle = lengthAngle
                    closest = b
                }
                val distance = angularDistance(p, closest) * EARTH_RADIUS_METERS
                if (distance <= maxDistanceMeters) {
                    val point = closest.point()
                    val tangent = normal.cross(closest)
                    val lat = Math.toRadians(point.latitude)
                    val lon = Math.toRadians(point.longitude)
                    val east = Vector3(-sin(lon), cos(lon), 0.0)
                    val north = Vector3(-sin(lat) * cos(lon), -sin(lat) * sin(lon), cos(lat))
                    val heading = (Math.toDegrees(atan2(tangent.dot(east), tangent.dot(north))) + 360.0) % 360.0
                    candidates += ProjectionCandidate(road.id, alongBase + clampedAngle * EARTH_RADIUS_METERS,
                        asin(p.dot(normal).coerceIn(-1.0, 1.0)) * EARTH_RADIUS_METERS,
                        distance, heading, index, point, road.levelTag)
                }
                alongBase += lengthAngle * EARTH_RADIUS_METERS
            }
            when {
                invalidSegment -> rejections += ProjectionRejection(road.id, "ANTIPODAL_SEGMENT")
                nonzeroSegments == 0 -> rejections += ProjectionRejection(road.id, "ZERO_LENGTH_ROAD")
                else -> raw += candidates
            }
        }
        val nearest = raw.minOfOrNull { it.distanceMeters } ?: return GeoProjectionResult(emptyList(), false, rejections)
        val plausible = raw.filter { candidate ->
            candidate.distanceMeters <= nearest + uncertaintyMeters &&
                // Dense vertices create endpoint candidates on either side of the actual foot.
                // These are not separate road hypotheses: retain local distance minima only.
                raw.none { neighbor -> neighbor.roadId == candidate.roadId &&
                    abs(neighbor.segmentIndex - candidate.segmentIndex) == 1 &&
                    neighbor.distanceMeters + 1e-6 < candidate.distanceMeters }
        }
            .sortedWith(compareBy<ProjectionCandidate> { it.distanceMeters }.thenBy { it.roadId }.thenBy { it.alongMeters })
        val candidates = mutableListOf<ProjectionCandidate>()
        plausible.forEach { candidate ->
            // At a shared vertex adjacent segments represent one road position. At a crossing,
            // distinct longitudinal positions of the same road remain competing hypotheses.
            if (candidates.none { it.roadId == candidate.roadId && abs(it.alongMeters - candidate.alongMeters) <= 0.1 }) {
                candidates += candidate
            }
        }
        return GeoProjectionResult(candidates, candidates.size > 1, rejections)
    }

    private fun rejected(reason: String) = GeoProjectionResult(emptyList(), false, listOf(ProjectionRejection(null, reason)))

    companion object { const val EARTH_RADIUS_METERS = 6_371_008.8 }
}

private data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(k: Double) = Vector3(x * k, y * k, z * k)
    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3) = Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
    fun norm() = sqrt(dot(this))
    fun normalized() = this * (1.0 / norm())
    fun point() = GeoPoint(Math.toDegrees(atan2(z, hypot(x, y))), Math.toDegrees(atan2(y, x)))
}

private fun GeoPoint.vector(): Vector3 {
    val lat = Math.toRadians(latitude)
    val lon = Math.toRadians(longitude)
    return Vector3(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
}

private fun angularDistance(a: Vector3, b: Vector3) = atan2(a.cross(b).norm(), a.dot(b))
