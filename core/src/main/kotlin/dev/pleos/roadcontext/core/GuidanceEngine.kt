package dev.pleos.roadcontext.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure state machine. The caller owns location projection and SDK lifecycle. */
class GuidanceEngine(
    private var segment: RoadSegment,
    private val config: GuidanceConfig = GuidanceConfig(),
    source: InputSource = InputSource.REPLAY_SYNTHETIC,
) {
    private var source = source
    private var routeGeneration = 0L
    private var routeId: String? = null
    private var available = true
    private var lastNow = -1L
    private var lastAccepted: ProjectedSample? = null
    private var previewed = false
    private var approached = false
    private var passed = false
    private var lastPreviewTime: Long? = null
    private var previewCount = 0
    private var passId = 0L
    private data class SuspendedPass(
        val sample: ProjectedSample,
        val routeGeneration: Long,
        val routeId: String?,
        val source: InputSource,
        val segmentId: String,
        val segmentVersion: String,
        val approached: Boolean,
        val passId: Long,
    )
    private var suspendedPass: SuspendedPass? = null
    var decision: GuidanceDecision = result(GuidanceState.WAITING, GuidanceReason.WAITING_FOR_LOCATION)
        private set

    fun update(sample: ProjectedSample, nowElapsedMs: Long, nowEpochMs: Long): GuidanceDecision {
        preflight(nowElapsedMs, nowEpochMs)?.let { return it }
        if (sample.source != source) return suppress(GuidanceReason.SOURCE_MISMATCH)
        if (sample.routeGeneration != routeGeneration) return suppress(GuidanceReason.WRONG_ROUTE_GENERATION)
        if (sample.routeId.isNullOrBlank()) return suppress(GuidanceReason.MISSING_ROUTE)
        if (routeId != null && routeId != sample.routeId) {
            clearPass()
            routeId = sample.routeId
            return suppress(GuidanceReason.ROUTE_CHANGED)
        }
        routeId = sample.routeId
        val measured = sample.measurementElapsedMs ?: sample.receivedElapsedMs
        if (sample.receivedElapsedMs < 0 || measured < 0 || sample.receivedElapsedMs > nowElapsedMs || measured > sample.receivedElapsedMs) return suppress(GuidanceReason.INVALID_LOCATION)
        if (nowElapsedMs - measured >= config.staleAfterMs) return suppress(GuidanceReason.STALE_LOCATION)
        if (!sample.alongMeters.isFinite() || !sample.crossMeters.isFinite() || sample.headingDeg == null || !sample.headingDeg.isFinite() || sample.headingDeg !in 0.0..<360.0) return suppress(GuidanceReason.INVALID_LOCATION)
        if (sample.ambiguous) return suppress(GuidanceReason.AMBIGUOUS_ROAD)
        if (abs(sample.crossMeters) > segment.corridorHalfWidthMeters || sample.alongMeters < segment.approachStartMeters) return suppress(GuidanceReason.OUTSIDE_CORRIDOR)
        val roadHeading = sample.roadHeadingDeg ?: segment.headingDeg
        if (!roadHeading.isFinite() || roadHeading !in 0.0..<360.0) return suppress(GuidanceReason.INVALID_LOCATION)
        if (angleDelta(sample.headingDeg, roadHeading) > config.headingToleranceDeg) return suppress(GuidanceReason.WRONG_DIRECTION)
        val recovery = suspendedPass?.takeIf {
            it.routeGeneration == routeGeneration && it.routeId == sample.routeId && it.source == sample.source &&
                it.segmentId == segment.id && it.segmentVersion == segment.version
        }
        val previous = lastAccepted ?: recovery?.sample
        if (previous != null) {
            val previousMeasured = previous.measurementElapsedMs ?: previous.receivedElapsedMs
            if (measured < previousMeasured || sample.receivedElapsedMs < previous.receivedElapsedMs) return suppress(GuidanceReason.OUT_OF_ORDER)
            // Re-delivery may have a new receipt timestamp while referring to the same measurement.
            // Compare sensor content, not transport arrival time; freshness still uses measured time.
            if (measured == previousMeasured && sample.copy(receivedElapsedMs = previous.receivedElapsedMs) != previous) return suppress(GuidanceReason.OUT_OF_ORDER)
            val dt = (measured - previousMeasured) / 1000.0
            val allowed = config.maxJumpBaseMeters + config.maxTravelMetersPerSecond * dt
            if (abs(sample.alongMeters - previous.alongMeters) > allowed) return suppress(GuidanceReason.POSITION_JUMP)
        }
        if (recovery != null) {
            val priorTime = recovery.sample.measurementElapsedMs ?: recovery.sample.receivedElapsedMs
            val verifiedMeasurementClock = source == InputSource.REPLAY_SYNTHETIC ||
                (sample.measurementElapsedMs != null && recovery.sample.measurementElapsedMs != null)
            val gap = measured - priorTime
            if (verifiedMeasurementClock && gap > 0 && gap < config.staleAfterMs &&
                sample.alongMeters >= recovery.sample.alongMeters) {
                // Resume an already-issued explanation; this is not a new preview or a new pass.
                previewed = true
                approached = recovery.approached
                passId = recovery.passId
                suspendedPass = null
            } else if (gap >= config.staleAfterMs) {
                suspendedPass = null
            }
        }
        lastAccepted = sample
        val remaining = segment.mergeStartMeters - sample.alongMeters
        if (passed) {
            if (remaining > config.previewDistanceMeters) {
                passed = false
                previewed = false
                approached = false
                passId++
            } else return set(GuidanceState.PASSED, GuidanceReason.SEGMENT_ALREADY_PASSED)
        }
        if (sample.alongMeters >= segment.mergeEndMeters + config.passedOffsetMeters) {
            if (previewed && previous != null) {
                passed = true
                return set(GuidanceState.PASSED, GuidanceReason.SEGMENT_PASSED)
            }
            return suppress(GuidanceReason.LATE_ARRIVAL)
        }
        if (remaining > config.previewDistanceMeters) {
            // Keep a shown card stable when GPS jitters backwards around the preview boundary.
            return if (previewed) set(GuidanceState.PREVIEW, GuidanceReason.STRUCTURE_PREVIEW, remaining)
            else set(GuidanceState.WAITING, GuidanceReason.BEFORE_PREVIEW)
        }
        if (!previewed) {
            if (remaining <= config.approachingDistanceMeters) return suppress(GuidanceReason.LATE_ARRIVAL)
            if (!cooldownExpired(nowElapsedMs)) return suppress(GuidanceReason.COOLDOWN)
            previewed = true
            previewCount++
            lastPreviewTime = nowElapsedMs
        }
        return if (remaining <= config.approachingDistanceMeters || approached) {
            approached = true
            set(GuidanceState.APPROACHING, GuidanceReason.NEAR_MERGE, max(0.0, remaining))
        } else set(GuidanceState.PREVIEW, GuidanceReason.STRUCTURE_PREVIEW, remaining)
    }

    fun tick(nowElapsedMs: Long, nowEpochMs: Long): GuidanceDecision {
        preflight(nowElapsedMs, nowEpochMs)?.let { return it }
        val sample = lastAccepted ?: return decision
        val time = sample.measurementElapsedMs ?: sample.receivedElapsedMs
        if (nowElapsedMs - time >= config.staleAfterMs) return suppress(GuidanceReason.STALE_LOCATION)
        return decision
    }

    fun changeRoute(generation: Long): GuidanceDecision {
        if (generation < routeGeneration) return suppress(GuidanceReason.WRONG_ROUTE_GENERATION)
        routeGeneration = generation
        routeId = null
        clearPass()
        return set(GuidanceState.WAITING, GuidanceReason.ROUTE_CHANGED)
    }

    /** Change provider within a test/diagnostic session; keep cumulative metrics, discard its inputs. */
    fun switchSource(source: InputSource, generation: Long): GuidanceDecision {
        if (generation < routeGeneration) return suppress(GuidanceReason.WRONG_ROUTE_GENERATION)
        this.source = source
        routeGeneration = generation
        routeId = null
        clearPass()
        return set(GuidanceState.WAITING, GuidanceReason.WAITING_FOR_LOCATION)
    }

    /** TBT distances have no verified cumulative/segment contract and cannot drive road geometry. */
    fun observeUnverifiedTbt(): GuidanceDecision = decision

    fun reset(source: InputSource): GuidanceDecision {
        this.source = source
        routeGeneration = 0
        routeId = null
        lastNow = -1
        lastPreviewTime = null
        previewCount = 0
        passId = 0
        available = true
        clearPass()
        return set(GuidanceState.WAITING, GuidanceReason.WAITING_FOR_LOCATION)
    }

    fun setAvailable(value: Boolean): GuidanceDecision {
        if (available == value) return decision
        if (!value) {
            suspendedPass = lastAccepted?.takeIf { previewed && !passed }?.let {
                SuspendedPass(it, routeGeneration, routeId, source, segment.id, segment.version, approached, passId)
            }
        }
        available = value
        clearPass(clearRecovery = false)
        return if (value) set(GuidanceState.WAITING, GuidanceReason.WAITING_FOR_LOCATION)
        else set(GuidanceState.UNAVAILABLE, GuidanceReason.SOURCE_UNAVAILABLE)
    }

    fun replaceSegment(value: RoadSegment): GuidanceDecision {
        segment = value
        clearPass()
        return suppress(GuidanceReason.DATA_CHANGED)
    }

    private fun preflight(now: Long, wallTime: Long): GuidanceDecision? {
        if (now < 0 || now < lastNow) return suppress(GuidanceReason.CLOCK_REGRESSION)
        lastNow = now
        if (!available) return set(GuidanceState.UNAVAILABLE, GuidanceReason.SOURCE_UNAVAILABLE)
        if (!segment.enabled) return suppress(GuidanceReason.DATA_DISABLED)
        if (segment.validationErrors().isNotEmpty()) return set(GuidanceState.UNAVAILABLE, GuidanceReason.DATA_INVALID)
        if (wallTime < segment.evidence.checkedAtEpochMs || wallTime >= segment.evidence.expiresAtEpochMs) return suppress(GuidanceReason.DATA_EXPIRED)
        if (source != InputSource.REPLAY_SYNTHETIC && segment.synthetic) return suppress(GuidanceReason.SYNTHETIC_DATA_IN_REAL_MODE)
        return null
    }

    private fun clearPass(clearRecovery: Boolean = true) {
        lastAccepted = null; previewed = false; approached = false; passed = false; passId++
        if (clearRecovery) suspendedPass = null
    }
    private fun cooldownExpired(now: Long) = lastPreviewTime?.let { now - it >= config.cooldownMs } ?: true
    private fun suppress(reason: GuidanceReason) = set(GuidanceState.SUPPRESSED, reason)
    private fun set(state: GuidanceState, reason: GuidanceReason, distance: Double? = null): GuidanceDecision {
        decision = result(state, reason, distance)
        return decision
    }
    private fun result(state: GuidanceState, reason: GuidanceReason, distance: Double? = null) = GuidanceDecision(
        state, reason, distance, previewCount, source, segment.id, segment.version, passId,
    )
    private fun angleDelta(a: Double, b: Double): Double { val d = abs(a - b) % 360.0; return min(d, 360.0 - d) }
}
