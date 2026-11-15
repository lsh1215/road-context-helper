package dev.pleos.roadcontext

/** The supported reference roads. Unknown profiles never silently become another road. */
internal enum class RoadReplayCase(val profile: String, val asset: String) {
    YANGHWA("yanghwa", "route.json"),
    MANGWON_SHORT_EXIT("mangwon-b", "mangwon-b.json"),
    MANGWON_LANE_END("mangwon-c", "mangwon-c.json"),
    EUNGBONG("eungbong", "eungbong.json");

    val isMangwon: Boolean get() = this == MANGWON_SHORT_EXIT || this == MANGWON_LANE_END

    companion object {
        fun fromProfile(profile: String): RoadReplayCase? = entries.firstOrNull { it.profile == profile }
    }
}
