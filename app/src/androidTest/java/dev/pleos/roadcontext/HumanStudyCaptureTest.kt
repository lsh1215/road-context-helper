package dev.pleos.roadcontext

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Native, app-area-only research stimuli. No participants, human responses, SDK connection,
 * actual road approval, or AC-27 success is established by generating these images.
 * Fixture contract: verification/human-study/stimuli.json, agreed with the protocol owner.
 */
class HumanStudyCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appRightEntryA() = appEntry(MergeSide.RIGHT, false)
    @Test fun appRightEntryB() = appEntry(MergeSide.RIGHT, true)
    @Test fun appLeftEntryA() = appEntry(MergeSide.LEFT, false)
    @Test fun appLeftEntryB() = appEntry(MergeSide.LEFT, true)

    @Test fun appUnsupportedA() = captureApp("app-unsupported-a", statusState(
        "안내 대기", "도로 구조 자료를 준비하고 있습니다", "검증된 구간에서만 구조를 안내합니다.",
    ))
    @Test fun appUnsupportedB() = captureApp("app-unsupported-b", statusState(
        "안내 대기", "도로 자료를 확인하고 있습니다", "정보가 확인되면 구조 안내를 이어갑니다.",
    ))
    @Test fun appUncertainA() = captureApp("app-uncertain-a", statusState(
        "안내 대기", "위치와 경로를 확인하고 있습니다", "정보가 확인되면 구조 안내를 이어갑니다.",
    ))
    @Test fun appUncertainB() = captureApp("app-uncertain-b", statusState(
        "연결 확인 중", "위치 정보를 기다립니다", "연결이 확인되면 지원 구간을 살펴봅니다.",
    ))

    @Test fun baselineRightEntryA() = captureBaseline("baseline-right_entry-a", BaselineCopy("↖", "400m", "왼쪽 방향"))
    @Test fun baselineRightEntryB() = captureBaseline("baseline-right_entry-b", BaselineCopy("↖", "120m", "왼쪽 방향"))
    @Test fun baselineLeftEntryA() = captureBaseline("baseline-left_entry-a", BaselineCopy("↗", "400m", "오른쪽 방향"))
    @Test fun baselineLeftEntryB() = captureBaseline("baseline-left_entry-b", BaselineCopy("↗", "120m", "오른쪽 방향"))
    @Test fun baselineUnsupportedA() = captureBaseline("baseline-unsupported-a", BaselineCopy("↑", "500m", "직진"))
    @Test fun baselineUnsupportedB() = captureBaseline("baseline-unsupported-b", BaselineCopy("↑", "300m", "직진"))
    @Test fun baselineUncertainA() = captureBaseline("baseline-uncertain-a", BaselineCopy(
        null, null, "위치 확인 중", "위치 정보가 확인되면 안내합니다.",
    ))
    @Test fun baselineUncertainB() = captureBaseline("baseline-uncertain-b", BaselineCopy(
        null, null, "안내 연결 확인 중", "정보가 연결되면 안내합니다.",
    ))

    private fun appEntry(side: MergeSide, approaching: Boolean) {
        val sideId = side.name.lowercase()
        val form = if (approaching) "b" else "a"
        captureApp("app-${sideId}_entry-$form", RoadUiState(
            phase = if (approaching) "합류 구간 접근" else "미리 보는 도로 구조",
            title = if (approaching) "앞에서 차로가\n합쳐집니다" else "진입 차로는\n앞까지 이어집니다",
            message = if (approaching) "진입 차로가 끝에서 본선과 연결됩니다." else "진입 차로가 끝부분에서 본선과 연결됩니다.",
            detail = "도로 구조 설명이며, 현재 차로와 합류 가능 여부는 판단하지 않습니다.",
            distance = if (approaching) "약 120m 앞" else "약 400m 앞",
            simulation = true, sourceLabel = "가상 구간 시연", inputSource = DiagramSource.REPLAY_SYNTHETIC,
            showDiagram = true, settingsAllowed = false, playing = false, dark = false,
            diagramModel = DiagramModel(side, DiagramSource.REPLAY_SYNTHETIC,
                "synthetic:study-$sideId-$form", synthetic = true, approved = true),
        ))
    }

    private fun statusState(phase: String, title: String, message: String) = RoadUiState(
        phase = phase, title = title, message = message,
        detail = "정차 중 가상 구간 체험을 이용할 수 있습니다.",
        simulation = true, sourceLabel = "가상 구간 시연", inputSource = DiagramSource.REPLAY_SYNTHETIC,
        showDiagram = false, settingsAllowed = false, dark = false,
    )

    private fun captureApp(id: String, state: RoadUiState) {
        render { RoadScreen(state, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("source").assertTextEquals("가상 구간 시연")
        compose.onNodeWithTag("title").assertIsDisplayed().assertTextEquals(state.title)
        compose.onNodeWithTag("message").assertIsDisplayed().assertTextEquals(state.message)
        if (state.showDiagram) compose.onNodeWithTag("diagram").assertIsDisplayed()
        else compose.onNodeWithTag("diagram").assertDoesNotExist()
        val fixture = JSONObject()
            .put("phase", state.phase).put("title", state.title).put("message", state.message)
            .put("detail", state.detail).put("distance", state.distance ?: JSONObject.NULL)
            .put("mergeSide", state.diagramModel?.mergeSide?.name ?: JSONObject.NULL)
            .put("segmentId", state.diagramModel?.segmentId ?: JSONObject.NULL)
            .put("showDiagram", state.showDiagram).put("synthetic", true)
            .put("settingsAllowed", false).put("inputSource", "REPLAY_SYNTHETIC")
        save(id, "APP", fixture)
    }

    private fun captureBaseline(id: String, copy: BaselineCopy) {
        render { BaselineScreen(copy) }
        compose.onNodeWithTag("baseline_primary").assertIsDisplayed().assertTextEquals(copy.primary)
        compose.onNodeWithTag("baseline_source").assertTextEquals("평가용 예시")
        compose.onNodeWithTag("diagram").assertDoesNotExist()
        save(id, "BASELINE", JSONObject().put("header", "방향 안내").put("badge", "평가용 예시")
            .put("directionArrow", copy.arrow ?: JSONObject.NULL).put("distance", copy.distance ?: JSONObject.NULL)
            .put("primary", copy.primary).put("secondary", copy.secondary).put("brandedNavigation", false))
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            // The research contract intentionally fixes both scale and density, recording PNG px.
            // This is a native fixture, not a claim about physical display size or viewing distance.
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.requiredSize(1682.dp, 1210.dp).testTag("study_surface")) { content() }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            try { compose.onAllNodesWithTag("study_surface").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
        compose.waitForIdle()
    }

    private fun save(id: String, condition: String, fixture: JSONObject) {
        val bitmap = compose.onNodeWithTag("study_surface").captureToImage().asAndroidBitmap()
        assertEquals("Native capture width must match the shared study canvas", 1682, bitmap.width)
        assertEquals("Native capture height must match the shared study canvas", 1210, bitmap.height)
        val folder = DeviceTestSupport.outputDirectory("human-study-captures")
        val png = File(folder, "$id.png")
        png.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val hash = MessageDigest.getInstance("SHA-256").digest(png.readBytes()).joinToString("") { "%02x".format(it) }
        val metadata = JSONObject().put("id", id).put("condition", condition)
            .put("captureMethod", "Compose study_surface captureToImage; app area only")
            .put("capturedWidthPx", bitmap.width).put("capturedHeightPx", bitmap.height)
            .put("fixtureWidthDp", 1682).put("fixtureHeightDp", 1210)
            .put("density", 1.0).put("fontScale", 1.0).put("theme", "LIGHT")
            .put("sha256", hash).put("fixture", fixture)
            .put("sourceBuild", JSONObject.NULL).put("sourceRun", JSONObject.NULL)
            .put("reviewStatus", "PENDING").put("humanEvaluationStatus", "NOT_RUN")
        File(folder, "$id.json").writeText(metadata.toString(2))
    }
}

private data class BaselineCopy(val arrow: String?, val distance: String?, val primary: String, val secondary: String = "")

/** Test-only generic directional comparator. Not part of the product or an imitation of a brand. */
@Composable private fun BaselineScreen(copy: BaselineCopy) {
    val background = Color(0xFFF4F7FA)
    val ink = Color(0xFF17212B)
    val secondary = Color(0xFF475569)
    val accent = Color(0xFF07588B)
    val attention = Color(0xFF754400)
    MaterialTheme(colorScheme = lightColorScheme(background = background, surface = Color.White, onSurface = ink)) {
        Column(Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("방향 안내", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("평가용 예시", color = attention, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(IntrinsicSize.Max).testTag("baseline_source")
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, attention, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("방향 정보", color = secondary, fontSize = 22.sp, modifier = Modifier.weight(1f))
                copy.distance?.let { Text(it, color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(24.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    copy.arrow?.let { Text(it, color = accent, fontSize = 160.sp, lineHeight = 176.sp) }
                    Text(copy.primary, color = ink, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(IntrinsicSize.Max).testTag("baseline_primary"))
                    if (copy.secondary.isNotBlank()) Text(copy.secondary, color = ink, fontSize = 28.sp, lineHeight = 38.sp,
                        modifier = Modifier.width(IntrinsicSize.Max))
                }
            }
            // Match the real app's reserved footer area; no extra hints about the study answers.
            Text("주행 중에는 방향 안내만 표시합니다", color = secondary, fontSize = 20.sp)
        }
    }
}
