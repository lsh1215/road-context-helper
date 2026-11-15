package dev.pleos.roadcontext

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Instrument-cluster-inspired illustration, not an ADAS/sensor visualization.
 * The caller owns the SDK visibility gate. The repeating vehicle is always an example.
 */
@Composable
internal fun ClusterRoadIllustration(
    dark: Boolean,
    modifier: Modifier = Modifier,
    scenario: ClusterScenario = ClusterScenario.MERGE,
    animate: Boolean = true,
    mirror: Boolean = false,
    notice: String = "연결 구조 예시 · 실제 차로 아님",
) {
    // Only an explicit scenario/playback change restarts the illustrative sequence.
    key(scenario, animate) {
        ClusterRoadScene(dark, modifier, scenario, animate, mirror, notice)
    }
}

@Composable
private fun ClusterRoadScene(
    dark: Boolean,
    modifier: Modifier,
    scenario: ClusterScenario,
    animate: Boolean,
    mirror: Boolean,
    notice: String,
) {
    val shift: State<Float>
    val opacity: State<Float>
    val roadPhase: State<Float>
    if (animate) {
        val animation = rememberInfiniteTransition(label = "cluster-lane-example")
        val sequence = scenario == ClusterScenario.SHORT_EXIT
        val cycle = if (sequence) 2850 else 1450
        shift = animation.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
            durationMillis = cycle
            0f at 0
            0f at 100 using FastOutSlowInEasing
            if (sequence) {
                (1f / 3f) at 850
                (1f / 3f) at 1000 using FastOutSlowInEasing
                (2f / 3f) at 1750
                (2f / 3f) at 1900 using FastOutSlowInEasing
                1f at 2650
                1f at cycle
            } else {
                1f at 1100
                1f at cycle
            }
        }), label = "cluster-lateral-shift")
        opacity = animation.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
            durationMillis = cycle
            0f at 0
            1f at 100
            1f at (cycle - 150)
            0f at (cycle - 50)
            0f at cycle
        }), label = "cluster-example-opacity")
        roadPhase = animation.animateFloat(0f, 1f,
            infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "cluster-road-flow")
    } else {
        shift = remember { mutableFloatStateOf(0f) }
        opacity = remember { mutableFloatStateOf(1f) }
        roadPhase = remember { mutableFloatStateOf(0f) }
    }
    val description = if (mirror) "오른쪽에서 본선 합류 후 왼쪽 출구로 이어지는 연결 관계. 차로 수와 간격은 축약한 도식." else scenario.roadDescription
    val caption = if (dark) Color(0xFFA7B9C9) else Color(0xFF526A7B)

    Column(modifier.testTag("context_diagram")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Keep the perspective scene at a sensible width even in a full-width app window.
            BoxWithConstraints(Modifier.widthIn(max = 1180.dp).fillMaxHeight().fillMaxWidth().clipToBounds()) {
                val density = LocalDensity.current
                val width = with(density) { maxWidth.toPx() }
                val height = with(density) { maxHeight.toPx() }
                val laneWidth = when (scenario) {
                    ClusterScenario.SHORT_EXIT -> min(width * .175f, height * .40f)
                    else -> min(width * .245f, height * .47f)
                }
                val horizon = height * .10f
                val floor = height * .95f
                val depth = .79f
                fun project(lane: Float, z: Float): Offset {
                    val scale = .07f + .93f * z
                    return Offset(width * (if (scenario == ClusterScenario.MERGE) .45f else .5f) + lane * (if (mirror) -1f else 1f) * laneWidth * scale,
                        horizon + (floor - horizon) * z)
                }
                val carWidth = (laneWidth * (.07f + .93f * depth) * .83f)
                    .coerceAtMost(with(density) { 160.dp.toPx() })
                val carHeight = carWidth * 1.04f
                Canvas(Modifier.fillMaxSize().testTag("context_route_example").semantics {
                    contentDescription = "${description} 녹색 면은 설명용 경로이며 실제 차로 인식이나 차량 제어 상태가 아님."
                }) {
                    if (size.width > 0f && size.height > 0f) {
                        if (scenario == ClusterScenario.MERGE) {
                            drawClusterRoad(dark, roadPhase.value, ::project)
                        } else {
                            drawScenarioRoad(dark, roadPhase.value, scenario, ::project)
                        }
                    }
                }
                Canvas(Modifier
                    .size(with(density) { carWidth.toDp() }, with(density) { carHeight.toDp() })
                    .graphicsLayer {
                        val progress = shift.value
                        val carCenter = when (scenario) {
                            ClusterScenario.MERGE, ClusterScenario.EXIT_ONLY -> project(1f - progress, depth)
                            ClusterScenario.SHORT_EXIT -> project(-1.5f + 3f * progress, depth)
                        }
                        translationX = carCenter.x - carWidth / 2
                        translationY = carCenter.y - carHeight * .72f
                        alpha = opacity.value
                        // A lane-change explanation translates horizontally, never rotates.
                    }
                    .testTag("context_vehicle").semantics {
                        contentDescription = "${description} 앞을 향한 차량 모형의 이동 예시. 실제 차량 위치나 자동 차선 변경 상태가 아님."
                    }) {
                    drawRearVehicle(dark)
                }
            }
        }
        Text(notice, color = caption,
            fontSize = 20.sp, lineHeight = 28.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                .testTag("context_example_notice"))
    }
}

