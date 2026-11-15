package dev.pleos.roadcontext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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

@Composable
internal fun LaneEndingDiagram(dark: Boolean, modifier: Modifier = Modifier, animate: Boolean = true,
    sourceMonth: String = "2026.07", pinkTarget: Boolean = false) {
    var progress by remember { mutableFloatStateOf(.04f) }
    LaunchedEffect(animate) {
        if (animate) {
            var previous = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                progress = (progress + (now - previous) / (LaneEndingMotion.PERIOD_MS * 1_000_000f)) % 1f
                previous = now
            }
        }
    }
    val ink = if (dark) Color(0xFFE9F4FA) else Color(0xFF18384B)
    val muted = if (dark) Color(0xFFA7BFCE) else Color(0xFF587384)
    val mint = if (dark) Color(0xFF72E9C8) else Color(0xFF128A70)
    val pink = if (dark) Color(0xFFFF8DB8) else Color(0xFFD83878)
    Column(modifier.testTag("context_diagram")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }; val h = with(density) { maxHeight.toPx() }
            val laneWidth = min(w * .23f, h * .53f)
            fun project(lane: Float, z: Float) = Offset(w * .42f + lane * laneWidth * (.22f + .78f * z), h * (.05f + .90f * z))
            val carWidth = min(laneWidth * .62f, with(density) { 144.dp.toPx() })
            val carHeight = carWidth * 1.04f
            Canvas(Modifier.fillMaxSize().testTag("context_route_example").semantics {
                contentDescription = if (pinkTarget) "응봉지하차도 출구의 진입 차로가 앞으로 이어지다가 좁아지며 왼쪽 분홍색 유도선 방면에 연결되는 도로 구조. 차로 수와 간격을 축약한 예시."
                    else "오른쪽 진입 차로가 앞까지 이어지다가 좁아지며 끝나고 본선에 연결되는 도로 구조."
            }) {
                fun shape(vararg vertices: Pair<Float, Float>) = Path().apply {
                    vertices.forEachIndexed { i, (lane, z) -> val a = project(lane, z)
                        if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y) }; close()
                }
                val surface = if (dark) Color(0xFF2B414F) else Color(0xFFCBDCE7)
                val edge = if (dark) Color(0xFF8BAABB) else Color(0xFF8CA8BA)
                val white = if (dark) Color(0xFFC1D3DD) else Color.White
                drawPath(shape(-1.5f to .04f, .5f to .04f, .5f to .28f, 1.5f to .62f, 1.5f to 1f, -1.5f to 1f),
                    Brush.verticalGradient(listOf(surface.copy(alpha = .38f), surface)))
                if (pinkTarget) {
                    // Directional landmarks, not detected current lanes or surveyed lane counts.
                    for (lane in listOf(-1f, 0f)) {
                        drawLine(pink.copy(alpha = .10f), project(lane, .96f), project(lane, .08f), 16.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(pink, project(lane, .96f), project(lane, .08f), 6.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                val route = Path().apply {
                    (listOf(1f to .98f, 1f to .65f, 0f to .28f) + if (pinkTarget) emptyList() else listOf(0f to .08f)).forEachIndexed { i, (lane,z) ->
                        val a = project(lane,z); if(i==0) moveTo(a.x,a.y) else lineTo(a.x,a.y)
                    }
                }
                drawPath(route, mint.copy(alpha = .20f), style = Stroke(laneWidth * .27f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(route, mint, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val rightEdge = Path().apply {
                    listOf(1.5f to 1f,1.5f to .62f,.5f to .28f,.5f to .04f).forEachIndexed { i,(lane,z) ->
                        val a=project(lane,z);if(i==0)moveTo(a.x,a.y)else lineTo(a.x,a.y)
                    }
                }
                drawPath(rightEdge, edge, style=Stroke(3.dp.toPx(), join=StrokeJoin.Round))
                drawLine(edge,project(-1.5f,.04f),project(-1.5f,1f),2.dp.toPx())
                for(i in 0..11) {
                    val z=.06f+i*.08f
                    drawLine(white,project(-.5f,z),project(-.5f,z+.035f),2.5.dp.toPx())
                    if(z>=.30f)drawLine(white,project(.5f,z),project(.5f,z+.035f),2.5.dp.toPx())
                }
                val end=project(.5f,.28f)
                drawCircle(mint.copy(alpha=.16f),17.dp.toPx(),end)
                drawCircle(mint,6.dp.toPx(),end)
            }
            Canvas(Modifier.size(with(density){carWidth.toDp()},with(density){carHeight.toDp()})
                .graphicsLayer {
                    val pose=LaneEndingMotion.pose(progress);val p=project(pose.lane,pose.depth)
                    translationX=p.x-carWidth/2;translationY=p.y-carHeight*.72f;alpha=pose.opacity
                }.testTag("context_vehicle").semantics { contentDescription="앞을 향한 차량이 진입 차로를 따라 진행한 뒤 본선에 합류하는 설명용 이동." }) { drawRearVehicle(dark) }
            if (pinkTarget) Column(Modifier.align(Alignment.TopStart).fillMaxWidth(.30f).padding(top=maxHeight*.12f, start=12.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("분홍색 유도선",color=pink,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("pink_target_label"))
                Text("내부순환로 방면",color=ink,fontSize=20.sp)
            }
            Column(Modifier.align(Alignment.TopEnd).fillMaxWidth(.29f).padding(top=maxHeight*.22f), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("차로 끝",color=mint,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("lane_end_label"))
                Text(if (pinkTarget) "왼쪽 합류 준비" else "여기서 본선과 연결",color=ink,fontSize=20.sp)
            }
            Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(.29f).padding(bottom=maxHeight*.12f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if (pinkTarget) "지하차도 출구" else "진입 차로",color=ink,fontSize=25.sp,fontWeight=FontWeight.Bold)
                Text("앞까지 이어집니다",color=muted,fontSize=20.sp,modifier=Modifier.testTag("lane_continues_label"))
            }
        }
        Text("$sourceMonth 로드뷰 기반 · 도로 구조 및 차량 이동 예시",color=muted,fontSize=15.sp,
            modifier=Modifier.fillMaxWidth().testTag("context_example_notice"),textAlign=TextAlign.Center)
    }
}
