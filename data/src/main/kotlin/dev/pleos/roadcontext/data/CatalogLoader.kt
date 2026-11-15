package dev.pleos.roadcontext.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import dev.pleos.roadcontext.core.GeoPoint
import dev.pleos.roadcontext.core.GeoProjector
import dev.pleos.roadcontext.core.RoadEvidence
import dev.pleos.roadcontext.core.RoadPolyline
import dev.pleos.roadcontext.core.RoadSegment
import java.net.URI

/** Side of the entrance lane relative to the main roadway. RIGHT enters from the right and merges left. Never the current vehicle lane or a maneuver command. */
enum class MergeSide { LEFT, RIGHT }

data class SourceProvenance(
    val sourceUrl: String,
    val sourceVersion: String,
    val sourceSha256: String,
    val attribution: String,
    val reviewer: String,
    val checkedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class RuntimeRoadEntry(
    val segment: RoadSegment,
    val centerline: RoadPolyline,
    val mergeSide: MergeSide,
    val provenance: SourceProvenance,
)

data class GeographicBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun isValid(): Boolean = listOf(south, west, north, east).all { it.isFinite() } &&
        south > -85 && north < 85 && south < north && west >= -180 && east <= 180 && west < east && east - west < 180

    fun contains(point: GeoPoint): Boolean = isValid() && point.isValid() &&
        point.latitude in south..north && point.longitude in west..east

    /** Conservative great-circle edge margin. Antimeridian/polar coverage is deliberately unsupported. */
    fun containsWithMargin(point: GeoPoint, marginMeters: Double): Boolean {
        if (!contains(point) || !marginMeters.isFinite() || marginMeters < 0) return false
        val radius = GeoProjector.EARTH_RADIUS_METERS
        val lat = Math.toRadians(point.latitude)
        val southDistance = radius * Math.toRadians(point.latitude - south)
        val northDistance = radius * Math.toRadians(north - point.latitude)
        fun meridianDistance(delta: Double) = radius * kotlin.math.asin(
            (kotlin.math.abs(kotlin.math.sin(Math.toRadians(delta))) * kotlin.math.cos(lat)).coerceIn(0.0, 1.0))
        return minOf(southDistance, northDistance, meridianDistance(point.longitude - west), meridianDistance(east - point.longitude)) >= marginMeters
    }
}

data class MatchingCoverage(
    val bounds: GeographicBounds,
    val provenance: SourceProvenance,
    val license: String,
    val competingRoadsReviewed: Boolean,
) {
    fun validationErrors(nowEpochMs: Long): List<String> = buildList {
        if (!bounds.isValid()) add("invalid_coverage_bounds")
        if (!competingRoadsReviewed) add("incomplete_competing_roads_review")
        if (license.isBlank() || listOf(provenance.sourceVersion, provenance.attribution, provenance.reviewer).any { it.isBlank() }) add("missing_coverage_provenance")
        val uri = runCatching { URI(provenance.sourceUrl) }.getOrNull()
        if (uri?.scheme != "https" || uri?.host.isNullOrBlank()) add("invalid_coverage_source")
        if (!provenance.sourceSha256.matches(Regex("[0-9a-f]{64}"))) add("invalid_coverage_source_hash")
        if (provenance.checkedAtEpochMs < 0 || provenance.checkedAtEpochMs > nowEpochMs ||
            provenance.expiresAtEpochMs <= nowEpochMs || provenance.expiresAtEpochMs <= provenance.checkedAtEpochMs) add("expired_or_invalid_coverage")
    }
    fun containsWithMargin(point: GeoPoint, marginMeters: Double) = bounds.containsWithMargin(point, marginMeters)
}

data class RuntimeRoadCatalog(
    val version: String,
    val entries: List<RuntimeRoadEntry>,
    val matchingCoverage: MatchingCoverage? = null,
    val candidateRoads: List<RoadPolyline> = emptyList(),
    val schemaVersion: Int = 2,
) {
    val supported: Boolean get() = entries.isNotEmpty()
}

sealed interface CatalogLoadResult {
    data class Loaded(val catalog: RuntimeRoadCatalog) : CatalogLoadResult
    data class Rejected(val errors: List<String>) : CatalogLoadResult
}

/**
 * Atomic runtime validator. A single invalid entry rejects the entire bundle. Metadata validation
 * does not replace independent source/topology review, and this loader never invents geometry.
 */