private fun DrawScope.drawClusterRoad(
    dark: Boolean,
    flow: Float,
    project: (Float, Float) -> Offset,
) {
    val roadNear = if (dark) Color(0xFF263C4B) else Color(0xFFB9CDDA)
    val roadFar = if (dark) Color(0xFF152635) else Color(0xFFE8F1F7)
    val edge = if (dark) Color(0xFF769DB3) else Color(0xFF91ACBE)
    val line = if (dark) Color(0xFFAAC8DA) else Color(0xFFF7FCFF)
    val mint = if (dark) Color(0xFF4BE7B9) else Color(0xFF12B98C)
    val cyan = if (dark) Color(0xFF53BFEA) else Color(0xFF70AFCC)

    fun polygon(vararg vertices: Pair<Float, Float>): Path = Path().apply {
        vertices.forEachIndexed { index, (lane, depth) ->
            val p = project(lane, depth)
            if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    fun path(vararg vertices: Pair<Float, Float>): Path = Path().apply {
        vertices.forEachIndexed { index, (lane, depth) ->
            val p = project(lane, depth)
            if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }
    // Broad, quiet illumination provides depth without a framed diagram or ornamental gauges.
    drawRect(Brush.radialGradient(
        listOf((if (dark) Color(0xFF28607A) else Color(0xFFB7E0F0)).copy(alpha = .25f), Color.Transparent),
        center = Offset(size.width * .45f, size.height * .43f), radius = size.width * .55f))

    val main = polygon(-1.5f to .03f, .5f to .03f, .5f to 1f, -1.5f to 1f)
    val entry = polygon(.5f to .31f, 1.5f to .60f, 1.5f to 1f, .5f to 1f)
    val roadBrush = Brush.verticalGradient(listOf(roadFar.copy(alpha = .15f), roadFar, roadNear),
        startY = size.height * .10f, endY = size.height)
    drawPath(main, roadBrush)
    drawPath(entry, Brush.verticalGradient(listOf(roadFar.copy(alpha = .3f), roadNear.copy(alpha = .72f)),
        startY = size.height * .3f, endY = size.height))

    // One destination lane, lit as a surface. Its boundaries converge at the horizon.
    val destinationLane = polygon(-.47f to .06f, .47f to .06f, .47f to 1f, -.47f to 1f)
    drawPath(destinationLane, Brush.verticalGradient(
        listOf(mint.copy(alpha = 0f), mint.copy(alpha = .11f), mint.copy(alpha = .25f)),
        startY = size.height * .12f, endY = size.height))

    // An angular corridor links the entry lane to the target. No decorative curved ribbon.
    val corridor = polygon(
        .80f to .96f, .80f to .79f, -.20f to .50f, -.20f to .18f,
        .20f to .18f, .20f to .48f, 1.20f to .77f, 1.20f to .96f,
    )
    drawPath(corridor, Brush.verticalGradient(
        listOf(mint.copy(alpha = .02f), mint.copy(alpha = .46f), mint.copy(alpha = .20f)),
        startY = size.height * .2f, endY = size.height))
    drawPath(path(1f to .95f, 1f to .78f, 0f to .49f, 0f to .20f),
        mint.copy(alpha = .65f), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter))

    // Edge markings taper in width naturally through the projected shoulder polygons.
    drawPath(polygon(-1.53f to .05f, -1.50f to .05f, -1.50f to 1f, -1.53f to 1f),
        edge.copy(alpha = if (dark) .75f else .90f))
    drawPath(path(.5f to .06f, .5f to .31f, 1.5f to .60f, 1.5f to 1f),
        cyan.copy(alpha = if (dark) .64f else .82f), style = Stroke(2.dp.toPx(), join = StrokeJoin.Miter))
    drawPath(path(.5f to .60f, .5f to 1f), line.copy(alpha = .65f), style = Stroke(2.dp.toPx()))

    // The restrained dash flow is illustrative movement, never speed telemetry.
    for (index in -1..7) {
        val z = (index + flow) / 7f
        val start = z.coerceAtLeast(.06f)
        val end = (z + .055f).coerceAtMost(1f)
        if (end <= start || start >= 1f) continue
        val strength = ((start - .04f) / .35f).coerceIn(0f, 1f)
        drawPath(polygon(-.515f to start, -.485f to start, -.485f to end, -.515f to end),
            line.copy(alpha = .75f * strength))
    }

    // A single distant chevron makes forward direction legible without labeling every line.
    val arrowDepth = .36f
    val arrow = path(-.13f to (arrowDepth + .055f), 0f to arrowDepth, .13f to (arrowDepth + .055f))
    drawPath(arrow, mint.copy(alpha = .9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Miter))

    // Fade the far and near edges into the surrounding dashboard rather than ending in a box.
    val surround = if (dark) Color(0xFF0E1923) else Color(0xFFF1F6FA)
    drawRect(Brush.verticalGradient(listOf(surround, surround.copy(alpha = 0f)),
        startY = 0f, endY = size.height * .25f), size = Size(size.width, size.height * .25f))
    drawRect(Brush.verticalGradient(listOf(surround.copy(alpha = 0f), surround),
        startY = size.height * .91f, endY = size.height),
        topLeft = Offset(0f, size.height * .91f), size = Size(size.width, size.height * .09f))
}

/** Shared visual grammar, with genuinely different road topology for each example. */
private fun DrawScope.drawScenarioRoad(
    dark: Boolean,
    flow: Float,
    scenario: ClusterScenario,
    project: (Float, Float) -> Offset,
) {
    val near = if (dark) Color(0xFF263C4B) else Color(0xFFB9CDDA)
    val far = if (dark) Color(0xFF152635) else Color(0xFFE8F1F7)
    val line = if (dark) Color(0xFFAAC8DA) else Color(0xFFF7FCFF)
    val mint = if (dark) Color(0xFF4BE7B9) else Color(0xFF12B98C)
    val cyan = if (dark) Color(0xFF53BFEA) else Color(0xFF70AFCC)
    fun shape(points: List<Pair<Float, Float>>, closed: Boolean = false) = Path().apply {
        points.forEachIndexed { index, (lane, depth) ->
            val p = project(lane, depth)
            if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        if (closed) close()
    }
    val roadBrush = Brush.verticalGradient(listOf(far.copy(alpha = .15f), far, near),
        startY = size.height * .1f, endY = size.height)
    fun road(vararg points: Pair<Float, Float>) = drawPath(shape(points.toList(), true), roadBrush)
    fun edge(vararg points: Pair<Float, Float>) = drawPath(shape(points.toList()),
        cyan.copy(alpha = if (dark) .65f else .85f),
        style = Stroke(2.dp.toPx(), join = StrokeJoin.Miter))
    fun dashes(lane: Float, nearDepth: Float = 1f, farDepth: Float = .06f) {
        for (index in -1..8) {
            val start = ((index + flow) / 8f).coerceAtLeast(farDepth)
            val end = ((index + flow) / 8f + .05f).coerceAtMost(nearDepth)
            if (end <= start || start >= nearDepth) continue
            val alpha = ((start - .04f) / .35f).coerceIn(0f, 1f) * .7f
            drawPath(shape(listOf(lane - .015f to start, lane + .015f to start,
                lane + .015f to end, lane - .015f to end), true), line.copy(alpha = alpha))
        }
    }
    fun route(vararg vertices: Pair<Float, Float>) {
        val points = vertices.toList()
        // Constant lane-relative width naturally tapers into perspective. Miter joins keep
        // the route angular and legible, rather than suggesting a curved driving trajectory.
        val ribbon = points.map { (x, z) -> x - .19f to z } +
            points.asReversed().map { (x, z) -> x + .19f to z }
        drawPath(shape(ribbon, true), Brush.verticalGradient(
            listOf(mint.copy(alpha = .04f), mint.copy(alpha = .42f), mint.copy(alpha = .20f)),
            startY = size.height * .1f, endY = size.height))
        drawPath(shape(points), mint.copy(alpha = .75f),
            style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    }
    fun laneGlow(vararg points: Pair<Float, Float>) = drawPath(shape(points.toList(), true),
        Brush.verticalGradient(listOf(mint.copy(alpha = 0f), mint.copy(alpha = .18f)),
            startY = size.height * .12f, endY = size.height))
    fun arrow(lane: Float, depth: Float) = drawPath(shape(listOf(
        lane - .13f to depth + .055f, lane to depth, lane + .13f to depth + .055f)),
        mint.copy(alpha = .9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

    drawRect(Brush.radialGradient(
        listOf((if (dark) Color(0xFF28607A) else Color(0xFFB7E0F0)).copy(alpha = .25f), Color.Transparent),
        center = Offset(size.width * .5f, size.height * .43f), radius = size.width * .55f))
    when (scenario) {
        ClusterScenario.SHORT_EXIT -> {
            // Four mainline lanes, a left entry taper in the foreground, and a distinct
            // right exit above. Three short straight runs separate the lane transitions.
            road(-2f to .04f, 2f to .04f, 2f to 1f, -2f to 1f)
            road(-2f to .62f, -1f to .62f, -2f to 1f, -2.85f to 1f)
            // The exit joins the mainline only between these two boundary points.
            // Starting its inner edge inside a lane crossed the far mainline edge.
            road(2f to .38f, 3.65f to .04f, 2.65f to .04f, 2f to .18f)
            laneGlow(1.03f to .27f, 1.97f to .27f, 1.97f to 1f, 1.03f to 1f)
            route(-1.5f to .97f, -1.5f to .88f, -.5f to .73f, -.5f to .66f,
                .5f to .51f, .5f to .44f, 1.5f to .29f, 1.5f to .24f, 3.1f to .06f)
            edge(-2f to .05f, -2f to .62f, -2.85f to 1f)
            edge(2f to 1f, 2f to .38f, 3.65f to .04f)
            // At this compressed horizon, the outer silhouette is sufficient.
            // Internal fork edges competed with the route centerline.
            dashes(-1f); dashes(0f); dashes(1f)
        }
        ClusterScenario.EXIT_ONLY -> {
            road(-1.5f to .04f, .5f to .04f, .5f to 1f, -1.5f to 1f)
            road(.5f to 1f, 1.5f to 1f, 1.5f to .56f, 3f to .05f,
                2f to .05f, .5f to .56f)
            laneGlow(-.47f to .05f, .47f to .05f, .47f to 1f, -.47f to 1f)
            route(1f to .97f, 1f to .85f, 0f to .60f, 0f to .15f)
            edge(-1.5f to .05f, -1.5f to 1f)
            edge(.5f to .05f, .5f to .56f, 2f to .05f)
            edge(1.5f to 1f, 1.5f to .56f, 3f to .05f)
            dashes(-.5f); dashes(.5f, farDepth = .56f)
            arrow(0f, .32f)
        }
        ClusterScenario.MERGE -> Unit // Kept in its original renderer for SDK-demo parity.
    }
    val surround = if (dark) Color(0xFF0E1923) else Color(0xFFF1F6FA)
    drawRect(Brush.verticalGradient(listOf(surround, surround.copy(alpha = 0f)),
        startY = 0f, endY = size.height * .25f), size = Size(size.width, size.height * .25f))
    drawRect(Brush.verticalGradient(listOf(surround.copy(alpha = 0f), surround),
        startY = size.height * .91f, endY = size.height),
        topLeft = Offset(0f, size.height * .91f), size = Size(size.width, size.height * .09f))
}

/** Rear-view vehicle with visible roof and hatch planes, always facing the road horizon. */
internal fun DrawScope.drawRearVehicle(dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    fun shape(vararg vertices: Pair<Float, Float>) = Path().apply {
        vertices.forEachIndexed { index, (x, y) ->
            if (index == 0) moveTo(w * x, h * y) else lineTo(w * x, h * y)
        }
        close()
    }
    // Contact shadow, wheels and dark lower body anchor the vehicle on the road plane.
    drawOval(Brush.radialGradient(listOf(Color(0xFF0B2434).copy(alpha = .36f), Color.Transparent),
        center = Offset(w * .5f, h * .83f), radius = w * .54f),
        topLeft = Offset(-w * .04f, h * .68f), size = Size(w * 1.08f, h * .27f))
    listOf(.13f, .72f).forEach { x ->
        drawRoundRect(Color(0xFF122331), Offset(w * x, h * .62f),
            Size(w * .15f, h * .22f), CornerRadius(w * .035f))
    }
    val shell = shape(.22f to .28f, .30f to .14f, .70f to .14f, .78f to .28f,
        .88f to .54f, .88f to .72f, .81f to .82f, .19f to .82f, .12f to .72f, .12f to .54f)
    drawPath(shell, Brush.horizontalGradient(listOf(Color(0xFF738F9F), Color(0xFFEAF3F8),
        Color(0xFFF9FCFE), Color(0xFF9BB3C3))))
    drawPath(shell, Color(0xFF5F7A8C), style = Stroke(w * .014f))

    // The roof is a plane, not a top-down cartoon hood. Glass sits below its rear edge.
    drawPath(shape(.30f to .14f, .70f to .14f, .76f to .29f, .24f to .29f),
        Brush.verticalGradient(listOf(Color(0xFFF7FBFE), Color(0xFFB4CCDB)),
            startY = h * .14f, endY = h * .29f))
    drawLine(Color.White.copy(alpha = .88f), Offset(w * .32f, h * .16f),
        Offset(w * .68f, h * .16f), w * .012f, cap = StrokeCap.Round)
    val glass = shape(.25f to .30f, .75f to .30f, .81f to .51f, .19f to .51f)
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF45697D), Color(0xFF152F43)),
        startY = h * .30f, endY = h * .52f))
    drawPath(shape(.29f to .31f, .71f to .31f, .73f to .36f, .27f to .36f),
        Color(0xFFB8D5E4).copy(alpha = .24f))
    drawLine(Color(0xFF0D2535), Offset(w * .20f, h * .53f), Offset(w * .80f, h * .53f), w * .022f)

    // Rear hatch, thin signature lamps, inset plate and separated bumper surfaces.
    drawPath(shape(.17f to .54f, .83f to .54f, .85f to .71f, .78f to .76f, .22f to .76f, .15f to .71f),
        Brush.verticalGradient(listOf(Color(0xFFF6FAFC), Color(0xFF9EB8C9)),
            startY = h * .54f, endY = h * .76f))
    drawLine(Color(0xFF6B8797).copy(alpha = .65f), Offset(w * .18f, h * .57f),
        Offset(w * .82f, h * .57f), w * .009f)
    drawRoundRect(Color(0xFF793C48), Offset(w * .17f, h * .59f),
        Size(w * .66f, h * .032f), CornerRadius(w * .01f))
    drawRoundRect(Color(0xFFF18E93), Offset(w * .18f, h * .59f),
        Size(w * .64f, h * .013f), CornerRadius(w * .005f))
    drawRoundRect(Color(0xFF6D8898), Offset(w * .39f, h * .665f),
        Size(w * .22f, h * .058f), CornerRadius(w * .014f))
    drawRoundRect(Color(0xFFE0EBF2), Offset(w * .405f, h * .674f),
        Size(w * .19f, h * .04f), CornerRadius(w * .008f))
    drawPath(shape(.18f to .75f, .82f to .75f, .78f to .81f, .22f to .81f),
        Brush.verticalGradient(listOf(Color(0xFF4F6B7B), Color(0xFF193241)),
            startY = h * .75f, endY = h * .81f))
    drawLine(Color(0xFFE9F4FA).copy(alpha = if (dark) .65f else .92f),
        Offset(w * .24f, h * .77f), Offset(w * .76f, h * .77f), w * .012f)
    listOf(.10f, .83f).forEach { x ->
        drawRoundRect(Color(0xFF829EAF), Offset(w * x, h * .43f),
            Size(w * .07f, h * .045f), CornerRadius(w * .015f))
    }
}
