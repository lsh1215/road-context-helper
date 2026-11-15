package dev.pleos.roadcontext.core

import org.junit.Assert.*
import org.junit.Test

/** Recovery preserves an existing pass; it does not waive cooldown or route/data validation. */
class AvailabilityRecoveryTest {
    private val epoch = 1_000L
    private fun point(x: Double, time: Long, source: InputSource = InputSource.REPLAY_SYNTHETIC) =
        ProjectedSample(x, 0.0, 90.0, time, source, 0, "same-route", measurementElapsedMs = time)
    private fun GuidanceEngine.move(x: Double, time: Long) = update(point(x, time), time, epoch)
    private fun pause(e: GuidanceEngine) {
        assertFalse(e.setAvailable(false).displayable)
        assertEquals(GuidanceState.UNAVAILABLE, e.decision.state)
        assertFalse(e.setAvailable(true).displayable)
        assertEquals(GuidanceState.WAITING, e.decision.state)
    }

    @Test fun freshContinuousSamePassResumesWithoutIssuingAnotherPreview() {
        val e = GuidanceEngine(syntheticSegment())
        val original = e.move(250.0, 0)
        pause(e)
        assertFalse(e.tick(500, epoch).displayable)
        val resumed = e.move(260.0, 1_000)
        assertEquals(GuidanceState.PREVIEW, resumed.state)
        assertEquals(original.previewCount, resumed.previewCount)
        assertEquals(original.passId, resumed.passId)
    }

    @Test fun approachingPassResumesAtApproachingStage() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        val original = e.move(460.0, 5_000)
        pause(e)
        val resumed = e.move(470.0, 6_000)
        assertEquals(GuidanceState.APPROACHING, resumed.state)
        assertEquals(original.passId, resumed.passId)
        assertEquals(1, resumed.previewCount)
    }

    @Test fun exactTenSecondObservationGapDoesNotResumeEvenWithFreshNewMeasurement() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        val result = e.move(260.0, 10_000)
        assertFalse(result.displayable)
        assertEquals(GuidanceReason.COOLDOWN, result.reason)
        assertEquals(1, result.previewCount)
    }

    @Test fun newlyReceivedOldMeasurementDoesNotResume() {
        val e = GuidanceEngine(syntheticSegment())
        val initial = point(250.0, 0)
        e.update(initial, 0, epoch)
        pause(e)
        val repeated = initial.copy(receivedElapsedMs = 1_000)
        assertFalse(e.update(repeated, 1_000, epoch).displayable)
        assertEquals(1, e.decision.previewCount)
        assertTrue(e.move(260.0, 2_000).displayable)
        assertEquals(1, e.decision.previewCount)
    }

    @Test fun staleSampleCannotResume() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        val result = e.update(point(260.0, 1_000), 11_000, epoch)
        assertEquals(GuidanceReason.STALE_LOCATION, result.reason)
        assertFalse(result.displayable)
    }

    @Test fun reverseAmbiguousOutsideAndJumpSamplesRemainRejected() {
        val bad = listOf(
            point(260.0, 1_000).copy(headingDeg = 270.0),
            point(260.0, 1_000).copy(ambiguous = true),
            point(260.0, 1_000).copy(crossMeters = 100.0),
            point(850.0, 1_000),
        )
        bad.forEach { sample ->
            val e = GuidanceEngine(syntheticSegment())
            e.move(250.0, 0)
            pause(e)
            val result = e.update(sample, 1_000, epoch)
            assertFalse(result.displayable)
            assertEquals(1, result.previewCount)
        }
    }

    @Test fun backwardPositionDoesNotProveContinuousForwardRecovery() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        assertFalse(e.move(240.0, 1_000).displayable)
        assertEquals(1, e.decision.previewCount)
    }

    @Test fun routeGenerationChangeDiscardsSuspendedPass() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        e.changeRoute(1)
        val result = e.update(point(260.0, 1_000).copy(routeGeneration = 1), 1_000, epoch)
        assertFalse(result.displayable)
        assertEquals(GuidanceReason.COOLDOWN, result.reason)
    }

    @Test fun routeIdChangeDiscardsSuspendedPass() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        assertEquals(GuidanceReason.ROUTE_CHANGED, e.update(point(260.0, 1_000).copy(routeId = "other"), 1_000, epoch).reason)
        assertFalse(e.update(point(270.0, 2_000).copy(routeId = "other"), 2_000, epoch).displayable)
    }

    @Test fun dataVersionChangeDiscardsSuspendedPass() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        e.replaceSegment(syntheticSegment().copy(version = "2"))
        assertFalse(e.move(260.0, 1_000).displayable)
        assertEquals(1, e.decision.previewCount)
    }

    @Test fun sourceChangeNeverRevivesSyntheticPass() {
        val e = GuidanceEngine(syntheticSegment())
        e.move(250.0, 0)
        pause(e)
        e.switchSource(InputSource.REAL_SDK, 1)
        assertEquals(GuidanceReason.SYNTHETIC_DATA_IN_REAL_MODE,
            e.update(point(260.0, 1_000, InputSource.REAL_SDK).copy(routeGeneration = 1), 1_000, epoch).reason)
        assertFalse(e.decision.displayable)
    }

    @Test fun realSourceWithoutMeasuredClockCannotResumeFromReceiptFreshnessAlone() {
        val segment = syntheticSegment().let { it.copy(synthetic = false,
            evidence = it.evidence.copy(originalCrs = "EPSG:4326", sourceUrl = "https://example.test/test-only")) }
        val e = GuidanceEngine(segment, source = InputSource.REAL_SDK)
        e.update(point(250.0, 0, InputSource.REAL_SDK).copy(measurementElapsedMs = null), 0, epoch)
        pause(e)
        assertFalse(e.update(point(260.0, 1_000, InputSource.REAL_SDK).copy(measurementElapsedMs = null), 1_000, epoch).displayable)
    }

    @Test fun realSourceContractWithFreshMeasuredClockResumesTestOnlyPass() {
        // Source mode exercises the real-input contract; this is not an actual SDK or road test.
        val segment = syntheticSegment().let { it.copy(synthetic = false,
            evidence = it.evidence.copy(originalCrs = "EPSG:4326", sourceUrl = "https://example.test/test-only")) }
        val e = GuidanceEngine(segment, source = InputSource.REAL_SDK)
        val original = e.update(point(250.0, 0, InputSource.REAL_SDK), 0, epoch)
        pause(e)
        val resumed = e.update(point(260.0, 1_000, InputSource.REAL_SDK), 1_000, epoch)
        assertTrue(resumed.displayable)
        assertEquals(original.passId, resumed.passId)
        assertEquals(1, resumed.previewCount)
    }

    @Test fun repeatedAvailabilityNotificationsDoNotInvalidateAnActivePass() {
        val e = GuidanceEngine(syntheticSegment())
        val initial = e.move(250.0, 0)
        assertEquals(initial, e.setAvailable(true))
        e.setAvailable(false)
        e.setAvailable(false)
        e.setAvailable(true)
        assertTrue(e.move(260.0, 1_000).displayable)
        assertEquals(initial.passId, e.decision.passId)
        assertEquals(1, e.decision.previewCount)
    }
}
