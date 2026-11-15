package dev.pleos.roadcontext

import org.json.JSONArray
import org.json.JSONObject

/** Offline association only: expected values never enter the composition observer. */
internal object RoadCommitTestSupport {
    data class Check(val requiredScopes: Set<String>, val records: List<RoadCompositionObservation.Record>,
        val missingScopes: Set<String>, val elapsedMs: Double?) {
        val withinBudget: Boolean get() = missingScopes.isEmpty() && (elapsedMs == null || elapsedMs <= 250.0)
        fun json(): JSONObject = JSONObject().put("requiredScopes", JSONArray(requiredScopes.toList()))
            .put("recordIds", JSONArray(records.map { it.recordId }))
            .put("missingScopes", JSONArray(missingScopes.toList()))
            .put("compositionAppliedMs", elapsedMs ?: JSONObject.NULL)
            .put("within250Ms", withinBudget)
            .put("newFeedbackClaimed", requiredScopes.isNotEmpty())
    }

    fun check(snapshot: RoadCompositionObservation.Snapshot, afterRecordId: Long, startedNs: Long,
        actualState: RoadUiState, requiredScopes: Set<String>): Check =
        checkMatching(snapshot, afterRecordId, startedNs, requiredScopes) { it == actualState }

    fun checkMatching(snapshot: RoadCompositionObservation.Snapshot, afterRecordId: Long, startedNs: Long,
        requiredScopes: Set<String>, stateMatches: (RoadUiState) -> Boolean): Check {
        val eligible = snapshot.records.filter {
            it.sessionId == snapshot.sessionId && it.recordId > afterRecordId &&
                it.appliedElapsedNs >= startedNs && stateMatches(it.state)
        }
        // First matching record per required scope, not fastest among retries or later samples.
        val matched = requiredScopes.mapNotNull { scope -> eligible.firstOrNull { it.scope == scope } }
        val missing = requiredScopes - matched.map { it.scope }.toSet()
        val elapsed = if (requiredScopes.isEmpty() || missing.isNotEmpty()) null
            else (matched.maxOf { it.appliedElapsedNs } - startedNs) / 1_000_000.0
        return Check(requiredScopes, matched, missing, elapsed)
    }

    fun report(snapshot: RoadCompositionObservation.Snapshot): JSONObject = JSONObject()
        .put("sessionId", snapshot.sessionId).put("droppedRecords", snapshot.droppedRecords)
        .put("records", JSONArray(snapshot.records.map { record ->
            val state = record.state
            JSONObject().put("recordId", record.recordId).put("scope", record.scope)
                .put("appliedElapsedNs", record.appliedElapsedNs).put("branch", record.branch)
                .put("actualState", JSONObject().put("title", state.title).put("message", state.message)
                    .put("phase", state.phase).put("sourceLabel", state.sourceLabel).put("detail", state.detail)
                    .put("distance", state.distance ?: JSONObject.NULL).put("showDiagram", state.showDiagram)
                    .put("simulation", state.simulation).put("playing", state.playing)
                    .put("settingsAllowed", state.settingsAllowed).put("dark", state.dark).put("setup", state.setup)
                    .put("permissionRequestPending", state.permissionRequestPending)
                    .put("diagnostic", state.diagnostic).put("inputSource", state.inputSource?.name ?: JSONObject.NULL)
                    .put("diagramModel", state.diagramModel?.let { JSONObject().put("segmentId", it.segmentId)
                        .put("mergeSide", it.mergeSide.name).put("source", it.source.name)
                        .put("synthetic", it.synthetic).put("approved", it.approved) } ?: JSONObject.NULL))
        }))
        .put("scope", "Actual RoadScreen outer and nested GuidanceContent successful composition SideEffect timestamps. State is captured from actual renderer parameters; no state is injected or invalidated by observation. This is composition application, not drawing or physical presentation. Records can be skipped for unchanged UI. Observer overhead is included, never subtracted.")
}
