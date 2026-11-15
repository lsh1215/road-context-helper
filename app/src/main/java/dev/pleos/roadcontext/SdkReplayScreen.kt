package dev.pleos.roadcontext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A dedicated, quiet instrument-style shell for the explicit GPS demonstration. */
@Composable
internal fun SdkReplayScreen(
    state: SdkRouteReplayVisual,
    dark: Boolean,
    controlsAllowed: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onScenarios: (() -> Unit)? = null,
) {
    val background = if (dark) Color(0xFF0E1923) else Color(0xFFF1F6FA)
    val ink = if (dark) Color(0xFFEDF5FA) else Color(0xFF192C3C)
    val secondary = if (dark) Color(0xFFA5B8C7) else Color(0xFF526979)
    val accent = if (dark) Color(0xFF82DECA) else Color(0xFF166E63)
    Column(Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("초행길", color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (controlsAllowed && onScenarios != null) TextButton(onClick = onScenarios,
                modifier = Modifier.heightIn(min = 76.dp).testTag("cluster_demo")) {
                Text("도로 상황 데모", color = accent, fontSize = 18.sp)
            }
            Text("GPS 시연", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("source").background(accent.copy(alpha = .08f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp))
        }
        SdkRouteReplayPanel(state, dark, Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.contextCase == "eungbong") Spacer(Modifier.weight(1f))
            else Text("© OpenStreetMap contributors", color = secondary, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.weight(1f))
            if (controlsAllowed) {
                TextButton(onClick = onRestart, modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 76.dp).testTag("sdk_restart")) {
                    Text("다시 준비", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                TextButton(onClick = onExit, modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 76.dp).testTag("sdk_exit")) {
                    Text("종료", color = secondary, fontSize = 18.sp)
                }
            }
        }
    }
}
