package dev.pleos.roadcontext.integration

/** Provider is deliberately distinct from the real/replay provenance used by the product. */
enum class LocationProvider { NAVI_HELPER, FUSED_LOCATION }

data class SourceLocation(
    val latitude: Double,
    val longitude: Double,
    val heading: Double?,
    /** Raw SDK timestamp. Clock domain has not yet been verified; never infer elapsed realtime. */
    val measuredTimeMillis: Long?,
    val receivedElapsedMillis: Long,
    val provider: LocationProvider,
    /** SDK confidence is not documented as a meter accuracy or a calibrated probability. */
    val confidence: Float? = null,
    val routeId: String? = null,
    val currentRouteIndex: Int? = null,
    val source: String = "REAL_SDK",
)

data class SourceTurn(val type: String, val rawDistance: Double, val description: String)

sealed interface SourceEvent {
    data class Location(val sample: SourceLocation, val selectedForPosition: Boolean) : SourceEvent
    data class Turns(
        val items: List<SourceTurn>,
        val receivedElapsedMillis: Long,
        val observedRouteGeneration: Long,
        // NaviHelper TBT callbacks contain neither a route ID nor a request correlation token.
        val routeAssociationVerified: Boolean = false,
    ) : SourceEvent
    data class RouteBoundary(val generation: Long, val reason: String) : SourceEvent
    data class RouteState(val routing: Boolean, val mode: String, val receivedElapsedMillis: Long) : SourceEvent
    data class Connection(val component: String, val state: String) : SourceEvent
    data class Failure(val component: String, val code: String, val detail: String) : SourceEvent
    data class SubscriptionCount(val count: Int) : SourceEvent
}
