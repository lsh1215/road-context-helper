> README 정리 전 개발 기록입니다. 과거 실행 경로와 APK 상태를 포함하며 현재 제품 소개는 저장소 루트 README를 참고하세요. 시험 원본 링크 일부는 로컬 환경에서만 열립니다.

# 초행길 / Road Context Helper

> 이 폴더가 이 앱의 독립 Git 저장소 루트입니다. [독립 빌드·GitHub 업로드 안내](../docs/repository-setup.md)를 참고하세요.

초행 운전자가 진입 차로의 지속·종료·합류 구조를 미리 이해하도록 돕는 Pleos Connect 앱입니다. 현재 차로, 주변 차량, 합류 가능 간격은 판단하지 않습니다.

## 개발 상태

구현·검증 진행 중입니다. 실제 SDK와 가상 도로 시연을 분리합니다. 공개 도로 샘플은 조사했지만 합류 구조·최신성·이용 조건을 검수한 실제 구간이 없어 실제 도로 안내는 활성화하지 않았습니다. 전체 완료 여부는 `verification/acceptance.json`을 기준으로 판정합니다. 기준별로 실제 증거를 연결하며 빌드 성공만으로 전체 기준을 통과시키지 않습니다.

## 실제 도로 GPS SDK 시험 (build-26)

양화대교 북단 → 강변북로 난지 방향의 공개 도로 선형 약809m를 실제 에뮬레이터 GPS로 재생할 수 있습니다. 화면 타이머가 아닌 Pleos FusedLocation 콜백으로 안내를 표시·종료합니다. 정방향 접근/통과, 도로 밖, 역방향 시나리오를 검증했습니다. 이는 debug 전용 지정 경로 시험이며 승인된 차로 안내 또는 기본 내비 경로 연동 완료가 아닙니다.

`python3 scripts/run-sdk-road-demo.py`를 이 저장소 루트에서 실행하세요. 자세한 성공 기준·재현·남은 범위는 [실도로 GPS SDK 시험](../docs/decisions/sdk-gps-road-replay.md)을 참고하세요. 최신 APK는 `verification/runs/build-26/road-context-helper-debug.apk`입니다. 아래 build-23/25 내용은 당시 기능별 검증 이력입니다.

## 현재 실행과 인계

최신 제품과 테스트 APK는 build-23입니다. 도로 면을 크게 구분한 화면과 정차 중 가상 차량 이동 시연을 추가했습니다. 전용 `RoadContext` 에뮬레이터의 운전자 user 10에서 ‘초행길’을 열고, 정차 상태에서 ‘가상 구간 체험’을 누르면 예고→접근→통과 흐름을 확인할 수 있습니다. 재생·정지와 단계 이동을 제공합니다.

최신 후보의 대상 회귀 24개, 합성 입력 1,000회, 실제 OS 권한 요청·허용 흐름을 검증했습니다. 자세한 실행 범위와 과거 실패는 `docs/development-status.md`를 참고하세요. 다음 개발자는 `docs/active-verification.md`부터 읽으면 현재 APK·검증 결과·남은 외부 조건을 확인할 수 있습니다.

## 구성

- `app`: Kotlin/Compose 네이티브 화면, 실제 SDK 세션, 시연 엔진 연결.
- `core`: Android에 의존하지 않는 도로 구조 판정 엔진과 독립 입력 검증.
- `integration`: NaviHelper·FusedLocation·AAOS 주행 UX 제한의 읽기 어댑터.
- `data`: 원자료 반입 검증 및 출처. 현재 실제 승인 구간 없음.
- `verification`: 성공 기준, 독립 시나리오, 실행 로그.

앱 ID는 `dev.pleos.roadcontext`입니다. 루트 `app`의 `dev.pleos.naviprobe`와 별도 설치됩니다. 서버·로그인·상용 지도 서비스는 앱 기동에 필요하지 않습니다.

## 도구·의존성

2026-11-14 확인: AGP 8.9.1 / Gradle 8.11.1 / Kotlin·Compose plugin 2.1.20 / Compose BOM 2025.04.01 / compile SDK 35 / target SDK 34 / min SDK 28 / Java target 17. 실행 호스트는 설치된 JDK 21을 사용합니다.

