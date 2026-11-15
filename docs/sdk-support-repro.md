# NaviHelper 에뮬레이터 위치 고정 현상 재현 보고서

작성일: 2026-11-14 · **외부 개발지원 문의용 초안, 발송·게시하지 않음**

Pleos Connect 에뮬레이터에서 정상 목적지 안내가 진행되는 동안 NaviHelper 위치는 기본 좌표에 머물렀습니다. 같은 입력에서 FusedLocation은21개 좌표 모두를 반환했습니다. SDK 결함이나 원인을 확정하려는 보고서가 아니라, 에뮬레이터에서 공식적으로 지원되는 위치 입력 방법과 두 위치 공급자의 지원 범위를 확인하기 위한 문의입니다. 실차·전체 도로 위치 기능이 불가능하다는 의미는 아닙니다.

## 환경과 권한

| 항목 | 확인 내용 |
|---|---|
| 실행 환경 | Apple Silicon arm64, Pleos Connect2.1.0 arm64/API34, Android Emulator37.2.13 |
| 시험 장치 | 전용 `emulator-5556`, Android foreground user10, 실제 차량 아님 |
| 위치 시험의 차량 상태 |기본 비교는 P/0. 추가 결합 시험은 D/2~25km/h가 관측된16.242초 시간창 포함 |
| Navi SDK | `ai.pleos.playground:NaviHelper:3.0.5` |
| 위치 SDK | `ai.pleos.playground:FusedLocation:1.1.3` |
| 설치 위치 서비스 | `ai.pleos.playground.service.location.fused`, versionName1.0.0/versionCode6 (`pm path`/`dumpsys package` 읽기 확인) |
| 제품 앱 | `dev.pleos.roadcontext`, compileSdk35/targetSdk34, build-10 |
| 제품 권한 | `pleos.car.permission.NAVI_ROUTE`, `pleos.car.permission.FUSED_LOCATION` |
| 런타임 허용 | 실제 시스템 권한 UI에서 FUSED_LOCATION 허용. SDK의 지원 여부 확인과 초기화 후 callback 등록 |
| 별도 경로 Probe | `dev.pleos.naviprobe`; NAVI_ROUTE, NAVI_ROUTE_SEARCH, NAVI_CUSTOM_MAP, NAVI_CUSTOM_ROUTE, NAVI_CUSTOM_ETC 선언 |

제품 앱은 위치·경로 읽기만 수행합니다. 목적지 설정은 기존 별도 Probe의 공개 `NaviHelper.requestRoute(RouteInfo)`로 실행했으며, 제품에 경로 쓰기 권한을 추가하지 않았습니다. Probe가 가진 권한 전체가 이 재현에 필수라는 주장은 아닙니다.

APK SHA-256:

```text
제품 build-10: 4cea7504943938ee290e39da54fcc88e14b058ef6701992dae6329799caabeba
기존 Probe:   c67d580c61510c24345d005fb1270f2269d4331eda28da9ee0da363878239fcc
```

## 재현 절차와 실제 결과

1. 별도 Probe에서 정상 공개 API로 목적지를 요청했습니다. Probe 및 제품 앱 모두에서 `mode=ROUTE_GUIDING`, nonempty routeId,10개TBT 수신을 확인했습니다. `isRouting=false`도 동시에 반환되므로 이를 ‘안내 비활성’으로 해석하지 않았습니다. requestRoute 호출 성공만으로 활성 상태를 판정하지 않았습니다.
2. 제품 앱의 실제입력 모드를 전경에서 실행했습니다. 활성mode 확인 후 위치 입력을 시작했습니다. 차량 기어·속도는 P/0을 유지했습니다.
3. 내비가 반환하던 공개 에뮬레이터 기본 위치인 **성남37.4127212,127.094681**에서 시작했습니다. 위도만 매3초0.00015씩 증가시키는21개 좌표를 입력했습니다.
4. 매 점마다 `geo fix`를 먼저 보내고, 같은 좌표의 정상 XOR checksum GGA/RMC를 순서대로 보냈습니다. NMEA 시각은 정수HHMMSS, RMC magnetic variation은 `0.0,W`입니다. 앱 callback이나 내부 상태는 변조하지 않았습니다.
5. 마지막 입력 후3초간 callback을 더 수집했습니다. 종료 후 공식 내비 UI의 ‘안내 종료’로 취소하고 SAFE_GUIDING 복귀를 확인했습니다.

프로젝트 루트에서 실행한 정확한 입력 명령:

```sh
python3 -B apps/road-context-helper/scripts/gps-synchronized-nmea.py \
  --adb .test-runtime/android-sdk/platform-tools/adb \
  --serial emulator-5556 \
  --output-dir apps/road-context-helper/verification/runs/active-route-01/trace
```

입력 도구는 기존 `gps-corrected-nmea.py`의 encoder를 재사용합니다. 다른 serial을 거부하고, 비어 있는 결과 디렉터리만 허용합니다. 위치 입력과 자동 재송신의 좌표를 일치시키기 위한 실험이며, geo fix가 생성하는 자동 NMEA 문장 자체를 수정하는 도구는 아닙니다.

