package dev.pleos.roadcontext

import dev.pleos.roadcontext.core.GuidanceState
import dev.pleos.roadcontext.data.LiveGuidanceResult
import dev.pleos.roadcontext.data.LiveGuidanceStatus

/** Converts a guidance decision into UI state; it does not read SDK events or own lifecycle. */
internal object RoadGuidancePresentation {
    fun clear(state: RoadUiState): RoadUiState = state.copy(
        showDiagram = false, diagramModel = null, distance = null, phase = "안내 대기",
        title = "위치와 경로를 확인하고 있습니다", message = "정보가 확인되면 구조 안내를 이어갑니다.",
    )

    fun live(state: RoadUiState, result: LiveGuidanceResult, locationUnsupported: Boolean): RoadUiState {
        if (locationUnsupported) return clear(state).copy(
            phase = "위치 기능 미지원", title = "이 차량에서는 위치 안내를 사용할 수 없습니다",
            message = "현재 환경이 필요한 위치 기능을 지원하지 않습니다.",
            detail = "정차 중 가상 구간 체험을 이용할 수 있습니다.",
        )

        val entry = result.entry
        if (result.displayable && entry != null) {
            val approaching = result.decision?.state == GuidanceState.APPROACHING
            return state.copy(
                showDiagram = true, inputSource = DiagramSource.REAL_SDK,
                diagramModel = DiagramModel(
                    if (entry.mergeSide == dev.pleos.roadcontext.data.MergeSide.LEFT) MergeSide.LEFT else MergeSide.RIGHT,
                    DiagramSource.REAL_SDK, entry.segment.id, false, true,
                ),
                phase = if (approaching) "합류 구간 접근" else "미리 보는 도로 구조",
                title = if (approaching) "앞에서 차로가 합쳐집니다" else "진입 차로는 앞까지 이어집니다",
                message = if (approaching) "진입 차로가 끝에서 본선과 연결됩니다." else "진입 차로가 끝부분에서 본선과 연결됩니다.",
                detail = "도로 구조 설명이며 현재 차로와 합류 가능 여부는 판단하지 않습니다.",
                distance = result.decision?.distanceToMergeMeters?.takeIf { it > 0 }?.let { "약 ${it.toInt()}m 앞" },
            )
        }

        return clear(state).copy(
            phase = "안내 대기",
            title = when (result.status) {
                LiveGuidanceStatus.NO_APPROVED_ROADS -> "도로 구조 자료를 준비하고 있습니다"
                LiveGuidanceStatus.CATALOG_REJECTED, LiveGuidanceStatus.EVIDENCE_INVALID -> "도로 자료를 확인하고 있습니다"
                LiveGuidanceStatus.PASSED -> "합류 구간을 지났습니다"
                else -> "위치와 경로를 확인하고 있습니다"
            },
            message = if (result.status == LiveGuidanceStatus.NO_APPROVED_ROADS) "검증된 구간에서만 구조를 안내합니다." else "정보가 확인되면 구조 안내를 이어갑니다.",
            detail = "정차 중 가상 구간 체험을 이용할 수 있습니다.",
        )
    }
}
