package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.ceil

/** Measures input-state submission to successful Compose commit, not photon latency or SDK transit. */
class RoadUiCommitPerformanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thousandReceivedStateUpdatesCommitWithinBudget() {
        val sequence = mutableStateOf(-1)
        val submitted = ConcurrentHashMap<Int, Long>()
        val committed = ConcurrentHashMap<Int, Long>()
        compose.setContent {
            val index = sequence.value
            RoadScreen(RoadUiState(simulation = true, phase = "측정 입력 $index",
                title = "진입 차로의 연결 구조", message = "위치 입력 $index", settingsAllowed = false), {}, {}, {}, {}, {}, {})
            SideEffect {
                submitted[index]?.let { start -> committed.putIfAbsent(index, SystemClock.elapsedRealtimeNanos() - start) }
            }
        }
        var success = false
        try {
            // Warm-up samples use negative indices and are excluded from the measured 1,000.
            for (index in -50 until 1000) {
                compose.runOnIdle {
                    submitted[index] = SystemClock.elapsedRealtimeNanos()
                    sequence.value = index
                }
                compose.waitForIdle()
                compose.waitUntil(5_000) { committed.containsKey(index) }
            }
            val durations = (0 until 1000).map { committed.getValue(it) / 1_000_000.0 }
            val p95 = durations.sorted()[ceil(durations.size * .95).toInt() - 1]
            assertEquals(1000, durations.size)
            assertTrue("Compose commit p95 $p95 ms exceeds 250 ms", p95 <= 250.0)
            success = true
        } finally {
            val durations = (0 until 1000).mapNotNull { committed[it]?.div(1_000_000.0) }
            val result = JSONObject().put("passed", success).put("source", "REPLAY_SYNTHETIC")
                .put("warmupSamples", 50).put("measuredSamples", durations.size)
                .put("durationsMs", JSONArray(durations)).put("clock", "SystemClock.elapsedRealtimeNanos")
                .put("scope", "State received on UI thread -> Compose SideEffect after successful commit on actual host window. Excludes SDK transit, input dispatch queue, pixel presentation and engine execution.")
            if (durations.isNotEmpty()) result.put("p95Ms", durations.sorted()[ceil(durations.size * .95).toInt() - 1])
            DeviceTestSupport.save("ui-commit-performance", result)
        }
    }
}
