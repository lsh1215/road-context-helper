package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/** Debug-only opt-in observation. No Compose state, rendering, input, or model mutation. */
internal object RoadCompositionObservation {
    data class Record(
        val sessionId: Long,
        val recordId: Long,
        val scope: String,
        val state: RoadUiState,
        val branch: String,
        val appliedElapsedNs: Long,
    )
    data class Snapshot(val sessionId: Long, val records: List<Record>, val droppedRecords: Long)
    private data class Session(val id: Long, val capacity: Int,
        val records: ArrayDeque<Record> = ArrayDeque(), var dropped: Long = 0)
    private val lock = Any()
    private var nextSession = 0L
    private var nextRecord = 0L
    @Volatile private var active: Session? = null

    fun begin(capacity: Int = 20_000): Long = synchronized(lock) {
        require(capacity in 1..100_000)
        check(active == null) { "An observation session is already active" }
        Session(++nextSession, capacity).also { active = it }.id
    }

    fun snapshot(sessionId: Long): Snapshot = synchronized(lock) {
        val session = requireNotNull(active) { "No active observation session" }
        check(session.id == sessionId)
        Snapshot(session.id, session.records.toList(), session.dropped)
    }

    fun end(sessionId: Long): Snapshot = synchronized(lock) {
        val result = snapshot(sessionId)
        active = null
        result
    }

    internal fun activeSessionId(): Long? = active?.id

    internal fun record(sessionId: Long, scope: String, state: RoadUiState, branch: String) {
        // Timestamp precedes bookkeeping. Retain the composition's immutable state, never read
        // model.ui here (which might already refer to a different update).
        val applied = SystemClock.elapsedRealtimeNanos()
        synchronized(lock) {
            val session = active ?: return
            if (session.id != sessionId) return // An old queued effect cannot enter a new session.
            if (session.records.size == session.capacity) {
                session.records.removeFirst()
                session.dropped++
            }
            session.records.addLast(Record(session.id, ++nextRecord, scope, state, branch, applied))
        }
    }
}

@Composable
internal fun ObserveRoadComposition(state: RoadUiState, scope: String, branch: String) {
    // This plain-memory read does not subscribe to anything and cannot trigger recomposition.
    // Tests start observation before a natural product input; registration never refreshes UI.
    val sessionId = RoadCompositionObservation.activeSessionId() ?: return
    SideEffect { RoadCompositionObservation.record(sessionId, scope, state, branch) }
}