공식 호환 근거: [AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes), [Compose compiler 설정](https://developer.android.com/jetpack/androidx/releases/compose-kotlin). 각 모듈의 build.gradle이 정확한 고정 버전의 원본입니다.

NaviHelper 3.0.5와 FusedLocation 1.1.3은 공식 Pleos Maven을 사용합니다. 실제 위치 신호에 필요한 최소 권한은 `pleos.car.permission.NAVI_ROUTE`, `pleos.car.permission.FUSED_LOCATION`입니다. 경로 설정·차량 제어 권한은 선언하지 않습니다. 상세 공식 근거와 가용성 확인은 `docs/decisions/sdk.md`를 참고하세요. FusedLocation의 신뢰도는 미터 단위 정확도로 해석하지 않습니다.

## 로컬 빌드

`local.properties`에 로컬 Android SDK의 `sdk.dir`을 지정하세요. 이 파일과 산출물은 소스 관리 대상이 아닙니다. `.env`는 사용하지 않습니다.

```sh
./gradlew clean assembleDebug assembleDebugAndroidTest test lint --max-workers=1 --no-daemon
```

이 명령은 기존 산출물을 지운 뒤 앱·계측 APK, JVM 검사, lint를 실행합니다. `--max-workers=1`은 공유 개발 환경에서 빌드 부하를 제한하기 위한 옵션입니다. 에뮬레이터 성능 측정은 빌드가 종료된 다음 별도로 실행합니다.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

현재 제품 후보는 `verification/runs/build-23/road-context-helper-debug.apk`이며 SHA-256은 `2585e2e5bc0074c64be57bb86a02559849008ade2402660507adc1507ecfa16e`입니다. 새 화면은 ui-redesign-01, 차량 시연은 ui-vehicle-01에서 각각 38개 검사를 통과했습니다. 이전 main16/test17로 ui-08의 24개 회귀 검사와 pipeline-04의 1,000회 입력 검사를 통과했습니다. 예전 빌드의 전체 화면·30분 내구 결과는 이 후보의 전체 재검증으로 간주하지 않습니다.

여러 에뮬레이터가 연결되어 있을 때 `connectedDebugAndroidTest`를 그대로 실행하지 마세요. 이번 작업의 전용 기기를 지정해 설치·계측합니다. 아래 명령은 앱 폴더에서 실행하며 SDK의 `adb`가 PATH에 있다는 전제입니다.

```sh
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument --user 10 -w \
  -e evidenceRun ui_unique_run_id \
  -e class dev.pleos.roadcontext.RoadScreenTest,dev.pleos.roadcontext.RoadResponsiveTest,dev.pleos.roadcontext.RoadViewModelRegressionTest,dev.pleos.roadcontext.MainActivityFeedbackTest,dev.pleos.roadcontext.MainActivityLifecycleTest,dev.pleos.roadcontext.RoadUiCommitPerformanceTest,dev.pleos.roadcontext.RoadCompositionObservationTest,dev.pleos.roadcontext.RoadActualControlCommitTest,dev.pleos.roadcontext.HumanStudyCaptureTest \
  dev.pleos.roadcontext.test/androidx.test.runner.AndroidJUnitRunner
```

실제 제품의 시연 입력 1,000회를 검사하는 실행은 다음과 같이 분리합니다. 제품의 기존 입력 callback과 실제 화면의 composition 적용 시각을 연결하며, 750개 변경 입력은 각각 250ms 기준을 검사합니다. 화면 내용이 그대로인 250개 입력에 새 피드백이 생겼다고 주장하지 않습니다. composition 적용은 물리 픽셀 표시 완료와 다릅니다. 관찰점은 debug에서 명시적으로 켠 테스트 세션만 기록하며 release에는 저장·시계 읽기·SideEffect가 없습니다.

```sh
adb -s emulator-5556 shell am instrument --user 10 -w \
  -e evidenceRun pipeline_unique_run_id \
  -e class dev.pleos.roadcontext.RoadReplayPipelinePerformanceTest \
  dev.pleos.roadcontext.test/androidx.test.runner.AndroidJUnitRunner
```

`evidenceRun`은 실행마다 새 값으로 바꿉니다. 허용 문자는 소문자 영문·숫자·`_`·`-`이고 첫 글자는 영문 또는 숫자이며 최대 64자입니다. 결과는 앱 내부 `files/verification-runs/<evidenceRun>/`에 저장됩니다. 실패한 실행의 원본을 덮어쓰거나 예전 결과를 새 성공으로 집계하지 않습니다.

위 명령은 재현 방법이며 최신 전체 검증 성공 선언이 아닙니다. 실제 OS 권한 흐름은 별도 opt-in 검사입니다. build-18 검사는 테스트 화면 갱신 동기화 누락으로 실패했고 원본을 보존했습니다. 테스트만 수정한 build-19의 permission-flow-02는 요청 중 composition 120.800584ms와 실제 OS 허용 후 복귀를 확인했습니다. OS 팝업의 물리 표시 시간은 측정하지 않았습니다. 실행별 빌드·APK·결과와 미검증 범위는 `docs/development-status.md`와 `verification/acceptance.json`에서 확인합니다.

현재 Pleos 이미지의 운전자 Android user는 10입니다. 다른 이미지에서는 `adb -s emulator-5556 shell am get-current-user`로 확인해야 합니다. 계측 결과는 셸 종료 코드만 보지 말고 `OK (N tests)`와 실패 목록을 확인합니다. 30분 내구 검사는 별도로 다음과 같이 실행합니다.

```sh
adb -s emulator-5556 shell am instrument --user 10 -w \
  -e evidenceRun endurance_unique_run_id \
  -e class dev.pleos.roadcontext.RoadEnduranceTest -e enduranceSeconds 1800 \
  dev.pleos.roadcontext.test/androidx.test.runner.AndroidJUnitRunner
```

도로 도우미 전용 에뮬레이터는 앱 폴더의 `sh scripts/start-emulator.sh`로 실행합니다. 화면 창이 필요하면 `sh scripts/start-emulator.sh --window`를 사용합니다. AVD `RoadContext`, adb serial `emulator-5556`을 사용합니다. 다른 세션이 사용하는 `emulator-5554`에 설치·위치 주입·종료를 하지 않습니다. Pleos Connect 2.1.0 arm64/API34, Emulator 37.2.13, 전체 디스플레이 2560×1440입니다. 앱 창 크기는 실행 시 측정하며 전체 디스플레이 크기와 다릅니다. 동일 에뮬레이터에 두 작업자가 동시에 위치·경로를 입력하지 않습니다.

## 실제 입력과 시연

기본 화면은 실제 연결입니다. 정차·플랫폼 UX 정책 확인 후 ‘가상 구간 체험’을 선택하면 명시적인 가상 도로를 동일 순수 엔진으로 재생합니다. 가상 차량은 정차 중 재생할 때 도로 연결 예시를 9초 동안 한 번 보여줍니다. 일시정지하면 현재 위치에 멈추고, 재개하면 이어서 이동합니다. 처음부터 재생하면 예시도 초기화됩니다. 실제 입력·기록 재생에는 차량 모형을 표시하지 않습니다. 이 움직임은 GPS 기반 현재 차로·실제 주행 경로가 아니며 시연은 SDK 위치 갱신 성공의 증거가 아닙니다. 실제 위치가 수신되더라도 도로 원자료가 승인되지 않았으면 합류 도식을 만들지 않습니다.

현재 디버그 빌드는 앱 내부 `files/diagnostics.jsonl`에 실제 콜백과 시연 결정을 출처와 함께 기록합니다. 릴리즈 빌드에서는 이 진단 기록을 실행하지 않습니다. 사용자 위치를 외부 서버로 전송하지 않습니다.

## 검증과 미완료 범위

초기 빌드·독립 fixture·화면 검사의 실패 로그와 수정 후 실행을 모두 `verification/runs/`에 보관합니다. 예를 들어 ui-01/ui-02의 텍스트 배치 실패는 문단과 Text 노드 폭을 맞춘 뒤 ui-03에서 15개 검사 통과로 재검증했습니다. 범위별 최신 판정은 `verification/acceptance.json`과 `docs/development-status.md`를 확인하세요.

실제 FusedLocation 위치 갱신은 sdk-01의 60초 입력으로 확인했습니다. NaviHelper 위치 고정 문제, 두 입력의 경로 일관성, Fused confidence의 정확도 단위는 해결된 것으로 취급하지 않습니다. 실제 도로 catalog는 비어 있습니다. 검증되지 않은 정확도 값을 임의로 넣거나 가상 도로를 실제 입력 모드에 연결하지 않습니다.

`verification/human-study/`에는 2초 노출 평가 절차와 빈 응답 양식이 있습니다. 실제 참가자 결과를 채우기 전에는 사용자 이해 기준이 통과한 것이 아닙니다. 실차 시험도 수행하지 않았습니다.

빌드 전에 `python3 scripts/snapshot-source.py verification/runs/<run>/source.json`을 실행하면 허용된 소스·테스트 파일의 해시를 남길 수 있습니다. 로컬 자격 정보와 설정은 읽지 않습니다. 빌드 중 소스가 변경되었다면 해당 실행을 고정 소스 재현 증거로 쓰지 말고 다시 빌드합니다.
