package dev.pleos.roadcontext

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EungbongDemoTest {
    @get:Rule val compose = createComposeRule()
    private fun frame() { compose.mainClock.advanceTimeBy(64); compose.waitForIdle() }

    @Test fun unsupportedReplayProfileCannotSilentlyShowYanghwaRoad() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            SdkRoadReplaySession(context, "unreviewed-road")
            fail("An unreviewed profile must not open another road's reference asset")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unsupported replay profile"))
        }
    }

    @Test fun eungbongAssetAndGpsPanelUseCorrectRoadAndHideAfterPass() {
        val session = SdkRoadReplaySession(InstrumentationRegistry.getInstrumentation().targetContext, "eungbong")
        assertEquals("eungbong", session.road.id)
        assertTrue(session.start < session.junction && session.junction < session.end)
        assertTrue(session.road.points.all { it.longitude > 127.03 && it.longitude < 127.05 })
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(SdkRouteReplayVisual(session.name, session.road.points, null, null,
            "GUIDANCE", 100.0, session.start, session.end, true, 5, session.junction, "eungbong", 334.5629, 390.2343, 103.5629, 588.2641, 627.9231))
        compose.setContent { SdkRouteReplayPanel(state.value, false, Modifier.fillMaxSize()) }
        frame()
        compose.onNodeWithText("응봉지하차도 출구 · 분홍색 유도선 합류").assertIsDisplayed()
        compose.onNodeWithTag("sdk_position_vehicle").assertIsDisplayed()
        compose.onNodeWithTag("lane_end_distance").assertTextEquals("약 230–300m")
        compose.onNodeWithText("출구 뒤에도 진입 차로가 이어집니다").assertIsDisplayed()
        val before = compose.onNodeWithTag("sdk_position_vehicle").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(4000); compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("sdk_position_vehicle").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.value = state.value.copy(alongMeters = session.junction - 30) }
        frame()
        compose.onNodeWithText("먼저 오른쪽 진입 차로가 끝납니다").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        val advanced = compose.onNodeWithTag("sdk_position_vehicle").fetchSemanticsNode().boundsInRoot
        assertTrue(advanced.top < before.top)
        assertTrue(advanced.left < before.left) // Perspective path remains on the road.
        compose.runOnIdle { state.value = state.value.copy(alongMeters = 360.0) }; frame()
        compose.onNodeWithTag("lane_end_distance").assertTextEquals("차로 끝 구간 부근")
        compose.runOnIdle { state.value = state.value.copy(status = "STALE", visible = false) }; frame()
        compose.onNodeWithTag("sdk_position_vehicle").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(status = "GUIDANCE", visible = true, alongMeters = 400.0) }; frame()
        compose.onNodeWithTag("lane_end_distance").assertTextEquals("약 180–230m")
        compose.onNodeWithTag("second_lane_end_region").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(alongMeters = 640.0) }; frame()
        compose.onNodeWithTag("lane_end_distance").assertTextEquals("차로 끝 구간 지남")
        compose.runOnIdle { state.value = state.value.copy(status = "PASSED", visible = false) }
        frame()
        compose.onNodeWithTag("sdk_position_diagram").assertDoesNotExist()
    }

    @Test fun parkedGallerySwitchesRoadNamesAndEvidenceDates() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ClusterDemoGallery(false, true, false, {}, {}, null, "eungbong") }
        frame()
        compose.onNodeWithTag("demo_source").assertTextEquals("응봉 관측 자료 기반 도식")
        compose.onNodeWithTag("second_lane_end_region").assertIsDisplayed()
        compose.onNodeWithTag("case_mangwon_lane_end").performClick(); frame()
        compose.onNodeWithTag("demo_source").assertTextEquals("망원 관측 자료 기반 도식")
        compose.onNodeWithTag("context_example_notice").assertTextEquals("2026.07 로드뷰 기반 · 도로 구조 및 차량 이동 예시")
        compose.onNodeWithTag("case_eungbong_lane_end").performClick(); frame()
        compose.onNodeWithTag("demo_source").assertTextEquals("응봉 관측 자료 기반 도식")
    }
}
