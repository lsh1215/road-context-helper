package dev.pleos.roadcontext

/** Schematic lane coordinates; no live lane detection or measured taper distance. */
internal object LaneEndingMotion {
    const val PERIOD_MS = 3000L
    data class Pose(val lane: Float, val depth: Float, val opacity: Float)
    fun pose(progress: Float): Pose {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val approach = (p / .40f).coerceIn(0f, 1f)
        val t = ((p - .40f) / .43f).coerceIn(0f, 1f)
        val movement = t * t * (3f - 2f * t)
        return Pose(1f - movement,
            if (p <= .40f) .91f - .26f * approach else .65f - .37f * movement,
            when { p < .04f -> p / .04f; p > .91f -> ((1f - p) / .09f).coerceIn(0f, 1f); else -> 1f })
    }
}
