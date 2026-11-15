# 현재 검증 자료

2026-11-15 기준. 이전 build-07~35 및 리팩토링 과정의 실행 원본은 로컬 공간 정리로 삭제했다. 현재 데모와 그 근거는 다음 위치에 보존한다. `verification/`는 Git에서 제외되므로 실행 원본은 로컬 작업 환경에서만 볼 수 있다.

- **응봉:** `verification/runs/eungbong-two-lanes-17`. 두 진입 차로의 2→1→0 종료, 도로 위 차량, 실제 SDK 종방향 위치, 두 끝 구간 거리와 안내 표시/종료. 동일 GPS 128개, SDK 콜백 131개, 단위·화면 검사 103개 통과. 현재 테스트 APK·소스 스냅샷·해시는 이 실행 폴더에 있다.
- **망원:** `verification/runs/mangwon-markings-comparison-07`. 승인된 비교 편집본. 우리 앱 원본·실선/점선 관측 근거는 `mangwon-markings-06`, 카카오 화면·음성·GPS 원본은 `kakao-context-03`에 있다. 과거 망원 녹화의 검증을 최신 APK에서 재실행한 것으로 표현하지 않는다.
- **최종 영상:** `demos/eungbong-two-lanes-v4`와 `demos/mangwon-short-exit-v1`의 전체·핵심 MP4 총 4개.

응봉 녹화와 경로 생성 도구가 사용하던 이전 입력은 최신 실행의 `support/`에 모았다. 상세 정리 목록은 로컬 `verification/cleanup-manifest.json`에 있다.

현재 차로 감지·주변 차량 간격 판단·전국 도로 자동 분류·실차 검증은 완료된 기능이 아니다. `verification/acceptance.json`은 과거 실행 근거가 섞인 개발 기준 기록이므로 현재 빌드의 전체 성공표로 사용하지 않는다.
