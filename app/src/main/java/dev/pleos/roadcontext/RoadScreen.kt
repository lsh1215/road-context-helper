package dev.pleos.roadcontext

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class RoadPalette(val background: Color, val surface: Color, val ink: Color,
    val secondary: Color, val accent: Color, val muted: Color, val attention: Color, val illustrationRoute: Color)

private fun roadPalette(dark: Boolean) = if (dark) RoadPalette(
    Color(0xFF111820), Color(0xFF1B2733), Color(0xFFF4F7FA),
    Color(0xFFBBC7D4), Color(0xFF8AC7FF), Color(0xFF94A3B8), Color(0xFFFFD58A), Color(0xFFFF80BC),
) else RoadPalette(
    Color(0xFFF4F7FA), Color.White, Color(0xFF17212B),
    Color(0xFF475569), Color(0xFF07588B), Color(0xFF526277), Color(0xFF754400), Color(0xFFC52C78),
)

/** The side occupied by the entering lane, relative to the main road in travel direction. */
enum class MergeSide { LEFT, RIGHT }

enum class DiagramSource { REAL_SDK, REPLAY_SYNTHETIC, REPLAY_CAPTURED }

/** A presentation model supplied only after the data layer validates the road evidence. */
data class DiagramModel(
    val mergeSide: MergeSide,
    val source: DiagramSource,
    val segmentId: String,
    val synthetic: Boolean,
    val approved: Boolean = false,
)

data class RoadUiState(
    val title: String = "앞으로의 길을, 미리 이해하세요",
    val message: String = "복잡한 진입로의 연결 구조를 짧게 알려드립니다.",
    val phase: String = "안내 대기",
    val sourceLabel: String = "실제 입력 모드",
    val detail: String = "위치와 지원 구간을 확인하고 있습니다",
    val distance: String? = null,
    val showDiagram: Boolean = false,
    val simulation: Boolean = false,
    val playing: Boolean = false,
    val settingsAllowed: Boolean = false,
    val dark: Boolean = false,
    val setup: Boolean = false,
    val diagnostic: String = "",
    val inputSource: DiagramSource? = null,
    val diagramModel: DiagramModel? = null,
    val permissionRequestPending: Boolean = false,
    val sdkReplay: SdkRouteReplayVisual? = null,
    val clusterDemo: Boolean = false,
    val clusterDemoProfile: String? = null,
)

private fun RoadUiState.resolvedSource(): DiagramSource = inputSource ?: when {
    sourceLabel.contains("기록") -> DiagramSource.REPLAY_CAPTURED
    simulation -> DiagramSource.REPLAY_SYNTHETIC
    else -> DiagramSource.REAL_SDK
}

/** Missing/invalid live evidence must never fall back to the synthetic topology. */
internal fun RoadUiState.approvedDiagram(): DiagramModel? {
    if (!showDiagram) return null
    val source = resolvedSource()
    if (simulation != (source != DiagramSource.REAL_SDK)) return null
    val model = diagramModel ?: if (source == DiagramSource.REPLAY_SYNTHETIC) DiagramModel(
        MergeSide.RIGHT, source, "synthetic:merge-right-v1", synthetic = true, approved = true,
    ) else return null
    if (!model.approved || model.segmentId.isBlank() || model.source != source) return null
    if (model.synthetic != (source == DiagramSource.REPLAY_SYNTHETIC)) return null
    if (!model.synthetic && model.segmentId.startsWith("synthetic:")) return null
    return model
}

