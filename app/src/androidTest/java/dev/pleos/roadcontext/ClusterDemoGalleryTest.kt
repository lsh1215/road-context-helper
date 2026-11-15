package dev.pleos.roadcontext

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClusterDemoGalleryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun roadTypesUseReferenceStyleAndPassageHidesDrawing() {
        compose.setContent { ClusterDemoGallery(false, true, false, {}, {}, null) }
        RoadDemoCase.entries.forEach { roadCase ->
            compose.onNodeWithTag("demo_case_${roadCase.id}").performScrollTo().performClick()
            compose.onNodeWithTag("demo_title").assertTextEquals(roadCase.title)
            compose.onNodeWithTag("demo_source").assertTextEquals(roadCase.source)
            val vehicleTag = if (roadCase == RoadDemoCase.TWO_STAGE_LANE_END) "sdk_position_vehicle" else "context_vehicle"
            compose.onNodeWithTag(vehicleTag).assertIsDisplayed()
            compose.onNodeWithTag("stage_preview").performClick()
            compose.onNodeWithTag("demo_title").assertTextEquals(roadCase.previewTitle)
            compose.onNodeWithTag("stage_passed").performClick()
            compose.onNodeWithTag(vehicleTag).assertDoesNotExist()
            compose.onNodeWithTag("demo_passed").assertIsDisplayed()
        }
    }

    @Test fun restrictedStateRemovesDrawingAndControls() {
        val allowed = mutableStateOf(true)
        compose.setContent { ClusterDemoGallery(false, allowed.value, false, {}, {}, null) }
        compose.runOnIdle { allowed.value = false }
        compose.onNodeWithTag("demo_restricted").assertIsDisplayed()
        compose.onNodeWithTag("context_diagram").assertDoesNotExist()
        compose.onNodeWithTag("demo_case_lane_end_rl").assertDoesNotExist()
        compose.runOnIdle { allowed.value = true }
        compose.onNodeWithTag("context_diagram").assertIsDisplayed()
    }

    @Test fun laneEndExamplesMoveInOppositeDirectionsWithoutRotating() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ClusterDemoGallery(false, true, true, {}, {}, null) }
        fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
        fun car() = compose.onNodeWithTag("context_vehicle").fetchSemanticsNode().boundsInRoot

        advance(100)
        val rightEntry = car()
        advance(2200)
        val leftMainline = car()
        assertTrue(leftMainline.center.x < rightEntry.center.x - 40f)
        assertTrue(kotlin.math.abs(rightEntry.width - leftMainline.width) < 1f)

        compose.onNodeWithTag("demo_case_lane_end_lr").performScrollTo().performClick()
        advance(100)
        val leftEntry = car()
        advance(2200)
        val rightMainline = car()
        assertTrue("Mirrored lane end must move right: entry=${leftEntry.center.x}, mainline=${rightMainline.center.x}",
            rightMainline.center.x > leftEntry.center.x + 40f)
        compose.onNodeWithTag("demo_source").assertTextContains("실제 구간 아님", substring = true)
    }
}
