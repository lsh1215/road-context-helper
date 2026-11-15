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

/** Parked-only examples, with source-dated road previews. Live visibility remains SDK-gated. */
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
    var roadProfile by rememberSaveable(initialProfile) {
        mutableStateOf(RoadReplayCase.fromProfile(initialProfile.orEmpty())?.takeUnless { it == RoadReplayCase.YANGHWA }?.profile)
    }
    val roadCase = roadProfile?.let(RoadReplayCase::fromProfile)
    val mangwon = roadCase == RoadReplayCase.MANGWON_SHORT_EXIT
    val laneEnding = roadCase == RoadReplayCase.MANGWON_LANE_END || roadCase == RoadReplayCase.EUNGBONG
    val eungbong = roadCase == RoadReplayCase.EUNGBONG
    var selected by rememberSaveable { mutableStateOf(ClusterScenario.SHORT_EXIT.id) }
    var stage by rememberSaveable { mutableIntStateOf(1) }
    var tour by rememberSaveable { mutableStateOf(false) }
    val scenario = ClusterScenario.entries.first { it.id == selected }
    val foreground = if (dark) Color(0xFFE3EDF4) else Color(0xFF183548)
    val secondary = if (dark) Color(0xFFA7B9C9) else Color(0xFF526A7B)
    val background = if (dark) Color(0xFF111C29) else Color(0xFFEAF2F8)

    LaunchedEffect(tour, playing, selected, stage, controlsAllowed) {
        if (tour && playing && controlsAllowed) {
            delay(when (stage) { 0 -> 3_000L; 1 -> if (scenario == ClusterScenario.SHORT_EXIT) 9_000L else 6_000L; else -> 2_000L })
            if (stage < 2) stage++ else {
                selected = ClusterScenario.entries[(scenario.ordinal + 1) % ClusterScenario.entries.size].id
                stage = 0
            }
        }
    }
    Column(Modifier.fillMaxSize().background(background).padding(horizontal = 28.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("도로 상황 데모", color = foreground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(if (eungbong) "응봉 관측 자료 기반 도식" else if (mangwon || laneEnding) "망원 관측 자료 기반 도식" else "가상 도로 시연", color = secondary, fontSize = 16.sp, modifier = Modifier.testTag("demo_source"))
        }
        if (!controlsAllowed) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("정차하면 시연을 이어갈 수 있습니다", color = foreground, fontSize = 26.sp,
                    modifier = Modifier.testTag("demo_restricted"))
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = eungbong, onClick = { roadProfile = RoadReplayCase.EUNGBONG.profile; stage = 1; tour = false },
                label = { Text("응봉 분홍색 합류", fontSize = 18.sp) },
                modifier = Modifier.heightIn(min = 76.dp).testTag("case_eungbong_lane_end"))
            FilterChip(selected = roadCase == RoadReplayCase.MANGWON_LANE_END, onClick = { roadProfile = RoadReplayCase.MANGWON_LANE_END.profile; stage = 1; tour = false },
                label = { Text("망원 차로 끝", fontSize = 18.sp) },
                modifier = Modifier.heightIn(min = 76.dp).testTag("case_mangwon_lane_end"))
            FilterChip(selected = mangwon, onClick = { roadProfile = RoadReplayCase.MANGWON_SHORT_EXIT.profile; stage = 1; tour = false },
                label = { Text("망원 실선·점선", fontSize = 18.sp) },
                modifier = Modifier.heightIn(min = 76.dp).testTag("case_mangwon_markings"))
            ClusterScenario.entries.forEach { item ->
                FilterChip(selected = roadCase == null && scenario == item, onClick = { roadProfile = null; selected = item.id; stage = 1; tour = false },
                    label = { Text(item.label, fontSize = 18.sp) },
                    modifier = Modifier.heightIn(min = 76.dp).testTag("case_${item.id}"))
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
            Text(if (eungbong) "출구 뒤, 분홍색 유도선 방면으로 합류" else if (laneEnding) "진입 차로는 앞까지 이어집니다" else if (mangwon) "합류 후 실선, 그다음 짧은 왼쪽 진출 준비" else if (stage == 0) scenario.previewTitle else scenario.title,
                modifier = Modifier.fillMaxWidth().testTag("demo_title"), textAlign = TextAlign.Center,
                fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = foreground)
            Text(if (eungbong) "응봉지하차도 출구 · 이어지는 진입 차로와 앞쪽 차로 끝" else if (laneEnding) "망원 합류부 · 앞에서 차로가 끝나며 강변북로 본선과 연결" else if (mangwon) "내부순환로 → 강변북로 구리방향 → 양화대교 · 이동 여유가 없으면 다음 경로" else when (scenario) {
                ClusterScenario.MERGE -> "진입 차로가 끝에서 본선으로 연결됩니다"
                ClusterScenario.SHORT_EXIT -> "합류 전에 다음 진출 확인 · 여유가 없으면 다음 경로 이용"
                ClusterScenario.EXIT_ONLY -> "오른쪽은 진출 전용 · 본선은 왼쪽으로 계속"
            }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
                fontSize = 18.sp, color = secondary)
            if (eungbong) EungbongGalleryDiagram(dark, playing && stage == 1, Modifier.weight(1f).fillMaxWidth())
            else if (laneEnding) LaneEndingDiagram(dark, Modifier.weight(1f).fillMaxWidth(), animate = playing && stage == 1, sourceMonth = "2026.07", pinkTarget = false)
            else if (mangwon) MangwonRestrictionDiagram(dark, Modifier.weight(1f).fillMaxWidth(), animate = playing && stage == 1)
            else ClusterRoadIllustration(dark, Modifier.weight(1f).fillMaxWidth(), scenario,
                animate = playing && stage == 1)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                tour = !tour
                if (tour) { roadProfile = null; selected = ClusterScenario.MERGE.id; stage = 0; if (!playing) onTogglePlay() }
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
 val context=androidx.compose.ui.platform.LocalContext.current
 val session=remember { SdkRoadReplaySession(context,"eungbong") }
 val initial=remember { session.visual(session.gate.tick(android.os.SystemClock.elapsedRealtime())) }
 var along by remember { mutableDoubleStateOf(120.0) }
 LaunchedEffect(animate) { while(animate) { delay(100);along=if(along>680) 40.0 else along+12.0 } }
 EungbongPositionDiagram(initial.copy(status="GUIDANCE",visible=true,alongMeters=along),dark,modifier,actualSdk=false)
}
