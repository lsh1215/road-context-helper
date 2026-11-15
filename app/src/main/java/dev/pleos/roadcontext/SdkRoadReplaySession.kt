package dev.pleos.roadcontext

import android.content.Context
import dev.pleos.roadcontext.core.*
import org.json.JSONObject

/** A selected-road emulator experiment. This is not the approved production road catalog. */
internal class SdkRoadReplaySession(context: Context, val profile: String = "yanghwa") {
    private val asset = when (profile) { "mangwon-b", "mangwon-c", "eungbong" -> "$profile.json"; else -> "route.json" }
    private val json = JSONObject(context.assets.open("gps-replay/$asset").bufferedReader().use { it.readText() })
    val name: String = json.getString("name")
    val road = RoadPolyline(json.getString("id"), json.getJSONArray("points").let { points ->
        (0 until points.length()).map { index -> points.getJSONArray(index).let { GeoPoint(it.getDouble(1), it.getDouble(0)) } }
    })
    val start = json.getDouble("startAlongMeters")
    val end = json.getDouble("endAlongMeters")
    val junction = json.getDouble("junctionAlongMeters")
    private val laneEnd = json.optJSONObject("laneEndZone")
    private val secondLaneEnd = json.optJSONObject("secondLaneEndZone")
    val gate = SdkRouteReplayGate(road, start, end)
    var count = 0
    var lastPoint: GeoPoint? = null
    var lastHeading: Double? = null
    fun reset() { gate.reset(); count = 0; lastPoint = null; lastHeading = null }
    fun visual(result: SdkRouteReplayResult) = SdkRouteReplayVisual(
        roadName = name, points = road.points, vehicle = lastPoint, headingDeg = lastHeading,
        status = result.state.name, alongMeters = if(result.state == SdkRouteReplayState.PASSED)
            lastPoint?.let { GeoProjector().project(it,listOf(road),0.0,20.0).unambiguousCandidate?.alongMeters }
            else result.projection?.alongMeters,
        startAlongMeters = start, endAlongMeters = end, junctionAlongMeters = junction,
        secondLaneEndFromMeters = secondLaneEnd?.getDouble("fromAlongMeters"),
        secondLaneEndToMeters = secondLaneEnd?.getDouble("toAlongMeters"),
        laneEndFromMeters = laneEnd?.getDouble("fromAlongMeters"),
        laneEndToMeters = laneEnd?.getDouble("toAlongMeters"),
        tunnelExitMeters = if (json.has("tunnelExitAlongMeters")) json.getDouble("tunnelExitAlongMeters") else null,
        visible = result.visible, receivedCount = count, contextCase = json.optString("contextCase", "yanghwa"),
    )
}
