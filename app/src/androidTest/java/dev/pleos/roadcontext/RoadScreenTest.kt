package dev.pleos.roadcontext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

/**
 * Native Compose rendering tests with explicitly supplied UI states.
 * These do not instantiate MainActivity, a car service, or a live SDK source.
 * Passing cannot establish actual parked/driving policy, AC-19/21, or human comprehension.
 * The 1200 x 900 dp surface is a design fixture, not proof of all physical display sizes.
 */
class RoadScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()
    private var layoutContext = "render not started"
    private val textDiagnostics = mutableListOf<String>()

    private fun render(
        state: RoadUiState,
        fontScale: Float = 1f,
        onDemo: () -> Unit = {},
        onLive: () -> Unit = {},
        onPlay: () -> Unit = {},
        onNext: () -> Unit = {},
        onReset: () -> Unit = {},
        onTheme: () -> Unit = {},
        actualWindow: Boolean = false,
    ) {
        compose.setContent {
            val nativeDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(nativeDensity, fontScale)) {
                BoxWithConstraints(Modifier.fillMaxSize().testTag("test_host")) {
                    val hostWidth = maxWidth
                    val hostHeight = maxHeight
                    SideEffect {
                        layoutContext = "mode=${if (actualWindow) "ACTUAL_HOST" else "DESIGN_FIXTURE_1200x900"}; " +
                            "hostDp=${hostWidth.value}x${hostHeight.value}; " +
                            "density=$nativeDensity; fontScale=$fontScale; " +
                            "fixtureExceedsHost=${!actualWindow && (hostWidth < 1200.dp || hostHeight < 900.dp)}"
                    }
                    val surface = if (actualWindow) Modifier.fillMaxSize() else Modifier.requiredSize(1200.dp, 900.dp)
                    Box(surface.testTag("test_surface")) {
                        RoadScreen(state, onDemo, onLive, onPlay, onNext, onReset, onTheme)
                    }
                }
            }
        }
        // Await the actual test host rather than assuming the OEM activity transition has completed.
        // A missing hierarchy remains a failure after this bounded wait; it is never skipped.
        compose.waitUntil(timeoutMillis = 5_000) {
            try { compose.onAllNodesWithTag("source").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
    }

    private fun assertCopy(state: RoadUiState) {
        compose.onNodeWithTag("title").assertIsDisplayed().assertTextEquals(state.title)
        compose.onNodeWithTag("message").assertIsDisplayed().assertTextEquals(state.message)
        compose.onNodeWithTag("phase").assertIsDisplayed().assertTextEquals(state.phase)
        compose.onNodeWithTag("source").assertIsDisplayed()
        listOf("title", "message", "phase", "source").forEach(::assertNoTextOverflow)
    }

    private fun assertNoTextOverflow(tag: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue("Missing layout result for $tag", it(results))
        }
        assertTrue("No text layout result for $tag", results.isNotEmpty())
        results.forEach { result ->
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val lines = (0 until result.lineCount).joinToString { line ->
                "$line:[${result.getLineLeft(line)},${result.getLineTop(line)},${result.getLineRight(line)},${result.getLineBottom(line)}];ellipsis=${result.isLineEllipsized(line)}"
            }
            val diagnostic = JSONObject()
                .put("test", testName.methodName).put("tag", tag).put("context", layoutContext)
                .put("sizePx", "${result.size.width}x${result.size.height}")
                .put("paragraphPx", "${result.multiParagraph.width}x${result.multiParagraph.height}")
                .put("constraints", result.layoutInput.constraints.toString())
                .put("fontSize", result.layoutInput.style.fontSize.toString())
                .put("lineHeight", result.layoutInput.style.lineHeight.toString())
                .put("didOverflowWidth", result.didOverflowWidth).put("didOverflowHeight", result.didOverflowHeight)
                .put("hasVisualOverflow", result.hasVisualOverflow).put("lineCount", result.lineCount)
                .put("nodeBoundsPx", bounds.toString()).put("rootBoundsPx", rootBounds.toString())
                .put("lines", lines).toString()
            textDiagnostics += diagnostic
            Log.i("RoadUiTestLayout", diagnostic)
            assertFalse("Text overflow for $tag: $diagnostic", result.hasVisualOverflow)
        }
    }

    @After fun captureActualHostDiagnostics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir, "ui-diagnostics/${testName.methodName}")
        directory.mkdirs()
        try {
            val activities = mutableListOf<String>()
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { activity ->
                    val decor = activity.window.decorView
                    val visible = Rect()
                    decor.getWindowVisibleDisplayFrame(visible)
                    activities += "${activity.javaClass.name}: decorPx=${decor.width}x${decor.height}; visible=$visible; " +
                        "density=${activity.resources.displayMetrics.density}; fontScale=${activity.resources.configuration.fontScale}"
                }
            }
            val summary = "$layoutContext\n${activities.joinToString("\n")}"
            Log.i("RoadUiTestLayout", "${testName.methodName}: $summary")
            File(directory, "host.txt").writeText(summary)
            File(directory, "text-layout.jsonl").writeText(textDiagnostics.joinToString("\n"))
            try { File(directory, "semantics.txt").writeText(compose.onRoot().printToString()) }
            catch (failure: Exception) { File(directory, "semantics-error.txt").writeText(failure.toString()) }
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(directory, "device.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
        } catch (failure: Exception) {
            Log.e("RoadUiTestLayout", "Could not capture diagnostics for ${testName.methodName}", failure)
        }
    }

    @Test fun actualHostWaitingTextHasNoOverflowOrAncestorClipping() {
        val state = RoadUiState()
        render(state, actualWindow = true)
        assertCopy(state)
        assertEssentialNodesInsideHost()
    }

    @Test fun actualHostPreviewTextHasNoOverflowOrAncestorClipping() {
        val state = preview()
        render(state, actualWindow = true)
        assertCopy(state)
        assertEssentialNodesInsideHost()
    }

    private fun assertEssentialNodesInsideHost() {
        val host = compose.onNodeWithTag("test_host").getUnclippedBoundsInRoot()
        listOf("title", "message", "phase", "source").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag outside actual host: $bounds; host=$host; $layoutContext",
                bounds.left >= host.left && bounds.top >= host.top && bounds.right <= host.right && bounds.bottom <= host.bottom)
        }
    }

    @Test fun waitingLiveStateHasSourceAndNoInventedDiagram() {
        val state = RoadUiState()
        render(state)
        assertCopy(state)
        compose.onNodeWithTag("source").assertTextEquals("실제 입력 모드")
        compose.onNodeWithTag("diagram").assertDoesNotExist()
    }

    @Test fun previewShowsSyntheticStructureAndLaneUnknownDescription() {
        val state = preview()
        render(state)
        assertCopy(state)
        compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
        compose.onNodeWithTag("diagram").assertIsDisplayed().assertContentDescriptionEquals(
            "가상 도로 구조. 오른쪽 진입 차로가 이어진 뒤 끝에서 왼쪽 본선과 연결됨. 차량의 현재 차로는 표시하지 않음."
        )
    }

    @Test fun approachingShowsShortenedCopy() {
        val state = preview().copy(
            phase = "합류 구간 접근", title = "앞에서 차로가\n합쳐집니다",
            message = "진입 차로가 끝에서 본선과 연결됩니다.", distance = "약 150m 앞"
        )
        render(state)
        assertCopy(state)
        compose.onNodeWithTag("diagram").assertIsDisplayed()
    }

    @Test fun passedStateRemovesStructure() {
        val state = preview().copy(
            phase = "구간 통과", title = "합류 구간을 지났습니다",
            message = "다음 지원 구간에서 다시 안내합니다.", showDiagram = false, distance = null
        )
        render(state)
        assertCopy(state)
        compose.onNodeWithTag("diagram").assertDoesNotExist()
    }

    @Test fun connectionErrorDoesNotClaimRoadIsClear() {
        val state = RoadUiState(
            phase = "연결 확인 중", title = "위치 정보를 기다립니다",
            message = "연결이 확인되면 지원 구간을 살펴봅니다.", settingsAllowed = false
        )
        render(state)
        assertCopy(state)
        compose.onNodeWithTag("diagram").assertDoesNotExist()
    }

    @Test fun permissionDeniedStateHasNoParkedActions() {
        val state = RoadUiState(
            phase = "권한 확인 필요", title = "위치 권한을 확인해 주세요",
            message = "권한이 확인되기 전에는 도로 구조를 안내하지 않습니다.", settingsAllowed = false
        )
        render(state)
        assertCopy(state)
        listOf("demo", "live", "play", "next", "reset", "theme").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
    }

    @Test fun demoReadyHasVisibleSourceAndNoPrematureStructure() {
        val state = RoadUiState(
            phase = "시연 준비", title = "진입로를 미리 살펴보세요",
            message = "차로가 이어지는 구간과 합류 지점을 설명합니다.",
            simulation = true, settingsAllowed = true, showDiagram = false
        )
        render(state)
        assertCopy(state)
        compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
        compose.onNodeWithTag("diagram").assertDoesNotExist()
        compose.onNodeWithTag("play").assertIsDisplayed()
    }

    @Test fun restrictionsHideAllActionsEvenForPlayingSimulation() {
        render(preview().copy(settingsAllowed = false, playing = true))
        listOf("demo", "live", "play", "next", "reset", "theme").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
    }

    @Test fun simulationActionsDispatchExactlyOnceAndHaveMinimumTouchBounds() {
        val counts = mutableMapOf("play" to 0, "next" to 0, "reset" to 0, "live" to 0)
        fun increment(key: String) { counts[key] = counts.getValue(key) + 1 }
        render(preview().copy(settingsAllowed = true),
            onPlay = { increment("play") }, onNext = { increment("next") },
            onReset = { increment("reset") }, onLive = { increment("live") })
        counts.keys.forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed()
                .assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp).performClick()
        }
        compose.runOnIdle { counts.forEach { (key, count) -> assertEquals(key, 1, count) } }
    }

    @Test fun liveActionsDispatchExactlyOnceAndHaveMinimumTouchBounds() {
        var demos = 0
        var themes = 0
        render(RoadUiState(settingsAllowed = true), onDemo = { demos++ }, onTheme = { themes++ })
        listOf("demo", "theme").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
                .assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp).performClick()
        }
        compose.runOnIdle { assertEquals(1, demos); assertEquals(1, themes) }
    }

    @Test fun recordedInputBadgeDoesNotSayLive() {
        render(RoadUiState(simulation = true, sourceLabel = "SDK 기록 재생"))
        compose.onNodeWithTag("source").assertIsDisplayed().assertTextEquals("기록 재생")
    }

    @Test fun syntheticDiagramCannotBeDisplayedAsLiveRoad() {
        render(preview().copy(simulation = false, sourceLabel = "실제 내비 연결"))
        compose.onNodeWithTag("diagram").assertDoesNotExist()
        compose.onNodeWithTag("title").assertTextEquals("도로 구조 자료를 확인하고 있습니다")
        compose.onNodeWithTag("message").assertTextEquals("확인된 자료가 연결되면 구조를 표시합니다.")
    }

    @Test fun enlargedDarkPreviewPreservesEssentialCopy() {
        val state = preview().copy(dark = true)
        render(state, fontScale = 1.3f)
        assertCopy(state)
        compose.onNodeWithTag("diagram").assertIsDisplayed()
    }

    private fun preview() = RoadUiState(
        phase = "미리 보는 도로 구조", title = "진입 차로는\n앞까지 이어집니다",
        message = "진입 차로가 끝부분에서 본선과 연결됩니다.",
        detail = "도로 구조 설명이며, 현재 차로와 합류 가능 여부는 판단하지 않습니다.",
        distance = "약 400m 앞", simulation = true, showDiagram = true, settingsAllowed = false
    )
}