class CatalogLoader {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(json: String, nowEpochMs: Long): CatalogLoadResult = try {
        require(nowEpochMs >= 0) { "invalid_now" }
        require(json.length <= 2_000_000) { "catalog_too_large" }
        val root = gson.fromJson(json, JsonElement::class.java).obj("catalog")
        val schemaVersion = root.integer("schemaVersion")
        require(schemaVersion in 1L..2L) { "unsupported_schema" }
        require(root.text("sourceMode") == "REAL_WORLD") { "real_source_required" }
        val version = root.text("catalogVersion")
        val list = root.array("approvedSegments")
        require(list.size() <= 256) { "too_many_segments" }
        val entries = list.mapIndexed { index, element -> parseEntry(element.obj("entry[$index]"), nowEpochMs) }
        require(entries.map { it.segment.id }.distinct().size == entries.size) { "duplicate_segment_id" }
        if (entries.isNotEmpty()) require(schemaVersion == 2L) { "schema_2_required_for_supported_roads" }
        val hasCoverage = root.has("matchingCoverage") && !root.get("matchingCoverage").isJsonNull
        val coverage = if (hasCoverage) parseCoverage(root.objectValue("matchingCoverage"), nowEpochMs) else null
        val candidates = if (root.has("candidateRoads")) {
            val raw = root.array("candidateRoads")
            require(raw.size() <= 2000) { "too_many_candidate_roads" }
            raw.map { parseCandidate(it.obj("candidateRoad")) }
        } else emptyList()
        require(candidates.map { it.id }.distinct().size == candidates.size) { "duplicate_candidate_road_id" }
        if (entries.isNotEmpty()) {
            require(coverage != null) { "matching_coverage_required" }
            require(candidates.isNotEmpty()) { "candidate_roads_required" }
            entries.forEach { entry ->
                val road = candidates.singleOrNull { it.id == entry.segment.id } ?: error("supported_road_missing_from_candidates")
                require(road.points == entry.centerline.points && road.crs == entry.centerline.crs) { "candidate_geometry_mismatch" }
                require(entry.centerline.points.all { coverage.bounds.contains(it) }) { "supported_geometry_outside_coverage" }
            }
        }
        CatalogLoadResult.Loaded(RuntimeRoadCatalog(version, entries, coverage, candidates, schemaVersion.toInt()))
    } catch (error: Exception) {
        CatalogLoadResult.Rejected(listOf(error.message ?: error.javaClass.simpleName))
    }

    private fun parseCoverage(value: JsonObject, now: Long): MatchingCoverage {
        require(value.text("crs") == "EPSG:4326") { "invalid_coverage_crs" }
        val bounds = value.objectValue("bounds")
        val evidence = value.objectValue("evidence")
        val checks = evidence.objectValue("completenessChecks")
        val reviewed = listOf("fullBounds", "parallelRoads", "gradeSeparatedRoads", "ramps", "intersections").all { checks.boolean(it) }
        val coverage = MatchingCoverage(
            GeographicBounds(bounds.number("south"), bounds.number("west"), bounds.number("north"), bounds.number("east")),
            SourceProvenance(evidence.text("sourceUrl"), evidence.text("sourceVersion"), evidence.text("sourceSha256"),
                evidence.text("attribution"), evidence.text("reviewer"), evidence.integer("checkedAtEpochMs"), evidence.integer("expiresAtEpochMs")),
            evidence.text("license"), reviewed,
        )
        require(coverage.validationErrors(now).isEmpty()) { coverage.validationErrors(now).joinToString() }
        return coverage
    }

    private fun parseCandidate(value: JsonObject): RoadPolyline {
        require(value.text("crs") == "EPSG:4326") { "invalid_candidate_crs" }
        val points = parseCoordinates(value.array("centerline"))
        val level = if (value.has("levelTag")) value.text("levelTag") else null
        val line = RoadPolyline(value.text("id"), points, "EPSG:4326", level)
        require(GeoProjector().project(points.first(), listOf(line), 0.0, 1.0).rejections.isEmpty()) { "invalid_candidate_geometry" }
        return line
    }

    private fun parseCoordinates(coordinates: JsonArray): List<GeoPoint> {
        require(coordinates.size() in 2..10_000) { "invalid_centerline_size" }
        return coordinates.map { raw ->
            val pair = raw as? JsonArray ?: error("coordinate_must_be_array")
            require(pair.size() == 2) { "coordinate_requires_lon_lat" }
            val lon = pair[0].finiteNumber("longitude")
            val lat = pair[1].finiteNumber("latitude")
            require(lon in -180.0..180.0 && lat in -90.0..90.0) { "coordinate_out_of_range" }
            GeoPoint(lat, lon)
        }.also { points -> require(points.zipWithNext().all { (a, b) -> a != b }) { "duplicate_adjacent_vertex" } }
    }

