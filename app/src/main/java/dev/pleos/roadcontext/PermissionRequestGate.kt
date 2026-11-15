package dev.pleos.roadcontext

import androidx.lifecycle.SavedStateHandle

/** Main-thread request ownership. SavedStateHandle is the sole pending authority. */
internal class PermissionRequestGate(private val savedState: SavedStateHandle) {
    val pending: Boolean get() = savedState[STATE_KEY] ?: false

    fun launch(onPendingChanged: (Boolean) -> Unit, request: () -> Unit): Boolean {
        if (pending) return false
        savedState[STATE_KEY] = true
        try {
            onPendingChanged(true)
            request()
        } catch (failure: Exception) {
            complete(onPendingChanged)
            throw failure
        }
        return true
    }

    /** Permission granted, denied, cancelled, or launch failed: every terminal outcome releases. */
    fun complete(onPendingChanged: (Boolean) -> Unit): Boolean {
        val wasPending = pending
        savedState[STATE_KEY] = false
        onPendingChanged(false)
        return wasPending
    }

    companion object { const val STATE_KEY = "location_permission_request_pending" }
}
