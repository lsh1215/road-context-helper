package dev.pleos.roadcontext

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ClusterDemoGalleryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyScenarioHasItsOwnMeaningAndPassageHidesDrawing() {
        compose.setContent { ClusterDemoGallery(false, true, false, {}, {}, null) }
        compose.onNodeWithTag("demo_title").assertTextEquals(ClusterScenario.SHORT_EXIT.title)
        ClusterScenario.entries.forEach { scenario ->
            compose.onNodeWithTag("case_${scenario.id}").performScrollTo().performClick()
            compose.onNodeWithTag("demo_title").assertTextEquals(scenario.title)
            compose.onNodeWithTag("context_route_example").assertContentDescriptionEquals(
                "${scenario.roadDescription} 녹색 면은 설명용 경로이며 실제 차로 인식이나 차량 제어 상태가 아님.")
            compose.onNodeWithTag("stage_preview").performClick()
            compose.onNodeWithTag("demo_title").assertTextEquals(scenario.previewTitle)
            compose.onNodeWithTag("stage_passed").performClick()
            compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
            compose.onNodeWithTag("demo_passed").assertIsDisplayed()
            compose.onNodeWithTag("stage_guidance").performClick()
            compose.onNodeWithTag("context_vehicle").assertIsDisplayed()
        }
    }

    @Test fun restrictedStateRemovesSyntheticDrawingAndControls() {
        val allowed = mutableStateOf(true)
        compose.setContent { ClusterDemoGallery(false, allowed.value, true, {}, {}, null) }
        compose.runOnIdle { allowed.value = false }
        compose.onNodeWithTag("demo_restricted").assertIsDisplayed()
        compose.onNodeWithTag("context_diagram").assertDoesNotExist()
        compose.onNodeWithTag("case_short_exit").assertDoesNotExist()
        compose.runOnIdle { allowed.value = true }
        compose.onNodeWithTag("context_diagram").assertIsDisplayed()
    }

    @Test fun closeExitCrossesThreeLanesInSeparateForwardFacingSteps() {
        compose.mainClock.autoAdvance = false
        val playing = mutableStateOf(true)
        compose.setContent { ClusterDemoGallery(false, true, playing.value, { playing.value = !playing.value }, {}, null) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true); compose.waitForIdle() }
        fun car() = compose.onNodeWithTag("context_vehicle").fetchSemanticsNode().boundsInRoot
        advance(100)
        val start = car()
        advance(750)
        val first = car()
        advance(150)
        assertTrue("First lane has a readable hold", abs(first.center.x - car().center.x) < 2f)
        advance(750)
        val second = car()
        advance(150)
        assertTrue("Second lane has a readable hold", abs(second.center.x - car().center.x) < 2f)
        advance(750)
        val third = car()
        assertTrue(first.center.x > start.center.x + 20f)
        assertTrue(second.center.x > first.center.x + 20f)
        assertTrue(third.center.x > second.center.x + 20f)
        assertTrue(abs(start.width - third.width) < 1f && abs(start.height - third.height) < 1f)
        compose.onNodeWithTag("demo_play").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        val paused = car()
        advance(3_000)
        assertTrue("Paused example must stay still", abs(paused.center.x - car().center.x) < 1f)
    }
}
