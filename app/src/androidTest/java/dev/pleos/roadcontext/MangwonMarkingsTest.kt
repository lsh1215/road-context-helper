package dev.pleos.roadcontext

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class MangwonMarkingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourcedPreviewHasTwoDistinctZonesAndPauseResumesWithoutReset() {
        compose.mainClock.autoAdvance = false
        val playing = mutableStateOf(true)
        compose.setContent { ClusterDemoGallery(false, true, playing.value,
            { playing.value = !playing.value }, {}, null, "mangwon-b") }
        fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
        fun car() = compose.onNodeWithTag("context_vehicle").fetchSemanticsNode().boundsInRoot
        advance(100)
        compose.onNodeWithTag("mangwon_solid").assertIsDisplayed()
        compose.onNodeWithTag("mangwon_dashed").assertIsDisplayed()
        compose.onNodeWithTag("marking_source").assertTextContains("2026.06–07", substring = true)
        val start = car()
        advance(2200)
        val left = car()
        assertTrue(start.center.x - left.center.x > 50f)
        assertTrue(abs(start.width - left.width) < 1f && abs(start.height - left.height) < 1f)
        compose.onNodeWithTag("demo_play").performClick()
        advance(32)
        val paused = car()
        advance(2000)
        assertTrue(abs(paused.center.x - car().center.x) < 1f)
        compose.onNodeWithTag("demo_play").performClick()
        advance(32)
        assertTrue("Resume must not reset to right entry", abs(paused.center.x - car().center.x) < 10f)
        advance(1600)
        assertTrue("Next cycle returns to entry", car().center.x > left.center.x + 50f)
        compose.onNodeWithTag("stage_passed").performClick()
        advance(32)
        compose.onNodeWithTag("context_vehicle").assertDoesNotExist()
        compose.onNodeWithTag("mangwon_solid").assertDoesNotExist()
    }
}
