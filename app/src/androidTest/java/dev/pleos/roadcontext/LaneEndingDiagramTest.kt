package dev.pleos.roadcontext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LaneEndingDiagramTest {
    @get:Rule val compose=createComposeRule()
    @Test fun roadPreviewProgressesThenMergesAndPausesWithoutReset() {
        compose.mainClock.autoAdvance=false
        val playing=mutableStateOf(true)
        compose.setContent { ClusterDemoGallery(false,true,playing.value,{playing.value=!playing.value},{},null,"mangwon-c") }
        fun advance(ms:Long){compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()}
        fun car()=compose.onNodeWithTag("context_vehicle").fetchSemanticsNode().boundsInRoot
        advance(100)
        compose.onNodeWithTag("lane_continues_label").assertIsDisplayed()
        compose.onNodeWithTag("lane_end_label").assertIsDisplayed()
        compose.onNodeWithTag("mangwon_solid").assertDoesNotExist()
        val start=car();advance(850);val forward=car();advance(1350);val merged=car()
        assertTrue(forward.center.y<start.center.y-20)
        assertTrue(merged.center.x<forward.center.x-40)
        assertTrue(abs(start.width-merged.width)<1 && abs(start.height-merged.height)<1)
        compose.onNodeWithTag("demo_play").performClick();advance(32);val paused=car();advance(3000)
        assertTrue(abs(paused.center.x-car().center.x)<1)
        compose.onNodeWithTag("demo_play").performClick();advance(32)
        assertTrue(abs(paused.center.x-car().center.x)<10)
        compose.onNodeWithTag("stage_passed").performClick();advance(32)
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
    }
}
