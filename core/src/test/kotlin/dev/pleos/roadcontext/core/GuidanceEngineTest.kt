package dev.pleos.roadcontext.core

import org.junit.Assert.*
import org.junit.Test

class GuidanceEngineTest {
    private fun point(x: Double, time: Long, heading: Double? = 90.0, generation: Long = 0) =
        ProjectedSample(x, 0.0, heading, time, InputSource.REPLAY_SYNTHETIC, generation, "test-route")
    private fun GuidanceEngine.move(x: Double, time: Long) = update(point(x, time), time, 1_000_000L)

    @Test fun `normal trace previews once approaches and passes`() {
        val engine = GuidanceEngine(syntheticSegment())
        assertEquals(GuidanceState.WAITING, engine.move(199.0, 0).state)
        assertEquals(GuidanceState.PREVIEW, engine.move(200.0, 1000).state)
        assertEquals(GuidanceState.PREVIEW, engine.move(449.0, 6000).state)
        assertEquals(GuidanceState.APPROACHING, engine.move(450.0, 7000).state)
        engine.move(600.0, 10_000)
        engine.move(800.0, 15_000)
        val end = engine.move(900.0, 20_000)
        assertEquals(GuidanceState.PASSED, end.state)
        assertEquals(1, end.previewCount)
        assertFalse(end.displayable)
    }

    @Test fun `one hundred repeated coordinates do not reannounce`() {
        val engine = GuidanceEngine(syntheticSegment())
        repeat(100) { engine.move(250.0, it * 100L) }
        assertEquals(1, engine.decision.previewCount)
        assertEquals(GuidanceState.PREVIEW, engine.decision.state)
    }

    @Test fun `repeated sensor measurement with later receipt preserves card without refreshing age`() {
        val e = GuidanceEngine(syntheticSegment())
        val sample = point(250.0, 1000).copy(measurementElapsedMs = 1000)
        e.update(sample, 1000, 1)
        repeat(100) {
            val time = 1001L + it
            val d = e.update(sample.copy(receivedElapsedMs = time), time, 1)
            assertEquals(GuidanceState.PREVIEW, d.state)
            assertEquals(1, d.previewCount)
        }
        assertEquals(GuidanceReason.STALE_LOCATION, e.update(sample.copy(receivedElapsedMs = 11_000), 11_000, 1).reason)
    }

    @Test fun `conflicting measurements sharing a timestamp are rejected`() {
        val e = GuidanceEngine(syntheticSegment())
        val sample = point(250.0, 1000).copy(measurementElapsedMs = 1000)
        e.update(sample, 1000, 1)
        val conflicting = sample.copy(alongMeters = 251.0, receivedElapsedMs = 1001)
        assertEquals(GuidanceReason.OUT_OF_ORDER, e.update(conflicting, 1001, 1).reason)
    }

    @Test fun `delayed route control event cannot crash or rewind generation`() {
        val e = GuidanceEngine(syntheticSegment())
        e.changeRoute(2)
        assertEquals(GuidanceReason.WRONG_ROUTE_GENERATION, e.changeRoute(1).reason)
        assertEquals(GuidanceState.PREVIEW, e.update(point(250.0, 1000, generation = 2), 1000, 1).state)
    }

    @Test fun `reverse direction never previews`() {
        val e = GuidanceEngine(syntheticSegment())
        val d = e.update(point(250.0, 0, 270.0), 0, 1)
        assertEquals(GuidanceReason.WRONG_DIRECTION, d.reason)
        assertEquals(0, d.previewCount)
    }

    @Test fun `curved geometry changes baseline without rewriting vehicle heading`() {
        val e = GuidanceEngine(syntheticSegment())
        val forward = point(250.0, 1000, 0.0).copy(roadHeadingDeg = 0.0)
        assertEquals(GuidanceState.PREVIEW, e.update(forward, 1000, 1).state)
        val reversed = point(260.0, 2000, 180.0).copy(roadHeadingDeg = 0.0)
        assertEquals(GuidanceReason.WRONG_DIRECTION, e.update(reversed, 2000, 1).reason)
    }

