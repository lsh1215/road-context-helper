package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real screen callbacks with actual platform restrictions; timing excludes display scan-out. */
class MainActivityFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualPlayButtonUpdatesStateWithin250ms() {
        DeviceTestSupport.requireActualLocationPermission()
        val model = compose.runOnIdle { ViewModelProvider(compose.activity)[RoadViewModel::class.java] }
        compose.waitUntil(15_000) { model.ui.settingsAllowed }
        compose.onNodeWithTag("demo").performClick()
        compose.waitForIdle()
        val samples = JSONArray()
        var passed = false
        try {
            repeat(20) { index ->
                val expectedPlaying = index % 2 == 0
                var elapsedMs = Double.NaN
                var clickStartedNs = 0L
                compose.onNodeWithTag("play").performSemanticsAction(SemanticsActions.OnClick) { action ->
                    clickStartedNs = SystemClock.elapsedRealtimeNanos()
                    assertTrue("Actual button callback did not accept click", action())
                    assertEquals(expectedPlaying, model.ui.playing)
                    elapsedMs = (SystemClock.elapsedRealtimeNanos() - clickStartedNs) / 1_000_000.0
                }
                compose.waitForIdle()
                compose.onNodeWithTag("play").assertTextEquals(if (expectedPlaying) "일시정지" else "시연 재생")
                // Timestamp only after the changed label is observable in the composed semantics.
                // Includes synchronization/assertion overhead: conservative relative to commit,
                // but neither a touch-dispatch measurement nor a physical pixel/scan-out timing.
                val semanticLabelObservedMs =
                    (SystemClock.elapsedRealtimeNanos() - clickStartedNs) / 1_000_000.0
                samples.put(JSONObject().put("index", index).put("stateFeedbackMs", elapsedMs)
                    .put("semanticLabelObservedMs", semanticLabelObservedMs)
                    .put("callbackStartedElapsedNs", clickStartedNs)
                    .put("playing", expectedPlaying))
                assertTrue("Button callback-to-state feedback $elapsedMs ms exceeds 250ms", elapsedMs <= 250.0)
                assertTrue("Button callback-to-observed semantic label $semanticLabelObservedMs ms exceeds 250ms (includes test synchronization overhead)",
                    semanticLabelObservedMs <= 250.0)
            }
            passed = true
        } finally {
            compose.runOnIdle { if (model.ui.playing) model.togglePlay() }
            DeviceTestSupport.save("actual-button-feedback", JSONObject().put("passed", passed)
                .put("samples", samples).put("source", "REPLAY_SYNTHETIC")
                .put("sampleTarget", 20).put("thresholdEachSampleMs", 250)
                .put("clock", "SystemClock.elapsedRealtimeNanos")
                .put("scope", "Real MainActivity play/pause: separate callback-to-model feedback and callback-to-observed recomposed semantic-label latency. The latter includes waitForIdle/assertion overhead and must meet 250ms for every sample. Excludes pre-callback input dispatch and physical pixel/scan-out latency; not a physical-display certification."))
        }
    }
}
