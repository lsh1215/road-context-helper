package dev.pleos.roadcontext

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

/** Virtual layout fixtures inside the actual Pleos window, not extra supported physical displays. */
class RoadResponsiveTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val name = TestName()

    @Test fun compactNormal() = verify(600, 900, 1f)
    @Test fun compactEnlarged() = verify(600, 900, 1.3f)
    @Test fun shortWideNormal() = verify(1200, 600, 1f)
    @Test fun shortWideEnlarged() = verify(1200, 600, 1.3f)
    @Test fun actualWindowSizedNormal() = verify(1682, 1210, 1f)
    @Test fun actualWindowSizedEnlarged() = verify(1682, 1210, 1.3f)

    @Test fun compactNormalDark() = verify(600, 900, 1f, dark = true)
    @Test fun compactEnlargedLight() = verify(600, 900, 1.3f, dark = false)
    @Test fun shortWideNormalDark() = verify(1200, 600, 1f, dark = true)
    @Test fun shortWideEnlargedLight() = verify(1200, 600, 1.3f, dark = false)
    @Test fun actualWindowSizedNormalDark() = verify(1682, 1210, 1f, dark = true)
    @Test fun actualWindowSizedEnlargedLight() = verify(1682, 1210, 1.3f, dark = false)

    @Test fun leftMergeCompactEnlarged() = verify(600, 900, 1.3f, dark = true, side = MergeSide.LEFT)
    @Test fun leftMergeShortWideEnlarged() = verify(1200, 600, 1.3f, dark = false, side = MergeSide.LEFT)

    @Test fun unsupportedStatusInSmallEnlargedWindow() = verify(600, 900, 1.3f, uiState = RoadUiState(
        phase = "위치 기능 미지원", title = "이 차량에서는 위치 안내를 사용할 수 없습니다",
        message = "현재 환경이 필요한 위치 기능을 지원하지 않습니다.",
        detail = "", settingsAllowed = false, dark = true,
    ))

    @Test fun permissionStatusInSmallEnlargedWindow() = verify(600, 900, 1.3f, uiState = RoadUiState(
        phase = "위치 권한 필요", title = "위치 정보 사용이 필요합니다",
        message = "주변 도로 구조를 안내하기 위한 권한입니다.",
        detail = "정차 중 앱 권한 설정에서 허용할 수 있습니다.", settingsAllowed = false,
    ))

    @Test fun connectionErrorInSmallEnlargedWindow() = verify(600, 900, 1.3f, uiState = RoadUiState(
        phase = "연결 확인 중", title = "위치 정보를 기다립니다",
        message = "연결이 확인되면 지원 구간을 살펴봅니다.",
        detail = "정차 중 가상 구간 체험을 이용할 수 있습니다.", settingsAllowed = false, dark = true,
    ))

    @Test fun passedStatusInSmallEnlargedWindow() = verify(600, 900, 1.3f, uiState = RoadUiState(
        phase = "구간 통과", title = "합류 구간을 지났습니다",
        message = "다음 지원 구간에서 다시 안내합니다.", detail = "",
        sourceLabel = "가상 구간 시연", inputSource = DiagramSource.REPLAY_SYNTHETIC,
        simulation = true, showDiagram = false, settingsAllowed = false,
    ))

    private fun verify(width: Int, height: Int, scale: Float,
        dark: Boolean = scale > 1f, side: MergeSide = MergeSide.RIGHT, uiState: RoadUiState? = null) {
        val state = uiState ?: RoadUiState(
            title = "진입 차로는\n앞까지 이어집니다",
            message = "진입 차로가 끝부분에서 본선과 연결됩니다.",
            detail = "도로 구조 설명이며, 현재 차로와 합류 가능 여부는 판단하지 않습니다.",
            phase = "미리 보는 도로 구조", sourceLabel = "가상 구간 시연",
            simulation = true, showDiagram = true, settingsAllowed = true,
            dark = dark, distance = "약 400m 앞", inputSource = DiagramSource.REPLAY_SYNTHETIC,
            diagramModel = DiagramModel(side, DiagramSource.REPLAY_SYNTHETIC,
                "synthetic:responsive-${side.name.lowercase()}", synthetic = true, approved = true),
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("responsive_surface")) {
                    RoadScreen(state, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            try { compose.onAllNodesWithTag("source").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
        val surface = compose.onNodeWithTag("responsive_surface").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("title").assertTextEquals(state.title)
        compose.onNodeWithTag("message").assertTextEquals(state.message)
        compose.onNodeWithTag("phase").assertTextEquals(state.phase)
        compose.onNodeWithTag("source").assertTextEquals(if (state.simulation) "가상 구간 시연" else state.sourceLabel)
        if (state.showDiagram) {
            compose.onNodeWithTag("diagram").assertIsDisplayed().assertHeightIsAtLeast(120.dp)
            val enteringSide = if (side == MergeSide.RIGHT) "오른쪽" else "왼쪽"
            val joiningSide = if (side == MergeSide.RIGHT) "왼쪽" else "오른쪽"
            compose.onNodeWithTag("diagram").assertContentDescriptionEquals(
                "가상 도로 구조. $enteringSide 진입 차로가 이어진 뒤 끝에서 $joiningSide 본선과 연결됨. 차량의 현재 차로는 표시하지 않음."
            )
        } else compose.onNodeWithTag("diagram").assertDoesNotExist()
        compose.onNodeWithTag("small_window").assertDoesNotExist()
        val essentialTags = listOf("title", "message", "source", "phase") + if (state.showDiagram) listOf("diagram") else emptyList()
        essentialTags.forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag is clipped by $width x $height fixture: $bounds versus $surface",
                bounds.left >= surface.left && bounds.top >= surface.top &&
                    bounds.right <= surface.right && bounds.bottom <= surface.bottom)
            if (tag != "diagram") {
                val results = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    assertTrue(it(results))
                }
                assertTrue(results.isNotEmpty())
                results.forEach { assertFalse("$tag has text overflow: ${it.size}; ${it.layoutInput.constraints}", it.hasVisualOverflow) }
            }
        }
        if (state.settingsAllowed) listOf("play", "live").forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(76.dp).assertWidthIsAtLeast(76.dp)
        } else listOf("play", "live", "next", "reset", "demo", "theme", "permission").forEach { tag ->
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    @After fun saveScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.filesDir, "responsive-diagnostics/${name.methodName}")
        folder.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "device.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
