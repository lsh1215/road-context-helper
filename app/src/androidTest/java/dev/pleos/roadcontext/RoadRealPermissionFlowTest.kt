package dev.pleos.roadcontext

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * OPT-IN DRAFT. The operator records/restores the original grant outside this test and supplies
 * a genuinely denied FUSED_LOCATION permission. The operator alone observes/clicks the OS dialog.
 * This test never grants/revokes permissions, injects RoadUiState, or manipulates OS dialog controls.
 * Missing opt-in is a JUnit assumption skip before Activity launch, never a passing observation.
 */
class RoadRealPermissionFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val optIn = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue("Real permission flow is NOT_RUN unless -e realPermissionFlow true is explicit",
                    InstrumentationRegistry.getArguments().getString("realPermissionFlow") == "true")
                val run = InstrumentationRegistry.getArguments().getString("evidenceRun")
                check(run != null && run.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))) {
                    "A unique valid evidenceRun is required before Activity launch"
                }
                check(!File(DeviceTestSupport.outputDirectory("verification-device"), "real-permission-flow.json").exists()) {
                    "Refusing to overwrite an existing real-permission-flow artifact"
                }
                base.evaluate()
            }
        }
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(optIn).around(compose)

    private data class PlatformObservation(val atElapsedNs: Long, val permission: Int, val ui: RoadUiState)

    @Test fun realPermissionRequestAppliesPendingThenOperatorRestoresGrant() {
        val permissionName = "pleos.car.permission.FUSED_LOCATION"
        val permissionContext = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = JSONObject().put("schemaVersion", 2).put("status", "STARTED")
            .put("optIn", true).put("permissionName", permissionName)
            .put("initialPlatformPermission", permissionContext.checkSelfPermission(permissionName))
            .put("thresholdPendingCompositionMs", 250).put("operatorRestoreTimeoutMs", 120_000)
            .put("clock", "SystemClock.elapsedRealtimeNanos")
        val errors = mutableListOf<String>()
        val exceptions = JSONArray()
        val platformChanges = JSONArray()
        var model: RoadViewModel? = null
        var session: Long? = null
        var boundaryRecordId = 0L
        var callbackStartedNs = 0L
        var callbackFinishedNs = 0L
        var callbackInvoked = false
        var callbackInvocationCount = 0
        var callbackAccepted = false
        var callbackUi: RoadUiState? = null
        var firstPending: RoadCompositionObservation.Record? = null
        var restored: PlatformObservation? = null
        var preconditionsPassed = false
        var nativeRestorationOraclePassed = false
        val latest = AtomicReference<PlatformObservation?>()
        val stopPolling = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        var poller: Runnable? = null
        var lastLogged: PlatformObservation? = null
        var observerEnded: RoadCompositionObservation.Snapshot? = null

        fun recordFailure(stage: String, error: Throwable) {
            errors += "$stage: ${error.javaClass.simpleName}: ${error.message}"
            exceptions.put(JSONObject().put("stage", stage).put("type", error.javaClass.name)
                .put("message", error.message).put("stack", error.stackTraceToString()))
        }
        fun requireCondition(condition: Boolean, message: String) {
            check(condition) { message }
        }
        fun observePending() {
            val active = session ?: return
            if (firstPending != null || callbackStartedNs == 0L) return
            // First chronological match only; no fastest-record or successful-run selection.
            firstPending = RoadCompositionObservation.snapshot(active).records.firstOrNull {
                it.recordId > boundaryRecordId && it.appliedElapsedNs >= callbackStartedNs &&
                    it.scope == "outer" && it.branch == "controls" && it.state.setup &&
                    it.state.permissionRequestPending && !it.state.simulation && it.state.settingsAllowed
            }
            firstPending?.let {
                Log.i("RoadRealPermissionFlow", "PENDING_COMPOSITION_OBSERVED record=${it.recordId}; operator may approve OS dialog")
            }
        }
        fun observeRestoration() {
            val observation = latest.get() ?: return
            if (observation.atElapsedNs < callbackStartedNs) return
            val previous = lastLogged
            if (previous == null || previous.permission != observation.permission || previous.ui != observation.ui) {
                platformChanges.put(JSONObject().put("atElapsedNs", observation.atElapsedNs)
                    .put("platformPermission", observation.permission)
                    .put("pending", observation.ui.permissionRequestPending).put("setup", observation.ui.setup)
                    .put("simulation", observation.ui.simulation).put("sourceLabel", observation.ui.sourceLabel)
                    .put("inputSource", observation.ui.inputSource?.name ?: JSONObject.NULL)
                    .put("settingsAllowed", observation.ui.settingsAllowed))
                lastLogged = observation
            }
            if (observation.atElapsedNs <= callbackStartedNs + 120_000_000_000L &&
                observation.permission == PackageManager.PERMISSION_GRANTED &&
                !observation.ui.permissionRequestPending && !observation.ui.setup && !observation.ui.simulation &&
                (observation.ui.inputSource == null || observation.ui.inputSource == DiagramSource.REAL_SDK)) {
                if (restored == null) restored = observation
            }
        }
        fun waitForOperatorRestoration() {
            if (!callbackInvoked || restored != null) return
            val deadline = callbackStartedNs + 120_000_000_000L
            // Instrumentation-thread wait only. Main thread remains free for real OS callbacks,
            // lifecycle events and Compose; root monitors this one instrumentation run asynchronously.
            while (SystemClock.elapsedRealtimeNanos() < deadline && restored == null) {
                observePending()
                observeRestoration()
                if (restored == null) SystemClock.sleep(50)
            }
            observePending()
            observeRestoration()
        }

        try {
            requireCondition(InstrumentationRegistry.getArguments().getString("evidenceRun") != null,
                "An explicit unique evidenceRun is required to isolate partial/failed artifacts")
            requireCondition(permissionContext.checkSelfPermission(permissionName) == PackageManager.PERMISSION_DENIED,
                "Precondition failed: real FUSED_LOCATION must already be DENIED; this test will not revoke it")
            model = compose.runOnIdle { ViewModelProvider(compose.activity)[RoadViewModel::class.java] }
            compose.waitUntil(15_000) { requireNotNull(model).ui.settingsAllowed }
            val initial = compose.runOnIdle { requireNotNull(model).ui }
            requireCondition(initial.setup && !initial.permissionRequestPending && !initial.simulation && initial.settingsAllowed,
                "Precondition failed: actual setup, no outstanding request, live mode and parked policy are required")
            compose.onNodeWithTag("source").assertTextEquals("실제 입력 모드")
            compose.onNodeWithTag("permission").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("demo").assertIsDisplayed().assertIsEnabled()
            raw.put("initialAppSetup", initial.setup).put("initialSettingsAllowed", initial.settingsAllowed)
                .put("initialSourceLabel", initial.sourceLabel)
            preconditionsPassed = true

            session = RoadCompositionObservation.begin()
            boundaryRecordId = RoadCompositionObservation.snapshot(requireNotNull(session)).records.lastOrNull()?.recordId ?: 0L
            // Plain main-thread reads, no Compose state write or new observer-driven invalidation.
            // These 100ms polls are recovery diagnostics; their observed times are not UI latency.
            poller = object : Runnable {
                override fun run() {
                    if (stopPolling.get()) return
                    latest.set(PlatformObservation(SystemClock.elapsedRealtimeNanos(),
                        permissionContext.checkSelfPermission(permissionName), requireNotNull(model).ui))
                    mainHandler.postDelayed(this, 100)
                }
            }
            mainHandler.post(requireNotNull(poller))
            try {
                compose.onNodeWithTag("permission").performSemanticsAction(SemanticsActions.OnClick) { callback ->
                    callbackInvocationCount++
                    check(callbackInvocationCount == 1) { "The rendered callback may be invoked only once" }
                    callbackStartedNs = SystemClock.elapsedRealtimeNanos()
                    callbackInvoked = true
                    callbackAccepted = callback()
                    callbackUi = requireNotNull(model).ui
                    callbackFinishedNs = SystemClock.elapsedRealtimeNanos()
                }
            } catch (failure: Throwable) {
                recordFailure("actual_permission_callback", failure)
            }
            Log.i("RoadRealPermissionFlow", "REQUEST_CALLBACK_ATTEMPTED; waiting up to 120s for operator-restored real grant")
            if (callbackInvoked) {
                // Harness correction: ComposeTestRule owns a virtual frame clock. Plain Handler/
                // SystemClock polling alone does not drive recomposition. Use the same normal
                // synchronization as the existing control tests, without changing product state,
                // adding a delay before the OS launch, or manually advancing any frame clock.
                // The OS window can occlude/pause this Activity. Synchronization may consequently
                // wait for the operator's Allow; the operator monitors this run independently.
                raw.put("postCallbackSyncStartedElapsedNs", SystemClock.elapsedRealtimeNanos())
                try {
                    compose.waitForIdle()
                    raw.put("postCallbackSyncCompleted", true)
                } catch (failure: Throwable) {
                    raw.put("postCallbackSyncCompleted", false)
                    recordFailure("post_callback_compose_synchronization", failure)
                } finally {
                    raw.put("postCallbackSyncFinishedElapsedNs", SystemClock.elapsedRealtimeNanos())
                }
            }
            // Do not assert pending latency before restoration: a slow/missing pending composition
            // must not tear down an open OS prompt before the operator can restore the original grant.
            waitForOperatorRestoration()
            if (restored != null) {
                try {
                    compose.onNodeWithTag("source").assertTextEquals("실제 입력 모드")
                    compose.onNodeWithTag("permission").assertDoesNotExist()
                    compose.onNodeWithTag("demo").assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithTag("theme").assertIsDisplayed().assertIsEnabled()
                    requireCondition(permissionContext.checkSelfPermission(permissionName) == PackageManager.PERMISSION_GRANTED,
                        "Actual permission no longer GRANTED at final native oracle")
                    nativeRestorationOraclePassed = true
                } catch (failure: Throwable) { recordFailure("restored_native_oracle", failure) }
            }
        } catch (failure: Throwable) {
            recordFailure("flow", failure)
        } finally {
            // Also honor restoration wait if another observation/assertion unexpectedly failed
            // after launch. Same original deadline: no extra retries or extension of the 120s window.
            try { waitForOperatorRestoration() }
            catch (failure: Throwable) { recordFailure("restoration_wait_cleanup", failure) }
            stopPolling.set(true)
            poller?.let { mainHandler.removeCallbacks(it) }
            try {
                session?.let { observerEnded = RoadCompositionObservation.end(it) }
            } catch (failure: Throwable) { recordFailure("observer_end", failure) }

            val pendingMs = firstPending?.let { (it.appliedElapsedNs - callbackStartedNs) / 1_000_000.0 }
            if (!preconditionsPassed) errors += "PRECONDITIONS_NOT_SATISFIED"
            if (!callbackInvoked) errors += "INPUT_CALLBACK_NOT_INVOKED"
            if (callbackInvocationCount != 1) errors += "RENDERED_CALLBACK_COUNT_NOT_ONE"
            if (callbackInvoked && !callbackAccepted) errors += "INPUT_CALLBACK_NOT_ACCEPTED"
            if (callbackInvoked && callbackUi?.permissionRequestPending != true) errors += "CALLBACK_PENDING_STATE_MISSING"
            if (firstPending == null) errors += "ACTUAL_PENDING_COMPOSITION_MISSING"
            else if (requireNotNull(pendingMs) > 250.0) errors += "PENDING_COMPOSITION_EXCEEDED_250MS"
            if (observerEnded?.droppedRecords != 0L) errors += "COMPOSITION_OBSERVER_INCOMPLETE_OR_DROPPED"
            if (restored == null) errors += "GRANTED_AND_APP_RECOVERY_NOT_OBSERVED_WITHIN_ORIGINAL_120S_WINDOW"
            if (!nativeRestorationOraclePassed) errors += "RESTORED_NATIVE_SOURCE_CONTROLS_NOT_VERIFIED"
            val finalPermission = permissionContext.checkSelfPermission(permissionName)
            if (finalPermission != PackageManager.PERMISSION_GRANTED) errors += "OPERATOR_RESTORATION_STILL_REQUIRED"
            val passed = errors.isEmpty()
            raw.put("status", if (passed) "PASS" else "FAIL_OR_PARTIAL").put("passed", passed)
                .put("preconditionsPassed", preconditionsPassed).put("callbackInvoked", callbackInvoked)
                .put("callbackAccepted", callbackAccepted).put("renderedCallbackInvocationCount", callbackInvocationCount)
                .put("callbackStartedElapsedNs", callbackStartedNs).put("callbackFinishedElapsedNs", callbackFinishedNs)
                .put("callbackPendingState", callbackUi?.permissionRequestPending ?: JSONObject.NULL)
                .put("firstPendingRecordId", firstPending?.recordId ?: JSONObject.NULL)
                .put("pendingCompositionMs", pendingMs ?: JSONObject.NULL)
                .put("platformChanges", platformChanges).put("restorationObservedElapsedNs", restored?.atElapsedNs ?: JSONObject.NULL)
                .put("nativeRestorationOraclePassed", nativeRestorationOraclePassed)
                .put("finalPlatformPermission", finalPermission).put("operatorRestorationStillRequired", finalPermission != PackageManager.PERMISSION_GRANTED)
                .put("errors", JSONArray(errors)).put("exceptions", exceptions)
                .put("compositionDiagnostics", observerEnded?.let(RoadCommitTestSupport::report) ?: JSONObject.NULL)
                .put("harnessCorrection", "permission01 used ComposeTestRule but polled only Handler/SystemClock after input, leaving the virtual Compose clock undriven. This revision performs one normal waitForIdle immediately after the actual callback, matching existing control tests. Original failed evidence is retained. No state, input order, 250ms threshold or first-record attribution is changed; no manual clock advance or forced draw is introduced. A separately preregistered single run diagnoses the harness defect, not selection of a successful retry.")
                .put("synchronizationLimit", "waitForIdle may wait for OS-window dismissal or framework idle timeout. The root operator remains responsible for restoring the original grant while instrumentation is blocked. The recovery loop never extends its original callback-start-plus-120s deadline; a blocking framework synchronization call is not itself a cancellable 120s watchdog. If the OS pauses/occludes app composition and pending is still absent, that remains a missing measurement, not grounds for product delay or an OS-feedback failure claim.")
                .put("scope", "Exactly one actual rendered permission callback under actual denied permission and parked setup. First actual outer composition carrying permissionRequestPending=true must apply within 250ms. Operator handles the real OS dialog; test observes actual granted permission plus pending=false/setup=false and native live-source controls afterward.")
                .put("limits", "No OS dialog visibility or pixel deadline claim. No draw/presentation, process death/SavedState restoration, actual launcher count or duplicate-launch inference. One rendered callback is not proof of one platform launch. Recovery may use the callback or real-grant lifecycle reconciliation; their internal identity is not instrumented. Main-thread 100ms sampling and debug observer add un-subtracted overhead. Missing events are failures/partial evidence, not zero duration or success. If the operator grants before pending ever composes, missing pending remains a failure; no forced wait/state or successful retry is introduced.")
            DeviceTestSupport.save("real-permission-flow", raw)
        }
        assertTrue("Actual permission flow failed/partial; raw evidence preserved: ${errors.joinToString()}", errors.isEmpty())
    }
}
