package dev.pleos.roadcontext.data

import dev.pleos.roadcontext.core.*

/** Adapter assertions must come from validated clocks/context, never from receiving a callback. */
data class LiveLocationInput(
    val point: GeoPoint,
    val vehicleHeadingDeg: Double?,
    val receivedElapsedMs: Long,
    val measuredElapsedMs: Long?,
    val measurementClockValidated: Boolean,
    val contextValidated: Boolean,
    val positionUncertaintyMeters: Double?,
    val routeId: String?,
    val routeGeneration: Long,
)

enum class LiveGuidanceStatus {
    WAITING, GUIDANCE, PASSED, SUPPRESSED, NO_APPROVED_ROADS, CATALOG_REJECTED,
    EVIDENCE_INVALID, SYNTHETIC_DATA_REJECTED, LOCATION_INVALID, HEADING_UNAVAILABLE,
    CLOCK_UNVERIFIED, CONTEXT_UNVERIFIED, UNCERTAINTY_UNVERIFIED, STALE_LOCATION,
    ROUTE_UNAVAILABLE, ROUTE_CHANGED, OLD_ROUTE, ROAD_AMBIGUOUS, OUTSIDE_SUPPORTED_ROAD,
    SOURCE_UNAVAILABLE, CLOCK_REGRESSION,
}

data class LiveGuidanceResult(
    val status: LiveGuidanceStatus,
    val decision: GuidanceDecision? = null,
    val entry: RuntimeRoadEntry? = null,
    val projection: ProjectionCandidate? = null,
) {
    val displayable: Boolean get() = status == LiveGuidanceStatus.GUIDANCE && decision?.displayable == true && entry != null
}

