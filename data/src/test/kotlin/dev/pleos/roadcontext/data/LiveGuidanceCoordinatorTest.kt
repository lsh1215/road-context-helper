package dev.pleos.roadcontext.data

import dev.pleos.roadcontext.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

/** All geometry/provenance below is FICTIONAL TEST-ONLY data, not an approved actual road. */
class LiveGuidanceCoordinatorTest {
    private val epoch = 1_000_000L
    private val metersPerDegree = GeoProjector.EARTH_RADIUS_METERS * PI / 180.0
    private fun entry(id: String = "fictional-test-only", northOffset: Double = 0.0): RuntimeRoadEntry {
        val evidence = RoadEvidence(true, "https://example.test/fictional-only", 0, 2_000_000,
            "EPSG:4326", "FICTIONAL-UNIT-TEST-NOT-REAL-ROAD", "test-only")
        val segment = RoadSegment(id, "test", 0.0, 600.0, 800.0, 90.0, 15.0, false, evidence)
        val centerline = RoadPolyline(id, listOf(GeoPoint(northOffset, 0.0), GeoPoint(northOffset, 0.02)))
        return RuntimeRoadEntry(segment, centerline, MergeSide.LEFT,
            SourceProvenance(evidence.sourceUrl, "test-only", "a".repeat(64), "Fictional test", "test-only", 0, 2_000_000))
    }
    private fun coverage() = MatchingCoverage(GeographicBounds(-0.05, -0.05, 0.05, 0.05),
        SourceProvenance("https://example.test/fictional-coverage", "test-only", "b".repeat(64),
            "Fictional unit-test coverage", "test-only", 0, 2_000_000), "TEST-ONLY-NOT-A-REAL-ROAD", true)
    private fun catalog(vararg roads: RuntimeRoadEntry) = CatalogLoadResult.Loaded(
        RuntimeRoadCatalog("test-only", roads.toList(), coverage(), roads.map { it.centerline }))
    private fun input(x: Double = 250.0, time: Long = 0) = LiveLocationInput(
        GeoPoint(0.0, x / metersPerDegree), 90.0, time, time,
        measurementClockValidated = true, contextValidated = true, positionUncertaintyMeters = 2.0,
        routeId = "fictional-route", routeGeneration = 1,
    )
    private fun LiveGuidanceCoordinator.move(x: Double, time: Long) = update(input(x, time), time, epoch + time)

