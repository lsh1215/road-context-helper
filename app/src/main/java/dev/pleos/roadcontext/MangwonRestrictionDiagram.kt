package dev.pleos.roadcontext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** Source-dated preview only. GPS controls visibility in the caller, never paint-boundary timing. */
@Composable
internal fun MangwonRestrictionDiagram(dark: Boolean, modifier: Modifier = Modifier, animate: Boolean = true,
    mirror: Boolean = false) {
    var progress by remember(mirror) { mutableFloatStateOf(.04f) }
    LaunchedEffect(animate, mirror) {
        if (animate) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                progress = (progress + (now - last) / (MangwonIllustrativeMotion.PERIOD_MS * 1_000_000f)) % 1f
                last = now
            }
        }
    }
    val ink = if (dark) Color(0xFFE8F2F8) else Color(0xFF173447)
    val muted = if (dark) Color(0xFFA5BACA) else Color(0xFF546E7F)
    val amber = if (dark) Color(0xFFFFC66D) else Color(0xFF996018)
    val mint = if (dark) Color(0xFF78E5CF) else Color(0xFF137868)
    Column(modifier.testTag("context_diagram")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val laneWidth = min(width * .145f, height * .51f)
            fun project(lane: Float, z: Float) = Offset(
                width * (if (mirror) .61f else .39f) + lane * (if (mirror) -1f else 1f) * laneWidth * (.16f + .84f * z),
                height * (.04f + .90f * z))
            val carWidth = min(laneWidth * .60f, with(density) { 132.dp.toPx() })
            val carHeight = carWidth * 1.04f
            Canvas(Modifier.fillMaxSize().testTag("context_route_example").semantics {
                contentDescription = if (mirror)
                    "왼쪽에서 합류한 뒤 실선에서는 차로 유지, 이후 점선 구간에서 오른쪽 진출 준비. 좌우 반전한 가상 도식이며 실제 구간 자료가 아님."
                else "망원 합류 후 지하차도 실선에서는 차로 유지, 이후 점선 구간에서 왼쪽 양화대교 진출 준비. 차로 수와 길이를 축약한 설명용 도식."
            }) {
                fun polygon(vararg points: Pair<Float, Float>) = Path().apply {
                    points.forEachIndexed { i, (lane, z) ->
                        val a = project(lane, z)
                        if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
                    }; close()
                }
                drawPath(polygon(-2f to .03f, 2f to .03f, 2f to 1f, -2f to 1f),
                    if (dark) Color(0xFF263744) else Color(0xFFD0DEE7))
                drawPath(polygon(-2f to .60f, 2f to .60f, 2f to 1f, -2f to 1f), amber.copy(alpha = .12f))
                drawPath(polygon(-2f to .12f, 2f to .12f, 2f to .60f, -2f to .60f), mint.copy(alpha = .12f))
                // Left exit is an outline, not an additional line cutting across the route.
                drawPath(polygon(-2f to .32f, -3.3f to .04f, -2.3f to .04f, -1f to .32f),
                    if (dark) Color(0xFF263744) else Color(0xFFD0DEE7))
                val marking = if (dark) Color(0xFFB7CAD7) else Color.White
                for (lane in -1..1) {
                    drawLine(marking, project(lane.toFloat(), .60f), project(lane.toFloat(), 1f), 3.dp.toPx())
                    for (i in 0..6) {
                        val z = .12f + i * .07f
                        drawLine(marking, project(lane.toFloat(), z), project(lane.toFloat(), z + .035f), 2.dp.toPx())
                    }
                }
                listOf(-2f, 2f).forEach { lane ->
                    drawLine(muted.copy(alpha = .65f), project(lane, .34f), project(lane, 1f), 2.dp.toPx())
                }
                val path = Path().apply {
                    val points = listOf(1.5f to .98f, 1.5f to .52f, -1.5f to .29f, -2.8f to .04f)
                    points.forEachIndexed { i, (lane, z) ->
                        val a = project(lane, z)
                        if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
                    }
                }
                drawPath(path, mint.copy(alpha = .20f), style = Stroke(laneWidth * .30f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, mint, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Canvas(Modifier.size(with(density) { carWidth.toDp() }, with(density) { carHeight.toDp() })
                .graphicsLayer {
                    val pose = MangwonIllustrativeMotion.pose(progress)
                    val center = project(pose.lane, pose.depth)
                    translationX = center.x - carWidth / 2
                    translationY = center.y - carHeight * .72f
                    alpha = pose.opacity
                    // No yaw: the car always faces forward, including lateral movement.
                }.testTag("context_vehicle").semantics {
                    contentDescription = "앞을 향한 차량 모형. 실선을 지난 뒤에만 옆으로 이동하는 설명용 반복 시연. 실제 차량 위치 아님."
                }) { drawRearVehicle(dark) }

            Column(Modifier.align(if (mirror) Alignment.TopStart else Alignment.TopEnd).fillMaxWidth(.30f).fillMaxHeight().padding(top = 4.dp),
                verticalArrangement = Arrangement.SpaceAround) {
                Text(if (mirror) "오른쪽 진출 연결" else "왼쪽 양화대교 진출", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                ZoneLabel("02  점선 구간", "짧은 진출 준비", "주변 차량 확인 후 이동", mint, dark, "mangwon_dashed")
                ZoneLabel(if (mirror) "01  실선 구간" else "01  지하차도 실선", "차로 유지", "이 구간에서는 변경 제한", amber, dark, "mangwon_solid")
            }
        }
        Text(if (mirror) "가상 좌우 반전 도식 · 실제 도로 구조 자료 아님" else "2026.06–07 로드뷰 기반 · 공사 중, 현재 차선은 현장 확인", color = muted,
            fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("marking_source"))
        Text("차로 수·길이 축약 / 차량 이동은 설명용 / 변경 가능 시점 판정 아님", color = muted,
            fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("context_example_notice"))
    }
}

@Composable
private fun ZoneLabel(title: String, action: String, detail: String, color: Color, dark: Boolean, tag: String) {
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = if (dark) .10f else .07f), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(action, color = color, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = if (dark) Color(0xFFBACBD6) else Color(0xFF4F6877), fontSize = 15.sp)
    }
}
