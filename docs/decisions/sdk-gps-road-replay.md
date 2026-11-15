# 실제 도로 GPS 재생으로 위치 안내 검증

2026-11-14. 목적: 운전 없이 에뮬레이터의 위치를 실제 도로 선형대로 재생하고, Pleos FusedLocation SDK 콜백으로 접근 안내가 켜지고 통과 후 사라지는 것을 검증한다.

## 범위와 출처

시험 구간은 양화대교 북단에서 강변북로 난지 방향으로 연결되는 진입로. OSM 원문 지오메트리와 서울시설공단 연결 설명을 확보한다. 본선 연결과 차로 소멸은 다르다. 차선·표지와 차로 유지 조건은 확인되지 않았으므로 '끝까지 가세요', 현재 차로, 끼어들기 가능 여부를 안내하지 않는다. OSM 좌표는 측량 정확도 또는 최신 차선 정보로 취급하지 않는다.

이 기능은 debug 전용 '실도로 GPS 시험'이다. 정상 제품의 검수 카탈로그·경로 정합성·정확도 게이트는 그대로 유지한다. 선택한 시험 선형에 대한 위치와 진행 방향만 비교한다. 주변 모든 도로를 구분하는 production map matching 또는 내비 경로 연동이 완료됐다는 의미가 아니다.

## 입력 경로

호스트 재생 도구 → emulator-5556 geo fix + checksum NMEA → Android 위치 시스템 → Pleos FusedLocation 1.1.3 → 기존 PleosNavigationReader → 시험 구간 판정 → Compose 화면.

앱 intent는 시험 모드를 선택할 뿐 위치·진행 단계·안내 표시 여부를 전달하지 않는다. 앱에는 재생 타이머나 정해진 단계 순서가 없다. SDK의 새로운 위치 콜백만 위치를 전진시킨다. 앱 내 1초 타이머는 오래된 위치 안내를 숨기는 데만 쓴다. 기존 synthetic 시연과 별도 모드이며 입력이 실제 SDK를 통과해도 좌표의 기원은 가상 GPS라고 명시한다.

## 성공 기준

1. 공개 도로 원문, 출처, 라이선스, 연결 노드와 시험 경계 산출 근거를 보존한다.
2. 에뮬레이터 입력 좌표와 SDK 수신 좌표를 대조한다. 최소 10개 이동 위치가 5m 이내로 일치하고 SDK 위치가 실제로 변한다.
3. 진입 전 안내 없음 → 동일 방향의 연속 2개 신선한 위치 후 안내 → 종료 경계 통과 후 안내 없음 순서가 SDK 콜백으로 발생한다.
4. 반대 방향·경로 밖 입력은 즉시 안내를 숨기고, 복귀는 새 위치 2개를 요구한다. 측정 시각 역전·중복 입력으로 새 진입 판정을 만들지 않는다. 5초 지난 위치는 숨긴다.
5. 새 세션 초기화, 앱 중지 후 재개 시 이전 표시가 남지 않는다. FusedLocation 오류는 숨기고 NaviHelper 오류를 위치 성공으로 바꾸지 않는다.
6. 기본 실입력 모드·가상 시연의 동작과 기존 안전 게이트를 변경하지 않는다. debug 자산/진입 기능은 release에서 활성화되지 않는다.
7. core 회귀와 새로운 경계 검사, APK 빌드, 전용 emulator-5556/user10 실측을 통과한다. 실패한 실행은 보존하고 수정 후 재검증한다.
8. 시연 화면의 출처에 GPS 재생과 Pleos SDK를 표시하며, 정차 상태를 유지한다. 기본 내비의 지도·위치 문제 해결을 이 시험의 성공으로 주장하지 않는다.

## 검증 산출물