| 항목 | 실측 결과 |
|---|---|
| 입력 |21점, 첫 입력~마지막 입력60.007초 |
| 콘솔·문장 검증 |63개 명령 모두OK, 저장된42개NMEA checksum 재계산 일치 |
| FusedLocation |63개 callback,21개 고유좌표,21개 입력 모두2m 이내 대응 |
| NaviHelper 위치 |31개 callback, 고유좌표1개. 시작 성남 좌표 유지 |
| 안내 상태 |입력 구간의 모든 mode 관측이 ROUTE_GUIDING |
| TBT |31개 callback 모두10개 항목. 진입·진출 enum이 같은 목록에 존재 |
| routeId |같은 nonempty ID 유지 |

Fused의2m 비교는 에뮬레이터 입력 전달 검증이며 실차 GPS·차로 정확도 주장이 아닙니다. 이전 SAFE_GUIDING 시험 `nmea-03`에서도 Fused21개/Navi1개 고유좌표였습니다. 활성 경로라는 조건을 바꿔도 이번 현상은 해결되지 않았습니다. 올바른 checksum과 콘솔OK는 내비 엔진이 해당 NMEA를 실제 채택했다는 증거가 아닙니다.

**추가로 D + 양의 실제 차량 속도 + 활성 경로 + GPS 입력을 한 차례 결합했습니다.** `combined-drive-01`은 같은 APK에서 정상 ROUTE_GUIDING·TBT10 확인 후21점/60.004초를 입력했습니다. 전체 구간의 Fused 고유좌표21개, Navi1개였습니다. 공식 Console의 양의 속도10표본(2~25km/h) 최초~최종16.242초를 시계보정해 SDK 시각과 맞춘 결과, Fused13callback/6고유좌표, Navi8callback/1고유좌표였습니다. 실제 클러스터11km/h도 촬영했습니다. GPS 속도 필드는18.52km/h·좌표 진행은약20km/h인 반면 차량 속도는 변동했습니다. 약2초 간격 표본 사이 상태나60초 연속 주행·속도 일치를 보장하는 시험은 아닙니다. Navi가 차속을 처리하는 방식은 미확인이고 모든 주행 조건의 위치 갱신 불가로 확대하지 않습니다.

추가 시험은 위 명령의 출력 경로를 `apps/road-context-helper/verification/runs/combined-drive-01/trace`로 바꾸고 `--public-log-only`를 붙여 실행했습니다. 이 옵션은 private 파일을 읽지 않고 검증 앱이 직접 출력한 공개 SDK JSON 로그만 수집합니다.

## 증거 위치

아래 경로는 저장소 기준 상대경로이며, 외부 업로드나 전송은 하지 않았습니다.

- 최종 분석·APK 정보: `apps/road-context-helper/verification/runs/active-route-01/{summary,environment}.json`
- 활성 안내 사전확인: 같은 디렉터리 `probe-route-request.jsonl`, `product-preflight.jsonl`
- 원본 입력·시계: `trace/inputs.jsonl`, `trace/environment.json`
- SDK 원본: `trace/diagnostics-before.jsonl`, `trace/diagnostics-after.jsonl`, `trace/logcat-raw.txt`
- 시험 시각으로 분리한 응답: `location-route-events-window.jsonl`
- 종료·복구 증거: `native-after-cancel.xml`, `product-after-cancel.jsonl`, `restored-product.xml`
- 구현: `integration/src/main/java/dev/pleos/roadcontext/integration/PleosNavigationReader.kt`, 루트 `app/src/main/java/dev/pleos/naviprobe/ProbeActivity.java`
- 추가 결합 시험: `apps/road-context-helper/verification/runs/combined-drive-01/`의 `summary.json`, `console-samples.jsonl`, `trace/logcat-raw.txt`, `sdk-events-positive-window.jsonl`, `positive-device.png`. 실행 방식과 자동 승인 검토로 private 파일 복사를 중단한 정확한 사유는 같은 디렉터리 `README.md`에 기록했습니다.

이 본문에는 개인 계정·인증정보·주소·개인 목적지 좌표를 포함하지 않았습니다. 상세 로그는 로컬 증거로 보관하며 문의에는 필요한 최소 발췌를 사용할 수 있습니다.

## 확인을 요청할 사항