    @Test fun `ambiguous road is not guessed`() {
        val e = GuidanceEngine(syntheticSegment())
        assertEquals(GuidanceReason.AMBIGUOUS_ROAD, e.update(point(250.0, 0).copy(ambiguous = true), 0, 1).reason)
    }

    @Test fun `outside corridor is not matched`() {
        val e = GuidanceEngine(syntheticSegment())
        assertEquals(GuidanceReason.OUTSIDE_CORRIDOR, e.update(point(250.0, 0).copy(crossMeters = 15.01), 0, 1).reason)
        assertEquals(GuidanceState.PREVIEW, e.update(point(250.0, 1).copy(crossMeters = 15.0), 1, 1).state)
    }

    @Test fun `stale threshold is inclusive then suppresses`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        assertTrue(e.tick(9_999, 1).displayable)
        assertEquals(GuidanceReason.STALE_LOCATION, e.tick(10_000, 1).reason)
        assertFalse(e.decision.displayable)
    }

    @Test fun `fresh receipt does not renew old measurement`() {
        val e = GuidanceEngine(syntheticSegment())
        val d = e.update(point(250.0, 20_000).copy(measurementElapsedMs = 0), 20_000, 1)
        assertEquals(GuidanceReason.STALE_LOCATION, d.reason)
    }

    @Test fun `GPS jump past segment is not passage evidence`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        val d = e.move(950.0, 1000)
        assertEquals(GuidanceReason.POSITION_JUMP, d.reason)
        assertFalse(d.displayable)
    }

    @Test fun `late start does not replay preview`() {
        val e = GuidanceEngine(syntheticSegment())
        assertEquals(GuidanceReason.LATE_ARRIVAL, e.move(450.0, 0).reason)
        assertEquals(0, e.decision.previewCount)
    }

    @Test fun `route event cancels card and rejects delayed old generation`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        assertFalse(e.changeRoute(1).displayable)
        val old = e.update(point(300.0, 1000), 1000, 1)
        assertEquals(GuidanceReason.WRONG_ROUTE_GENERATION, old.reason)
        assertFalse(old.displayable)
    }

    @Test fun `same route id still changes generation`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        e.changeRoute(1)
        val d = e.update(point(100.0, 1000, generation = 1), 1000, 1)
        assertEquals(GuidanceState.WAITING, d.state)
    }

    @Test fun `changing route id without event clears old card`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        val d = e.update(point(300.0, 1000).copy(routeId = "new-route"), 1000, 1)
        assertEquals(GuidanceReason.ROUTE_CHANGED, d.reason)
        assertFalse(d.displayable)
    }

    @Test fun `invalid values are rejected without throwing`() {
        val samples = listOf(point(Double.NaN, 0), point(Double.POSITIVE_INFINITY, 0), point(250.0, 0, null), point(250.0, 0, 360.0))
        samples.forEach { assertEquals(GuidanceReason.INVALID_LOCATION, GuidanceEngine(syntheticSegment()).update(it, 0, 1).reason) }
    }

    @Test fun `missing route does not fabricate route identity`() {
        val e = GuidanceEngine(syntheticSegment())
        assertEquals(GuidanceReason.MISSING_ROUTE, e.update(point(250.0, 0).copy(routeId = null), 0, 1).reason)
    }

    @Test fun `old samples cannot replace newer state`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 1000)
        assertEquals(GuidanceReason.OUT_OF_ORDER, e.update(point(200.0, 900), 1100, 1).reason)
    }

    @Test fun `future timestamps are invalid`() {
        val e = GuidanceEngine(syntheticSegment())
        assertEquals(GuidanceReason.INVALID_LOCATION, e.update(point(250.0, 200), 100, 1).reason)
    }

    @Test fun `monotonic clock regression suppresses while wall clock does not drive cooldown`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 1000)
        assertEquals(GuidanceReason.CLOCK_REGRESSION, e.tick(999, 1).reason)
        // Wall clock changes only affect explicit evidence expiry, not sample ages.
        assertEquals(GuidanceState.PREVIEW, e.update(point(300.0, 2000), 2000, 100).state)
    }

    @Test fun `source reset purges prior session`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        val d = e.reset(InputSource.REAL_SDK)
        assertEquals(0, d.previewCount)
        assertFalse(d.displayable)
        assertEquals(GuidanceReason.SYNTHETIC_DATA_IN_REAL_MODE, e.tick(1, 1).reason)
    }

    @Test fun `captured SDK data is not live and cannot use synthetic map`() {
        val e = GuidanceEngine(syntheticSegment(), source = InputSource.REPLAY_CAPTURED)
        assertEquals(GuidanceReason.SYNTHETIC_DATA_IN_REAL_MODE, e.tick(0, 1).reason)
        assertEquals(InputSource.REPLAY_CAPTURED, e.decision.source)
    }

    @Test fun `data version change removes old card immediately`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        val d = e.replaceSegment(syntheticSegment().copy(version = "2"))
        assertEquals(GuidanceReason.DATA_CHANGED, d.reason)
        assertEquals("2", d.segmentVersion)
        assertFalse(d.displayable)
    }

    @Test fun `data expiry includes equality boundary`() {
        val s = syntheticSegment().let { it.copy(evidence = it.evidence.copy(expiresAtEpochMs = 100)) }
        val e = GuidanceEngine(s)
        assertEquals(GuidanceState.PREVIEW, e.update(point(250.0, 0), 0, 99).state)
        assertEquals(GuidanceReason.DATA_EXPIRED, e.tick(1, 100).reason)
    }

    @Test fun `invalid topology and unapproved evidence fail closed`() {
        val s = syntheticSegment()
        listOf(s.copy(mergeEndMeters = 500.0), s.copy(headingDeg = -1.0), s.copy(evidence = s.evidence.copy(approved = false))).forEach {
            assertEquals(GuidanceReason.DATA_INVALID, GuidanceEngine(it).tick(0, 1).reason)
        }
    }

    @Test fun `unavailable must get a new sample before displaying again`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        assertEquals(GuidanceState.UNAVAILABLE, e.setAvailable(false).state)
        assertEquals(GuidanceState.WAITING, e.setAvailable(true).state)
        assertFalse(e.tick(100, 1).displayable)
    }

    @Test fun `preview and approach jitter do not regress stage`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(200.0, 0)
        assertEquals(GuidanceState.PREVIEW, e.move(199.0, 1000).state)
        e.move(450.0, 6000)
        assertEquals(GuidanceState.APPROACHING, e.move(449.0, 7000).state)
        assertEquals(1, e.decision.previewCount)
    }

    @Test fun `new pass only after passage and cooldown`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(200.0, 0)
        e.move(450.0, 5000)
        e.move(700.0, 10_000)
        e.move(900.0, 15_000)
        assertEquals(GuidanceState.WAITING, e.move(100.0, 299_999).state)
        assertEquals(GuidanceState.WAITING, e.move(100.0, 300_000).state)
        assertEquals(2, e.move(200.0, 301_000).previewCount)
    }

    @Test fun `transient ambiguity cannot regress an approaching pass to preview`() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(200.0, 0)
        e.move(450.0, 5000)
        e.update(point(449.0, 6000).copy(ambiguous = true), 6000, 1)
        assertEquals(GuidanceState.APPROACHING, e.move(449.0, 7000).state)
        assertEquals(1, e.decision.previewCount)
    }

    @Test fun `deterministic replay decisions match exactly`() {
        fun replay(): List<GuidanceDecision> {
            val e = GuidanceEngine(syntheticSegment())
            return (0..1000).map { e.move(it.toDouble(), it * 50L) }
        }
        val expected = replay()
        repeat(3) { assertEquals(expected, replay()) }
    }

    @Test fun `valid real metadata can be used only with matching source`() {
        val s = syntheticSegment().let { it.copy(id = "test-real-metadata", synthetic = false,
            evidence = it.evidence.copy(originalCrs = "EPSG:4326", sourceUrl = "https://example.test/evidence")) }
        val e = GuidanceEngine(s, source = InputSource.REAL_SDK)
        assertEquals(GuidanceReason.SOURCE_MISMATCH, e.update(point(250.0, 0), 0, 1).reason)
        assertEquals(GuidanceState.PREVIEW, e.update(point(250.0, 1).copy(source = InputSource.REAL_SDK), 1, 1).state)
    }
}