실행별 입력 로그, SDK/판정 로그, 진입 전/안내 중/통과 후 화면, 짧은 화면 녹화, 앱 및 route 원문 해시, 자동 비교 요약을 verification/runs/sdk-road-replay-* 에 남긴다. 정차 기어/차량 속도는 유지하며 GPS 이동 속도와 차량 속도는 다른 신호임을 명시한다.

## 입력 문제와 수정

첫 시험 sdk-road-replay-01은 좌표는 이동했지만 `geo fix`가 만드는 주기 입력의 방향이 0도로 덮여 대부분 WRONG_DIRECTION이었고 정상 시연 기준에 실패했다. 앱의 방향 검사를 없애거나 도로 접선으로 바꾸지 않았다. 전용 에뮬레이터에 공식 `-grpc 8561` 제어 포트를 열고 `EmulatorController.setGps`로 위도·경도·bearing을 함께 설정했다. getGps readback 및 Android dumpsys location에서 같은 bearing을 확인한 후 Pleos SDK 콜백에서도 방향이 일치했다. goldfish NMEA 파서에 맞는 checksum GGA/RMC 전송도 유지한다.

공식 입력 스키마는 설치 emulator/lib/emulator_controller.proto의 GpsState/setGps/getGps다. 이 로컬 포트는 우리 앱 API가 아니며 SDK 콜백을 직접 만들지 않는다. 좌표 변경은 에뮬레이터 GNSS에만 적용한다. gRPC speed와 이미지의 GPS 속도 변환에는 별도 단위 확인이 필요하므로 본 시험에서 차량 속도·GPS 속도 정확도를 주장하지 않는다. 판정에 속도를 사용하지 않으며 재생 진행 속도는 좌표 간 거리/호스트 재생시간으로 정의한다.

도로 이탈 첫 시험은 앞선 주행의 마지막 좌표가 SDK 구독 직후 수신되어 PASSED가 유지됐다. sdk-road-replay-offroad-01은 실패로 보존했다. 재생 도구가 앱 구독 전에 새 시작 위치를 먼저 입력하도록 고쳤고, 재시험은 OUTSIDE만 발생했다. 새 화면을 강제로 단계 변경한 것이 아니라 새 시험의 GNSS 초기 상태를 올바르게 준비한 변경이다.

## 실행 방법

현재 emulator-5556에는 build-27이 설치되어 있다. 같은 창에서 아래 명령으로 약 1분 재생을 반복한다. 새 로그 폴더가 자동 생성된다. '시험 다시 준비'는 앱 감지 세션만 초기화하며 GPS를 되감지 않는다. GPS 재생은 반드시 호스트 도구로 실행한다.

```sh
python3 apps/road-context-helper/scripts/run-sdk-road-demo.py
```

에뮬레이터가 꺼져 있으면 먼저 아래 명령으로 실행한다. 이미 RoadContext가 켜져 있으면 중복 실행하지 않는다. 설치된 debug APK와 위치 권한이 필요하다.

```sh
sh apps/road-context-helper/scripts/start-emulator.sh --window --gps-replay
```

직접 선택해 시험할 때는 앱의 '실도로 GPS 시험'을 사용한다. 모드는 debug 빌드+에뮬레이터+정차 제어 허용+위치 권한을 모두 요구한다. route.json은 debug APK에만 포함되고 release APK에는 없다.

## 검증 범위