/** Explicit real-input path. No simulated coordinates, guesses of current lane, or TBT distance math. */
class LiveGuidanceCoordinator(
    initialCatalog: CatalogLoadResult,
    private val config: GuidanceConfig = GuidanceConfig(),
) {
    private var catalogResult = initialCatalog
    private val projector = GeoProjector()
    private val engines = mutableMapOf<String, GuidanceEngine>()
    private var routeGeneration = -1L
    private var routeId: String? = null
    private var available = true
    private var lastNow = -1L
    private var activeKey: String? = null
    private var lastInput: LiveLocationInput? = null
    var result = LiveGuidanceResult(LiveGuidanceStatus.WAITING)
        private set

    fun update(input: LiveLocationInput, nowElapsedMs: Long, nowEpochMs: Long): LiveGuidanceResult {
        commonGate(nowElapsedMs, nowEpochMs)?.let { return it }
        if (input.routeGeneration < 0 || input.routeId.isNullOrBlank()) return reject(LiveGuidanceStatus.ROUTE_UNAVAILABLE)
        if (input.routeGeneration < routeGeneration) return reject(LiveGuidanceStatus.OLD_ROUTE)
        if (input.routeGeneration > routeGeneration) invalidateRoute(input.routeGeneration)
        if (routeId != null && routeId != input.routeId) {
            engines.values.forEach { it.changeRoute(routeGeneration) }
            routeId = input.routeId
            lastInput = null
            return reject(LiveGuidanceStatus.ROUTE_CHANGED)
        }
        routeId = input.routeId
        if (!input.contextValidated) return reject(LiveGuidanceStatus.CONTEXT_UNVERIFIED)
        if (!input.measurementClockValidated || input.measuredElapsedMs == null) return reject(LiveGuidanceStatus.CLOCK_UNVERIFIED)
        if (!input.point.isValid() || input.receivedElapsedMs < 0 || input.measuredElapsedMs < 0 ||
            input.receivedElapsedMs > nowElapsedMs || input.measuredElapsedMs > input.receivedElapsedMs) return reject(LiveGuidanceStatus.LOCATION_INVALID)
        if (nowElapsedMs - input.measuredElapsedMs >= config.staleAfterMs) return reject(LiveGuidanceStatus.STALE_LOCATION)
        val vehicleHeading = input.vehicleHeadingDeg
        if (vehicleHeading == null || !vehicleHeading.isFinite() || vehicleHeading !in 0.0..<360.0) return reject(LiveGuidanceStatus.HEADING_UNAVAILABLE)
        val uncertainty = input.positionUncertaintyMeters
        if (uncertainty == null || !uncertainty.isFinite() || uncertainty < 0) return reject(LiveGuidanceStatus.UNCERTAINTY_UNVERIFIED)
        val catalog = (catalogResult as CatalogLoadResult.Loaded).catalog
        val searchDistance = catalog.entries.maxOf { it.segment.corridorHalfWidthMeters } + uncertainty
        // Candidate completeness must cover the complete search area, not merely the fix center.
        // An unsupported neighboring road can otherwise disappear and create false uniqueness.
        val coverage = catalog.matchingCoverage ?: return reject(LiveGuidanceStatus.EVIDENCE_INVALID)
        if (!coverage.containsWithMargin(input.point, searchDistance)) return reject(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD)
        val projection = projector.project(input.point, catalog.candidateRoads, uncertainty, searchDistance)
        if (projection.rejections.isNotEmpty()) return reject(LiveGuidanceStatus.EVIDENCE_INVALID)
        if (projection.ambiguous) return reject(LiveGuidanceStatus.ROAD_AMBIGUOUS)
        val candidate = projection.unambiguousCandidate ?: return reject(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD)
        val entry = catalog.entries.singleOrNull { it.segment.id == candidate.roadId } ?: return reject(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD)
        // The error envelope must fit within the supported corridor, not merely its center point.
        if (candidate.distanceMeters + uncertainty > entry.segment.corridorHalfWidthMeters) return reject(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD)
        val key = "${entry.segment.id}:${entry.segment.version}"
        val engine = engines.getOrPut(key) {
            GuidanceEngine(entry.segment, config, InputSource.REAL_SDK).also { it.changeRoute(routeGeneration) }
        }
        activeKey = key
        lastInput = input
        val decision = engine.update(ProjectedSample(candidate.alongMeters, candidate.crossMeters,
            vehicleHeading, input.receivedElapsedMs, InputSource.REAL_SDK, input.routeGeneration,
            input.routeId, measurementElapsedMs = input.measuredElapsedMs, roadHeadingDeg = candidate.headingDeg),
            nowElapsedMs, nowEpochMs)
        val status = when {
            decision.displayable -> LiveGuidanceStatus.GUIDANCE
            decision.state == GuidanceState.PASSED -> LiveGuidanceStatus.PASSED
            decision.state == GuidanceState.WAITING -> LiveGuidanceStatus.WAITING
            else -> LiveGuidanceStatus.SUPPRESSED
        }
        return LiveGuidanceResult(status, decision, entry, candidate).also { result = it }
    }

    fun tick(nowElapsedMs: Long, nowEpochMs: Long): LiveGuidanceResult {
        commonGate(nowElapsedMs, nowEpochMs)?.let { return it }
        val measured = lastInput?.measuredElapsedMs
        if (measured != null && nowElapsedMs - measured >= config.staleAfterMs) return reject(LiveGuidanceStatus.STALE_LOCATION)
        val engine = activeKey?.let { engines[it] } ?: return result
        val decision = engine.tick(nowElapsedMs, nowEpochMs)
        if (!decision.displayable && result.displayable) return reject(LiveGuidanceStatus.SUPPRESSED)
        return result // never restore a hidden card without a new validated input
    }

    fun onRouteChanged(generation: Long): LiveGuidanceResult {
        if (generation < routeGeneration || generation < 0) return reject(LiveGuidanceStatus.OLD_ROUTE)
        invalidateRoute(generation)
        return reject(LiveGuidanceStatus.WAITING)
    }

    fun setAvailable(value: Boolean): LiveGuidanceResult {
        available = value
        engines.values.forEach { it.setAvailable(value) }
        activeKey = null
        lastInput = null
        return reject(if (value) LiveGuidanceStatus.WAITING else LiveGuidanceStatus.SOURCE_UNAVAILABLE)
    }

    fun replaceCatalog(catalog: CatalogLoadResult): LiveGuidanceResult {
        catalogResult = catalog
        engines.clear()
        activeKey = null
        lastInput = null
        return reject(if (catalog is CatalogLoadResult.Rejected) LiveGuidanceStatus.CATALOG_REJECTED else LiveGuidanceStatus.WAITING)
    }

    private fun commonGate(now: Long, epoch: Long): LiveGuidanceResult? {
        if (now < 0 || now < lastNow) return reject(LiveGuidanceStatus.CLOCK_REGRESSION)
        lastNow = now
        if (!available) return reject(LiveGuidanceStatus.SOURCE_UNAVAILABLE)
        val catalog = (catalogResult as? CatalogLoadResult.Loaded)?.catalog ?: return reject(LiveGuidanceStatus.CATALOG_REJECTED)
        if (catalog.entries.isEmpty()) return reject(LiveGuidanceStatus.NO_APPROVED_ROADS)
        if (catalog.entries.any { it.segment.synthetic }) return reject(LiveGuidanceStatus.SYNTHETIC_DATA_REJECTED)
        val coverage = catalog.matchingCoverage
        if (catalog.schemaVersion != 2 || coverage == null || coverage.validationErrors(epoch).isNotEmpty() ||
            catalog.candidateRoads.isEmpty()) return reject(LiveGuidanceStatus.EVIDENCE_INVALID)
        if (catalog.entries.any { entry ->
                val candidate = catalog.candidateRoads.singleOrNull { it.id == entry.segment.id }
                candidate == null || candidate.points != entry.centerline.points || candidate.crs != entry.centerline.crs ||
                    entry.centerline.points.any { !coverage.bounds.contains(it) }
            }) return reject(LiveGuidanceStatus.EVIDENCE_INVALID)
        if (catalog.entries.any { it.segment.validationErrors().isNotEmpty() || !it.segment.enabled ||
                epoch < it.segment.evidence.checkedAtEpochMs || epoch >= it.segment.evidence.expiresAtEpochMs ||
                it.centerline.id != it.segment.id }) return reject(LiveGuidanceStatus.EVIDENCE_INVALID)
        return null
    }

    private fun invalidateRoute(generation: Long) {
        routeGeneration = generation
        routeId = null
        engines.values.forEach { it.changeRoute(generation) }
        activeKey = null
        lastInput = null
    }
    private fun reject(status: LiveGuidanceStatus): LiveGuidanceResult = LiveGuidanceResult(status).also { result = it }
}
