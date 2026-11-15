package dev.pleos.roadcontext.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/**
 * Read-only SDK adapter. Call start/close on the main thread; all consumer events use that thread.
 * It never writes a destination, injects coordinates, chooses a vehicle lane or joins stale TBTs
 * to positions. A successful connection does not assert that position updates are moving.
 */
class PleosNavigationReader(
    context: Context,
    private val selectedProvider: LocationProvider = LocationProvider.FUSED_LOCATION,
    private val consumer: (SourceEvent) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var epoch = 0L
    private var running = false
    private var generation = 0L
    private var routeId: String? = null
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var naviListener: NaviHelperEventListener? = null
    private var fusedListener: FusedLocationListener? = null
    private var naviRegistered = false
    private var fusedRegistered = false
    private var poll: Runnable? = null

    val activeSubscriptionCount: Int get() = (if (naviRegistered) 1 else 0) + (if (fusedRegistered) 1 else 0)

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Start reader on main thread" }
        if (running) return
        running = true
        val session = ++epoch
        routeId = null
        boundary("session_started")
        startNavi(session)
        if (selectedProvider == LocationProvider.FUSED_LOCATION) startFused(session)
    }

    private fun deliver(session: Long, action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (running && session == epoch) action()
        } else handler.post { if (running && session == epoch) action() }
    }

    private fun boundary(reason: String) {
        generation++
        consumer(SourceEvent.RouteBoundary(generation, reason))
    }

    private fun failure(component: String, throwable: Throwable) {
        consumer(SourceEvent.Failure(component, throwable.javaClass.simpleName, throwable.message.orEmpty()))
    }

    private fun startNavi(session: Long) {
        consumer(SourceEvent.Connection("NaviHelper", "CONNECTING"))
        try {
            val sdk = NaviHelper(context)
            navi = sdk
            val listener = object : NaviHelperEventListener {
                override fun onCurrentLocationInfo(info: CurrentLocationInfo) = deliver(session) {
                    val nextId = info.routeId?.takeIf { it.isNotBlank() }
                    if (routeId != nextId) {
                        routeId = nextId
                        boundary("route_id_changed")
                    }
                    location(SourceLocation(
                        latitude = info.latitude, longitude = info.longitude,
                        heading = info.heading.takeIf { it.isFinite() && it >= 0 && it < 360 },
                        measuredTimeMillis = null, receivedElapsedMillis = SystemClock.elapsedRealtime(),
                        provider = LocationProvider.NAVI_HELPER, routeId = nextId,
                        currentRouteIndex = info.currentRouteIndex,
                    ))
                }
                override fun onTBTInfo(items: List<TBTInfo>) = deliver(session) {
                    consumer(SourceEvent.Turns(items.map {
                        SourceTurn(it.type.name, it.distance, it.description)
                    }, SystemClock.elapsedRealtime(), generation))
                }
                override fun onRouteStarted(info: RouteStartInfo) = deliver(session) { boundary("route_started") }
                override fun onRouteEnded() = deliver(session) { boundary("route_ended") }
                override fun onRouteCancelled() = deliver(session) { boundary("route_cancelled") }
                override fun onReRouteEnded() = deliver(session) { boundary("reroute_ended") }
                override fun onDestinationChanged() = deliver(session) { boundary("destination_changed") }
                override fun onRoutingCompleted() = deliver(session) { boundary("routing_completed") }
                override fun onRouteOptionChanged(option: RouteOption) = deliver(session) { boundary("route_option_changed") }
                override fun onWaypointChanged(info: WaypointChangedInfo) = deliver(session) { boundary("waypoint_changed") }
                override fun onRouteStateInfo(info: RouteStateInfo) = deliver(session) {
                    consumer(SourceEvent.RouteState(info.isRouting, info.naviMode.name, SystemClock.elapsedRealtime()))
                }
                override fun onNavigationStatus(status: NavigationStatus) = deliver(session) {
                    consumer(SourceEvent.Connection("Navigation", status.name))
                }
                override fun onError(error: NaviErrorCode) = deliver(session) {
                    consumer(SourceEvent.Failure("NaviHelper", error.name, error.toString()))
                }
            }
            naviListener = listener
            sdk.initialize(onConnected = {
                deliver(session) {
                    // Initialization callback can only attach one listener/poll loop per session.
                    if (!naviRegistered) {
                        sdk.addListener(listener)
                        naviRegistered = true
                        consumer(SourceEvent.SubscriptionCount(activeSubscriptionCount))
                        consumer(SourceEvent.Connection("NaviHelper", "CONNECTED"))
                        schedulePoll(session, sdk)
                    }
                }
            }, onFailed = { throwable -> deliver(session) { failure("NaviHelper", throwable) } })
        } catch (e: Exception) { failure("NaviHelper", e) }
    }

    private fun schedulePoll(session: Long, sdk: NaviHelper) {
        var polls = 0
        val task = object : Runnable {
            override fun run() {
                if (!running || session != epoch) return
                try {
                    sdk.getCurrentLocationInfo()
                    sdk.getTBTInfo()
                    if (polls++ % 5 == 0) sdk.getRouteStateInfo()
                } catch (e: Exception) { failure("NaviHelper", e) }
                handler.postDelayed(this, 2_000)
            }
        }
        poll = task
        handler.post(task)
    }

    private fun startFused(session: Long) {
        consumer(SourceEvent.Connection("FusedLocation", "CHECKING_SUPPORT"))
        try {
            val sdk = FusedLocation(context)
            if (!sdk.isSupported()) {
                consumer(SourceEvent.Connection("FusedLocation", "UNSUPPORTED"))
                return
            }
            fused = sdk
            sdk.initialize()
            val listener = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) = deliver(session) {
                    if (location == null) {
                        consumer(SourceEvent.Connection("FusedLocation", "NO_LOCATION"))
                    } else {
                        this@PleosNavigationReader.location(SourceLocation(
                            latitude = location.latitude, longitude = location.longitude,
                            heading = location.heading.toDouble().takeIf { it.isFinite() && it >= 0 && it < 360 },
                            measuredTimeMillis = location.timestampMillis.takeIf { it > 0 },
                            receivedElapsedMillis = SystemClock.elapsedRealtime(),
                            provider = LocationProvider.FUSED_LOCATION,
                            confidence = location.confidence.takeIf { it.isFinite() },
                        ))
                    }
                }
                override fun onError(exception: Exception) = deliver(session) { failure("FusedLocation", exception) }
            }
            fusedListener = listener
            sdk.registerFusedLocationCallback(listener)
            fusedRegistered = true
            consumer(SourceEvent.SubscriptionCount(activeSubscriptionCount))
            consumer(SourceEvent.Connection("FusedLocation", "SUBSCRIPTION_REQUESTED"))
        } catch (e: Exception) { failure("FusedLocation", e) }
    }

    private fun location(sample: SourceLocation) {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite() ||
            sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0) {
            consumer(SourceEvent.Failure(sample.provider.name, "INVALID_COORDINATES", "SDK sample rejected"))
            return
        }
        consumer(SourceEvent.Location(sample, sample.provider == selectedProvider))
    }

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Close reader on main thread" }
        if (!running) return
        running = false
        epoch++ // Ignore queued events from the old SDK objects, including initialization completion.
        poll?.let(handler::removeCallbacks)
        poll = null
        val oldNavi = navi
        val oldFused = fused
        try { naviListener?.let { if (naviRegistered) oldNavi?.removeListener(it) } }
        catch (e: Exception) { failure("NaviHelper.removeListener", e) }
        try { fusedListener?.let { if (fusedRegistered) oldFused?.unregisterFusedLocationCallback(it) } }
        catch (e: Exception) { failure("FusedLocation.unregister", e) }
        try { oldNavi?.release() } catch (e: Exception) { failure("NaviHelper.release", e) }
        try { oldFused?.release() } catch (e: Exception) { failure("FusedLocation.release", e) }
        navi = null; fused = null; naviListener = null; fusedListener = null
        naviRegistered = false; fusedRegistered = false; routeId = null
        consumer(SourceEvent.SubscriptionCount(0))
        consumer(SourceEvent.Connection("Reader", "CLOSED"))
    }
}