@Composable fun RoadScreen(state: RoadUiState, onDemo: () -> Unit, onLive: () -> Unit,
    onPlay: () -> Unit, onNext: () -> Unit, onReset: () -> Unit, onTheme: () -> Unit, onSdkReplay: (() -> Unit)? = null,
    onClusterDemo: (() -> Unit)? = null) {
    val palette = roadPalette(state.dark)
    val scheme = if (state.dark) darkColorScheme(primary = palette.accent, onPrimary = palette.background,
        background = palette.background, surface = palette.surface, onSurface = palette.ink)
    else lightColorScheme(primary = palette.accent, onPrimary = Color.White,
        background = palette.background, surface = palette.surface, onSurface = palette.ink)
    MaterialTheme(colorScheme = scheme) {
        if (state.clusterDemo) {
            ClusterDemoGallery(state.dark, state.settingsAllowed, state.playing,
                onPlay, onLive, onSdkReplay, state.clusterDemoProfile)
            ObserveRoadComposition(state, "outer", if (state.settingsAllowed) "controls" else "restricted")
            return@MaterialTheme
        }
        state.sdkReplay?.let { replay ->
            SdkReplayScreen(replay, state.dark, state.settingsAllowed,
                onRestart = { onSdkReplay?.invoke() }, onExit = onLive, onScenarios = onClusterDemo)
            ObserveRoadComposition(state, "outer", if (state.settingsAllowed) "controls" else "restricted")
            return@MaterialTheme
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.background).safeDrawingPadding()) {
            val wide = maxWidth >= 900.dp && maxHeight >= 480.dp
            val tight = maxHeight < 740.dp || LocalDensity.current.fontScale > 1.3f
            val outerPadding = if (tight) 16.dp else if (wide) 32.dp else 24.dp
            val inlineHeader = wide || maxWidth >= 560.dp
            Column(Modifier.fillMaxSize().padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(if (tight) 16.dp else 24.dp)) {
                Header(state, palette, inlineHeader)
                GuidanceStatus(state, palette, wide)
                GuidanceContent(state, palette, wide, Modifier.weight(1f).fillMaxWidth())
                if (state.settingsAllowed) {
                    ParkedActions(state, palette, wide, tight, onDemo, onLive, onPlay, onNext, onReset, onTheme)
                    if (BuildConfig.DEBUG && onClusterDemo != null && !state.permissionRequestPending)
                        TextButton(onClick = onClusterDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("cluster_demo")) {
                            Text("도로 상황 데모", fontSize = 20.sp)
                        }
                    if(BuildConfig.DEBUG && onSdkReplay!=null && !state.simulation && !state.setup)
                        TextButton(onClick=onSdkReplay,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("sdk_replay")) {
                            Text("실도로 GPS 시험",fontSize=20.sp)
                        }
                } else if (!tight) {
                    Text("주행 중에는 구조 안내만 표시합니다", color = palette.secondary, fontSize = 20.sp)
                }
            }
            ObserveRoadComposition(state, "outer", if (state.settingsAllowed) "controls" else "restricted")
        }
    }
}

