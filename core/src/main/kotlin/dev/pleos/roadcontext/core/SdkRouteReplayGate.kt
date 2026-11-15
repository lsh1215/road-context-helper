package dev.pleos.roadcontext.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SdkRouteReplaySample(
    val point: GeoPoint,
    val headingDeg: Double?,
    val measuredElapsedMs: Long?,
    val receivedElapsedMs: Long,
)

enum class SdkRouteReplayState { WAITING, GUIDANCE, PASSED, OUTSIDE, WRONG_DIRECTION, STALE, INVALID, JUMP }

data class SdkRouteReplayResult(
    val state: SdkRouteReplayState,
    val projection: ProjectionCandidate? = null,
) {
    val visible: Boolean get() = state == SdkRouteReplayState.GUIDANCE
}

/**
 * A gate for an explicitly selected, controlled GPS replay experiment, not production road matching.
 * The window is a test trigger on a real road's reference geometry; it approves neither lane
 * topology nor a merge maneuver. The caller must obtain samples from the actual location SDK and
 * validate its measurement clock before converting timestamps into elapsed-realtime milliseconds.
 * No route identity, accuracy estimate, or approved road evidence is manufactured here.
 */
class SdkRouteReplayGate(
    private val road: RoadPolyline,
    private val startAlongMeters: Double,
    private val endAlongMeters: Double,
) {
    private val projector = GeoProjector()
    private var lastNow = -1L
    private var newestMeasured = -1L
    private var newestReceived = -1L
    private var latestSample: SdkRouteReplaySample? = null
    private var motionAnchor: SdkRouteReplaySample? = null
    private var confirmations = 0
    var result = SdkRouteReplayResult(SdkRouteReplayState.WAITING)
        private set

    init {
        require(startAlongMeters.isFinite() && endAlongMeters.isFinite() &&
            startAlongMeters >= 0 && endAlongMeters > startAlongMeters) { "Invalid replay window" }
        require(road.points.size >= 2) { "Replay road needs at least two vertices" }
        val geometry = projector.project(road.points.last(), listOf(road), 0.0, CORRIDOR_METERS)
        require(geometry.rejections.isEmpty() && geometry.unambiguousCandidate != null) { "Invalid replay geometry" }
        require(geometry.unambiguousCandidate!!.alongMeters >= endAlongMeters + EXIT_MARGIN_METERS) {
            "Replay road must include the post-window exit margin"
        }
    }

    fun update(sample: SdkRouteReplaySample, nowElapsedMs: Long): SdkRouteReplayResult {
        if (result.state == SdkRouteReplayState.PASSED) return result
        if (!acceptClock(nowElapsedMs)) return reject(SdkRouteReplayState.INVALID)
        val measured = sample.measuredElapsedMs
        val heading = sample.headingDeg
        if (!sample.point.isValid() || heading == null || !heading.isFinite() || heading !in 0.0..<360.0 ||
            measured == null || measured < 0 || sample.receivedElapsedMs < measured ||
            sample.receivedElapsedMs > nowElapsedMs || measured < newestMeasured ||
            sample.receivedElapsedMs < newestReceived) return reject(SdkRouteReplayState.INVALID)
        if (nowElapsedMs - measured >= STALE_AFTER_MS) return reject(SdkRouteReplayState.STALE)
        if (measured == newestMeasured) {
            // Re-delivery of one fix cannot provide a second confirmation or restore hidden UI.
            val old = latestSample
            if (old?.point != sample.point || old.headingDeg != heading) return reject(SdkRouteReplayState.INVALID)
            newestReceived = sample.receivedElapsedMs
            return result
        }
        if (newestMeasured >= 0 && measured - newestMeasured >= STALE_AFTER_MS) {
            // A lifecycle pause or delayed timer must not let old confirmation survive a gap.
            confirmations = 0
            motionAnchor = null
            result = SdkRouteReplayResult(SdkRouteReplayState.WAITING)
        }
        newestMeasured = measured
        newestReceived = sample.receivedElapsedMs
        latestSample = sample
        val projected = projector.project(sample.point, listOf(road), 0.0, CORRIDOR_METERS)
        if (projected.rejections.isNotEmpty() || projected.ambiguous) return reject(SdkRouteReplayState.INVALID)
        val position = projected.unambiguousCandidate ?: return reject(SdkRouteReplayState.OUTSIDE)
        val directionError = abs((heading - position.headingDeg + 540.0) % 360.0 - 180.0)
        if (directionError > HEADING_TOLERANCE_DEG) return reject(SdkRouteReplayState.WRONG_DIRECTION, position)
        val previous = motionAnchor
        motionAnchor = sample
        if (previous != null) {
            val seconds = (measured - previous.measuredElapsedMs!!) / 1000.0
            if (distanceMeters(previous.point, sample.point) > 100.0 + 45.0 * seconds) {
                // Adopt the new anchor, but require two subsequent plausible fixes to reacquire.
                confirmations = 0
                return SdkRouteReplayResult(SdkRouteReplayState.JUMP, position).also { result = it }
            }
        }
        if (position.alongMeters >= endAlongMeters + EXIT_MARGIN_METERS) {
            confirmations = 0
            return SdkRouteReplayResult(SdkRouteReplayState.PASSED, position).also { result = it }
        }
        if (position.alongMeters < startAlongMeters ||
            (position.alongMeters > endAlongMeters && !result.visible)) {
            confirmations = 0
            return SdkRouteReplayResult(SdkRouteReplayState.WAITING, position).also { result = it }
        }
        confirmations = (confirmations + 1).coerceAtMost(2)
        return SdkRouteReplayResult(
            if (confirmations >= 2) SdkRouteReplayState.GUIDANCE else SdkRouteReplayState.WAITING,
            position,
        ).also { result = it }
    }

    fun tick(nowElapsedMs: Long): SdkRouteReplayResult {
        if (result.state == SdkRouteReplayState.PASSED) return result
        if (!acceptClock(nowElapsedMs)) return reject(SdkRouteReplayState.INVALID)
        if (newestMeasured >= 0 && nowElapsedMs - newestMeasured >= STALE_AFTER_MS) return reject(SdkRouteReplayState.STALE)
        return result // A timer can hide a card but can never show one.
    }

    fun reset() {
        lastNow = -1L
        newestMeasured = -1L
        newestReceived = -1L
        latestSample = null
        motionAnchor = null
        confirmations = 0
        result = SdkRouteReplayResult(SdkRouteReplayState.WAITING)
    }

    private fun acceptClock(now: Long): Boolean {
        if (now < 0 || now < lastNow) return false
        lastNow = now
        return true
    }

    private fun reject(state: SdkRouteReplayState, projection: ProjectionCandidate? = null): SdkRouteReplayResult {
        confirmations = 0
        motionAnchor = null
        return SdkRouteReplayResult(state, projection).also { result = it }
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val latDelta = Math.toRadians(b.latitude - a.latitude)
        val lonDelta = Math.toRadians(b.longitude - a.longitude)
        val haversine = (sin(latDelta / 2) * sin(latDelta / 2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lonDelta / 2) * sin(lonDelta / 2)).coerceIn(0.0, 1.0)
        return 2 * GeoProjector.EARTH_RADIUS_METERS * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    companion object {
        const val STALE_AFTER_MS = 5_000L
        const val CORRIDOR_METERS = 20.0
        const val HEADING_TOLERANCE_DEG = 45.0
        const val EXIT_MARGIN_METERS = 20.0
    }
}
