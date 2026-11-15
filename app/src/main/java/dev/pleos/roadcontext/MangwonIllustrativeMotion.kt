package dev.pleos.roadcontext

/** Normalized drawing coordinates, NOT surveyed road distances or live lane permissions. */
internal object MangwonIllustrativeMotion {
    const val PERIOD_MS = 3400L
    const val SOLID_END_DEPTH = .60f
    data class Pose(val lane: Float, val depth: Float, val opacity: Float)

    fun pose(progress: Float): Pose {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        // Clear the solid-line section before any sideways movement. The short lead-out
        // also leaves room for the rear of the drawn car to clear the boundary.
        val approach = (p / .32f).coerceIn(0f, 1f)
        val raw = ((p - .32f) / .50f).coerceIn(0f, 1f)
        val shift = raw * raw * (3f - 2f * raw)
        return Pose(
            lane = 1.5f - 3f * shift,
            depth = if (p <= .32f) .91f - .39f * approach else .52f - .23f * shift,
            opacity = when {
                p < .04f -> p / .04f
                p > .90f -> ((1f - p) / .10f).coerceIn(0f, 1f)
                else -> 1f
            },
        )
    }
}
