package dev.pleos.roadcontext

import android.app.Application
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

/**
 * Gate and saved-state boundary regressions, NOT actual denied-permission dialogs or latency tests.
 * No permission is revoked/granted. Restored-pending fixtures model persisted request ownership;
 * no externally authored RoadUiState is injected and fake callback delays are not timing evidence.
 */
class PermissionRequestRegressionTest {
    @Test fun repeatedAndReentrantRequestsLaunchOnceUntilTerminalCompletion() = DeviceTestSupport.main {
        val gate = PermissionRequestGate(SavedStateHandle())
        val changes = mutableListOf<Boolean>()
        var launches = 0
        assertTrue(gate.launch(changes::add) {
            launches++
            assertFalse(gate.launch(changes::add) { launches++ })
        })
        repeat(100) { assertFalse(gate.launch(changes::add) { launches++ }) }
        assertEquals(1, launches)
        assertEquals(listOf(true), changes)
        assertTrue(gate.complete(changes::add))
        assertFalse(gate.pending)
        assertTrue(gate.launch(changes::add) { launches++ })
        assertEquals(2, launches)
        gate.complete(changes::add)
        Unit
    }

    @Test fun launchExceptionReleasesPersistedOwnershipAndAllowsRetry() = DeviceTestSupport.main {
        val handle = SavedStateHandle()
        val gate = PermissionRequestGate(handle)
        val changes = mutableListOf<Boolean>()
        val expectedFailure = IllegalStateException("test-only launch failure")
        try {
            gate.launch(changes::add) { throw expectedFailure }
            fail("The original launch error must propagate to the UI error handler")
        } catch (actual: IllegalStateException) { assertSame(expectedFailure, actual) }
        assertFalse(gate.pending)
        assertEquals(false, handle.get<Boolean>(PermissionRequestGate.STATE_KEY))
        assertEquals(listOf(true, false), changes)
        assertTrue(gate.launch(changes::add) {})
        gate.complete(changes::add)
        Unit
    }

    @Test fun savedRegistryAndApplicationHandleFactoryRestorePendingAndCompletedStates() = DeviceTestSupport.main {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val pendingArgs = Bundle().apply { putBoolean(PermissionRequestGate.STATE_KEY, true) }
        val saved = SavedOwner().use { first ->
            val firstModel = ViewModelProvider(first, SavedStateViewModelFactory(application, first, pendingArgs))[RoadViewModel::class.java]
            assertTrue(firstModel.ui.permissionRequestPending)
            first.save()
        }
        val completedSaved = SavedOwner(saved).use { restored ->
            val model = ViewModelProvider(restored, SavedStateViewModelFactory(application, restored))[RoadViewModel::class.java]
            assertTrue(model.ui.permissionRequestPending)
            model.setRestrictions(true, false, 0)
            var launches = 0
            assertFalse(model.requestLocationPermission { launches++ })
            assertEquals(0, launches)
            model.permissionResult(false) // denied/cancelled contract result releases restored ownership
            assertFalse(model.ui.permissionRequestPending)
            restored.save()
        }
        SavedOwner(completedSaved).use { completed ->
            val completedModel = ViewModelProvider(completed, SavedStateViewModelFactory(application, completed))[RoadViewModel::class.java]
            assertFalse(completedModel.ui.permissionRequestPending)
        }
    }

    @Test fun stopKeepsPendingAndDemoCannotRaceTheResultOrBeOverwrittenByAnOldResult() = withPendingModel { model ->
        model.setRestrictions(true, false, 0)
        model.stop()
        assertTrue(model.ui.permissionRequestPending)
        model.demo()
        assertFalse(model.ui.simulation)
        model.live()
        assertTrue(model.ui.permissionRequestPending)
        model.permissionResult(false)
        assertFalse(model.ui.permissionRequestPending)
        assertEquals("위치 권한 필요", model.ui.phase)
        model.demo()
        val demo = model.ui
        model.permissionResult(false) // stale/duplicate denial must not replace the synthetic scene
        assertEquals(demo, model.ui)
        model.permissionResult(true)
        assertEquals(demo, model.ui)
    }

    @Test fun grantedContractAndGrantedReentryUseExistingRealPermissionWithoutLaunchingOsUi() {
        DeviceTestSupport.requireActualLocationPermission()
        withPendingModel { model ->
            val app = ApplicationProvider.getApplicationContext<Application>()
            assertEquals(true, ActivityResultContracts.RequestPermission()
                .getSynchronousResult(app, "pleos.car.permission.FUSED_LOCATION")?.value)
            model.setRestrictions(true, false, 0)
            model.start() // actual platform grant reconciles a restored pending owner
            assertFalse(model.ui.permissionRequestPending)
            assertFalse(model.ui.setup)
            var launches = 0
            assertFalse(model.requestLocationPermission { launches++ })
            assertEquals(0, launches)
        }
    }

    @Test fun actualActivityDefaultFactoryCreatesAndRetainsApplicationSavedStateHandleConstructor() {
        DeviceTestSupport.requireActualLocationPermission()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var original: RoadViewModel? = null
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[RoadViewModel::class.java]
                assertFalse(requireNotNull(original).ui.permissionRequestPending)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(original, ViewModelProvider(activity)[RoadViewModel::class.java])
            }
        }
    }

    private fun withPendingModel(block: (RoadViewModel) -> Unit) = DeviceTestSupport.main {
        val handle = SavedStateHandle(mapOf(PermissionRequestGate.STATE_KEY to true))
        val model = RoadViewModel(ApplicationProvider.getApplicationContext<Application>(), handle)
        val store = ViewModelStore().apply { put("permission-boundary", model) }
        try { block(model) } finally { store.clear() }
    }

    /** Public saved-state APIs exercise an actual Bundle round trip, not a copied boolean alone. */
    private class SavedOwner(restored: Bundle? = null) : SavedStateRegistryOwner, ViewModelStoreOwner, AutoCloseable {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init {
            controller.performAttach()
            controller.performRestore(restored)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        fun save(): Bundle = Bundle().also { controller.performSave(it) }
        override fun close() {
            viewModelStore.clear()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
