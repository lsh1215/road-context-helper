package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real-time 30-minute MainActivity test. Inputs remain explicitly synthetic. */
class RoadEnduranceTest {
    @Test fun thirtyMinuteActualTimeSyntheticReplay() {
        val seconds = InstrumentationRegistry.getArguments().getString("enduranceSeconds")?.toLongOrNull()
        assumeTrue("Manual opt-in: -e enduranceSeconds 1800; default runs skip this class", seconds != null)
        require(seconds == 1800L) { "Do not shorten the 30-minute acceptance run" }
        DeviceTestSupport.requireActualLocationPermission()
        var started = SystemClock.elapsedRealtime()
        val cycles = JSONArray()
        var success = false
        var currentStep = -1
        var currentCycle = 0
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: RoadViewModel
                scenario.onActivity { model = ViewModelProvider(it)[RoadViewModel::class.java] }
                DeviceTestSupport.await("actual parked UX policy", 15_000) { DeviceTestSupport.main { model.ui.settingsAllowed } }
                started = SystemClock.elapsedRealtime() // exclude permission/policy/launch setup
                val deadline = started + seconds!! * 1000
                while (SystemClock.elapsedRealtime() < deadline) {
                    currentCycle++
                    DeviceTestSupport.main { assertTrue(model.ui.settingsAllowed); model.demo() }
                    var previewEntries = 0
                    var priorPhase = ""
                    val states = JSONArray()
                    for (step in 0 until 8) {
                        currentStep = step
                        SystemClock.sleep(3_000)
                        scenario.onActivity { assertFalse("Activity finishing during replay", it.isFinishing) }
                        val state = DeviceTestSupport.main {
                            assertTrue("Real platform stopped allowing demo controls", model.ui.settingsAllowed)
                            model.next(); model.ui
                        }
                        assertTrue(state.simulation)
                        assertEquals(DiagramSource.REPLAY_SYNTHETIC, state.inputSource)
                        if (state.phase == "미리 보는 도로 구조" && priorPhase != state.phase) previewEntries++
                        priorPhase = state.phase
                        states.put(JSONObject().put("step", step).put("phase", state.phase)
                            .put("diagram", state.showDiagram).put("atElapsedMs", SystemClock.elapsedRealtime()))
                    }
                    assertEquals("One preview entry per complete synthetic passage", 1, previewEntries)
                    val finalState = DeviceTestSupport.main { model.ui }
                    assertEquals("구간 통과", finalState.phase)
                    assertFalse(finalState.showDiagram)
                    cycles.put(JSONObject().put("cycle", currentCycle).put("previewEntries", previewEntries)
                        .put("states", states))
                    checkpoint(started, cycles, false, currentCycle, currentStep)
                }
                assertTrue(SystemClock.elapsedRealtime() - started >= 1_800_000)
                assertTrue("No completed passages", cycles.length() > 0)
                success = true
            }
        } finally { checkpoint(started, cycles, success, currentCycle, currentStep) }
    }

    private fun checkpoint(started: Long, cycles: JSONArray, passed: Boolean, cycle: Int, step: Int) {
        DeviceTestSupport.save("endurance-1800", JSONObject().put("passed", passed)
            .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("startedElapsedMs", started)
            .put("completedCycles", cycles.length()).put("currentCycle", cycle).put("currentStep", step)
            .put("cycles", cycles).put("source", "REPLAY_SYNTHETIC")
            .put("scope", "Actual-time MainActivity/model replay with actual platform UX policy. Preview transitions observed in UI state; parent must also inspect raw decision/logcat for count, crash and ANR. No real route or physical vehicle claim."))
    }
}
