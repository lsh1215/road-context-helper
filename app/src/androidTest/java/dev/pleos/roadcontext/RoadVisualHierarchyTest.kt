package dev.pleos.roadcontext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** New visual-priority regression on the actual wide host, using explicitly synthetic UI input.
 * Existing screen/responsive tests own source validation, callbacks, text overflow and touch bounds.
 * This does not establish road correctness, physical display compliance or human comprehension.
 */
class RoadVisualHierarchyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualWideLightPrioritizesRoadStructure() = verify(dark = false, scale = 1f)

    @Test fun actualWideDarkEnlargedPrioritizesRoadStructure() = verify(dark = true, scale = 1.3f)

    private fun verify(dark: Boolean, scale: Float) {
        val state = RoadUiState(
            title = "진입 차로는\n앞까지 이어집니다",
            message = "진입 차로가 끝부분에서 본선과 연결됩니다.",
            detail = "도로 구조 설명이며, 현재 차로와 합류 가능 여부는 판단하지 않습니다.",
            phase = "미리 보는 도로 구조", sourceLabel = "가상 구간 시연",
            distance = "약 400m 앞", simulation = true, showDiagram = true,
            settingsAllowed = true, dark = dark, inputSource = DiagramSource.REPLAY_SYNTHETIC,
            diagramModel = DiagramModel(MergeSide.RIGHT, DiagramSource.REPLAY_SYNTHETIC,
                "synthetic:visual-hierarchy", synthetic = true, approved = true),
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                Box(Modifier.fillMaxSize().testTag("visual_hierarchy_host")) {
                    RoadScreen(state, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            try { compose.onAllNodesWithTag("diagram_panel").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
        val host = compose.onNodeWithTag("visual_hierarchy_host").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("diagram_panel").getUnclippedBoundsInRoot()
        val explanation = compose.onNodeWithTag("guidance_explanation").getUnclippedBoundsInRoot()
        val contentWidth = maxOf(panel.right.value, explanation.right.value) -
            minOf(panel.left.value, explanation.left.value)
        val fraction = (panel.right.value - panel.left.value) / contentWidth
        val diagnostic = JSONObject().put("source", "REPLAY_SYNTHETIC")
            .put("hostDp", host.toString()).put("diagramPanelDp", panel.toString())
            .put("explanationDp", explanation.toString()).put("contentWidthDp", contentWidth)
            .put("diagramWidthFraction", fraction.toDouble()).put("requiredMinimumFraction", 0.60)
            .put("dark", dark).put("fontScale", scale.toDouble())
            .put("scope", "Actual wide host with synthetic component state; not a physical-display or real-road test.")
        // Persist observed geometry before assertions, so a failed candidate remains diagnosable.
        DeviceTestSupport.save("visual-hierarchy-${if (dark) "dark" else "light"}", diagnostic)

        assertTrue("This regression requires the actual wide emulator host: $host", host.right - host.left >= 900.dp)
        assertTrue("Road and explanation must occupy separate horizontal regions: $diagnostic",
            panel.right <= explanation.left || explanation.right <= panel.left)
        assertTrue("The road must occupy at least 60% of guidance content width: $diagnostic", fraction >= 0.60f)
        compose.onNodeWithTag("diagram").assertIsDisplayed().assertHeightIsAtLeast(120.dp)
        compose.onNodeWithTag("title").assertTextEquals(state.title)
        compose.onNodeWithTag("message").assertTextEquals(state.message)
        compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
        listOf("diagram_panel", "diagram", "guidance_explanation", "title", "message", "source").forEach { tag ->
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag extends outside actual host: $bounds vs $host",
                bounds.left >= host.left && bounds.top >= host.top &&
                    bounds.right <= host.right && bounds.bottom <= host.bottom)
        }
    }
}
