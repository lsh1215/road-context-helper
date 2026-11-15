package dev.pleos.roadcontext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Parked road-structure categories, using the same diagrams as the reference cases. */
@Composable
internal fun ClusterDemoGallery(
    dark: Boolean,
    controlsAllowed: Boolean,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onExit: () -> Unit,
    onSdkReplay: (() -> Unit)?,
    initialProfile: String? = null,
) {
    var selectedId by rememberSaveable(initialProfile) {
        mutableStateOf(RoadDemoCase.fromRoadProfile(initialProfile).id)
    }
    val selected = RoadDemoCase.entries.firstOrNull { it.id == selectedId }
        ?: RoadDemoCase.LANE_END_RIGHT_TO_LEFT
    var stage by rememberSaveable { mutableIntStateOf(1) }
    var tour by rememberSaveable { mutableStateOf(false) }
    val foreground = if (dark) Color(0xFFE3EDF4) else Color(0xFF183548)
    val secondary = if (dark) Color(0xFFA7B9C9) else Color(0xFF526A7B)
    val background = if (dark) Color(0xFF111C29) else Color(0xFFEAF2F8)

    LaunchedEffect(tour, playing, selectedId, stage, controlsAllowed) {
        if (tour && playing && controlsAllowed) {
            delay(when (stage) { 0 -> 2_000L; 1 -> if (selected == RoadDemoCase.TWO_STAGE_LANE_END) 5_000L else 4_000L; else -> 1_500L })
            if (stage < 2) stage++ else {
                selectedId = RoadDemoCase.entries[(selected.ordinal + 1) % RoadDemoCase.entries.size].id
                stage = 0
            }
        }
    }

    Column(Modifier.fillMaxSize().background(background).padding(horizontal = 28.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("도로 상황 데모", color = foreground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(selected.source, color = secondary, fontSize = 16.sp, modifier = Modifier.testTag("demo_source"))
        }
        if (!controlsAllowed) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("정차하면 시연을 이어갈 수 있습니다", color = foreground, fontSize = 26.sp,
                    modifier = Modifier.testTag("demo_restricted"))
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoadDemoCase.entries.forEach { item ->
                FilterChip(
                    selected = selected == item,
                    onClick = { selectedId = item.id; stage = 1; tour = false },
                    label = { Text(item.label, fontSize = 18.sp) },
                    modifier = Modifier.heightIn(min = 76.dp).testTag("demo_case_${item.id}"),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("미리보기", "구간 안내", "통과").forEachIndexed { index, label ->
                TextButton(onClick = { stage = index; tour = false },
                    modifier = Modifier.heightIn(min = 56.dp).testTag(listOf("stage_preview", "stage_guidance", "stage_passed")[index])) {
                    Text(label, color = if (stage == index) foreground else secondary,
                        fontWeight = if (stage == index) FontWeight.Bold else FontWeight.Normal, fontSize = 18.sp)
                }
            }
        }

        if (stage == 2) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("안내 구간을 지났습니다", fontSize = 30.sp, color = foreground, modifier = Modifier.testTag("demo_passed"))
                Spacer(Modifier.height(12.dp))
                Text("다음 복잡한 구간에서 다시 안내합니다", fontSize = 20.sp, color = secondary)
            }
        } else {
            Text(if (stage == 0) selected.previewTitle else selected.title,
                modifier = Modifier.fillMaxWidth().testTag("demo_title"), textAlign = TextAlign.Center,
                fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = foreground)
            Text(selected.description, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center, fontSize = 18.sp, color = secondary)
            val diagramModifier = Modifier.weight(1f).fillMaxWidth()
            when (selected) {
                RoadDemoCase.TWO_STAGE_LANE_END -> EungbongGalleryDiagram(dark, playing && stage == 1, diagramModifier)
                RoadDemoCase.LANE_END_RIGHT_TO_LEFT, RoadDemoCase.LANE_END_LEFT_TO_RIGHT ->
                    LaneEndingDiagram(dark, diagramModifier, animate = playing && stage == 1,
                        sourceMonth = "2026.07", mirror = selected.mirrored)
                RoadDemoCase.CLOSE_EXIT_RIGHT_TO_LEFT, RoadDemoCase.CLOSE_EXIT_LEFT_TO_RIGHT ->
                    MangwonRestrictionDiagram(dark, diagramModifier, animate = playing && stage == 1,
                        mirror = selected.mirrored)
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                tour = !tour
                if (tour) { selectedId = RoadDemoCase.entries.first().id; stage = 0; if (!playing) onTogglePlay() }
            }, modifier = Modifier.heightIn(min = 76.dp).testTag("demo_tour")) {
                Text(if (tour) "순회 중지" else "전체 순회", fontSize = 18.sp)
            }
            TextButton(onClick = onTogglePlay, modifier = Modifier.heightIn(min = 76.dp).testTag("demo_play")) {
                Text(if (playing) "일시정지" else "재생", fontSize = 18.sp)
            }
            if (onSdkReplay != null) TextButton(onClick = onSdkReplay, modifier = Modifier.heightIn(min = 76.dp)) {
                Text("GPS 시험", fontSize = 18.sp)
            }
            TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 76.dp)) { Text("실제 입력", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun EungbongGalleryDiagram(dark: Boolean, animate: Boolean, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SdkRoadReplaySession(context, RoadReplayCase.EUNGBONG.profile) }
    val initial = remember { session.visual(session.gate.tick(android.os.SystemClock.elapsedRealtime())) }
    var along by remember { mutableDoubleStateOf(120.0) }
    LaunchedEffect(animate) {
        if (animate) {
            var previous = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val next = along + (now - previous) / 1_000_000_000.0 * 290.0
                along = if (next > 680.0) 40.0 else next
                previous = now
            }
        }
    }
    EungbongPositionDiagram(initial.copy(status = "GUIDANCE", visible = true, alongMeters = along),
        dark, modifier, actualSdk = false)
}
