package dev.pleos.roadcontext

import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test

/** Collector/association regressions only; these synthetic records are never UI latency evidence. */
class RoadCompositionObservationTest {
    @Test fun boundedCollectorReportsLossAndRejectsRecordsFromEndedSession() {
        val first = RoadCompositionObservation.begin(capacity = 2)
        try {
            repeat(3) { index -> RoadCompositionObservation.record(first, "outer", RoadUiState(title = "$index"), "controls") }
            val snapshot = RoadCompositionObservation.snapshot(first)
            assertEquals(1L, snapshot.droppedRecords)
            assertEquals(listOf("1", "2"), snapshot.records.map { it.state.title })
            assertTrue(snapshot.records[0].recordId < snapshot.records[1].recordId)
        } finally { RoadCompositionObservation.end(first) }
        val second = RoadCompositionObservation.begin()
        try {
            RoadCompositionObservation.record(first, "outer", RoadUiState(), "controls")
            assertTrue(RoadCompositionObservation.snapshot(second).records.isEmpty())
        } finally { RoadCompositionObservation.end(second) }
    }

    @Test fun wrongStateAndOldRecordsCannotCompleteMissingRequiredScope() {
        val session = RoadCompositionObservation.begin()
        try {
            val actual = RoadUiState(title = "actual")
            RoadCompositionObservation.record(session, "guidance", actual, "text")
            val boundary = RoadCompositionObservation.snapshot(session).records.last().recordId
            val start = SystemClock.elapsedRealtimeNanos()
            RoadCompositionObservation.record(session, "outer", actual, "controls")
            RoadCompositionObservation.record(session, "guidance", actual.copy(title = "other"), "text")
            val check = RoadCommitTestSupport.check(RoadCompositionObservation.snapshot(session), boundary,
                start, actual, setOf("outer", "guidance"))
            assertEquals(setOf("guidance"), check.missingScopes)
            assertNull(check.elapsedMs)
            assertFalse(check.withinBudget)
            RoadCompositionObservation.record(session, "guidance", actual, "text")
            val complete = RoadCommitTestSupport.check(RoadCompositionObservation.snapshot(session), boundary,
                start, actual, setOf("outer", "guidance"))
            assertTrue(complete.missingScopes.isEmpty())
            assertEquals(2, complete.records.size)
            assertNotNull(complete.elapsedMs)
        } finally { RoadCompositionObservation.end(session) }
    }

    @Test fun unchangedInputDoesNotClaimFreshFeedbackOrInventZeroDuration() {
        val session = RoadCompositionObservation.begin()
        try {
            val check = RoadCommitTestSupport.check(RoadCompositionObservation.snapshot(session), 0,
                SystemClock.elapsedRealtimeNanos(), RoadUiState(), emptySet())
            assertTrue(check.records.isEmpty())
            assertNull(check.elapsedMs)
            assertFalse(check.json().getBoolean("newFeedbackClaimed"))
        } finally { RoadCompositionObservation.end(session) }
    }
}
