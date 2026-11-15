package dev.pleos.roadcontext

/** Curated fictional road examples; none is a live lane estimate or a driving command. */
internal enum class ClusterScenario(
    val id: String,
    val label: String,
    val title: String,
    val detail: String,
    val previewTitle: String,
    val roadDescription: String,
) {
    MERGE(
        "merge", "본선 연결", "진입 차로 끝에서 본선 연결",
        "가상 구간 · 오른쪽 진입 차로가 끝에서 본선과 연결됩니다. 도로 구조를 미리 확인하고 표지와 주변 교통에 맞춰 합류하세요.",
        "100m 앞 · 본선 연결",
        "오른쪽 진입 차로가 끝나며 왼쪽 본선으로 연결되는 도로 구조 예시.",
    ),
    SHORT_EXIT(
        "short_exit", "합류 후 가까운 진출", "합류 뒤 300m 안에 오른쪽 진출",
        "가상 구간 · 왼쪽으로 합류한 뒤 4개 본선 차로 중 오른쪽 끝 차로에서 진출하는 경로입니다. 합류 전에 다음 진출을 확인하고 여유 있게 준비하세요. 안전하게 이동할 여유가 없으면 다음 경로를 이용하세요.",
        "합류 뒤 300m · 오른쪽 진출 준비",
        "왼쪽에서 합류한 뒤 4개 본선 차로를 세 단계로 이동해 오른쪽 진출로로 이어지는 가상 도로 구조 예시.",
    ),
    EXIT_ONLY(
        "exit_only", "진출 전용 차로", "본선 계속 가려면 왼쪽으로 준비",
        "가상 구간 · 오른쪽 차로는 진출로로 이어지고 본선은 왼쪽으로 계속됩니다. 본선 주행을 위해 미리 차로를 확인하세요. 안전한 차로 변경이 어려우면 진출 후 경로를 다시 확인하세요.",
        "200m 앞 · 오른쪽 차로 진출 전용",
        "오른쪽 끝 차로가 진출 전용으로 분리되며 차량은 왼쪽 차로로 이동해 본선을 유지하는 도로 구조 예시.",
    ),
}