    @Test fun `validated input projects into engine preview approach and passage`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        assertEquals(LiveGuidanceStatus.WAITING, c.move(0.0, 0).status)
        val preview = c.move(250.0, 5000)
        assertTrue(preview.displayable)
        assertEquals(InputSource.REAL_SDK, preview.decision!!.source)
        assertEquals("fictional-test-only", preview.entry!!.segment.id)
        assertEquals(250.0, preview.projection!!.alongMeters, 0.001)
        assertEquals(GuidanceState.APPROACHING, c.move(500.0, 10_000).decision!!.state)
        c.move(750.0, 15_000)
        val passed = c.move(950.0, 20_000)
        assertEquals(LiveGuidanceStatus.PASSED, passed.status)
        assertEquals(1, passed.decision!!.previewCount)
        assertFalse(passed.displayable)
    }

    @Test fun `overlapping roads hide guidance and associations`() {
        val c = LiveGuidanceCoordinator(catalog(entry("ground"), entry("bridge")))
        val result = c.move(250.0, 0)
        assertEquals(LiveGuidanceStatus.ROAD_AMBIGUOUS, result.status)
        assertNull(result.decision)
        assertNull(result.entry)
    }

    @Test fun `sensor uncertainty envelope must fit the road corridor`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        val r = c.update(input().copy(positionUncertaintyMeters = 20.0), 0, epoch)
        assertEquals(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD, r.status)
    }

    @Test fun `unvalidated measurement clock never passes using receipt time alone`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        assertEquals(LiveGuidanceStatus.CLOCK_UNVERIFIED, c.update(input().copy(measurementClockValidated = false), 0, epoch).status)
        assertEquals(LiveGuidanceStatus.CLOCK_UNVERIFIED, c.update(input().copy(measuredElapsedMs = null), 0, epoch).status)
    }

    @Test fun `location and navigation context must be explicitly coherent`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        val r = c.update(input().copy(contextValidated = false), 0, epoch)
        assertEquals(LiveGuidanceStatus.CONTEXT_UNVERIFIED, r.status)
        assertFalse(r.displayable)
    }

    @Test fun `missing heading route and uncertainty do not get fabricated defaults`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        assertEquals(LiveGuidanceStatus.ROUTE_UNAVAILABLE, c.update(input().copy(routeId = null), 0, epoch).status)
        assertEquals(LiveGuidanceStatus.HEADING_UNAVAILABLE, c.update(input().copy(vehicleHeadingDeg = null), 0, epoch).status)
        assertEquals(LiveGuidanceStatus.UNCERTAINTY_UNVERIFIED, c.update(input().copy(positionUncertaintyMeters = null), 0, epoch).status)
    }

    @Test fun `stale measurements and future timestamps reject and remove old card`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        assertTrue(c.move(250.0, 0).displayable)
        assertEquals(LiveGuidanceStatus.STALE_LOCATION, c.tick(10_000, epoch + 10_000).status)
        assertNull(c.result.entry)
        assertEquals(LiveGuidanceStatus.LOCATION_INVALID, c.update(input(time = 20_000), 10_001, epoch).status)
    }

    @Test fun `out of supported geometry does not keep old card`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        c.move(250.0, 0)
        val r = c.update(input(time = 1000).copy(point = GeoPoint(1.0, 1.0)), 1000, epoch)
        assertEquals(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD, r.status)
        assertFalse(r.displayable)
        assertFalse(c.tick(2000, epoch).displayable)
    }

    @Test fun `old route generation is rejected after explicit event`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        c.move(250.0, 0)
        assertFalse(c.onRouteChanged(2).displayable)
        assertEquals(LiveGuidanceStatus.OLD_ROUTE, c.move(300.0, 1000).status)
    }

    @Test fun `same generation different route id cancels existing guidance`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        c.move(250.0, 0)
        val r = c.update(input(time = 1000).copy(routeId = "changed"), 1000, epoch)
        assertEquals(LiveGuidanceStatus.ROUTE_CHANGED, r.status)
        assertNull(r.decision)
    }

    @Test fun `rejected empty expired and synthetic catalogs fail explicitly`() {
        assertEquals(LiveGuidanceStatus.CATALOG_REJECTED, LiveGuidanceCoordinator(CatalogLoadResult.Rejected(listOf("test"))).move(250.0, 0).status)
        assertEquals(LiveGuidanceStatus.NO_APPROVED_ROADS, LiveGuidanceCoordinator(catalog()).move(250.0, 0).status)
        val expired = entry().let { it.copy(segment = it.segment.copy(evidence = it.segment.evidence.copy(expiresAtEpochMs = epoch))) }
        assertEquals(LiveGuidanceStatus.EVIDENCE_INVALID, LiveGuidanceCoordinator(catalog(expired)).move(250.0, 0).status)
        val synthetic = entry().let { it.copy(segment = syntheticSegment()) }
        assertEquals(LiveGuidanceStatus.SYNTHETIC_DATA_REJECTED, LiveGuidanceCoordinator(catalog(synthetic)).move(250.0, 0).status)
    }

    @Test fun `catalog replacement and disconnect invalidate visible entry`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        c.move(250.0, 0)
        assertFalse(c.replaceCatalog(catalog()).displayable)
        assertEquals(LiveGuidanceStatus.NO_APPROVED_ROADS, c.tick(1000, epoch).status)
        c.replaceCatalog(catalog(entry()))
        assertEquals(LiveGuidanceStatus.SOURCE_UNAVAILABLE, c.setAvailable(false).status)
        assertFalse(c.setAvailable(true).displayable)
        assertFalse(c.tick(2000, epoch).displayable)
    }

    @Test fun `curved road uses geometry baseline but never substitutes vehicle heading`() {
        val e = entry().let { it.copy(centerline = RoadPolyline(it.segment.id, listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0025), GeoPoint(0.02, 0.0025)))) }
        val c = LiveGuidanceCoordinator(catalog(e))
        val forward = input().copy(point = GeoPoint(0.001, 0.0025), vehicleHeadingDeg = 0.0)
        assertTrue(c.update(forward, 0, epoch).displayable)
        val reversed = forward.copy(vehicleHeadingDeg = 180.0, receivedElapsedMs = 1000, measuredElapsedMs = 1000)
        val result = c.update(reversed, 1000, epoch)
        assertEquals(LiveGuidanceStatus.SUPPRESSED, result.status)
        assertEquals(GuidanceReason.WRONG_DIRECTION, result.decision!!.reason)
    }

    @Test fun `clock regression rejects instead of replaying prior card`() {
        val c = LiveGuidanceCoordinator(catalog(entry()))
        c.move(250.0, 1000)
        assertEquals(LiveGuidanceStatus.CLOCK_REGRESSION, c.tick(999, epoch).status)
        assertNull(c.result.decision)
    }

    @Test fun `unsupported overlapping road prevents false unique supported match`() {
        val supported = entry()
        val unsupported = supported.centerline.copy(id = "unsupported-overpass", levelTag = "upper")
        val loaded = catalog(supported).let { CatalogLoadResult.Loaded(it.catalog.copy(candidateRoads = listOf(supported.centerline, unsupported))) }
        val result = LiveGuidanceCoordinator(loaded).move(250.0, 0)
        assertEquals(LiveGuidanceStatus.ROAD_AMBIGUOUS, result.status)
        assertNull(result.entry)
        assertNull(result.decision)
    }

    @Test fun `unsupported parallel road inside uncertainty remains competing candidate`() {
        val supported = entry()
        val unsupported = entry("unsupported-parallel", northOffset = 0.00001).centerline
        val loaded = catalog(supported).let { CatalogLoadResult.Loaded(it.catalog.copy(candidateRoads = listOf(supported.centerline, unsupported))) }
        assertEquals(LiveGuidanceStatus.ROAD_AMBIGUOUS, LiveGuidanceCoordinator(loaded).move(250.0, 0).status)
    }

    @Test fun `unique unsupported candidate clears formerly displayed supported card`() {
        val supported = entry()
        val unsupported = entry("unsupported-parallel", northOffset = 0.0001).centerline
        val loaded = catalog(supported).let { CatalogLoadResult.Loaded(it.catalog.copy(candidateRoads = listOf(supported.centerline, unsupported))) }
        val c = LiveGuidanceCoordinator(loaded)
        assertTrue(c.move(250.0, 0).displayable)
        val otherRoad = input(time = 1000).copy(point = GeoPoint(0.0001, 250.0 / metersPerDegree))
        assertEquals(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD, c.update(otherRoad, 1000, epoch).status)
        assertNull(c.result.decision)
        assertFalse(c.tick(2000, epoch).displayable)
    }

    @Test fun `no coverage incomplete coverage and legacy supported catalog cannot guide`() {
        val base = catalog(entry()).catalog
        val invalid = listOf(
            base.copy(matchingCoverage = null),
            base.copy(matchingCoverage = coverage().copy(competingRoadsReviewed = false)),
            base.copy(candidateRoads = emptyList()),
            base.copy(schemaVersion = 1),
        )
        invalid.forEach { c ->
            assertEquals(LiveGuidanceStatus.EVIDENCE_INVALID,
                LiveGuidanceCoordinator(CatalogLoadResult.Loaded(c)).move(250.0, 0).status)
        }
    }

    @Test fun `fix center within coverage is insufficient when search envelope crosses edge`() {
        val base = catalog(entry()).catalog
        val narrow = coverage().copy(bounds = GeographicBounds(-0.00001, -0.05, 0.05, 0.05))
        assertTrue(narrow.bounds.contains(input().point))
        val c = LiveGuidanceCoordinator(CatalogLoadResult.Loaded(base.copy(matchingCoverage = narrow)))
        assertEquals(LiveGuidanceStatus.OUTSIDE_SUPPORTED_ROAD, c.move(250.0, 0).status)
    }

    @Test fun `expired coverage invalidates earlier approved road guidance`() {
        val base = catalog(entry()).catalog
        val expired = coverage().copy(provenance = coverage().provenance.copy(expiresAtEpochMs = epoch + 1000))
        val c = LiveGuidanceCoordinator(CatalogLoadResult.Loaded(base.copy(matchingCoverage = expired)))
        assertTrue(c.move(250.0, 0).displayable)
        assertEquals(LiveGuidanceStatus.EVIDENCE_INVALID, c.tick(1000, epoch + 1000).status)
        assertNull(c.result.entry)
    }

    @Test fun `supported centerline must be present unchanged among full candidates`() {
        val base = catalog(entry()).catalog
        listOf(
            base.copy(candidateRoads = listOf(base.candidateRoads.single().copy(id = "other-road"))),
            base.copy(candidateRoads = listOf(base.candidateRoads.single().copy(points = base.candidateRoads.single().points.reversed()))),
        ).forEach { c ->
            assertEquals(LiveGuidanceStatus.EVIDENCE_INVALID,
                LiveGuidanceCoordinator(CatalogLoadResult.Loaded(c)).move(250.0, 0).status)
        }
    }
}
