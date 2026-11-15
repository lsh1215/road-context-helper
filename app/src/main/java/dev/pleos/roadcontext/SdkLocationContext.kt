package dev.pleos.roadcontext

import android.location.Location
import android.os.SystemClock
import dev.pleos.roadcontext.core.GeoPoint
import dev.pleos.roadcontext.data.LiveLocationInput
import dev.pleos.roadcontext.integration.LocationProvider
import dev.pleos.roadcontext.integration.SourceLocation
import kotlin.math.abs

/** Tracks SDK clock and route context without deciding which guidance to display. */
internal class SdkLocationContext {
    private var epochOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    private var lastNavi: SourceLocation? = null
    private var naviMovementObserved = false
    private var lastPosition: Pair<Double, Double>? = null

    var routeGeneration: Long = 0
        private set
    var locationCount: Int = 0
        private set
    var moved: Boolean = false
        private set

    fun refreshClockOffset(nowElapsedMs: Long = SystemClock.elapsedRealtime(), nowEpochMs: Long = System.currentTimeMillis()) {
        epochOffsetMs = nowEpochMs - nowElapsedMs
    }

    fun resetLiveSession() {
        locationCount = 0
        lastPosition = null
        moved = false
        clearNavigation()
    }

    fun clearNavigation() {
        lastNavi = null
        naviMovementObserved = false
    }

    fun onRouteBoundary(): Long {
        // Reader instances restart their own counters; the coordinator must stay monotonic.
        routeGeneration++
        clearNavigation()
        return routeGeneration
    }

    fun observe(location: SourceLocation) {
        if (location.provider != LocationProvider.NAVI_HELPER) return
        lastNavi?.let {
            if (it.latitude != location.latitude || it.longitude != location.longitude) {
                naviMovementObserved = true
            }
        }
        lastNavi = location
    }

    fun select(location: SourceLocation, nowElapsedMs: Long, nowEpochMs: Long): SelectedSdkLocation {
        locationCount++
        val position = location.latitude to location.longitude
        if (lastPosition != null && position != lastPosition) moved = true
        lastPosition = position

        // The tested FusedLocation provider uses epoch milliseconds. Validate drift for each fix.
        val measuredElapsedMs = location.measuredTimeMillis?.minus(epochOffsetMs)
        val clockValidated = location.provider == LocationProvider.FUSED_LOCATION &&
            measuredElapsedMs != null && abs((nowEpochMs - nowElapsedMs) - epochOffsetMs) <= 250 &&
            measuredElapsedMs in 0..nowElapsedMs
        val navi = lastNavi
        val contextValidated = navi != null && naviMovementObserved && !navi.routeId.isNullOrBlank() &&
            nowElapsedMs - navi.receivedElapsedMillis in 0..2000 && separationMeters(location, navi) <= 20.0
        val point = GeoPoint(location.latitude, location.longitude)
        return SelectedSdkLocation(
            point = point,
            measuredElapsedMs = measuredElapsedMs,
            clockValidated = clockValidated,
            liveInput = LiveLocationInput(
                point, location.heading, location.receivedElapsedMillis, measuredElapsedMs,
                clockValidated, contextValidated,
                positionUncertaintyMeters = null, // SDK confidence has no verified meter unit.
                routeId = navi?.routeId, routeGeneration = routeGeneration,
            ),
        )
    }

    private fun separationMeters(a: SourceLocation, b: SourceLocation): Double {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0].toDouble()
    }
}

internal data class SelectedSdkLocation(
    val point: GeoPoint,
    val measuredElapsedMs: Long?,
    val clockValidated: Boolean,
    val liveInput: LiveLocationInput,
)