1. Connect2.1.0 에뮬레이터에서 NaviHelper3.0.5의 연속 위치 갱신은 공식 지원 대상인가요? 필요한 내비 버전·이미지·추가 설정이 있나요?
2. NaviHelper가 사용하는 위치 공급자에 좌표를 전달하는 **공식 절차**는 무엇인가요? Android `geo fix`/`geo nmea`와 FusedLocation이 이동해도 내비 위치가 고정될 수 있는 구성인지 확인 부탁드립니다.
3. 정상 목적지 안내 상태에서 위치를 재현하는 공식 모의주행 UI·공개 API·지원 입력 포맷이 있나요? 비공개 API나 내부 상태 변경 없이 시험하려 합니다.
4. `FusedLocation.LocationData.confidence`의 단위·범위·의미는 무엇인가요? v1.1.3 공개 sources의 필드에는 단위 설명이 없었고 관측값은5였습니다. 현재 미터 accuracy로 해석하지 않고 있습니다.
5. 이 비교에 추가로 필요한 SDK/내비 진단 항목이 있다면 수집 방법을 안내 부탁드립니다. 원인이 입력 형식·에뮬레이터 지원 범위·설정·다른 조건 중 무엇인지 현재 확정하지 않았습니다.
6. Navi 위치 처리에 GPS와 차속의 일치나 최소 주행 시간이 필요한가요? P/0 비교 및 짧은 D/양의 차속 결합에서도 좌표는 고정됐지만 정속·속도 일치 조건은 미검증입니다. Console의 Cruise Target Speed는 목표값으로 문서화되어 있는데, 실제 차량 속도를20km/h로 유지하는 공식 시험 방법이 있나요?

confidence 조사 보충: [공식 예제](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app)는 값을 단위 없는 CONFIDENCE로 표시합니다. mock Location의 accuracy5f 설정만으로 서비스의 confidence 매핑을 입증할 수 없습니다. 설치 시스템 APK 읽기는 장치 권한으로 거부되어 추가 우회 없이 중단했습니다. 서비스 버전은 확인했지만 단위 계약·구현 매핑은 미확인입니다.

공식 사용 근거: [목적지 설정·취소](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations), [FusedLocation 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro).

## 별도 관측: UI 자동시험 중 홈 런처 종료

이 절은 위 위치 고정 현상과 원인을 연결하지 않는 별도 환경 관측입니다. 같은 전용 AVD에서 `ui-07` 자동시험을 실행하던 중 공개 로그에 다음 순서가 기록됐습니다. 시각은11월14일 장치 로그 기준입니다.

| 시각 | 원본 관측 |
|---|---|
|04:42:38.252 |`ai.umos.homescreen` PID2578에서 `FATAL EXCEPTION: main`; `IllegalArgumentException: Listener for taskId=1000316 already exists` |
|동일 시각 |stack에 `ShellTaskOrganizer.addListenerForTaskId`, `TaskViewManager.handleMultiWindowTaskAppeared` 기록 |
|04:42:39.648 |제품의 `MainActivityFeedbackTest.actualPlayButtonUpdatesStateWithin250ms` 시작 |
|04:42:40.069 |ActivityManager가 homescreen PID2578 사망 기록 |
|04:42:42.287 |제품 MainActivity의 Activity pause timeout 기록 |
|04:42:43.481 /50.487 |homescreen 새 PID17647 시작 /HomeActivity displayed 기록 |
|04:42:52.694 |TestRunner가 `Cannot run onActivity since Activity has been destroyed already` 보고 |

FATAL의 프로세스는 제품 `dev.pleos.roadcontext`가 아닌 homescreen입니다. 검토한 해당 구간에서는 제품의 FATAL·ANR·프로세스 사망 기록을 찾지 못했고, 제품 PID22960의 TestRunner는 이후 시험을 계속 실행했습니다. 실패 위치는 첫 Activity 접근으로 ViewModel을 얻는 준비 단계(`MainActivityFeedbackTest.kt:23`)이며 버튼 조작·250ms 판정에 도달하지 못했습니다. 이 실행을 버튼 응답 성능 PASS나 임계값 초과로 분류하지 않습니다. 런처 장애와 Activity 소실은 시간상 함께 관측됐지만 내부 인과와 제품의 관여 여부는 이 로그만으로 확정하지 않습니다.

원본: `apps/road-context-helper/verification/runs/ui-07/runtime-public-logcat.txt`의15993~16226행, 같은 폴더 `instrumentation.txt`. 런처 FATAL은 해당 Feedback 시험 시작보다1.396초 앞섰습니다. 앞선 자동시험의 반복 Activity 생성·제거가 촉발했을 가능성은 **미검증 가설**이며, taskId1000316의 실제 소유 Activity도 이 원본으로 확인하지 못했습니다. 제품·홈 런처 구현의 결함 원인을 추측해 수정하지 않았습니다.

지원 확인 요청: 이 이미지의 홈 런처에서 반복 ActivityScenario 생성/종료와 TaskView를 함께 시험할 때 알려진 제약 또는 공식 복구·진단 절차가 있는지 확인 부탁드립니다. 독립 단일 재시험 전에는 새 런처 PID가 일정 관측 구간 유지되고 새 FATAL이 없다는 로그, 제품 Activity가 정상 resume/attach되어 Compose root와 사전 onActivity 접근이 성공한다는 증거가 필요합니다. HomeActivity의 `Displayed` 한 줄이나 시간 경과만으로 완전 복구를 간주하지 않습니다. 실패 원본은 보존하고 재시험은 별도 실행으로 기록해야 합니다. 이 문서 검토 중 장치·프로세스·설정을 변경하거나 외부로 문의를 발송하지 않았습니다.
