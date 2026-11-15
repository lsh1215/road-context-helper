package dev.pleos.roadcontext

import android.os.Debug
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.ceil

/** Actual synthetic product path: button callback -> next -> engine -> model -> observed semantics. */
class RoadReplayPipelinePerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    // Independent expectations for the existing fictional local-meter road, not engine-derived output.
    private data class Expected(
        val alongMeters: Int,
        val phase: String,
        val title: String,
        val message: String,
        val distance: String?,
        val diagram: Boolean,
        val visibleChange: Boolean = true,
    )

    /** Complete immutable observation. It is captured without consulting the expected state. */
    private data class ObservedNode(
        val tag: String?,
        val texts: List<String>,
        val clippedLeft: Float,
        val clippedTop: Float,
        val clippedRight: Float,
        val clippedBottom: Float,
    ) {
        val hasVisibleBounds: Boolean get() = clippedRight > clippedLeft && clippedBottom > clippedTop
    }

    private data class SemanticCapture(
        val nodes: List<ObservedNode>,
        val fetchStartedElapsedNs: Long,
        val fetchCompletedElapsedNs: Long,
        val copyCompletedElapsedNs: Long,
        val fetchThreadCpuNs: Long,
        val copyThreadCpuNs: Long,
    )

    private val expected = listOf(
        Expected(0, "안내 대기", "조금 뒤, 진입로를 만납니다", "지원 구간에 가까워지면 구조를 보여드립니다.", null, false),
        Expected(100, "안내 대기", "조금 뒤, 진입로를 만납니다", "지원 구간에 가까워지면 구조를 보여드립니다.", null, false, false),
        Expected(200, "미리 보는 도로 구조", "진입 차로는\n앞까지 이어집니다", "진입 차로가 끝부분에서 본선과 연결됩니다.", "약 400m 앞", true),
        Expected(300, "미리 보는 도로 구조", "진입 차로는\n앞까지 이어집니다", "진입 차로가 끝부분에서 본선과 연결됩니다.", "약 300m 앞", true),
        Expected(450, "합류 구간 접근", "앞에서 차로가\n합쳐집니다", "진입 차로가 끝에서 본선과 연결됩니다.", "약 150m 앞", true),
        Expected(600, "합류 구간 접근", "앞에서 차로가\n합쳐집니다", "진입 차로가 끝에서 본선과 연결됩니다.", null, true),
        Expected(750, "합류 구간 접근", "앞에서 차로가\n합쳐집니다", "진입 차로가 끝에서 본선과 연결됩니다.", null, true, false),
        Expected(900, "구간 통과", "합류 구간을 지났습니다", "다음 지원 구간에서 다시 안내합니다.", null, false),
    )

    @Test fun thousandSyntheticInputsReachExpectedNativeUiWithinBudget() {
        DeviceTestSupport.requireActualLocationPermission()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[RoadViewModel::class.java] }
        compose.waitUntil(15_000) { model.ui.settingsAllowed }
        val samples = JSONArray()
        val measured = mutableListOf<Double>()
        val changed = mutableListOf<Double>()
        val phaseCounts = mutableMapOf<String, Int>()
        val commitChecks = mutableListOf<RoadCommitTestSupport.Check>()
        val warmupCycles = 10
        val measuredCycles = 125
        var passed = false
        val frameObserver = compose.runOnIdle {
            PipelineFrameMetricsObserver(compose.activity.window).also { it.start() }
        }
        val commitSession = RoadCompositionObservation.begin()
        try {
            repeat(warmupCycles + measuredCycles) { cycle ->
                // Reset is deliberately outside timing. Each cycle is a new complete passage.
                compose.runOnIdle {
                    assertTrue("Actual parked platform policy must allow controls", model.ui.settingsAllowed)
                    model.demo()
                    assertFalse(model.ui.playing)
                }
                compose.waitForIdle()
                compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
                expected.forEachIndexed { step, oracle ->
                    val priorRecordId = RoadCompositionObservation.snapshot(commitSession).records.lastOrNull()?.recordId ?: 0L
                    var startedNs = 0L
                    var modelFeedbackMs = Double.NaN
                    var callbackAccepted = false
                    var observedModel: RoadUiState? = null
                    compose.onNodeWithTag("next").performSemanticsAction(SemanticsActions.OnClick) { callback ->
                        startedNs = SystemClock.elapsedRealtimeNanos()
                        callbackAccepted = callback()
                        observedModel = model.ui
                        modelFeedbackMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0
                    }
                    val callbackReturnMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0
                    // fetchSemanticsNode synchronizes once. No extra waitForIdle or per-assert
                    // synchronization: every oracle field is checked against this same observation.
                    val capture = captureSemantics()
                    val snapshot = capture.nodes
                    val observedMs = (capture.copyCompletedElapsedNs - startedNs) / 1_000_000.0
                    val semanticErrors = semanticErrors(snapshot, oracle)
                    // Independent UI-change matrix for this fixed route: only the distance changes
                    // at 300m/600m; 100m/750m retain the same visible content and claim no new commit.
                    val requiredScopes = when (step) {
                        1, 6 -> emptySet()
                        3, 5 -> setOf("outer")
                        else -> setOf("outer", "guidance")
                    }
                    val commitCheck = RoadCommitTestSupport.check(
                        RoadCompositionObservation.snapshot(commitSession), priorRecordId, startedNs,
                        requireNotNull(observedModel), requiredScopes)
                    var sampleRecord: JSONObject? = null
                    if (cycle >= warmupCycles) {
                        measured += observedMs
                        commitChecks += commitCheck
                        if (oracle.visibleChange) changed += observedMs
                        phaseCounts[oracle.phase] = phaseCounts.getOrDefault(oracle.phase, 0) + 1
                        sampleRecord = JSONObject().put("index", measured.lastIndex)
                            .put("cycle", cycle - warmupCycles).put("step", step)
                            .put("alongLocalSyntheticMeters", oracle.alongMeters)
                            .put("expectedPhase", oracle.phase).put("expectedDiagram", oracle.diagram)
                            .put("visibleChangeExpected", oracle.visibleChange)
                            .put("callbackStartedElapsedNs", startedNs)
                            .put("callbackAccepted", callbackAccepted)
                            .put("modelStateObservedMs", modelFeedbackMs)
                            .put("callbackReturnObservedMs", callbackReturnMs)
                            .put("semanticStateObservedMs", observedMs)
                            .put("semanticFetchStartedElapsedNs", capture.fetchStartedElapsedNs)
                            .put("semanticFetchCompletedElapsedNs", capture.fetchCompletedElapsedNs)
                            .put("semanticCopyCompletedElapsedNs", capture.copyCompletedElapsedNs)
                            .put("semanticFetchElapsedMs", (capture.fetchCompletedElapsedNs - capture.fetchStartedElapsedNs) / 1_000_000.0)
                            .put("semanticCopyElapsedMs", (capture.copyCompletedElapsedNs - capture.fetchCompletedElapsedNs) / 1_000_000.0)
                            .put("semanticFetchThreadCpuMs", capture.fetchThreadCpuNs / 1_000_000.0)
                            .put("semanticCopyThreadCpuMs", capture.copyThreadCpuNs / 1_000_000.0)
                            .put("semanticNodeCount", snapshot.size)
                            .put("semanticErrors", JSONArray(semanticErrors))
                            .put("compositionObservation", commitCheck.json())
                        if (semanticErrors.isNotEmpty()) sampleRecord.put("failedSemanticSnapshot", JSONArray(snapshot.map {
                            JSONObject().put("tag", it.tag).put("texts", JSONArray(it.texts))
                                .put("clippedBounds", JSONArray(listOf(it.clippedLeft, it.clippedTop, it.clippedRight, it.clippedBottom)))
                        }))
                        samples.put(sampleRecord)
                    }
                    try {
                        assertTrue("Actual next callback rejected input", callbackAccepted)
                        assertModel(requireNotNull(observedModel), oracle)
                        assertTrue("Native semantics mismatch at cycle=$cycle step=$step: ${semanticErrors.joinToString()}", semanticErrors.isEmpty())
                    } finally {
                        val assertionsCompletedMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0
                        sampleRecord?.put("assertionsCompletedMs", assertionsCompletedMs)
                            ?.put("postSnapshotObserverAndAssertionMs", assertionsCompletedMs - observedMs)
                    }
                }
            }
            assertEquals(1000, measured.size)
            assertEquals(750, changed.size)
            assertEquals(750, commitChecks.count { it.requiredScopes.isNotEmpty() })
            assertEquals("Composition observation must not lose records", 0L,
                RoadCompositionObservation.snapshot(commitSession).droppedRecords)
            assertEquals(mapOf("안내 대기" to 250, "미리 보는 도로 구조" to 250,
                "합류 구간 접근" to 375, "구간 통과" to 125), phaseCounts)
            assertTrue("Synthetic product pipeline p95 ${p95(measured)}ms exceeds 250ms", p95(measured) <= 250.0)
            assertTrue("Changed semantic states p95 ${p95(changed)}ms exceeds 250ms", p95(changed) <= 250.0)
            assertTrue("Every designated changed input must apply required composition scopes within 250ms; failures=" +
                commitChecks.withIndex().filter { !it.value.withinBudget }.map { it.index },
                commitChecks.all { it.withinBudget })
            passed = true
        } finally {
            val commitSnapshot = RoadCompositionObservation.end(commitSession)
            val frameReport = runCatching { frameObserver.stopAndReport() }.getOrElse { failure ->
                JSONObject().put("collectionFailed", true)
                    .put("error", "${failure.javaClass.simpleName}: ${failure.message}")
            }
            val commitReport = RoadCommitTestSupport.report(commitSnapshot)
            val result = JSONObject().put("passed", passed).put("source", "REPLAY_SYNTHETIC")
                .put("measurementVersion", 4)
                .put("warmupCycles", warmupCycles).put("warmupInputs", warmupCycles * expected.size)
                .put("measuredCyclesTarget", measuredCycles).put("sampleTarget", 1000)
                .put("measuredSamples", measured.size).put("changedSemanticSamples", changed.size)
                .put("thresholdP95Ms", 250).put("samples", samples)
                .put("observedPhaseCounts", JSONObject(phaseCounts))
                .put("clock", "SystemClock.elapsedRealtimeNanos")
                .put("frameDiagnostics", frameReport)
                .put("compositionDiagnostics", commitReport)
                .put("changedCompositionSamples", commitChecks.count { it.requiredScopes.isNotEmpty() })
                .put("changedCompositionFailures", commitChecks.count { !it.withinBudget })
                .put("compositionAcceptance", "Every designated changed input, not p95: first matching actual-state record for each required scope must be present and the latest must be <=250ms from actual next callback entry. No-op inputs retain existing UI and claim no new feedback. This covers next replay only, not all AC24 controls or physical presentation.")
                .put("samplesAbove250Ms", measured.count { it > 250.0 })
                .put("changedSamplesAbove250Ms", changed.count { it > 250.0 })
                .put("maxObservedMs", measured.maxOrNull() ?: JSONObject.NULL)
                .put("scope", "Actual MainActivity next button callback -> production synthetic GuidanceEngine -> ViewModel -> one synchronized unmerged native root semantics snapshot. Clock ends only after all node tags, text and clipped bounds are copied. Includes callback return, synchronization and full snapshot collection; excludes later oracle assertions. Reset and 80 warm-up inputs excluded. One input completes before the next; no coalesced samples.")
                .put("measurementChange", "Version 1 ended timing after separately synchronized assertions; version 2 uses one independent snapshot. Version 3 adds fetch/copy and FrameMetrics diagnostics. Version 4 keeps these measurements and additionally observes actual RoadScreen outer/nested composition with opt-in debug SideEffects. All 750 designated changed next inputs must apply their required composition scopes within 250ms; the original semantic p95 gates remain. This instrumented debug build differs from prior binaries. Prior run artifacts remain unchanged; no sample or threshold is relaxed and no successful retry is selected.")
                .put("diagnosticPolicy", "Fetch elapsed includes synchronization, root lookup and observer scheduling; low test-thread CPU does not prove the UI was ready. All node tags/text/bounds are copied before semantic timing ends. Oracle checks, composition association and JSON run afterward. Additional observation costs are included, never subtracted. Frame-time coincidence does not identify a UI state. Composition records prove successful application only, not draw/pixel presentation. Passed requires existing semantic p95 gates and every designated changed next composition <=250ms, not the entire AC24 action matrix or complete FrameMetrics coverage.")
                .put("observationPolicy", "One snapshot attempt per input; no polling for a convenient expected state. Any mismatch fails immediately. Positive clipped semantics bounds prove in-root semantic visibility, not physical pixel presentation.")
                .put("unchangedStatePolicy", "100m WAITING and 750m APPROACHING legitimately retain the existing visible state. Both model and existing semantics are checked, but a new frame/commit is not claimed. Changed-state p95 is reported separately.")
                .put("excluded", JSONArray(listOf("SDK transit", "live coordinator and geographic projection",
                    "actual road data", "pre-callback dispatch", "physical pixel presentation", "real vehicle")))
            if (measured.isNotEmpty()) result.put("p95Ms", p95(measured))
            if (changed.isNotEmpty()) result.put("changedSemanticP95Ms", p95(changed))
            DeviceTestSupport.save("synthetic-replay-pipeline-performance", result)
        }
    }

    private fun assertModel(state: RoadUiState, oracle: Expected) {
        assertTrue(state.simulation)
        assertEquals(DiagramSource.REPLAY_SYNTHETIC, state.inputSource)
        assertFalse("Manual replay must not have a competing auto-play loop", state.playing)
        assertEquals(oracle.phase, state.phase)
        assertEquals(oracle.title, state.title)
        assertEquals(oracle.message, state.message)
        assertEquals(oracle.distance, state.distance)
        assertEquals(oracle.diagram, state.showDiagram)
    }

    private fun captureSemantics(): SemanticCapture {
        val fetchCpuStartedNs = Debug.threadCpuTimeNanos()
        val fetchStartedNs = SystemClock.elapsedRealtimeNanos()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val fetchCompletedNs = SystemClock.elapsedRealtimeNanos()
        val fetchCpuCompletedNs = Debug.threadCpuTimeNanos()
        val rootBounds = root.boundsInRoot
        val nodes = mutableListOf<ObservedNode>()
        fun visit(node: SemanticsNode) {
            val clipped = node.boundsInRoot.intersect(rootBounds)
            nodes += ObservedNode(
                node.config.getOrNull(SemanticsProperties.TestTag),
                node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty(),
                clipped.left, clipped.top, clipped.right, clipped.bottom,
            )
            node.children.forEach(::visit)
        }
        visit(root)
        val snapshot = nodes.toList()
        val copyCompletedNs = SystemClock.elapsedRealtimeNanos()
        val copyCpuCompletedNs = Debug.threadCpuTimeNanos()
        return SemanticCapture(snapshot, fetchStartedNs, fetchCompletedNs, copyCompletedNs,
            fetchCpuCompletedNs - fetchCpuStartedNs, copyCpuCompletedNs - fetchCpuCompletedNs)
    }

    private fun semanticErrors(snapshot: List<ObservedNode>, oracle: Expected): List<String> = buildList {
        fun text(tag: String, expectedText: String) {
            val nodes = snapshot.filter { it.tag == tag }
            if (nodes.size != 1) add("$tag expected one node, observed ${nodes.size}")
            else {
                if (nodes.single().texts != listOf(expectedText)) add("$tag text mismatch: ${nodes.single().texts}")
                if (!nodes.single().hasVisibleBounds) add("$tag has no visible semantic bounds")
            }
        }
        text("phase", oracle.phase)
        text("title", oracle.title)
        text("message", oracle.message)
        val distanceNodes = snapshot.filter { node -> node.texts.any { it.contains("약 ") } }
        if (oracle.distance == null) {
            if (distanceNodes.isNotEmpty()) add("distance expected absent, observed ${distanceNodes.flatMap { it.texts }}")
        } else if (distanceNodes.size != 1 || distanceNodes.single().texts != listOf(oracle.distance) || !distanceNodes.single().hasVisibleBounds) {
            add("distance expected visible '${oracle.distance}', observed ${distanceNodes.flatMap { it.texts }}")
        }
        val diagramNodes = snapshot.filter { it.tag == "diagram" }
        if (oracle.diagram) {
            if (diagramNodes.size != 1 || !diagramNodes.single().hasVisibleBounds) add("diagram expected one visible node")
        } else if (diagramNodes.isNotEmpty()) add("diagram expected absent")
    }

    private fun p95(values: List<Double>): Double = values.sorted()[ceil(values.size * .95).toInt() - 1]
}
