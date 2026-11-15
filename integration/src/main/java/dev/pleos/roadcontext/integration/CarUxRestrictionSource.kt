package dev.pleos.roadcontext.integration

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Unknown policy must not be interpreted as parked or unrestricted. */
data class SourceUxRestrictions(
    val known: Boolean,
    val requiresDistractionOptimization: Boolean?,
    val activeRestrictions: Int?,
    val maxStringLength: Int?,
    val maxContentItems: Int?,
    val maxContentDepth: Int?,
    val receivedElapsedMillis: Long,
    val reason: String,
) {
    /** Setup/diagnostics are offered only after the actual platform explicitly permits them. */
    val canConfigure: Boolean get() = known && requiresDistractionOptimization == false
}

/**
 * Reads the OEM's current UX restrictions; never derives policy from speed, gear, or mock inputs.
 * Main-thread lifecycle. The AAOS implementation is isolated so android.car remains optional.
 */
class CarUxRestrictionSource(
    context: Context,
    private val consumer: (SourceUxRestrictions) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private var backend: AutoCloseable? = null
    private var running = false
    private var generation = 0L

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Start UX source on main thread" }
        if (running) return
        running = true
        val session = ++generation
        consumer(unknown("CONNECTING"))
        if (Build.VERSION.SDK_INT < 29 ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            consumer(unknown("UNSUPPORTED_ENVIRONMENT"))
            return
        }
        try {
            val instance = AutomotiveBackend(context) { state ->
                if (running && generation == session) consumer(state)
            }
            backend = instance
            instance.start()
        } catch (e: LinkageError) {
            backend?.close()
            backend = null
            consumer(unknown("CAR_LIBRARY_UNAVAILABLE:${e.javaClass.simpleName}"))
        } catch (e: Exception) {
            backend?.close()
            backend = null
            consumer(unknown("CAR_CONNECTION_FAILED:${e.javaClass.simpleName}"))
        }
    }

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Close UX source on main thread" }
        if (!running) return
        running = false
        generation++
        try { backend?.close() } finally { backend = null; consumer(unknown("CLOSED")) }
    }

    private class AutomotiveBackend(
        private val context: Context,
        private val emit: (SourceUxRestrictions) -> Unit,
    ) : AutoCloseable {
        private val handler = Handler(Looper.getMainLooper())
        private var car: Car? = null
        private var manager: CarUxRestrictionsManager? = null
        private var active = true
        private var registered = false
        private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
            if (active) publish(restrictions)
        }

        fun start() {
            car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                if (!active) return@createCar
                if (!ready) {
                    detach()
                    emit(unknown("CAR_SERVICE_DISCONNECTED"))
                } else {
                    try {
                        detach()
                        val policy = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                        if (policy == null) {
                            emit(unknown("UX_MANAGER_UNAVAILABLE"))
                        } else {
                            manager = policy
                            policy.registerListener(listener)
                            registered = true
                            publish(policy.currentCarUxRestrictions)
                        }
                    } catch (e: Exception) {
                        detach()
                        emit(unknown("UX_READ_FAILED:${e.javaClass.simpleName}"))
                    }
                }
            }
            if (car == null) emit(unknown("CAR_CONNECTION_UNAVAILABLE"))
        }

        private fun publish(value: CarUxRestrictions?) {
            if (value == null) {
                emit(unknown("NO_CURRENT_POLICY"))
                return
            }
            emit(SourceUxRestrictions(
                known = true,
                requiresDistractionOptimization = value.isRequiresDistractionOptimization,
                activeRestrictions = value.activeRestrictions,
                maxStringLength = value.maxRestrictedStringLength,
                maxContentItems = value.maxCumulativeContentItems,
                maxContentDepth = value.maxContentDepth,
                receivedElapsedMillis = SystemClock.elapsedRealtime(),
                reason = "PLATFORM_POLICY",
            ))
        }

        private fun detach() {
            try { if (registered) manager?.unregisterListener() }
            catch (_: Exception) { /* Disconnected service cannot acknowledge removal. */ }
            registered = false
            manager = null
        }

        override fun close() {
            active = false
            detach()
            try { car?.disconnect() } catch (_: Exception) { /* Already disconnected. */ }
            car = null
        }
    }

    companion object {
        private fun unknown(reason: String) = SourceUxRestrictions(
            known = false, requiresDistractionOptimization = null, activeRestrictions = null,
            maxStringLength = null, maxContentItems = null, maxContentDepth = null,
            receivedElapsedMillis = SystemClock.elapsedRealtime(), reason = reason,
        )
    }
}
