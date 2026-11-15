package dev.pleos.roadcontext

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fixed road landmarks; only the longitudinal marker follows accepted SDK samples.
 * The car is on an illustrative entry-to-mainline path; lateral placement is NOT lane detection.
 * Interpolation ends at the received sample, never extrapolates and never repeats.
 */
@Composable
internal fun EungbongPositionDiagram(state: SdkRouteReplayVisual, dark: Boolean, modifier: Modifier, actualSdk: Boolean = true) {
    val from = state.laneEndFromMeters
    val to = state.laneEndToMeters
    val exit = state.tunnelExitMeters
    val along = state.alongMeters
    if (from == null || to == null || exit == null || along == null ||
        !from.isFinite() || !to.isFinite() || !exit.isFinite() || exit < 0 || exit >= from || from >= to) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("차로 끝 위치 자료를 확인하고 있습니다") }
        return
    }
    val secondFrom=state.secondLaneEndFromMeters ?: return
    val secondTo=state.secondLaneEndToMeters ?: return
    if (!secondFrom.isFinite() || !secondTo.isFinite() || secondFrom<=to || secondTo<=secondFrom) return
    val geometry=TwoStageLaneGeometry(from,to,secondFrom,secondTo)
    val second=along>to
    val progress = LaneEndZone(if(second) secondFrom else from, if(second) secondTo else to, exit).at(along) ?: return
    val displayed = animateFloatAsState(along.toFloat(), tween(350), label = "SDK longitudinal position").value
    val ink = if(dark) Color(0xFFEAF4FA) else Color(0xFF193649)
    val muted = if(dark) Color(0xFFA7BDCD) else Color(0xFF526E80)
    val mint = if(dark) Color(0xFF70E8CD) else Color(0xFF007C6C)
    val labelSurface = if(dark) Color(0xFF0E1923) else Color(0xFFF1F6FA)
    val amber = if(dark) Color(0xFFFFCC7B) else Color(0xFFAA610C)
    val pink = if(dark) Color(0xFFFF8DB8) else Color(0xFFD83878)
    Column(modifier.testTag("sdk_position_diagram"), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().background(mint.copy(alpha=.07f), RoundedCornerShape(18.dp)).padding(horizontal=24.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text(if(second) "② 남은 진입 차로 끝까지" else "① 오른쪽 진입 차로 끝까지",color=muted,fontSize=18.sp)
                Text(progress.label,color=ink,fontSize=34.sp,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("lane_end_distance"))
            }
            Column(horizontalAlignment=Alignment.End) {
                Text(if(actualSdk) "차량 진행 위치 · GPS" else "차량 이동 예시",color=mint,fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("차로 배치는 예시",color=muted,fontSize=17.sp)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density=LocalDensity.current
            val w=with(density){maxWidth.toPx()};val h=with(density){maxHeight.toPx()}
            val extent=state.endAlongMeters+45.0
            fun depth(m:Double)=(1.0-m.coerceIn(0.0,extent)/extent).toFloat()
            fun xy(lane:Float,m:Double):Offset {
                val z=depth(m);return Offset(w*.39f+lane*w*.118f*(.53f+.47f*z),h*(.13f+.74f*z))
            }
            fun y(m:Double)=xy(0f,m).y
            val diagramEnd=w*.87f
            Canvas(Modifier.fillMaxSize().testTag("sdk_road_geometry").semantics {
                contentDescription="두 진입 차로가 2개에서 1개, 이후 0개로 순서대로 끝나 본선에 합류하는 구조. 분홍색 유도선 방면과 오른쪽 차로 끝 범위를 구분. 인접 차로 구조를 축약함."
            }) {
                fun shape(vararg points:Pair<Float,Double>)=Path().apply { points.forEachIndexed { i,(lane,m)->val p=xy(lane,m);if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y) };close() }
                val surface=if(dark)Color(0xFF29414F)else Color(0xFFD4E2EA)
                val edge=if(dark)Color(0xFF8EACBD)else Color(0xFF8BA8BA)
                val boundary=listOf(2.5f to 0.0,2.5f to from,1.5f to to,1.5f to secondFrom,.5f to secondTo,.5f to extent)
                drawPath(shape(*((listOf(-1.5f to 0.0)+boundary+listOf(-1.5f to extent)).toTypedArray())),surface)
                drawPath(shape(.5f to 0.0,2.5f to 0.0,2.5f to from,1.5f to to,1.5f to secondFrom,.5f to secondTo),mint.copy(alpha=.12f))
                drawPath(shape(1.5f to from,2.5f to from,1.5f to to),amber.copy(alpha=.27f))
                drawPath(shape(.5f to secondFrom,1.5f to secondFrom,.5f to secondTo),amber.copy(alpha=.27f))
                val border=Path().apply { boundary.forEachIndexed { i,(lane,m)->val p=xy(lane,m);if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y) } }
                drawPath(border,edge,style=Stroke(3.dp.toPx(),join=StrokeJoin.Round))
                drawLine(edge,xy(-1.5f,0.0),xy(-1.5f,extent),2.dp.toPx())
                for(lane in listOf(-1f,0f))drawLine(pink,xy(lane,0.0),xy(lane,extent),5.dp.toPx(),StrokeCap.Round)
                for(m in 0..extent.toInt() step 25) {
                    drawLine(Color.White,xy(-.5f,m.toDouble()),xy(-.5f,(m+11).toDouble()),2.dp.toPx())
                    if(m<secondFrom-11)drawLine(Color.White,xy(.5f,m.toDouble()),xy(.5f,(m+11).toDouble()),2.dp.toPx())
                    if(m<from-11)drawLine(Color.White,xy(1.5f,m.toDouble()),xy(1.5f,(m+11).toDouble()),2.dp.toPx())
                }
                for ((a,b) in listOf(from to to, secondFrom to secondTo)) {
                    val p=xy(geometry.rightEdge((a+b)/2), (a+b)/2)
                    drawLine(amber,p,Offset(diagramEnd,p.y),2.dp.toPx())
                    drawCircle(amber,5.dp.toPx(),p)
                }
                val route=Path().apply { for(m in 0..extent.toInt() step 4) {
                    val p=xy(geometry.illustrativeLane(m.toDouble()),m.toDouble())
                    if(m==0)moveTo(p.x,p.y) else lineTo(p.x,p.y)
                } }
                drawPath(route,mint.copy(alpha=.22f),style=Stroke(20.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                drawPath(route,mint,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))

            }
            Column(Modifier.offset(x=8.dp,y=maxHeight*.02f)) {
                Text("분홍색 유도선",color=pink,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text("내부순환로 방면",color=muted,fontSize=17.sp)
            }
            Text("① 오른쪽 차로 끝",color=amber,fontSize=22.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.offset(x=maxWidth*.66f,y=with(density){y((from+to)/2).toDp()}-28.dp).background(labelSurface).padding(horizontal=8.dp).testTag("lane_end_region"))
            Text("② 남은 차로 끝",color=amber,fontSize=22.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.offset(x=maxWidth*.64f,y=with(density){y((secondFrom+secondTo)/2).toDp()}-28.dp).background(labelSurface).padding(horizontal=8.dp).testTag("second_lane_end_region"))
            Text("진입 2개 차로",color=muted,fontSize=21.sp,
                modifier=Modifier.align(Alignment.BottomEnd).padding(end=30.dp,bottom=30.dp))
            val carSize=112.dp
            Canvas(Modifier.size(carSize,carSize*1.04f).graphicsLayer {
                translationX=xy(geometry.illustrativeLane(displayed.toDouble()),displayed.toDouble()).x-carSize.toPx()/2
                translationY=y(displayed.toDouble())-carSize.toPx()*.52f
            }.testTag("sdk_position_vehicle").semantics {
                contentDescription="차량의 도로상 진행 위치. 차로 배치는 설명용 예시. 기준 경로 시작에서 ${along.toInt()}m. 현재 차로는 확인하지 않음."
            }) { drawRearVehicle(dark) }
        }
        Text("거리뷰 기반 끝 구간 · 거리 추정 · 인접 차로 구조 축약",color=muted,fontSize=14.sp,
            modifier=Modifier.align(Alignment.CenterHorizontally))
    }
}
