package dev.pleos.roadcontext

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pleos.roadcontext.core.GeoPoint
import kotlin.math.roundToInt

/** Visibility and longitudinal progress come from the SDK gate; no lane identification. */
data class SdkRouteReplayVisual(
    val roadName: String,
    val points: List<GeoPoint>,
    val vehicle: GeoPoint?,
    val headingDeg: Double?,
    val status: String,
    val alongMeters: Double?,
    val startAlongMeters: Double,
    val endAlongMeters: Double,
    val visible: Boolean,
    val receivedCount: Int,
    val junctionAlongMeters: Double? = null,
    val contextCase: String = "yanghwa",
    val laneEndFromMeters: Double? = null,
    val laneEndToMeters: Double? = null,
    val tunnelExitMeters: Double? = null,
    val secondLaneEndFromMeters: Double? = null,
    val secondLaneEndToMeters: Double? = null,
)

/** A single instruction and a perspective scene; the navigation app still owns the map. */
@Composable
fun SdkRouteReplayPanel(state: SdkRouteReplayVisual, dark: Boolean, modifier: Modifier = Modifier) {
    val ink = if (dark) Color(0xFFEDF5FA) else Color(0xFF192C3C)
    val secondary = if (dark) Color(0xFFA5B8C7) else Color(0xFF526979)
    val accent = if (dark) Color(0xFF82DECA) else Color(0xFF166E63)
    val validProgress = state.alongMeters?.isFinite() == true &&
        state.startAlongMeters.isFinite() && state.endAlongMeters.isFinite() &&
        state.endAlongMeters > state.startAlongMeters
    val active = state.visible && state.status == "GUIDANCE" && validProgress
    val roadCase = RoadReplayCase.fromProfile(state.contextCase)
    BoxWithConstraints(modifier.testTag("sdk_replay_panel")) {
        val landscape = maxWidth >= 1050.dp && maxWidth > maxHeight * 1.3f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active) {
                ConnectionHeading(state, roadCase, ink, secondary, landscape, Modifier.fillMaxWidth())
                if (roadCase?.isMangwon == true) Text(
                    if (roadCase == RoadReplayCase.MANGWON_SHORT_EXIT) "실선에서는 차로 유지 · 점선 구간에서 왼쪽 진출 준비"
                    else "진입 차로를 따라 진행하며 앞쪽 합류를 준비하세요",
                    color = ink, fontSize = 22.sp, lineHeight = 30.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("mangwon_context"))
                when (roadCase) {
                    RoadReplayCase.MANGWON_SHORT_EXIT -> MangwonRestrictionDiagram(dark, Modifier.weight(1f).fillMaxWidth())
                    RoadReplayCase.EUNGBONG -> EungbongPositionDiagram(state, dark, Modifier.weight(1f).fillMaxWidth())
                    RoadReplayCase.MANGWON_LANE_END -> LaneEndingDiagram(dark, Modifier.weight(1f).fillMaxWidth(),
                        sourceMonth = "2026.07", pinkTarget = false)
                    else -> ClusterRoadIllustration(dark, Modifier.weight(1f).fillMaxWidth(),
                        scenario = ClusterScenario.MERGE, mirror = false, notice = "연결 구조 예시 · 실제 차로 아님")
                }
                if (roadCase?.isMangwon == true) Text(
                    if (roadCase == RoadReplayCase.MANGWON_SHORT_EXIT) "합류 전에 출구 쪽을 확인하세요. 이동할 여유가 없으면 다음 경로를 이용하세요."
                    else "차로 끝과 주변 교통을 확인하며 합류를 준비하세요.",
                    color = secondary, fontSize = 18.sp, lineHeight = 26.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().testTag("sdk_replay_idle"), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(if (state.status == "PASSED") "안내 완료" else "도로 연결 안내", color = accent,
                            fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Text(if (state.status == "PASSED") "연결 구간을 지났습니다" else "필요한 구간에서\n안내합니다",
                            color = ink, fontSize = 34.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold)
                        Text(when (state.status) {
                            "PASSED" -> "다음 안내 전까지 기본 내비를 따라가세요."
                            "STALE", "INVALID", "JUMP" -> "위치를 확인하면 구간 안내를 이어갑니다."
                            "OUTSIDE", "WRONG_DIRECTION" -> "이 도로와 방향에는 안내할 구간이 없습니다."
                            else -> "연결 구간에 접근하면 도로 구조를 미리 보여드립니다."
                        }, color = secondary, fontSize = 22.sp, lineHeight = 32.sp)
                    }
                }
            }
            if (!active) Text("GPS 재생 · 도로 구조 예시", color = secondary,
                fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.testTag("sdk_replay_scope"))
        }
    }
}

@Composable
private fun ConnectionHeading(state: SdkRouteReplayVisual, roadCase: RoadReplayCase?, ink: Color, secondary: Color,
    wide: Boolean, modifier: Modifier) {
    val remaining = state.junctionAlongMeters?.takeIf { it.isFinite() }?.let { junction ->
        state.alongMeters?.let { junction - it }
    }?.takeIf { it > 0 }
    Column(modifier.testTag("sdk_replay_guidance"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (roadCase == RoadReplayCase.EUNGBONG) "응봉지하차도 출구 · 분홍색 유도선 합류" else if (roadCase?.isMangwon == true) "망원 합류부 · 내부순환로 → 강변북로 구리방향" else "양화대교 북단", color = secondary, fontSize = 18.sp, lineHeight = 26.sp,
            fontWeight = FontWeight.Medium)
        val message = if (remaining == null) "본선 연결 구간" else
            "${(remaining / 10).roundToInt().coerceAtLeast(1) * 10}m 앞 · 본선 연결"
        val contextualMessage = when (roadCase) {
            RoadReplayCase.MANGWON_SHORT_EXIT -> if (remaining != null) "합류 뒤 가까운 왼쪽 출구 · 미리 준비" else "양화대교 왼쪽 진출까지 연결 확인"
            RoadReplayCase.EUNGBONG -> when {
                state.secondLaneEndToMeters != null && (state.alongMeters ?: Double.NEGATIVE_INFINITY) > state.secondLaneEndToMeters -> "두 진입 차로의 합류 구간을 지났습니다"
                state.laneEndToMeters != null && (state.alongMeters ?: Double.NEGATIVE_INFINITY) > state.laneEndToMeters -> "남은 진입 차로도 앞에서 끝납니다"
                remaining == null || remaining <= 60 -> "먼저 오른쪽 진입 차로가 끝납니다"
                else -> "출구 뒤에도 진입 차로가 이어집니다"
            }
            RoadReplayCase.MANGWON_LANE_END -> if (remaining != null) "진입 차로는 앞까지 이어집니다" else "앞에서 차로가 끝나며 본선에 연결"
            else -> message
        }
        Text(contextualMessage, color = ink, fontSize = if (wide) 36.sp else 30.sp,
            lineHeight = if (wide) 46.sp else 40.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}