@Composable private fun GuidanceStatus(state: RoadUiState, palette: RoadPalette, wide: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(state.phase, color = palette.accent, fontSize = 22.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).testTag("phase"))
        // This is a stage indicator, not GPS-derived progress or an instruction to merge.
        if (wide && (state.approvedDiagram() != null || state.phase == "구간 통과")) {
            val current = when (state.phase) { "합류 구간 접근" -> 1; "구간 통과" -> 2; else -> 0 }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("예고", "접근", "통과").forEachIndexed { index, label ->
                    val selected = index == current
                    Text("${index + 1}  $label", color = if (selected) palette.accent else palette.secondary,
                        fontSize = 20.sp, lineHeight = 28.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.background(if (selected) palette.surface else Color.Transparent,
                            RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        state.distance?.let {
            Text(it, color = palette.ink, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("distance"))
        }
    }
}

@Composable private fun Header(state: RoadUiState, palette: RoadPalette, wide: Boolean) {
    if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("초행길", color = palette.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        SourceBadge(state, palette)
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("초행길", color = palette.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        SourceBadge(state, palette)
    }
}

@Composable private fun SourceBadge(state: RoadUiState, palette: RoadPalette) {
    val label = when (state.resolvedSource()) {
        DiagramSource.REPLAY_CAPTURED -> "기록 재생"
        DiagramSource.REPLAY_SYNTHETIC -> "가상 구간 시연"
        DiagramSource.REAL_SDK -> state.sourceLabel
    }
    val color = if (state.simulation) palette.attention else palette.secondary
    Text(label, color = color, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.width(IntrinsicSize.Max).testTag("source").background(palette.surface, RoundedCornerShape(12.dp))
            .border(1.dp, color, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable private fun GuidanceContent(state: RoadUiState, palette: RoadPalette, wide: Boolean, modifier: Modifier) {
    val diagram = state.approvedDiagram()
    val canDraw = diagram != null
    BoxWithConstraints(modifier) {
        val availableHeight = maxHeight
        val availableWidth = maxWidth
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val measurer = rememberTextMeasurer()
        val textWidth = with(density) { maxWidth.roundToPx() }
        val titleHeight = measurer.measure(state.title, MaterialTheme.typography.bodyLarge.copy(
            fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
            constraints = Constraints(maxWidth = textWidth)).size.height
        val messageHeight = measurer.measure(state.message, MaterialTheme.typography.bodyLarge.copy(
            fontSize = 28.sp, lineHeight = 38.sp),
            constraints = Constraints(maxWidth = textWidth)).size.height
        val explanationHeight = with(density) { (titleHeight + messageHeight).toDp() } + 16.dp
        // Reserve real road canvas height plus labels/padding; do not discard diagrams using
        // a fixed global height threshold unrelated to the actual message layout.
        val minimumDiagramHeight = 168.dp + (88 * fontScale).dp
        val roomForDiagram = if (wide) availableHeight >= minimumDiagramHeight
            else availableHeight >= minimumDiagramHeight + explanationHeight + 20.dp
        if (diagram != null && roomForDiagram) {
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val diagramWeight = if (availableWidth >= 1400.dp) .65f else .60f
                DiagramPanel(diagram, palette, Modifier.weight(diagramWeight).fillMaxHeight(),
                    playing = state.playing && state.settingsAllowed, phase = state.phase)
                Explanation(state, palette, Modifier.weight(1f - diagramWeight),
                    showDetail = availableHeight >= 500.dp, featured = availableHeight >= 500.dp)
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DiagramPanel(diagram, palette, Modifier.weight(1f).fillMaxWidth(),
                    playing = state.playing && state.settingsAllowed, phase = state.phase)
                Explanation(state, palette, Modifier.fillMaxWidth(), showDetail = false)
            }
        } else {
            val showScope = wide && maxWidth >= 1400.dp && availableHeight >= 500.dp
            Row(Modifier.fillMaxSize().background(palette.surface, RoundedCornerShape(28.dp)).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (availableHeight >= 440.dp) Text("도로 구조 안내", color = palette.accent,
                        fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                    Explanation(state, palette, Modifier.fillMaxWidth(),
                        showDetail = availableHeight >= (330 * fontScale).dp,
                        diagramUnavailable = state.showDiagram && diagram == null,
                        smallWindow = canDraw && !roomForDiagram)
                }
                if (showScope) GuidanceScope(palette, Modifier.weight(.65f))
            }
        }
        ObserveRoadComposition(state, "guidance", when {
            diagram != null && roomForDiagram -> if (wide) "diagram-wide" else "diagram-tall"
            state.showDiagram && diagram == null -> "text-invalid-diagram"
            canDraw && !roomForDiagram -> "text-small-window"
            else -> "text"
        })
    }
}

@Composable private fun Explanation(state: RoadUiState, palette: RoadPalette, modifier: Modifier,
    showDetail: Boolean, diagramUnavailable: Boolean = false, smallWindow: Boolean = false,
    featured: Boolean = false) {
    Column(modifier.testTag("guidance_explanation"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (featured) {
            Box(Modifier.width(48.dp).height(5.dp).background(palette.accent, RoundedCornerShape(4.dp)))
            Text("이 구간의 핵심", color = palette.accent, fontSize = 22.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold)
        }
        // Keep paragraph and node width identical. Pleos' text layout can otherwise report a
        // paragraph at maxWidth while returning a wrap-content node, including for short copy.
        Text(if (diagramUnavailable) "도로 구조 자료를 확인하고 있습니다" else state.title,
            color = palette.ink, fontSize = if (featured) 40.sp else 32.sp,
            lineHeight = if (featured) 52.sp else 40.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().testTag("title"))
        Text(if (diagramUnavailable) "확인된 자료가 연결되면 구조를 표시합니다." else state.message,
            color = palette.secondary, fontSize = 28.sp, lineHeight = 38.sp, modifier = Modifier.fillMaxWidth().testTag("message"))
        if (smallWindow) Text("구조 도식은 더 큰 창에서 확인할 수 있습니다", color = palette.secondary,
            fontSize = 20.sp, lineHeight = 28.sp, modifier = Modifier.fillMaxWidth().testTag("small_window"))
        else if (showDetail && state.detail.isNotBlank()) Text(state.detail, color = palette.secondary,
            fontSize = 22.sp, lineHeight = 30.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun GuidanceScope(palette: RoadPalette, modifier: Modifier) {
    Column(modifier.background(palette.background, RoundedCornerShape(24.dp)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("이 화면이 알려주는 것", color = palette.secondary, fontSize = 22.sp, lineHeight = 30.sp)
        Text("도로가 어떻게\n이어지는지", color = palette.ink, fontSize = 32.sp,
            lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = palette.muted)
        Text("현재 차로와 합류 가능 여부는\n판단하지 않습니다", color = palette.secondary,
            fontSize = 22.sp, lineHeight = 32.sp)
    }
}

@Composable private fun ParkedActions(state: RoadUiState, palette: RoadPalette, wide: Boolean, tight: Boolean,
    onDemo: () -> Unit, onLive: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit,
    onReset: () -> Unit, onTheme: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = palette.muted)
        if (state.setup) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action("위치 사용 허용", "permission", onLive, palette, Modifier.weight(1f), true, enabled = !state.permissionRequestPending)
            Action("가상 구간 체험", "demo", onDemo, palette, Modifier.weight(1f), enabled = !state.permissionRequestPending)
        } else if (state.simulation && wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(if (state.playing) "일시정지" else "시연 재생", "play", onPlay, palette, Modifier.weight(1.4f), true)
            Action("다음 장면", "next", onNext, palette, Modifier.weight(1f))
            Action("처음부터", "reset", onReset, palette, Modifier.weight(.85f), quiet = true)
            Action("실제 연결", "live", onLive, palette, Modifier.weight(1f), quiet = true)
        } else if (state.simulation) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(if (state.playing) "일시정지" else "시연 재생", "play", onPlay, palette, Modifier.weight(1f), true)
                Action("실제 연결", "live", onLive, palette, Modifier.weight(1f))
            }
            if (!tight && !state.showDiagram) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action("다음 장면", "next", onNext, palette, Modifier.weight(1f))
                Action("처음부터", "reset", onReset, palette, Modifier.weight(1f))
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action("가상 구간 체험", "demo", onDemo, palette, Modifier.weight(1f), true)
            Action(if (state.dark) "밝은 화면" else "어두운 화면", "theme", onTheme, palette, Modifier.weight(1f))
        }
    }
}

@Composable private fun Action(label: String, tag: String, click: () -> Unit, palette: RoadPalette,
    modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, quiet: Boolean = false) {
    Button(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 76.dp).widthIn(min = 76.dp).testTag(tag),
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) palette.accent else if (quiet) Color.Transparent else palette.surface,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else if (quiet) palette.secondary else palette.ink,
            disabledContainerColor = palette.surface, disabledContentColor = palette.secondary),
        border = if (primary || quiet) null else androidx.compose.foundation.BorderStroke(1.dp, palette.muted)) {
        Text(label, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun DiagramPanel(model: DiagramModel, palette: RoadPalette, modifier: Modifier,
    playing: Boolean, phase: String, contextExample: Boolean = false) {
    val entersFromRight = model.mergeSide == MergeSide.RIGHT
    val enteringSide = if (entersFromRight) "오른쪽" else "왼쪽"
    val joiningSide = if (entersFromRight) "왼쪽" else "오른쪽"
    val illustrativeVehicle = model.source == DiagramSource.REPLAY_SYNTHETIC && model.synthetic
    val originDescription = when (model.source) {
        DiagramSource.REPLAY_SYNTHETIC -> if (contextExample) "연결 구조 예시. 실제 차로 아님" else "가상 도로 구조"
        DiagramSource.REPLAY_CAPTURED -> "기록 재생 중인 검수된 도로 구조"
        DiagramSource.REAL_SDK -> "검수된 도로 구조"
    }
    Column(modifier.testTag("diagram_panel").background(palette.surface, RoundedCornerShape(28.dp))
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (contextExample) "연결 구조 예시" else "도로 연결 구조", color = palette.ink, fontSize = 22.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold)
            Text("진행 방향 ↑", color = palette.secondary, fontSize = 22.sp, lineHeight = 30.sp)
        }
        // Native Text labels remain legible at fontScale 1.3; only road geometry is drawn.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp).semantics {
            contentDescription = if (contextExample) "연결 구조 예시. 실제 차로 아님. 도로 연결 개념을 설명하며 현재 차로, 차로 종료 위치와 합류 가능 여부를 표시하지 않음."
                else "$originDescription. $enteringSide 진입 차로가 이어진 뒤 끝에서 $joiningSide 본선과 연결됨. 차량의 현재 차로는 표시하지 않음."
        }.testTag("diagram")) {
            val diagramHeight = maxHeight
            val richLabels = maxHeight >= 300.dp && maxWidth >= 480.dp
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                fun x(fraction: Float) = w * if (entersFromRight) fraction else 1f - fraction
                val mainLeft = x(.14f); val mainRight = x(.51f); val rampRight = x(.85f)
                val top = 8.dp.toPx(); val bottom = h - 4.dp.toPx()
                val joinY = h * .25f; val taperStart = h * .59f
                val mainSurface = Path().apply {
                    moveTo(mainLeft, bottom); lineTo(mainLeft, top)
                    lineTo(mainRight, top); lineTo(mainRight, bottom); close()
                }
                val rampSurface = Path().apply {
                    moveTo(mainRight, bottom); lineTo(mainRight, joinY)
                    lineTo(rampRight, taperStart); lineTo(rampRight, bottom); close()
                }
                drawPath(mainSurface, palette.muted.copy(alpha = .15f))
                drawPath(rampSurface, palette.accent.copy(alpha = .20f))
                // Continuous outer edge and a wide filled entering lane make its remaining
                // length visible. These schematic edges are not regulatory lane markings.
                drawLine(palette.muted, Offset(mainLeft, bottom), Offset(mainLeft, top), 4.dp.toPx())
                val outerEdge = Path().apply {
                    moveTo(rampRight, bottom); lineTo(rampRight, taperStart)
                    lineTo(mainRight, joinY); lineTo(mainRight, top)
                }
                drawPath(outerEdge, palette.accent, style = Stroke(7.dp.toPx()))
                drawLine(palette.muted, Offset(mainRight, bottom), Offset(mainRight, taperStart), 3.dp.toPx())
                // One stationary junction marker; it does not locate an actual vehicle.
                drawCircle(palette.surface, 13.dp.toPx(), Offset(mainRight, joinY))
                drawCircle(palette.accent, 8.dp.toPx(), Offset(mainRight, joinY))
            }
            if (illustrativeVehicle) SyntheticVehicle(model, playing, phase, palette, Modifier.matchParentSize(), contextExample)
            Column(Modifier.align(if (entersFromRight) Alignment.TopEnd else Alignment.TopStart)
                .offset(y = diagramHeight * .25f - 20.dp)
                .background(palette.surface, RoundedCornerShape(12.dp))
                .border(1.dp, palette.accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = if (richLabels) 12.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (contextExample) "본선 연결" else "합류 지점", color = palette.accent, fontSize = 22.sp, lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold)
                if (richLabels) Text(if (contextExample) "연결 방식의 예시" else "끝에서 본선과 연결", color = palette.secondary, fontSize = 20.sp, lineHeight = 28.sp)
            }
            if (richLabels) Text(if (contextExample) "진입로 예시" else "앞까지 이어지는 구간", color = palette.accent,
                fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(if (entersFromRight) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(bottom = 24.dp)
                    .background(palette.surface, RoundedCornerShape(10.dp)).padding(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (contextExample) "본선 예시" else if (entersFromRight) "본선" else "진입 차로", color = if (entersFromRight) palette.secondary else palette.accent,
                fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(if (contextExample) "진입로 예시" else if (entersFromRight) "진입 차로" else "본선", color = if (entersFromRight) palette.accent else palette.secondary,
                fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(if (contextExample) "연결 구조 예시 · 실제 차로 아님" else if (illustrativeVehicle) "가상 차량 경로 예시 · 실제 위치 아님" else "구조 설명 · 현재 차로 표시 아님",
            color = palette.secondary, fontSize = 20.sp, lineHeight = 28.sp,
            modifier = if (contextExample) Modifier.testTag("context_example_notice") else if (illustrativeVehicle) Modifier.testTag("synthetic_vehicle_notice") else Modifier)
    }
}

/** SDK gates visibility; the repeated motion explains a lane change, not measured progress. */
@Composable internal fun ContextRoadIllustration(dark: Boolean, modifier: Modifier = Modifier) {
    DiagramPanel(
        DiagramModel(MergeSide.RIGHT, DiagramSource.REPLAY_SYNTHETIC,
            "synthetic:context-connection-example", synthetic = true, approved = true),
        roadPalette(dark), modifier.testTag("context_diagram"), playing = false,
        phase = "연결 구조 설명", contextExample = true,
    )
}

/** One-second move, short destination hold, fade out/reset/fade in. No reverse travel. */
@Composable private fun repeatedLaneChangeMotion(): Pair<Float, Float> {
    val loop = rememberInfiniteTransition(label = "lane-change-example")
    val position = loop.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 1450
        0f at 0
        0f at 100 using FastOutSlowInEasing
        1f at 1100
        1f at 1450
    }), label = "example-position")
    val opacity = loop.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 1450
        0f at 0
        1f at 100
        1f at 1300
        0f at 1400
        0f at 1450
    }), label = "example-opacity")
    return position.value to opacity.value
}

