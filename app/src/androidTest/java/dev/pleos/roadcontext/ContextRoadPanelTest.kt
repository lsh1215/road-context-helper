package dev.pleos.roadcontext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.pleos.roadcontext.core.GeoPoint
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Presentation tests: a real SDK gate controls when the explanatory drawing exists.
 * These fixtures do not claim live SDK/road evidence; the device replay supplies that evidence.
 */
class ContextRoadPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun roadAndCarExistOnlyDuringTheGuidanceWindow() {
        val state = render(visible = false, status = "WAITING", along = 100.0)
        compose.onNodeWithTag("sdk_replay_idle").assertIsDisplayed()
        compose.onNodeWithTag("context_diagram").assertDoesNotExist()
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.onNodeWithTag("sdk_replay_road").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(visible = true, status = "GUIDANCE", alongMeters = 300.0) }
        compose.onNodeWithTag("sdk_replay_guidance").assertIsDisplayed()
        compose.onNodeWithTag("context_diagram").assertIsDisplayed()
        compose.onNodeWithTag("context_vehicle").assertIsDisplayed()
        compose.onNodeWithTag("context_example_notice").assertTextEquals("연결 구조 예시 · 실제 차로 아님")
        compose.onNodeWithTag("sdk_replay_idle").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(visible = false, status = "PASSED", alongMeters = 520.0) }
        compose.onNodeWithTag("sdk_replay_idle").assertIsDisplayed()
        compose.onNodeWithTag("context_diagram").assertDoesNotExist()
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.onNodeWithTag("sdk_replay_guidance").assertDoesNotExist()
    }

    @Test fun exampleCompletesQuicklyAndRepeatsWithoutNewGps() {
        compose.mainClock.autoAdvance = false
        render(visible = true, status = "GUIDANCE", along = 250.0)
        frame()
        compose.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
        compose.waitForIdle()
        val start = car()
        compose.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        compose.waitForIdle()
        val middle = car()
        compose.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        compose.waitForIdle()
        val end = car()
        assertTrue("One-second example must reach the other lane", start.center.x - end.center.x > 50f)
        assertTrue("Movement must include intermediate frames", separation(start, middle) > 10f && separation(middle, end) > 10f)
        assertTrue("The car must stay facing forward while changing lanes", abs(start.width - middle.width) < 1f && abs(start.height - middle.height) < 1f)
        compose.mainClock.advanceTimeBy(450, ignoreFrameDuration = true)
        compose.waitForIdle()
        val nextStart = car()
        assertTrue("The example must restart without needing another GPS fix: $start -> $nextStart", separation(start, nextStart) < 5f)
        compose.mainClock.advanceTimeBy(1_000, ignoreFrameDuration = true)
        compose.waitForIdle()
        assertTrue("The next cycle must also reach the other lane", separation(end, car()) < 5f)
    }

    @Test fun gpsUpdatesDoNotRestartExampleAndLeavingSegmentCancelsLoop() {
        compose.mainClock.autoAdvance = false
        val state = render(visible = true, status = "GUIDANCE", along = 250.0)
        frame()
        val start = car()
        compose.mainClock.advanceTimeBy(650, ignoreFrameDuration = true)
        compose.waitForIdle()
        val moving = car()
        compose.runOnIdle { state.value = state.value.copy(alongMeters = 460.0) }
        frame()
        val updated = car()
        assertTrue("GPS fixes must not reset the ongoing example", updated.center.x <= moving.center.x && separation(start, updated) > 50f)
        compose.runOnIdle { state.value = state.value.copy(visible = false, status = "PASSED", alongMeters = 520.0) }
        frame()
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3_000, ignoreFrameDuration = true)
        compose.waitForIdle()
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.onNodeWithTag("sdk_replay_idle").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(visible = true, status = "GUIDANCE", alongMeters = 300.0) }
        frame()
        assertTrue("A new visible segment must start a fresh example", separation(start, car()) < 5f)
        compose.runOnIdle { state.value = state.value.copy(status = "STALE") }
        frame()
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
    }

    @Test fun invalidProgressCannotCreateVehicle() {
        val state = render(visible = true, status = "GUIDANCE", along = null)
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(alongMeters = Double.NaN) }
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(alongMeters = 300.0, status = "STALE") }
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
    }

    @Test fun mangwonExitUsesRightEntryLeftExitAndHidesWhenStale() {
        compose.mainClock.autoAdvance = false
        val state = render(visible = true, status = "GUIDANCE", along = 300.0)
        compose.runOnIdle { state.value = state.value.copy(contextCase = "mangwon-b") }
        frame()
        compose.onNodeWithTag("mangwon_context").assertTextEquals("실선에서는 차로 유지 · 점선 구간에서 왼쪽 진출 준비")
        compose.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
        compose.onNodeWithTag("mangwon_solid").assertIsDisplayed()
        compose.onNodeWithTag("mangwon_dashed").assertIsDisplayed()
        val right = car()
        compose.mainClock.advanceTimeBy(2500, ignoreFrameDuration = true)
        val left = car()
        assertTrue("Mangwon must move from right entry toward left exit", right.center.x - left.center.x > 50f)
        assertTrue("Car keeps facing forward", abs(right.width - left.width) < 1f)
        compose.runOnIdle { state.value = state.value.copy(status = "STALE", visible = false) }
        frame() // Manual animation clock: commit the state change before inspecting semantics.
        compose.onNodeWithTag("mangwon_context").assertDoesNotExist()
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
    }

    @Test fun mangwonMainlineDoesNotShowAnUnusedExit() {
        compose.mainClock.autoAdvance = false
        val state = render(visible = true, status = "GUIDANCE", along = 300.0)
        compose.runOnIdle { state.value = state.value.copy(contextCase = "mangwon-c") }
        frame()
        compose.onNodeWithTag("mangwon_context").assertTextEquals("진입 차로를 따라 진행하며 앞쪽 합류를 준비하세요")
        compose.onNodeWithTag("context_example_notice").assertTextEquals("2026.07 로드뷰 기반 · 도로 구조 및 차량 이동 예시")
        compose.runOnIdle { state.value = state.value.copy(status = "PASSED", visible = false) }
        frame()
        compose.onNodeWithTag("context_diagram").assertDoesNotExist()
    }

    private fun render(visible: Boolean, status: String, along: Double?): MutableState<SdkRouteReplayVisual> {
        val state = mutableStateOf(SdkRouteReplayVisual(
            roadName = "양화대교 북단 → 강변북로 난지 방향 GPS 시험",
            points = listOf(GeoPoint(37.54859, 126.9105767), GeoPoint(37.5510415, 126.9025669)),
            vehicle = GeoPoint(37.54859, 126.9105767), headingDeg = 270.0,
            status = status, alongMeters = along, startAlongMeters = 224.566, endAlongMeters = 474.566,
            visible = visible, receivedCount = 10, junctionAlongMeters = 404.566,
        ))
        compose.setContent { SdkRouteReplayPanel(state.value, false, Modifier.fillMaxSize()) }
        return state
    }
    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }
    private fun car(): Rect = compose.onNodeWithTag("context_vehicle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    private fun separation(a: Rect, b: Rect) = abs(a.center.x - b.center.x) + abs(a.center.y - b.center.y)
}
