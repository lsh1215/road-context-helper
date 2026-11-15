package dev.pleos.roadcontext

/** Parked demo categories. A mirrored example is a schematic, not another surveyed road. */
internal enum class RoadDemoCase(
    val id: String,
    val label: String,
    val title: String,
    val previewTitle: String,
    val description: String,
    val source: String,
) {
    LANE_END_RIGHT_TO_LEFT(
        "lane_end_rl", "본선 연결 · 우→좌", "진입 차로는 앞까지 이어집니다",
        "앞에서 차로가 끝나 본선에 연결됩니다",
        "오른쪽 진입 차로가 끝에서 왼쪽 본선으로 이어지는 구조",
        "망원 차로 끝 사례 기반 도식",
    ),
    LANE_END_LEFT_TO_RIGHT(
        "lane_end_lr", "본선 연결 · 좌→우", "진입 차로는 앞까지 이어집니다",
        "앞에서 차로가 끝나 본선에 연결됩니다",
        "왼쪽 진입 차로가 끝에서 오른쪽 본선으로 이어지는 구조",
        "좌우 반전 도로 예시 · 실제 구간 아님",
    ),
    CLOSE_EXIT_RIGHT_TO_LEFT(
        "close_exit_rl", "가까운 진출 · 우→좌", "합류 후 왼쪽 진출을 미리 준비합니다",
        "합류 전에 가까운 진출 방향을 확인하세요",
        "실선 구간을 지난 뒤 짧은 점선 구간에서 왼쪽 진출로 연결",
        "망원 실선·점선 사례 기반 도식",
    ),
    CLOSE_EXIT_LEFT_TO_RIGHT(
        "close_exit_lr", "가까운 진출 · 좌→우", "합류 후 오른쪽 진출을 미리 준비합니다",
        "합류 전에 가까운 진출 방향을 확인하세요",
        "실선 구간을 지난 뒤 짧은 점선 구간에서 오른쪽 진출로 연결",
        "좌우 반전 도로 예시 · 실제 구간 아님",
    ),
    TWO_STAGE_LANE_END(
        "two_stage_lane_end", "두 차로 연속 소멸", "두 진입 차로가 순서대로 끝납니다",
        "출구 뒤에도 진입 차로가 이어집니다",
        "첫 차로와 남은 차로가 차례로 끝나 분홍색 유도선 방면에 연결",
        "응봉 관측 자료 기반 도식",
    );

    val mirrored: Boolean get() = this == LANE_END_LEFT_TO_RIGHT || this == CLOSE_EXIT_LEFT_TO_RIGHT

    companion object {
        fun fromRoadProfile(profile: String?): RoadDemoCase = when (profile) {
            RoadReplayCase.EUNGBONG.profile -> TWO_STAGE_LANE_END
            RoadReplayCase.MANGWON_SHORT_EXIT.profile -> CLOSE_EXIT_RIGHT_TO_LEFT
            else -> LANE_END_RIGHT_TO_LEFT
        }
    }
}