- core 75 + data 52 + app JVM 7 = 134개 통과. 추가 gate 경계 테스트 11개 포함.
- debug/release 빌드 성공. lintDebug 오류0, 기존 경고8.
- sdk-road-replay-02: 실제 SDK 이동 좌표54개, 입력65개 중63개가5m 이내 일치. 이 오차는 입력 전달 대조값이며 실차 GPS 정확도 주장이 아니다. WAITING → GUIDANCE(239.993m) → PASSED(495.082m).
- sdk-road-replay-offroad-02: 26개 입력 모두5m 이내 일치, OUTSIDE만 발생하고 표시0회.
- sdk-road-replay-reverse-01: 역방향 SDK 좌표16개, WRONG_DIRECTION만 발생하고 표시0회.
- 오래된 측정, 중복·역전 시각, 위치 점프, 이탈 후 재확인, 재설정은 core 단위 테스트로 확인. SDK 자체가 지속적으로 마지막 위치를 갱신하는 환경이므로 '호스트 재생 중단'을 위치 끊김 실측으로 간주하지 않는다.
- 현재 production 도로 카탈로그와 NaviHelper 경로 정합성 게이트는 그대로다. 시험 선형에 대한 동작 성공을 실도로 전체 차로 안내 출시 승인으로 승격하지 않는다. 이번 범위에 실차/운전자 이해도 평가나 전체 기존 계측 UI 회귀 재실행은 포함하지 않는다.

최종 build-26 시연 sdk-road-demo-03도 PASS. SDK 이동좌표52개, 입력65개 중61개가5m이내 대응, 방향오차최대0.005도. 안내 시작240.016m/종료495.082m. final-ui.xml에서 통과 문구 존재 및 접근 안내 문구 부재 확인. demo.mp4에 전체 화면78초 보존. GPS 기원은 가상이며 SDK 수신 경로는 실제다.


## build-27: 구간 한정 설명 UI와 기본 내비 재검증

전체 길 지도 대신 기존 큰 차량 모형/도로 구조를 재사용한다. 안내 전후는 대기 문구만, 유효한 GUIDANCE에서는 연결 구조 예시만 보여준다. 차량 위치는 SDK 진행거리 비율이며 설명용 그림이지 실제 차로 측위가 아니다. 시간만 진행하는 시험, null/NaN 진행률, 오래된 상태 숨김까지 계측 테스트 3개를 추가하고 기존 차량 테스트 4개와 함께 통과했다.

이번 기본 내비 실험은 활성 경로를 설정한 상태에서 원래 내비 위치 주변 20개 GPS를 입력했다. 공식 gRPC setGps와 정상 체크섬 GGA/RMC/GSA/GSV/VTG를 함께 사용했다. NaviHelper 11개 콜백의 좌표는 모두 37.4127212,127.094681로 동일했다. 입력과 원본 수신 기록은 verification/runs/native-sync-full-nmea-01에 있다. 실험 종료 후 임시 경로 안내를 종료했다.

공개 TripRequest는 목적지만, RouteWithWaypointsRequest는 목적지와 경유지만 받으며 출발 위치나 모의주행 시작 API는 확인되지 않았다. SIMULATION_GUIDING enum의 존재를 시작 API 지원으로 해석하지 않는다. 기본 내비의 정상 경로 선택 화면에서도 이번 점검에서는 모의주행 항목을 발견하지 못했다.

동일 현상의 커뮤니티 제보(제조사 공식 결함 인정/해결 답변은 아님):
- https://github.com/Pleos-Playground/pleos-playground-api/discussions/29
- https://github.com/Pleos-Playground/pleos-playground-api/discussions/32
- 빈 지도 제보: https://github.com/Pleos-Playground/pleos-playground-api/discussions/34

기본 내비 3D 화면과 우리 앱을 나란히 띄운 기록은 남기되, 기본 내비 지도/차량 이동 문제가 해결됐다는 증거로 취급하지 않는다. 네이티브 APK 수정이나 임의 SDK 응답 생성은 수행하지 않았다.

contextual-sdk-demo-05: PASS. 입력65개/실제 Fused콜백62개/서로 다른 좌표52개, 입력61개가5m 이내 대응했다. SDK 판정은 WAITING → GUIDANCE(239.993m) → PASSED(495.082m). 화면 관찰로 안내 전·후 도로/차량 부재, 안내 중 존재를 확인했다. 기본 내비는 계속 빈 지도/고정 위치였으며 동기 이동 성공으로 세지 않는다. demo.mp4는 이 상태를 그대로 녹화했다.
