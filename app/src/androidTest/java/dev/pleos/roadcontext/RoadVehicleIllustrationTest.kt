package dev.pleos.roadcontext

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Explicit component fixtures: the car is an illustration, never a measured vehicle position.
 * The motion test observes the rendered car node's bounds under the Compose test clock.
 * It does not inspect an animation progress variable or drive the SDK/engine.
 */
class RoadVehicleIllustrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun syntheticVehicleHasExplicitIllustrationNotice() {
        render(DiagramSource.REPLAY_SYNTHETIC)
        compose.onNodeWithTag("diagram").assertIsDisplayed()
        compose.onNodeWithTag("synthetic_vehicle").assertIsDisplayed()
            .assertWidthIsAtLeast(88.dp)
            .assertContentDescriptionEquals("가상 차량의 도로 연결 예시. 실제 차량의 현재 차로는 표시하지 않음.")
        compose.onNodeWithTag("synthetic_route").assertIsDisplayed()
            .assertContentDescriptionEquals("분홍색 가상 경로 예시")
        compose.onNodeWithTag("synthetic_vehicle_notice")
            .assertIsDisplayed().assertTextEquals("가상 차량 경로 예시 · 실제 위치 아님")
        val bounds = compose.onNodeWithTag("synthetic_vehicle").getUnclippedBoundsInRoot()
        DeviceTestSupport.save("vehicle-illustration-size", JSONObject()
            .put("vehicleBoundsDp", bounds.toString())
            .put("vehicleWidthDp", (bounds.right - bounds.left).value.toDouble())
            .put("minimumWidthDp", 88).put("syntheticRouteVisible", true)
            .put("scope", "Actual wide emulator component fixture; route color requires separate visual inspection."))
    }

    @Test fun approvedRealRoadDoesNotInventVehiclePosition() {
        render(DiagramSource.REAL_SDK)
        compose.onNodeWithTag("diagram").assertIsDisplayed()
        compose.onNodeWithTag("synthetic_vehicle").assertDoesNotExist()
        compose.onNodeWithTag("synthetic_vehicle_notice").assertDoesNotExist()
        compose.onNodeWithTag("synthetic_route").assertDoesNotExist()
    }

    @Test fun capturedRoadDoesNotInventVehiclePosition() {
        render(DiagramSource.REPLAY_CAPTURED)
        compose.onNodeWithTag("diagram").assertIsDisplayed()
        compose.onNodeWithTag("synthetic_vehicle").assertDoesNotExist()
        compose.onNodeWithTag("synthetic_vehicle_notice").assertDoesNotExist()
        compose.onNodeWithTag("synthetic_route").assertDoesNotExist()
    }

    @Test fun renderedCarMovesPausesResumesAndStopsAfterSingleExample() {
        compose.mainClock.autoAdvance = false
        val state = render(DiagramSource.REPLAY_SYNTHETIC)
        settleFrame()
        val initial = carBounds()
        var activeClockMs = 0L
        var activeStartedMs = compose.mainClock.currentTime
        compose.runOnIdle { state.value = state.value.copy(playing = true) }
        settleFrame()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val moving = carBounds()
        assertMoved("Playing must move the rendered car", initial, moving)
        activeClockMs += compose.mainClock.currentTime - activeStartedMs

        compose.runOnIdle { state.value = state.value.copy(playing = false) }
        settleFrame()
        val paused = carBounds()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val stillPaused = carBounds()
        assertStationary("Paused car must remain in the same rendered position", paused, stillPaused)

        activeStartedMs = compose.mainClock.currentTime
        compose.runOnIdle { state.value = state.value.copy(playing = true) }
        settleFrame()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val resumed = carBounds()
        assertMoved("Resume must continue the example", stillPaused, resumed)
        activeClockMs += compose.mainClock.currentTime - activeStartedMs

        // The user may leave replay playing while vehicle restrictions arrive. Motion must stop.
        compose.runOnIdle { state.value = state.value.copy(settingsAllowed = false) }
        settleFrame()
        val restricted = carBounds()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val stillRestricted = carBounds()
        assertStationary("Vehicle restrictions must stop the illustrative motion", restricted, stillRestricted)
        activeStartedMs = compose.mainClock.currentTime
        compose.runOnIdle { state.value = state.value.copy(settingsAllowed = true) }
        settleFrame()
        val unrestrictedStart = carBounds()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val unrestricted = carBounds()
        assertMoved("Returning to parked controls may resume the example", unrestrictedStart, unrestricted)

        // Only actively playing time counts. The larger, faster example must finish by ~6s,
        // not merely stop eventually; paused/restricted waits are excluded from this budget.
        val activeBeforeFinalAdvance = activeClockMs + compose.mainClock.currentTime - activeStartedMs
        assertTrue("Resume checks must finish before the completion deadline", activeBeforeFinalAdvance < 6_000)
        compose.mainClock.advanceTimeBy(6_000 - activeBeforeFinalAdvance)
        compose.waitForIdle()
        activeClockMs += compose.mainClock.currentTime - activeStartedMs
        assertTrue("Completion observation must stay within one test frame of 6s", activeClockMs <= 6_016)
        val completed = carBounds()
        assertMoved("Car must reach a later position before completing", unrestricted, completed)
        compose.mainClock.advanceTimeBy(731)
        compose.waitForIdle()
        val afterCompletion = carBounds()
        assertStationary("Completed example must not restart while playing stays true", completed, afterCompletion)
        DeviceTestSupport.save("vehicle-illustration-motion", JSONObject()
            .put("source", "REPLAY_SYNTHETIC").put("clock", "Controlled Compose test clock")
            .put("activeClockAtCompletionMs", activeClockMs).put("completionBudgetMs", 6_000)
            .put("initialBoundsPx", initial.toString()).put("movingBoundsPx", moving.toString())
            .put("pausedBoundsPx", paused.toString()).put("stillPausedBoundsPx", stillPaused.toString())
            .put("resumedBoundsPx", resumed.toString()).put("completedBoundsPx", completed.toString())
            .put("restrictedBoundsPx", restricted.toString()).put("stillRestrictedBoundsPx", stillRestricted.toString())
            .put("unrestrictedStartBoundsPx", unrestrictedStart.toString())
            .put("unrestrictedBoundsPx", unrestricted.toString())
            .put("afterCompletionBoundsPx", afterCompletion.toString())
            .put("scope", "Rendered illustrative-car bounds, not real vehicle motion or physical-pixel timing."))
    }

    private fun render(source: DiagramSource): MutableState<RoadUiState> {
        val synthetic = source == DiagramSource.REPLAY_SYNTHETIC
        val state = mutableStateOf(RoadUiState(
            title = "진입 차로는\n앞까지 이어집니다",
            message = "진입 차로가 끝부분에서 본선과 연결됩니다.",
            detail = "도로 구조 설명이며, 현재 차로와 합류 가능 여부는 판단하지 않습니다.",
            phase = "미리 보는 도로 구조", distance = "약 400m 앞",
            sourceLabel = when (source) {
                DiagramSource.REAL_SDK -> "실제 입력 모드"
                DiagramSource.REPLAY_SYNTHETIC -> "가상 구간 시연"
                DiagramSource.REPLAY_CAPTURED -> "기록 재생"
            },
            simulation = source != DiagramSource.REAL_SDK, showDiagram = true,
            settingsAllowed = true, inputSource = source,
            diagramModel = DiagramModel(MergeSide.RIGHT, source,
                if (synthetic) "synthetic:vehicle-example" else "fixture:approved-road",
                synthetic = synthetic, approved = true),
        ))
        compose.setContent { RoadScreen(state.value, {}, {}, {}, {}, {}, {}) }
        return state
    }

    private fun settleFrame() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun carBounds(): Rect = compose.onNodeWithTag("synthetic_vehicle")
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    private fun assertMoved(message: String, from: Rect, to: Rect) {
        assertTrue("$message: $from -> $to",
            abs(from.center.x - to.center.x) > 1f || abs(from.center.y - to.center.y) > 1f)
    }

    private fun assertStationary(message: String, from: Rect, to: Rect) {
        assertTrue("$message: $from -> $to",
            abs(from.center.x - to.center.x) <= 1f && abs(from.center.y - to.center.y) <= 1f)
    }
}
