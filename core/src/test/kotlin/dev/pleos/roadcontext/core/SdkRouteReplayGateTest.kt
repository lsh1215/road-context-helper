package dev.pleos.roadcontext.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class SdkRouteReplayGateTest {
    private val metersPerDegree = GeoProjector.EARTH_RADIUS_METERS * PI / 180.0
    private val road = RoadPolyline("reference-road", listOf(point(0.0), point(1000.0)))
    private fun point(along: Double, cross: Double = 0.0) = GeoPoint(cross / metersPerDegree, along / metersPerDegree)
    private fun gate() = SdkRouteReplayGate(road, 100.0, 400.0)
    private fun sample(along: Double, time: Long, cross: Double = 0.0, heading: Double? = 90.0) =
        SdkRouteReplaySample(point(along, cross), heading, time, time)
    private fun visibleGate(): SdkRouteReplayGate = gate().also {
        it.update(sample(120.0, 1_000), 1_000)
        assertTrue(it.update(sample(130.0, 2_000), 2_000).visible)
    }

    @Test fun `window enters after two SDK measurements and exits after margin`() {
        val gate = gate()
        assertEquals(SdkRouteReplayState.WAITING, gate.update(sample(50.0, 0), 0).state)
        assertFalse(gate.update(sample(110.0, 1_000), 1_000).visible)
        assertTrue(gate.update(sample(120.0, 2_000), 2_000).visible)
        assertTrue(gate.update(sample(220.0, 4_000), 4_000).visible)
        assertTrue(gate.update(sample(300.0, 6_000), 6_000).visible)
        assertTrue(gate.update(sample(410.0, 9_000), 9_000).visible)
        assertEquals(SdkRouteReplayState.PASSED, gate.update(sample(425.0, 10_000), 10_000).state)
        assertEquals(SdkRouteReplayState.PASSED, gate.update(sample(120.0, 12_000), 12_000).state)
        gate.reset()
        assertFalse(gate.update(sample(120.0, 13_000), 13_000).visible)
        assertTrue(gate.update(sample(130.0, 14_000), 14_000).visible)
    }

    @Test fun `outside road hides immediately and reentry needs two fresh fixes`() {
        val gate = visibleGate()
        assertEquals(SdkRouteReplayState.OUTSIDE, gate.update(sample(140.0, 3_000, cross = 30.0), 3_000).state)
        assertFalse(gate.update(sample(150.0, 4_000), 4_000).visible)
        assertTrue(gate.update(sample(160.0, 5_000), 5_000).visible)
    }

    @Test fun `opposite direction and invalid headings cannot show guidance`() {
        val gate = visibleGate()
        assertEquals(SdkRouteReplayState.WRONG_DIRECTION, gate.update(sample(140.0, 3_000, heading = 270.0), 3_000).state)
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(150.0, 4_000, heading = null), 4_000).state)
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(150.0, 5_000, heading = Double.NaN), 5_000).state)
        assertFalse(gate.update(sample(160.0, 6_000), 6_000).visible)
        assertTrue(gate.update(sample(170.0, 7_000), 7_000).visible)
    }

    @Test fun `stale timer hides and cannot restore without fresh SDK input`() {
        val gate = visibleGate()
        assertTrue(gate.tick(6_999).visible)
        assertEquals(SdkRouteReplayState.STALE, gate.tick(7_000).state)
        assertFalse(gate.tick(8_000).visible)
        assertFalse(gate.update(sample(150.0, 8_000), 8_000).visible)
        assertTrue(gate.update(sample(160.0, 9_000), 9_000).visible)
    }

    @Test fun `measurement silence resets confirmations even without timer ticks`() {
        val gate = visibleGate()
        assertFalse(gate.update(sample(160.0, 8_000), 8_000).visible)
        assertTrue(gate.update(sample(170.0, 9_000), 9_000).visible)
    }

    @Test fun `duplicate measurement cannot satisfy confirmation or restore rejected state`() {
        val gate = gate()
        val first = sample(120.0, 1_000)
        assertFalse(gate.update(first, 1_000).visible)
        assertFalse(gate.update(first.copy(receivedElapsedMs = 2_000), 2_000).visible)
        assertTrue(gate.update(sample(130.0, 3_000), 3_000).visible)
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(200.0, 3_000), 4_000).state)
        assertFalse(gate.update(sample(130.0, 3_000).copy(receivedElapsedMs = 4_000), 4_000).visible)
        assertFalse(gate.update(sample(140.0, 5_000), 5_000).visible)
        assertTrue(gate.update(sample(150.0, 6_000), 6_000).visible)
    }

    @Test fun `old delayed future and regressing clock samples hide guidance`() {
        val gate = visibleGate()
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(120.0, 1_500), 3_000).state)
        assertEquals(SdkRouteReplayState.STALE, gate.update(sample(140.0, 3_000), 8_000).state)
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(150.0, 9_000), 8_000).state)
        assertEquals(SdkRouteReplayState.INVALID, gate.tick(7_000).state)
        assertFalse(gate.update(sample(160.0, 9_000), 9_000).visible)
        assertTrue(gate.update(sample(170.0, 10_000), 10_000).visible)
    }

    @Test fun `jump hides and reacquires only after two plausible subsequent positions`() {
        val gate = visibleGate()
        assertEquals(SdkRouteReplayState.JUMP, gate.update(sample(350.0, 3_000), 3_000).state)
        assertFalse(gate.update(sample(360.0, 4_000), 4_000).visible)
        assertTrue(gate.update(sample(370.0, 5_000), 5_000).visible)
    }

    @Test fun `coordinates and timestamp validity are checked before projection`() {
        val gate = visibleGate()
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(140.0, 3_000).copy(point = GeoPoint(Double.NaN, 0.0)), 3_000).state)
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(140.0, 4_000).copy(measuredElapsedMs = null), 4_000).state)
        assertEquals(SdkRouteReplayState.INVALID, gate.update(sample(140.0, 5_000).copy(receivedElapsedMs = 4_999), 5_000).state)
    }

    @Test fun `late arrival after end does not briefly flash guidance`() {
        val gate = gate()
        assertFalse(gate.update(sample(410.0, 1_000), 1_000).visible)
        assertFalse(gate.update(sample(415.0, 2_000), 2_000).visible)
        assertEquals(SdkRouteReplayState.PASSED, gate.update(sample(425.0, 3_000), 3_000).state)
    }

    @Test fun `bad geometry and windows are rejected at construction`() {
        listOf(
            { SdkRouteReplayGate(road, -1.0, 400.0) },
            { SdkRouteReplayGate(road, 400.0, 100.0) },
            { SdkRouteReplayGate(road, 100.0, 990.0) },
            { SdkRouteReplayGate(road.copy(crs = "LOCAL"), 100.0, 400.0) },
            { SdkRouteReplayGate(road.copy(points = listOf(point(0.0))), 100.0, 400.0) },
        ).forEach { build ->
            try { build(); fail("Invalid configuration accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
