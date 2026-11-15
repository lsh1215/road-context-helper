package dev.pleos.roadcontext

import kotlin.math.ceil
import kotlin.math.floor

/** Roadview camera brackets are not a surveyed endpoint or a GPS error bound. */
internal data class LaneEndZone(val fromMeters: Double, val toMeters: Double, val tunnelExitMeters: Double) {
    init {
        require(fromMeters.isFinite() && toMeters.isFinite() && tunnelExitMeters.isFinite())
        require(tunnelExitMeters >= 0 && tunnelExitMeters < fromMeters && fromMeters < toMeters)
    }
    fun at(along: Double): LaneEndProgress? {
        if (!along.isFinite() || along < 0) return null
        return LaneEndProgress(along, (fromMeters - along).coerceAtLeast(0.0),
            (toMeters - along).coerceAtLeast(0.0), when {
                along < fromMeters -> LaneEndPhase.APPROACH
                along <= toMeters -> LaneEndPhase.END_REGION
                else -> LaneEndPhase.BEYOND
            })
    }
}
internal enum class LaneEndPhase { APPROACH, END_REGION, BEYOND }
internal data class LaneEndProgress(val alongMeters: Double, val nearMeters: Double,
    val farMeters: Double, val phase: LaneEndPhase) {
    val label: String get() = when (phase) {
        LaneEndPhase.APPROACH -> "약 ${(floor(nearMeters / 10) * 10).toInt()}–${(ceil(farMeters / 10) * 10).toInt()}m"
        LaneEndPhase.END_REGION -> "차로 끝 구간 부근"
        LaneEndPhase.BEYOND -> "차로 끝 구간 지남"
    }
}