    private fun parseEntry(entry: JsonObject, now: Long): RuntimeRoadEntry {
        require(entry.text("status") == "APPROVED") { "entry_unapproved" }
        require(!entry.boolean("synthetic")) { "synthetic_entry_in_real_catalog" }
        val geometry = entry.objectValue("geometry")
        require(geometry.text("crs") == "EPSG:4326") { "invalid_output_crs" }
        val points = parseCoordinates(geometry.array("centerline"))
        val evidence = entry.objectValue("evidence")
        val sourceUrl = evidence.text("sourceUrl")
        val uri = URI(sourceUrl)
        require(uri.scheme == "https" && !uri?.host.isNullOrBlank()) { "invalid_source_url" }
        val checked = evidence.integer("checkedAtEpochMs")
        val expires = evidence.integer("expiresAtEpochMs")
        require(checked in 0..now && expires > now && expires > checked) { "invalid_or_expired_evidence" }
        val sourceCrs = evidence.text("originalCrs")
        require(sourceCrs != "LOCAL_METERS_SYNTHETIC") { "synthetic_source_crs" }
        val checks = evidence.objectValue("checks")
        for (key in REQUIRED_CHECKS) require(checks.boolean(key)) { "review_check_missing:$key" }
        val hash = evidence.text("sourceSha256")
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "invalid_source_sha256" }
        val reviewer = evidence.text("reviewer")
        val coreEvidence = RoadEvidence(
            approved = true, sourceUrl = sourceUrl, checkedAtEpochMs = checked,
            expiresAtEpochMs = expires, originalCrs = sourceCrs,
            license = evidence.text("license"), reviewer = reviewer,
        )
        val segment = RoadSegment(
            id = entry.text("id"), version = entry.text("version"),
            approachStartMeters = entry.number("approachStartMeters"),
            mergeStartMeters = entry.number("mergeStartMeters"),
            mergeEndMeters = entry.number("mergeEndMeters"),
            headingDeg = entry.number("headingDeg"),
            corridorHalfWidthMeters = entry.number("corridorHalfWidthMeters"),
            synthetic = false, evidence = coreEvidence, enabled = entry.boolean("enabled"),
        )
        require(segment.enabled) { "disabled_segment" }
        require(segment.validationErrors().isEmpty()) { "invalid_segment:${segment.validationErrors().joinToString()}" }
        require(segment.approachStartMeters >= 0) { "negative_approach_distance" }
        val polyline = RoadPolyline(segment.id, points)
        val projector = GeoProjector()
        val start = projector.project(points.first(), listOf(polyline), 0.0, 1.0).unambiguousCandidate
            ?: error("invalid_or_ambiguous_centerline_start")
        val end = projector.project(points.last(), listOf(polyline), 0.0, 1.0).unambiguousCandidate
            ?: error("invalid_or_ambiguous_centerline_end")
        require(segment.mergeEndMeters + 100.0 <= end.alongMeters) { "centerline_missing_passed_margin" }
        val headingDelta = kotlin.math.abs((start.headingDeg - segment.headingDeg + 540.0) % 360.0 - 180.0)
        require(headingDelta <= 45.0) { "centerline_direction_mismatch" }
        val side = try { MergeSide.valueOf(entry.text("mergeSide")) } catch (_: IllegalArgumentException) { error("invalid_merge_side") }
        return RuntimeRoadEntry(segment, polyline, side, SourceProvenance(
            sourceUrl, evidence.text("sourceVersion"), hash, evidence.text("attribution"), reviewer, checked, expires,
        ))
    }

    companion object {
        private val REQUIRED_CHECKS = listOf("direction", "lanePersistence", "mergeEndpoint", "connectivity", "crsTransform", "currentness", "license")
    }
}

private fun JsonElement?.obj(name: String): JsonObject = this as? JsonObject ?: error("object_required:$name")
private fun JsonObject.required(key: String): JsonElement = get(key)?.takeUnless { it.isJsonNull } ?: error("missing:$key")
private fun JsonObject.text(key: String): String {
    val value = required(key)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "string_required:$key" }
    return value.asString.also { require(it.isNotBlank()) { "blank:$key" } }
}
private fun JsonObject.boolean(key: String): Boolean {
    val value = required(key)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "boolean_required:$key" }
    return value.asBoolean
}
private fun JsonObject.integer(key: String): Long {
    val value = required(key)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "integer_required:$key" }
    return value.asBigDecimal.longValueExact()
}
private fun JsonElement.finiteNumber(name: String): Double {
    require(isJsonPrimitive && asJsonPrimitive.isNumber) { "number_required:$name" }
    return asDouble.also { require(it.isFinite()) { "non_finite:$name" } }
}
private fun JsonObject.number(key: String): Double = required(key).finiteNumber(key)
private fun JsonObject.objectValue(key: String): JsonObject = required(key).obj(key)
private fun JsonObject.array(key: String): JsonArray = required(key) as? JsonArray ?: error("array_required:$key")
