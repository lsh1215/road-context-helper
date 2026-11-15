package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import dev.pleos.roadcontext.integration.PleosNavigationReader
import dev.pleos.roadcontext.integration.SourceEvent
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Actual Activity/SDK lifecycle tests. The retained preview is explicitly synthetic. */
class MainActivityLifecycleTest {
    @Test fun tenActualActivityRecreationsRetainPausedSyntheticPreview() {
        DeviceTestSupport.requireActualLocationPermission()
        val results = JSONArray()
        var success = false
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var original: RoadViewModel
                scenario.onActivity { original = ViewModelProvider(it)[RoadViewModel::class.java] }
                DeviceTestSupport.await("actual parked UX policy") { DeviceTestSupport.main { original.ui.settingsAllowed } }
                val expected = DeviceTestSupport.main {
                    original.demo()
                    repeat(3) { original.next() }
                    assertTrue(original.ui.simulation)
                    assertTrue(original.ui.showDiagram)
                    assertFalse(original.ui.playing)
                    original.ui
                }
                repeat(10) { index ->
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        assertFalse(activity.isFinishing)
                        val restored = ViewModelProvider(activity)[RoadViewModel::class.java]
                        assertSame("Activity recreation must retain the ViewModel", original, restored)
                        val actual = restored.ui
                        assertEquals(expected.simulation, actual.simulation)
                        assertEquals(expected.inputSource, actual.inputSource)
                        assertEquals(expected.title, actual.title)
                        assertEquals(expected.message, actual.message)
                        assertEquals(expected.phase, actual.phase)
                        assertEquals(expected.distance, actual.distance)
                        assertEquals(expected.showDiagram, actual.showDiagram)
                        assertEquals(expected.dark, actual.dark)
                        assertFalse(actual.playing)
                        results.put(JSONObject().put("cycle", index + 1).put("source", "REPLAY_SYNTHETIC")
                            .put("previewRetained", actual.showDiagram).put("atElapsedMs", SystemClock.elapsedRealtime()))
                    }
                }
                success = true
            }
        } finally {
            DeviceTestSupport.save("activity-recreation", JSONObject().put("passed", success).put("cycles", results)
                .put("scope", "Actual MainActivity recreation with synthetic paused preview; not process-death restore or real-road guidance."))
        }
    }

    @Test fun twentyRealSdkStartCloseCyclesReleaseClientSubscriptions() {
        DeviceTestSupport.requireActualLocationPermission()
        val results = JSONArray()
        var success = false
        val events = CopyOnWriteArrayList<SourceEvent>()
        // Keep a real foreground host, but stop its own SDK reader through the supported demo action.
        // The separately tested reader below still receives real SDK events, not demo positions.
        val host = ActivityScenario.launch(MainActivity::class.java)
        lateinit var hostModel: RoadViewModel
        val reader = DeviceTestSupport.main {
            PleosNavigationReader(DeviceTestSupport.instrumentation.targetContext) { events += it }
        }
        try {
            host.onActivity { hostModel = ViewModelProvider(it)[RoadViewModel::class.java] }
            DeviceTestSupport.await("actual parked UX policy for foreground host", 15_000) {
                DeviceTestSupport.main { hostModel.ui.settingsAllowed }
            }
            DeviceTestSupport.main { hostModel.demo() }
            repeat(20) { index ->
                events.clear()
                DeviceTestSupport.main { reader.start(); reader.start() } // idempotence, not two subscribers
                DeviceTestSupport.await("real NaviHelper + Fused registration and a selected SDK location", 15_000) {
                    DeviceTestSupport.main { reader.activeSubscriptionCount == 2 } &&
                        events.any { it is SourceEvent.Location && it.selectedForPosition }
                }
                val beforeClose = DeviceTestSupport.main { reader.activeSubscriptionCount }
                assertEquals(2, beforeClose)
                assertTrue(events.filterIsInstance<SourceEvent.SubscriptionCount>().all { it.count in 0..2 })
                assertEquals("Exactly one two-listener registration per session", 1,
                    events.filterIsInstance<SourceEvent.SubscriptionCount>().count { it.count == 2 })
                DeviceTestSupport.main { reader.close(); reader.close() }
                assertEquals(0, DeviceTestSupport.main { reader.activeSubscriptionCount })
                val locationsAtClose = events.count { it is SourceEvent.Location }
                SystemClock.sleep(2_100) // exceed the adapter's 2-second poll interval
                val failures = events.filterIsInstance<SourceEvent.Failure>()
                assertTrue("Actual SDK session or cleanup reported failure: $failures", failures.isEmpty())
                assertEquals("Closed session must not deliver further locations", locationsAtClose,
                    events.count { it is SourceEvent.Location })
                results.put(JSONObject().put("cycle", index + 1).put("source", "REAL_SDK")
                    .put("subscriptionsBeforeClose", beforeClose).put("subscriptionsAfterClose", 0)
                    .put("locationCallbacks", locationsAtClose).put("lateCallbacksAfterClose", 0))
            }
            success = true
        } finally {
            DeviceTestSupport.main { reader.close() }
            host.close()
            DeviceTestSupport.save("sdk-subscriptions", JSONObject().put("passed", success).put("cycles", results)
                .put("lastCycleEvents", JSONArray(events.map { it.toString() }))
                .put("scope", "Actual SDK callbacks and client registration counters; no claim to inspect hidden server-side listener counts or physical vehicle behavior."))
        }
    }
}
