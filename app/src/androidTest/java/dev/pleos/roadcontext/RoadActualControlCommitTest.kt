package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Existing actual controls only. No injected UiState, fake UI, OS grant, or UX-policy mutation. */
class RoadActualControlCommitTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun availableParkedControlsApplyEveryDesignatedChangedStateWithin250Ms() {
        DeviceTestSupport.requireActualLocationPermission()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[RoadViewModel::class.java] }
        compose.waitUntil(15_000) { model.ui.settingsAllowed }
        // Fixture positioning only. Measured transitions below invoke actual rendered callbacks.
        compose.runOnIdle { model.live() }
        compose.waitForIdle()
        val initialDark = compose.runOnIdle { model.ui.dark }
        val session = RoadCompositionObservation.begin()
        val samples = JSONArray()
        val checks = mutableListOf<RoadCommitTestSupport.Check>()
        var passed = false
        fun action(name: String, tag: String, scopes: Set<String>,
            stateMatches: (RoadUiState) -> Boolean, nativeOracle: () -> Unit) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
            val boundary = RoadCompositionObservation.snapshot(session).records.lastOrNull()?.recordId ?: 0L
            var start = 0L
            var accepted = false
            var after: RoadUiState? = null
            var before: RoadUiState? = null
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { callback ->
                before = model.ui
                start = SystemClock.elapsedRealtimeNanos()
                accepted = callback()
                after = model.ui
            }
            // Wait only to read observations and assert the independent UI oracle. The observer's
            // own monotonic timestamps are the endpoint, not the completion of this test wait.
            compose.waitForIdle()
            val observation = RoadCompositionObservation.snapshot(session)
            val check = RoadCommitTestSupport.checkMatching(observation, boundary, start, scopes, stateMatches)
            checks += check
            samples.put(JSONObject().put("index", samples.length()).put("action", name).put("tag", tag)
                .put("callbackStartedElapsedNs", start).put("callbackAccepted", accepted)
                .put("callbackStateMatches", after?.let(stateMatches) ?: false)
                .put("compositionObservation", check.json()))
            assertTrue("Actual $name callback rejected input", accepted)
            assertTrue("Actual $name callback produced unexpected model state", stateMatches(requireNotNull(after)))
            if (scopes.isEmpty()) assertEquals("No-op must retain the existing state", before, after)
            nativeOracle()
        }
        val both = setOf("outer", "guidance")
        try {
            // Predeclared five repetitions, ten existing-control inputs each: 45 changed, 5 no-op.
            repeat(5) {
                action("theme_toggle", "theme", both, { !it.simulation && it.dark != initialDark }) {
                    compose.onNodeWithTag("theme").assertTextEquals(if (!initialDark) "밝은 화면" else "어두운 화면")
                }
                action("theme_restore", "theme", both, { !it.simulation && it.dark == initialDark }) {
                    compose.onNodeWithTag("theme").assertTextEquals(if (initialDark) "밝은 화면" else "어두운 화면")
                }
                action("demo", "demo", both, { it.simulation && !it.playing && it.phase == "시연 준비" }) {
                    compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
                    compose.onNodeWithTag("title").assertTextEquals("진입로를 미리 살펴보세요")
                }
                action("play", "play", setOf("outer"), { it.simulation && it.playing }) {
                    compose.onNodeWithTag("play").assertTextEquals("일시정지")
                }
                action("pause", "play", setOf("outer"), { it.simulation && !it.playing }) {
                    compose.onNodeWithTag("play").assertTextEquals("시연 재생")
                    // A slow observation allowing the real 3s autoplay to advance invalidates the
                    // fixed next-input fixture. Fail visibly; do not silently reset or retry it.
                    compose.onNodeWithTag("phase").assertTextEquals("시연 준비")
                }
                action("next_0m", "next", both, { it.simulation && it.phase == "안내 대기" && it.distance == null }) {
                    compose.onNodeWithTag("title").assertTextEquals("조금 뒤, 진입로를 만납니다")
                }
                action("next_100m_unchanged", "next", emptySet(), { it.simulation && it.phase == "안내 대기" && it.distance == null }) {
                    compose.onNodeWithTag("title").assertTextEquals("조금 뒤, 진입로를 만납니다")
                }
                action("next_200m", "next", both, { it.simulation && it.phase == "미리 보는 도로 구조" && it.distance == "약 400m 앞" }) {
                    compose.onNodeWithTag("title").assertTextEquals("진입 차로는\n앞까지 이어집니다")
                    compose.onNodeWithTag("diagram").assertIsDisplayed()
                }
                action("reset", "reset", both, { it.simulation && !it.playing && it.phase == "시연 준비" && !it.showDiagram }) {
                    compose.onNodeWithTag("title").assertTextEquals("진입로를 미리 살펴보세요")
                    compose.onNodeWithTag("phase").assertTextEquals("시연 준비")
                }
                // Real SDK callbacks may coalesce the initial live waiting state before rendering.
                // Match the required live-mode outcome in actual observed states, without forcing
                // a transient exact state or inventing a successful real-location claim.
                action("live", "live", both, { !it.simulation && !it.setup && it.inputSource == DiagramSource.REAL_SDK }) {
                    compose.onNodeWithTag("demo").assertIsDisplayed()
                    compose.onNodeWithTag("theme").assertIsDisplayed()
                }
            }
            assertEquals(50, samples.length())
            assertEquals(45, checks.count { it.requiredScopes.isNotEmpty() })
            assertEquals(0L, RoadCompositionObservation.snapshot(session).droppedRecords)
            assertTrue("Every changed control input must apply required scopes within 250ms; failures=" +
                checks.withIndex().filter { !it.value.withinBudget }.map { it.index }, checks.all { it.withinBudget })
            passed = true
        } finally {
            val observation = RoadCompositionObservation.end(session)
            DeviceTestSupport.save("actual-control-composition", JSONObject().put("passed", passed)
                .put("sampleTarget", 50).put("changedTarget", 45).put("thresholdEveryChangedInputMs", 250)
                .put("samples", samples).put("compositionDiagnostics", RoadCommitTestSupport.report(observation))
                .put("scope", "Actual MainActivity parked controls under existing granted location permission; actual callbacks to successful outer/nested composition application. Semantic oracles verify visible control/content results. No input-dispatch, draw or physical-presentation timing claim. One predeclared 50-input sequence; latency failures retained, no selection or retry.")
                .put("notRun", JSONArray(listOf("Permission request/OS dialog/permission result latency: existing permission retained, OS UI not changed",
                    "Pending long-operation feedback and duplicate-submission prevention: no corresponding available control flow demonstrated",
                    "Actual driving-restriction transition: parked vehicle policy retained; no fabricated UX restriction input",
                    "Hidden controls in compact windows: test requires the actual wide emulator layout and does not add menus")))
                .put("ac24Overall", "PARTIAL_ONLY: this available-control matrix cannot close unexecuted permission/pending/UX conditions."))
        }
    }
}
