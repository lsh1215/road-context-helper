package dev.pleos.roadcontext.core

enum class InputSource { REAL_SDK, REPLAY_SYNTHETIC, REPLAY_CAPTURED }
enum class GuidanceState { WAITING, PREVIEW, APPROACHING, SUPPRESSED, PASSED, UNAVAILABLE }
enum class GuidanceReason {
    WAITING_FOR_LOCATION, BEFORE_PREVIEW, STRUCTURE_PREVIEW, NEAR_MERGE, SEGMENT_PASSED,
    WRONG_DIRECTION, AMBIGUOUS_ROAD, OUTSIDE_CORRIDOR, STALE_LOCATION, INVALID_LOCATION,
    CLOCK_REGRESSION, OUT_OF_ORDER, POSITION_JUMP, ROUTE_CHANGED, WRONG_ROUTE_GENERATION,
    MISSING_ROUTE, SOURCE_MISMATCH, SYNTHETIC_DATA_IN_REAL_MODE, DATA_INVALID, DATA_EXPIRED,
    DATA_CHANGED, DATA_DISABLED, LATE_ARRIVAL, COOLDOWN, SOURCE_UNAVAILABLE, SEGMENT_ALREADY_PASSED
}

/** These are projected road distances, not latitude/longitude and never current lane numbers. */
data class ProjectedSample(
    val alongMeters: Double,
    val crossMeters: Double,
    val headingDeg: Double?,
    val receivedElapsedMs: Long,
    val source: InputSource,
    val routeGeneration: Long,
    val routeId: String?,
    val ambiguous: Boolean = false,
    val measurementElapsedMs: Long? = null,
    /** Directed centerline heading at this projection; never a substitute for vehicle headingDeg. */
    val roadHeadingDeg: Double? = null,
)

data class RoadEvidence(
    val approved: Boolean,
    val sourceUrl: String,
    val checkedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val originalCrs: String,
    val license: String,
    val reviewer: String,
)

data class RoadSegment(
    val id: String,
    val version: String,
    val approachStartMeters: Double,
    val mergeStartMeters: Double,
    val mergeEndMeters: Double,
    val headingDeg: Double,
    val corridorHalfWidthMeters: Double,
    val synthetic: Boolean,
    val evidence: RoadEvidence,
    val enabled: Boolean = true,
) {
    fun validationErrors(): List<String> = buildList {
        if (id.isBlank() || version.isBlank()) add("missing_identity")
        if (!listOf(approachStartMeters, mergeStartMeters, mergeEndMeters, headingDeg, corridorHalfWidthMeters).all { it.isFinite() }) add("non_finite_geometry")
        if (!(approachStartMeters < mergeStartMeters && mergeStartMeters < mergeEndMeters)) add("invalid_topology")
        if (headingDeg !in 0.0..<360.0 || corridorHalfWidthMeters <= 0) add("invalid_direction_or_corridor")
        if (!evidence.approved) add("unapproved")
        if (evidence.expiresAtEpochMs <= evidence.checkedAtEpochMs) add("invalid_validity_window")
        if (listOf(evidence.sourceUrl, evidence.originalCrs, evidence.license, evidence.reviewer).any { it.isBlank() }) add("missing_provenance")
        if (synthetic && evidence.originalCrs != "LOCAL_METERS_SYNTHETIC") add("invalid_synthetic_crs")
        if (!synthetic && evidence.originalCrs == "LOCAL_METERS_SYNTHETIC") add("synthetic_crs_for_real_segment")
    }
}

data class GuidanceConfig(
    val previewDistanceMeters: Double = 400.0,
    val approachingDistanceMeters: Double = 150.0,
    val passedOffsetMeters: Double = 100.0,
    val staleAfterMs: Long = 10_000,
    val cooldownMs: Long = 300_000,
    val headingToleranceDeg: Double = 30.0,
    val maxJumpBaseMeters: Double = 200.0,
    val maxTravelMetersPerSecond: Double = 60.0,
) {
    init {
        require(previewDistanceMeters > approachingDistanceMeters && approachingDistanceMeters >= 0)
        require(passedOffsetMeters >= 0 && staleAfterMs > 0 && cooldownMs >= 0)
        require(headingToleranceDeg in 0.0..180.0)
        require(maxJumpBaseMeters > 0 && maxTravelMetersPerSecond > 0)
    }
}

data class GuidanceDecision(
    val state: GuidanceState,
    val reason: GuidanceReason,
    val distanceToMergeMeters: Double?,
    val previewCount: Int,
    val source: InputSource,
    val segmentId: String,
    val segmentVersion: String,
    val passId: Long,
) {
    val displayable: Boolean get() = state == GuidanceState.PREVIEW || state == GuidanceState.APPROACHING
}

/** Deliberately fictional, meter-only geometry. Never assign a real place name to this fixture. */
fun syntheticSegment(): RoadSegment = RoadSegment(
    id = "synthetic-merge-01", version = "1", approachStartMeters = 0.0,
    mergeStartMeters = 600.0, mergeEndMeters = 800.0, headingDeg = 90.0,
    corridorHalfWidthMeters = 15.0, synthetic = true,
    evidence = RoadEvidence(true, "fixture://synthetic-merge-01", 0L, Long.MAX_VALUE,
        "LOCAL_METERS_SYNTHETIC", "Project-owned fictional fixture", "synthetic-fixture-author"),
)