/** Context repeats a quick example while visible; the separate parked demo remains finite. */
@Composable private fun SyntheticVehicle(model: DiagramModel, playing: Boolean, phase: String, palette: RoadPalette, modifier: Modifier,
    contextExample: Boolean = false) {
    val progress = remember(model.segmentId, model.mergeSide) {
        Animatable(if (phase == "합류 구간 접근") .55f else 0f)
    }
    val repeatedMotion = if (contextExample) repeatedLaneChangeMotion() else null
    LaunchedEffect(model.segmentId, model.mergeSide, playing) {
        // Cancellation on pause retains the current value. Resume uses only the remaining
        // duration, and reaching 1 never starts another cycle. Removing the diagram resets it.
        if (!contextExample && playing && progress.value < 1f) progress.animateTo(1f,
            tween(durationMillis = ((1f - progress.value) * 4_000).roundToInt().coerceAtLeast(1),
                easing = LinearEasing))
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val vehicleWidth = minOf(with(density) { 96.dp.toPx() }, h * .17f, w * .18f)
        val vehicleHeight = vehicleWidth * (96f / 56f)
        val track = remember(w, h, model.mergeSide) { IllustrationTrack(w, h, model.mergeSide) }
        // Smooth the movement in time while keeping the guide itself a simple polyline.
        val t = progress.value
        val travel = repeatedMotion?.first ?: (t * t * t * (t * (t * 6f - 15f) + 10f))
        val center = track.point(travel)
        val ribbon = palette.illustrationRoute
        Canvas(Modifier.fillMaxSize().testTag(if (contextExample) "context_route_example" else "synthetic_route").semantics {
            contentDescription = if (contextExample) "도로 연결을 설명하는 분홍색 경로 예시. 실제 차로 안내선 아님." else "분홍색 가상 경로 예시"
        }) {
            val ribbonWidth = (vehicleWidth * .24f).coerceIn(8.dp.toPx(), 22.dp.toPx())
            drawPath(track.path, palette.surface, style = Stroke(ribbonWidth + 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Miter))
            drawPath(track.path, ribbon, style = Stroke(ribbonWidth, cap = StrokeCap.Round, join = StrokeJoin.Miter))
            // Stationary contrasting chevrons convey direction even before playback starts.
            // They describe the illustrative route, never an observed painted road marking.
            listOf(.18f, .50f, .83f).forEach { fraction ->
                val c = track.point(fraction)
                val p0 = track.point((fraction - .02f).coerceAtLeast(0f))
                val p1 = track.point((fraction + .02f).coerceAtMost(1f))
                val delta = p1 - p0
                val length = hypot(delta.x, delta.y).coerceAtLeast(1f)
                val forward = Offset(delta.x / length, delta.y / length)
                val side = Offset(-forward.y, forward.x)
                val arm = ribbonWidth * .40f
                val tip = c + forward * arm
                val back = c - forward * arm
                drawLine(palette.background, back + side * arm, tip, (ribbonWidth * .16f).coerceAtLeast(2.dp.toPx()), cap = StrokeCap.Round)
                drawLine(palette.background, back - side * arm, tip, (ribbonWidth * .16f).coerceAtLeast(2.dp.toPx()), cap = StrokeCap.Round)
            }
        }
        Canvas(Modifier.size(with(density) { vehicleWidth.toDp() }, with(density) { vehicleHeight.toDp() })
            .graphicsLayer {
                translationX = center.x - vehicleWidth / 2
                translationY = center.y - vehicleHeight / 2
                alpha = repeatedMotion?.second ?: 1f
                // Lane changes translate the icon; the vehicle always faces forward.
            }
            .testTag(if (contextExample) "context_vehicle" else "synthetic_vehicle").semantics {
                contentDescription = if (contextExample) "도로 연결을 설명하는 차량 모형. 실제 차량의 현재 차로와 차선 변경 지시가 아님."
                    else "가상 차량의 도로 연결 예시. 실제 차량의 현재 차로는 표시하지 않음."
            }) {
            val cw = size.width; val ch = size.height
            // Layered top-down car: tires, metallic body, glass, roof, hood and lamps.
            drawRoundRect(Color.Black.copy(alpha = .19f), Offset(cw * .05f, ch * .035f),
                Size(cw * .94f, ch * .95f), CornerRadius(cw * .22f))
            listOf(.21f, .67f).forEach { y ->
                listOf(.01f, .83f).forEach { x ->
                    drawRoundRect(Color(0xFF132536), Offset(cw * x, ch * y),
                        Size(cw * .16f, ch * .19f), CornerRadius(cw * .04f))
                }
            }
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF96BEDA), Color(0xFFF6FAFF), Color(0xFFC6DBEA))),
                Offset(cw * .10f, ch * .02f), Size(cw * .80f, ch * .94f), CornerRadius(cw * .24f))
            drawRoundRect(Color(0xFF31566E), Offset(cw * .10f, ch * .02f),
                Size(cw * .80f, ch * .94f), CornerRadius(cw * .24f), style = Stroke(cw * .035f))
            val windshield = Path().apply {
                moveTo(cw * .25f, ch * .25f); quadraticTo(cw * .5f, ch * .20f, cw * .75f, ch * .25f)
                lineTo(cw * .81f, ch * .39f); lineTo(cw * .19f, ch * .39f); close()
            }
            drawPath(windshield, Color(0xFF193C55))
            drawRoundRect(Color(0xFFDCEBF5), Offset(cw * .22f, ch * .43f),
                Size(cw * .56f, ch * .25f), CornerRadius(cw * .10f))
            drawRoundRect(Color(0xFF284D66), Offset(cw * .23f, ch * .71f),
                Size(cw * .54f, ch * .12f), CornerRadius(cw * .07f))
            drawLine(Color.White, Offset(cw * .30f, ch * .11f), Offset(cw * .70f, ch * .11f), cw * .045f)
            listOf(.20f, .66f).forEach { x ->
                drawRoundRect(Color(0xFFE8F5FF), Offset(cw * x, ch * .065f),
                    Size(cw * .14f, ch * .055f), CornerRadius(cw * .025f))
                drawRoundRect(Color(0xFF9A4653), Offset(cw * x, ch * .87f),
                    Size(cw * .14f, ch * .035f), CornerRadius(cw * .015f))
            }
        }
    }
}

/** Straight → diagonal lane change → straight. The car shares the line but never rotates. */
private class IllustrationTrack(width: Float, height: Float, side: MergeSide) {
    private val points = listOf(
        Offset(.68f, .82f), Offset(.68f, .60f),
        Offset(.325f, .33f), Offset(.325f, .22f),
    ).map { p ->
        Offset((if (side == MergeSide.RIGHT) p.x else 1f - p.x) * width, p.y * height)
    }
    private val cumulative = FloatArray(points.size).also { distances ->
        for (i in 1 until points.size) {
            val d = points[i] - points[i - 1]
            distances[i] = distances[i - 1] + hypot(d.x, d.y)
        }
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    fun point(fraction: Float): Offset {
        val distance = fraction.coerceIn(0f, 1f) * cumulative.last()
        var low = 0
        var high = cumulative.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) / 2
            if (cumulative[mid] <= distance) low = mid else high = mid
        }
        val span = cumulative[high] - cumulative[low]
        val ratio = if (span > 0f) (distance - cumulative[low]) / span else 0f
        return points[low] + (points[high] - points[low]) * ratio
    }
}

