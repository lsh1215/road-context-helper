# SDK 읽기 어댑터 결정

확인일: 2026-11-14. 소유 모듈: `integration`. 실제 실행 결과는 별도 verification run에 기록한다. 이 문서는 문서·바이너리 API 확인 및 구현 근거다.

## 의존성과 권한

| 기능 | 공개 artifact | 앱 권한 | 선택 이유 |
|---|---|---|---|
| 내비 위치·TBT·경로 상태 읽기 | `ai.pleos.playground:NaviHelper:3.0.5` | `pleos.car.permission.NAVI_ROUTE` | 기존 Probe가 이 버전에서 실제 콜백을 받음 |
| 차량 위치 구독 | `ai.pleos.playground:FusedLocation:1.1.3` | `pleos.car.permission.FUSED_LOCATION` | 독립적인 위치 업데이트·지원 환경 확인을 제공 |

공식 Maven 저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`다. 경로를 설정하거나 취소하는 API는 호출하지 않으며 NAVI_ROUTE_SEARCH·NAVI_CUSTOM_MAP 권한은 추가하지 않았다.

## 확인한 API

FusedLocation은 `isSupported()`를 검사한 뒤 초기화하고, 동일 `FusedLocationListener`를 등록/해제한 다음 SDK를 release한다. release 후 재시작은 새 인스턴스를 생성한다. `getLastKnownLocation`은 최신 측정을 강제하는 API가 아니므로 연속 이동 판정의 근거로 쓰지 않는다. 이 어댑터는 업데이트 구독을 사용한다.

공식 AAR `FusedLocation-1.1.3.aar`를 받아 `javap`로 타입을 확인했다. LocationData는 latitude/longitude/altitude Double, heading/confidence Float, timestampMillis Long을 제공한다. confidence의 단위·해석, timestamp의 clock domain을 실제 환경에서 검증하기 전에는 미터 정확도·확률·elapsedRealtime으로 해석하지 않는다.

NaviHelper는 연결 성공/실패 콜백이 있는 initialize overload를 사용하고 성공 후 listener와 2초 읽기 polling을 등록한다. 경로 경계 이벤트, routeId 변화, 위치·TBT·경로 상태를 각각 전달한다. 2초는 초기 검증용 선택이며 플랫폼 권장 주기라고 주장하지 않는다.

## 출처·일관성

- 위치 이벤트에는 `REAL_SDK`, 실제 provider, 수신 elapsedRealtime, 원본 측정 시각(있을 때), routeId(있을 때)를 보존한다.
- 기본 선택 위치는 FusedLocation이다. NaviHelper 위치도 비교 진단용으로 보내되 `selectedForPosition=false`로 구별한다. Fused가 실패해도 NaviHelper로 몰래 전환하지 않는다.
- `currentRouteIndex`는 그대로 전달할 뿐 현재 차로로 해석하지 않는다.
- TBT에는 routeId·request correlation token이 없다. 따라서 이벤트의 observedRouteGeneration은 수신 시 관측한 세대일 뿐, 응답이 그 경로에서 생성됐다는 보증이 아니다. `routeAssociationVerified=false`를 유지한다. 소비자는 이 목록만으로 위치와 정확히 결합한 실시간 안내를 확정하면 안 된다.
- 입력을 직접 변경하지 않는다. 위치 inject·경로 request는 별도 시험 도구 책임이다.

## 수명과 진단

start/close는 main thread API다. SDK의 비동기 callback은 main thread로 직렬화하고 epoch가 다른 callback을 버린다. 반복 start는 구독을 추가하지 않는다. close는 polling 취소→listener 해제→release 순서로 수행한다. SubscriptionCount는 앱이 등록했다고 추적하는 listener 수이며 원격 서비스의 구독 해제를 입증하는 수치는 아니다. 실제 생명주기 검증 시 이 값과 SDK/서비스 로그를 함께 확인한다.

읽기 실패·지원 불가·null 위치·잘못된 좌표는 별도 이벤트로 전달한다. 연결 성공이나 구독 요청이 곧 위치 이동 검증 성공은 아니다. 현재 구현에 자동 백오프 재연결은 없으며 화면/수명 관리자에서 close 후 새 start를 요청할 수 있다.

## 차량 UX 정책 읽기

`CarUxRestrictionSource`는 AAOS의 `CarUxRestrictionsManager`에서 현재 정책과 변경 이벤트를 읽는다. 실제 속도·기어 값으로 주행 제한을 추정하지 않는다. `known=false`이면 정차나 제한 해제로 해석하지 않는다. `canConfigure`는 known 상태이며 distraction optimization이 필요 없다는 실제 정책을 받았을 때만 true다.

API 35 `optional/android.car.jar`의 메서드를 javap로 확인했다. `useLibrary 'android.car', false`와 `<uses-library android:name="android.car" android:required="false"/>`를 사용한다. AAOS feature 및 API 29 이상 환경에서만 backend를 시작하며, 비자동차 환경·라이브러리 부재·연결 실패 시 제한 미확인 상태다. 서비스 연결은 대기하지 않는 lifecycle callback 방식으로 처리한다. close는 listener 제거 후 car disconnect를 수행한다. 앱의 distractionOptimized metadata 자체가 주행 중 표시 승인이나 정책 준수를 보증하지 않는다.

- [CarUxRestrictionsManager 공식 API](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)
- [CarUxRestrictions 정책 의미](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions)

## 위치 입력·Virtual Console 조사

2026-11-14 공식 문서 조사 결과이며, 아래 절차를 이 문서를 작성한 작업자가 에뮬레이터에서 실행한 것은 아니다.

- [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator): 에뮬레이터 2.1.0 이상, `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 사용한다. 앱 시험 담당자 한 명만 장치를 조작한다.
- [Virtual Console 전체 카드 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide): Motion에 gear/brake/odometer/steering 등이 나열되어 있으나 GPS 좌표·NMEA·위치 stream 입력 카드는 문서에서 찾지 못했다. 차량 애니메이션·주행 거리 변경을 GPS 이동으로 간주하지 않는다. Console이 위치 문제를 해결한다고 주장하지 않는다.
- [Pleos AVD 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator): VHAL 사용을 위해 cold boot가 요구된다. 현재 시험은 기존 cold boot AVD 설정을 유지한다.
- [Android Emulator Console 공식 가이드](https://developer.android.com/studio/run/emulator-console): `geo fix longitude latitude [altitude] [satellites] [velocity]`와 `geo nmea sentence`가 공식 입력 경로다. 경도가 먼저이며 velocity 단위는 knots다. Android GPS provider에 전달되는 위치 입력과 Pleos의 FusedLocation Service가 실제로 소비하는 입력은 동일하다고 보장되지 않는다.

확인 순서는 ① `FusedLocation.isSupported()` 실제 결과 ② `FUSED_LOCATION` 런타임 권한 ③ 차량 개인정보 위치 차단 상태 ④ 구독 오류/null callback ⑤ Android GPS 입력과 Fused/Navi 각 콜백 비교다.

특히 [FusedLocation 권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)는 `pleos.car.permission.FUSED_LOCATION`을 **dangerous 권한**으로 명시한다. manifest 선언 외에 앱의 런타임 요청과 결과 처리가 필요하다. `showLocationAccessBlockedPopup()`은 차량 개인정보 보호로 차단된 상태를 설명하는 팝업이며 권한을 자동으로 부여하지 않는다. Android coarse/fine/background 위치 권한과 Google 위치 SDK의 동작을 Pleos 보장으로 대신할 수 없다.

현재 공개 FusedLocation API에는 provider 이름을 선택하거나 내부 GPS/NMEA 경로를 설정하는 API가 확인되지 않았다. `isSupported()`는 SDK 지원 환경을 확인하는 API이지 GPS 공급자가 정상 이동함을 증명하는 API가 아니다. `SUBSCRIPTION_REQUESTED` 이후 유효 callback과 실제 변화가 관측되어야 이동 성공이다. 기존 NaviHelper의 checksum 실패는 Android GPS 입력 OK와 분리해서 기록한다.

## 실제 위치 갱신 검증: sdk-01

전용 `emulator-5556`, Pleos Connect 2.1.0 API34에서 APK를 설치하고 실제 권한 팝업의 허용 버튼을 선택했다. `FusedLocation.isSupported()`가 통과했으며 위치 구독과 UX 정책 callback을 실제로 받았다. 초기 권한 전 구독의 SecurityException은 권한 허용 후 재연결로 해소되었다. 권한 팝업의 시스템 문구는 ‘알 수 없는 작업’으로 보여 앱 자체의 사전 설명이 필요하다는 UX 결함도 발견했다.

공식 `geo fix`로 연속 21개 좌표를 60.009초 동안 입력했고 모두 Fused callback에서 관측했다. Fused 92개 callback의 고유 좌표는 초기 기본 위치를 포함해 22개다. 입력과 callback 좌표의 p95 차이는 약 0.185m, 입력→수신 지연 추정 p95는 553.5ms다. 시험 전 시계 기록은 초 단위, 종료 후 기록은 밀리초 왕복 측정이므로 보수적으로 1초 오차 여유를 추가하면 1,553.5ms다. 이는 에뮬레이터 입력 전달 오차와 지연이며 실차 GPS·차로 정확도를 의미하지 않는다.

Fused의 timestampMillis는 이 실행에서 단말 Unix epoch 밀리초와 일치하는 값이며 측정→수신 차이 p95는 47ms였다. confidence 값은 5였지만 단위·의미는 여전히 미확인이다. 이를 미터 오차로 변환하지 않았다.

같은 실행의 NaviHelper는 65개 callback 모두 성남 기본 위치였다. **Fused 위치 입력 경로 검증은 성공했지만 NaviHelper 위치 문제 해결이나 두 공급자의 경로 일관성을 검증한 것은 아니다.** 승인된 실제 구간 자료와 결합하는 AC-21은 미수행이다.

증거: `verification/runs/sdk-01/summary.json`, `diagnostics.jsonl`, `gps-inputs.jsonl`, `matched-inputs.json`, `clock-alignment.json`, `environment.json`, `android-location.txt`, `initial-screen.png`, `final-screen.png`. environment에는 시험 APK SHA256과 foreground user 10, 실제 UI 권한 허용 방식을 기록했다. 테스트는 다른 에뮬레이터 5554에 접근하지 않았다.

## NMEA 원인·다음 실험 조사

2026-11-14 읽기 조사. 아래는 공식 소스와 기존 로그를 연결한 가설이며 아직 새로운 NMEA 형식으로 실행 검증하지 않았다.

현재 AOSP `emu-master-dev`의 [gps.cpp](https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/emu/gps/src/android/gps.cpp)는 일반 위치 입력에서 GGA/RMC 문장을 만들 때 고정 체크섬 `47`을 사용한다. 파일에 체크섬 수정 TODO도 남아 있다. 관측된 Navi 로그의 mismatch와 부합하지만, 현재 설치 바이너리가 이 revision과 정확히 같다는 의미는 아니다.

[공식 console.cpp](https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/android-emu/android/console.cpp)의 `geo nmea`는 입력 문장을 `gpsSendNmea`로 전달한다. 앱 응답을 바꾸지 않고 올바른 문장을 입력해 비교하는 실험 경로가 있다.

기존 `scripts/send_gps.py`의 정상 checksum 문장도 형식 호환 문제가 있을 수 있다. [goldfish GnssHwListener 8e90ac6](https://android.googlesource.com/device/generic/goldfish/+/8e90ac6/gnss/gnss_hw_listener.cpp)는 시각을 정수 HHMMSS로 읽고 RMC의 variation 숫자·방향을 요구한다. 기존 스크립트는 HHMMSS.00과 마지막 빈 variation 필드를 사용한다. 이 parser에서는 parse 실패하면 NMEA listener로 전달하지 않는다. 현재 Pleos 이미지의 parser 구현 동일성은 미확인이나, 소수 없는 시각과 숫자 variation을 쓰는 비교시험이 필요하다.

다음 제안은 새 시험 도구에서만 적용한다. 기존 5554 고정 스크립트를 그대로 실행하지 않는다.

1. 장치 소유권 확보 후 전용 serial을 명시한다. 동일한 위치의 자동 `geo fix` 문장과 정밀 비교한다.
2. RMC body를 `GPRMC,HHMMSS,A,DDMM.mmmm,N,DDDMM.mmmm,E,speed,heading,DDMMYY,0.0,W` 형태로 만들고 XOR checksum을 계산한다. GGA도 HHMMSS 소수 부분을 제거한다.
3. 원본 입력, HAL parse 실패, 내비 checksum 실패, Fused/Navi callback을 함께 수집한다. 자동 재송신의 실패 문장이 남더라도 사용자 주입 문장 수신 여부를 별도로 확인한다.
4. 실제 Navi 좌표가 이동하면 연속 trace로 재검증하고 이전 Fused 성공과 구별한다. 해당 방법으로 차로 정확도·실차 안정성은 주장하지 않는다.

passive update를 끄는 대안은 아직 확인되지 않았다. [gRPC proto](https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/android-grpc/services/emulator-controller/proto/emulator_controller.proto)에 `passiveUpdate` 필드 설명이 있지만, 같은 브랜치 [EmulatorService.cpp](https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/android-grpc/services/emulator-controller/server/src/android/emulation/control/EmulatorService.cpp)의 `setGps`는 이를 적용하지 않는다. proto 필드만 믿고 false로 설정하면 문제가 해결된다고 판단하지 않는다. [PassiveGpsUpdater.cpp](https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/emu/gps/src/android/gps/PassiveGpsUpdater.cpp)는 1초 간격 재송신을 구현한다.

공개 Navi 데이터 sources JAR의 RouteInfo 마지막 Boolean은 `isMobileRequest`이며 simulation flag가 아니다. NavigationScreenRequest에도 mock drive 요청이 없다. 공개 API·문서에서 모의주행 시작 기능을 찾지 못했으며 내장 내비 UI의 지원 여부는 별도로 실제 확인해야 한다. 비공개 InternalApi를 이용해 모의주행을 만든 것으로 처리하지 않는다.

## 정상 체크섬 NMEA 비교시험 결과

후속 실행에서 위 가설을 시험했으나 위치 갱신은 해결되지 않았다. `scripts/gps-corrected-nmea.py`는 전용 `emulator-5556`만 허용하며 정수 HHMMSS·숫자 variation·계산된 XOR checksum을 사용한다. 에뮬레이터 응답이나 SDK callback을 수정하지 않는다.

- `verification/runs/nmea-01`: 21개 입력, 약 60초. 콘솔은 모두 OK였다. 최종 원본 분석에서 Fused는 고유 좌표 7개를 보여 일부 NMEA 입력 좌표를 받았지만 반복해서 이전 geo fix 좌표로 돌아왔다. 이는 passive 재송신과 경합하는 정황이다. Navi는 36개 응답 모두 성남 좌표였다. 마지막 구간에 별도 Probe로 전환했기 때문에 완전한 단일 foreground 통제 실험으로 해석하지 않는다.
- `verification/runs/nmea-02`: 기존 진단용 Probe를 배경에서 수집시키고 실제 내비 화면을 런처로 표시한 상태를 포함해 11개 입력, 약 30초 비교. Navi 좌표는 계속 성남 기본값이었다. 내비 전면 확인 screenshot과 원본 응답을 보존했다.

입력 문장 checksum은 파일에서 다시 계산해 전부 일치했다. console OK만으로 내비 수신 성공을 판단할 수 없으며, Fused의 일부 좌표 수신은 입력 경로 전체가 항상 막힌 것은 아니라는 증거다. Navi에서 왜 위치가 반영되지 않는지와 passive 재송신의 영향은 확정하지 못했다. 이 대안은 성공으로 보고하지 않는다. 검증 후 진단 Probe는 force-stop했고 다른 장치 5554에는 접근하지 않았다.

## confidence 정의의 확인 범위

공식 Maven의 `FusedLocation-1.1.3-sources.jar` 및 `fused-location-data-0.0.3-sources.jar`를 확인했다. 공개 `LocationData.kt`는 `confidence: Float`만 선언하며 KDoc·단위 설명이 없다. Mapper는 전송 DTO의 confidence를 그대로 복사한다. 따라서 관측값 5가 Android accuracy와 같아 보인다는 이유만으로 미터 정확도로 해석할 근거는 아직 없다.

추가로 [한국어 LocationData API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/entry)의 공개 HTML도 확인했으나 Dokka 본문 컨테이너가 비어 있어 단위 정의를 얻지 못했다. 한국어 소개는 v1.1.3, 영어 소개는 v1.1.2로 버전 표기가 달랐다. 현재 구현 버전의 공개 소스에 없는 의미를 다른 위치 SDK의 accuracy 규약으로 보완하지 않는다.

추가 bounded 읽기 조사에서 공개 SDK의 Constants는 `ai.pleos.playground.service.location.fused/.FusedLocationService`를 가리켰다. 전용5556에 `pm path` 및 `dumpsys package`만 조회한 결과 설치 서비스는 versionName1.0.0/versionCode6, minSdk31/targetSdk34, 경로 `/system_ext/priv-app/FusedLocationService/FusedLocationService.apk`였다. 이는 설치 구현의 식별 메타데이터이지 confidence의 API 계약이 아니다. 시스템 APK 실행 코드를 임시로 읽기 복사하려 했으나 장치의 Permission denied로 실패했다. 권한 우회나 앱 개인 데이터·인증정보 조회는 하지 않았고 서비스 상태도 바꾸지 않았다. 따라서 설치 서비스의 Android Location.accuracy→confidence 매핑은 확인하지 못했다.

[공식 FusedLocation 예제 MainScreen](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app/blob/main/app/src/main/java/ai/pleos/playground/location/fused/sample/ui/screen/MainScreen.kt)은 단위 없는 CONFIDENCE 레이블로 값을 표시하고 UI preview에는0.9를 사용한다. [FusedLocationClient](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app/blob/main/app/src/main/java/ai/pleos/playground/location/fused/sample/location/FusedLocationClient.kt)는 mock Android Location의 accuracy를5f로 설정하지만 서비스가 이를 confidence로 그대로 매핑한다는 코드는 아니다. 예제의 테스트 값과 UI preview도 단위 규약이 아니므로 confidence5=5m로 해석할 근거는 여전히 없다. 단위·범위·변환 규칙을 공식 지원에 문의하는 상태를 유지한다.

## 실제 UX 제한 전환 시험 경로 조사

2026-11-14 추가 읽기 조사이며 아래 입력은 아직 실행하지 않았다. 장치 소유자는 전용 `emulator-5556`만 조작하고, 입력 전후 원본 `CarUxRestrictionsManager` callback을 검증한다. 앱의 자체 데모 정책이나 GPS 속도를 실제 차량 UX 정책으로 대체하지 않는다.

1. **Android Emulator 공식 GUI 경로**: [Android Automotive 에뮬레이터 테스트 문서](https://developer.android.com/training/cars/testing/emulator)의 Extended controls → Car data → Car sensor data에서 Gear를 D, Car speed를 0보다 크게 설정한다. VHAL Properties 탭을 사용할 경우 `GEAR_SELECTION`은 `0x11400400`(D=8, P=4), `PERF_VEHICLE_SPEED`는 `0x11600207`(m/s)이다. 이 문서는 비-P 기어와 양의 실제 속도로 주행 상태를 재현하도록 안내한다. 정차 복원은 속도 0, 기어 P로 한다. Pleos 이미지에서 이 GUI가 해당 속성을 지원하는지는 아직 확인하지 않았다.
2. **Pleos 공식 Virtual Console 경로**: 담당자가 `adb -s emulator-5556 forward tcp:8088 tcp:8088`을 실행하고 `http://127.0.0.1:8088/`에 접속한다. Emulation Controls → Motion → Gear Selection의 P/D 변경은 공식 지원된다. 설치된 Console의 실제 속도 조작 기능도 화면에서 확인한다. 공개 전체 카드 목록에는 실제 속도 카드가 없고 ADAS의 Cruise Target Speed만 있다. 목표 속도는 실제 속도와 다르므로 이 카드만 바꾼 결과를 주행 전환 성공으로 기록하지 않는다. 현재 Pleos 문서만으로는 moving 상태까지 재현하는 완전한 절차를 확정할 수 없다.
3. **공개 AOSP VHAL 호스트 도구 후보**: [공식 도구 README](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-gsi/tools/emulator/)와 [vhal_emulator.py](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-gsi/tools/emulator/vhal_emulator.py)는 VHAL simulator의 TCP 33452에 ADB forwarding 후 `VehicleHalProto` GET/SET 메시지를 보내는 별도 경로를 제공한다. `Vhal(types, device='emulator-5556')`는 장치를 지정할 수 있으며, `setProperty(propertyId, areaId, value)`로 값을 보낸다. 다만 이 도구는 기본 VHAL simulator 서버가 필요하고 README도 유지보수 한계를 밝힌다. Pleos에서 해당 서버를 제공하는지 미확인이므로, 공식 GUI가 작동하지 않을 때 읽기 연결·GET_CONFIG 응답부터 확인해야 한다. 현재 로컬 emulator 배포물에서는 VehicleHalProto 및 해당 Python 도구가 발견되지 않았고 `lib/emulator_controller.proto`에도 VHAL 변경 RPC가 없다.

`cmd car_service help`의 non-user build 오류와 관련해 [Android14 CarShellCommand](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-gsi/service/src/com/android/car/CarShellCommand.java)를 확인했다. user build에서는 허용 목록 밖 명령을 차단하며 `inject-vhal-event`도 그 허용 목록에 없다. 따라서 같은 계열 명령을 바꾸어 반복 실행하면 해결된다고 기대하지 않는다. 이미지 변경·권한 우회는 이번 검증의 해결 방법으로 채택하지 않는다.

성공 판정은 `known=true`인 실제 UX callback에서 `requiresDistractionOptimization` 또는 제한 플래그가 변경되고, 동시에 앱의 설정/진단 진입이 정책에 맞게 제한되는 것이다. 원상 복구 후 허용 정책으로 돌아오는 것도 확인한다. 기어 UI 변경, 차량 애니메이션, 콘솔 SET 응답만으로는 성공이 아니다.

### VHAL TCP 읽기 비교시험: vhal-01

장치 담당자의 읽기 전용 시험 승인 후 `emulator-5556`의 TCP33452에 임시 ADB forwarding을 만들었다. 공식 Android14 `VehicleHalProto_pb2.py`의 descriptor로 `GET_CONFIG_ALL_CMD` 메시지(`08 02`)를 구성하고 공식 도구와 같은 길이 접두사 형식으로 보냈다. 응답 헤더 이전에 EOF가 발생해 GET_CONFIG 지원을 확인하지 못했다. 이후 예정했던 gear/speed GET_PROPERTY 및 모든 SET 메시지는 보내지 않았다. 임시 forwarding은 제거했다.

읽기 전용 `ss -ltn`에는 8088과 5555 수신 포트만 보였다. netlink 권한 경고도 있어 완전한 소켓 목록이라고 단정하지는 않는다. 이 결과에서 TCP33452 사용 가능성을 전제로 후속 변경 명령을 실행할 근거는 없다. 증거는 `verification/runs/vhal-01/`에 원본 summary, 스키마, probe, 수신 포트 출력으로 남겼다. 8088의 Pleos Virtual Console가 다음 공식 UI 확인 후보이며 이 시험은 UX 제한 전환 성공에 해당하지 않는다.

### Virtual Console 실제 UI 관측: console-01

전용 5556의 remote8088만 host 임시 포트55003으로 forwarding하고 ego lite로 화면을 읽었다. 공개 카드 목록에는 없었지만 메인 주행 시각화에 **P/R/N/D 버튼, ACCEL과 BRAKE 페달 버튼, 실제 속도 표시**가 있었다. 화면 안내는 `Select D + hold ACCEL`이다. 초기 로딩의 DDS OFFLINE은 이후 DDS ONLINE으로 변경되었다. 이 관측에서 상태는 P/0km/h였고 어떤 차량 제어도 실행하지 않았다. 따라서 'Pleos Console에는 실제 속도 조작이 없다'는 결론은 틀리며, 실제 주행 상태를 바꿀 공개 UI 후보를 확인한 것으로 갱신한다. 원본 snapshot과 screenshot은 `verification/runs/console-01/`에 저장했다. 실제 UX callback 전환 성공은 후속 실행으로 별도 판정한다.

후속으로 기기 소유권을 받은 뒤 같은 Console의 공식 UI만 조작하여 **실제 UX 정책 전환을 확인했다.** D 선택 직후 실제 앱의 `RoadContextUx` 로그에서 `known=true`, `requiresDistractionOptimization=true`, `activeRestrictions=80`이 수신되었고 앱의 시연·화면 테마 버튼이 숨겨졌다. D/0km/h 상태부터 제한이 적용되었다.

첫 semantic hover+hold에서는 속도 변화가 관측되지 않았다. 이를 성공으로 처리하지 않고 화면에서 확인한 페달 좌표로 mouse move/down을 실행했다. DOM의 실제 `pressed` 상태를 확인한 채 약 3.5초 유지하자 Console에 60km/h, 잠시 뒤 에뮬레이터 클러스터에 61km/h가 표시되었다. 앱은 차단되지 않고 계속 보였으며 설정 버튼은 숨겨진 상태였다. 이 이미지에서 정지 D와 주행 중 정책은 동일하여 가속 시 별도 정책 callback은 추가되지 않았다. 실제 PERF_VEHICLE_SPEED API 값을 별도로 수집한 것은 아니므로 속도 증거는 Console와 클러스터 화면으로 한정한다.

BRAKE를 5초 유지하여 0km/h가 된 것을 확인하고 P를 선택했다. 실제 callback이 `requiresDistractionOptimization=false`, `activeRestrictions=0`으로 돌아왔으며 앱의 시연·테마 버튼도 다시 표시되었다. P/0 복원 후 브라우저 작업공간을 종료하고 임시 forwarding 및 로그 수집을 정리했으며 기기를 담당자에게 반납했다. `summary.json`, `actions.jsonl`, 원본 `ux-live.log` 및 이번 앱 PID만 추출한 `ux-test-process.log`, `app-d.png`, `app-accel-held.png`, `app-restored.png`가 증거다. 실제 도로 자료가 없는 기본 화면에서의 한 차례 정책 전환 시험이며 모든 차량의 UX 정책이나 실제 경로 안내 성공을 의미하지 않는다.

## 다음 Navi 위치 가설 시험 준비: 동기화 입력

`scripts/gps-synchronized-nmea.py`는 기존 정상 NMEA encoder를 재사용한다. 아래는 실행 전에 정한 가설·계약이며, 후속 실제 실행 결과는 이 절 끝의 nmea-03에 기록했다. 기존 스크립트는 import 시 장치 접근이 발생하지 않도록 실행부에 main 가드를 추가했으며 기존 CLI 인자를 유지했다.

앞선 두 실패와 다른 가설은 다음과 같다. 서울로 한 번에 이동시키는 큰 위치 차이를 내비가 거부했을 수 있고, 정상 NMEA 사이의 passive 재송신이 이전 geo fix 위치로 되돌렸을 수 있다. 둘 다 확정된 원인이 아니다. 다음 실험은 내비가 계속 반환한 성남 기본 위치 `37.4127212,127.094681`에서 시작하고 위도를 매3초 `0.00015`씩 늘리는 21점 trace를 사용한다. 각 점에서 먼저 공식 `geo fix`로 같은 좌표를 설정하고 정상 checksum의 GGA/RMC를 순서대로 보낸다. NMEA 좌표는 포맷 정밀도 때문에 소수점 이하에서 양자화되므로 원본 위경도와 NMEA 원문을 함께 보존한다. geo fix가 내보내는 자동 NMEA의 고정 checksum 문제 자체가 해결되는 것은 아니다.

실행 계약:

- 기기 담당자가 5556 소유권을 확보한 뒤 실행한다. 다른 장치 serial은 코드에서 차단한다.
- 설치 APK의 hash/build, foreground user10, 실제입력 모드, 위치 권한 허용을 담당자가 기록한다. 앱은 전체 입력 동안 전경에 유지한다. 스크립트는 APK 설치·권한 부여·앱 모드 변경·callback 조작을 하지 않는다.
- 기존 시험 결과를 덮어쓰지 않도록 비어 있는 output 디렉터리만 허용한다. default 입력 간격은 21점에 걸쳐60초이며 마지막 입력 후3초 callback drain 및 전후 시계 측정이 추가된다.
- `inputs.jsonl`에 각 점과 명령 순서, 원본 NMEA, 실제 ADB stdout/stderr, 전후 host epoch/monotonic 시각을 저장한다. `environment.json`에 단말 clock3회 전후 측정과 왕복 오차를 기록한다. 단말 clock을 읽지 못하면 null로 남긴다.
- `diagnostics-before/after.jsonl`은 앱 파일 원본이며, 읽기 실패는 별도 status JSON에 보존한다. `logcat-raw.txt`는 로그를 지우지 않고 실행 중 NMEA/SDK context를 모은다. 기기 로그를 정정하거나 가짜 SDK 응답을 추가하지 않는다.
- 검증은 NaviHelper 공급자만 별도로 골라 **callback 개수·고유 좌표·입력과의 거리·측정/수신 시각**을 비교한다. 최소10개 고유 Navi 좌표가 입력과 대응하는지 확인하고, Fused 성공을 Navi 성공으로 합치지 않는다. 콘솔 OK나 한두 번의 좌표 변화만으로 연속 갱신을 성공 처리하지 않는다.
- 정상 입력 후에도 Navi가 고정 좌표를 반환하면 원인 미해결로 결론내린다. 성공하더라도 실제 경로/TBT·차로 정확도는 별도 검증이며 이번 위치 실험의 성공에 포함하지 않는다.

프로젝트 루트에서 장치 담당자가 실행한 명령:

```sh
python3 -B apps/road-context-helper/scripts/gps-synchronized-nmea.py \
  --adb .test-runtime/android-sdk/platform-tools/adb \
  --serial emulator-5556 \
  --output-dir apps/road-context-helper/verification/runs/nmea-03
```

준비 검증에서는 `--dry-run --adb /does-not-exist`로 장치 접근 없이21점·예정60초·42개 checksum·정수 HHMMSS·geo fix 선행순서를 검사했다. 5554 입력 거부도 확인했다. dry-run 결과는 실제 SDK 시험 증거가 아니다.

### 실행 결과: nmea-03

build-09 설치 APK(`33e0dbcfb024196165ebfc6fd3728b19a1c06bc3929d6ade0b4e4bd9beb87244`)로 전용5556에서 실행했다. 21점을60.005초에 걸쳐 입력했고63개 콘솔 명령은 모두 OK였다. 저장한42개 NMEA checksum도 별도로 재계산하여 모두 일치했다. 전후 단말시계 측정의 device-host offset 중앙값은 -364.5ms였으며 이 값을 이용해 시험 구간 응답을 분리했다.

- FusedLocation:63개 callback,21개 고유 좌표.21개 입력점 모두2m 이내 대응했다. 동기화된 이전 geo fix 좌표가 서울에 남아 되돌아오는 현상은 이번 trace에서 없었다.
- NaviHelper:32개 callback,1개 고유 좌표. 계속 기본 성남 위치였으며 첫 입력점만2m 이내 대응했다. **Navi 연속 위치 갱신 실패이며 원인은 미해결이다.**
- 경로 상태: `mode=SAFE_GUIDING`, `routing=false`, 빈 TBT 목록. 실제 목적지 경로를 안내하는 세션에서 실행한 것이 아니다. 앱 전경은60초 동안 유지했고 화면은 실제입력 기본 대기 상태였다.

비교 범위를 제한한다. sdk-01 원본도 SAFE_GUIDING/routing=false·빈TBT였다. nmea-02는 실제 내비 화면을 전면에 표시한 구간이 있었지만 Probe의 원본 route_state는 SAFE_GUIDING/isRouting=false이고 routeId는 빈 값이었다. **내비 화면을 켰다는 사실과 활성 목적지 경로 안내는 다르다.** 따라서 이 결과만으로 활성 경로 중 NaviHelper 위치 갱신까지 불가능하다고 단정하지 않는다. 기본 모드의 고정 위치 문제는 세 가지 입력 방법으로 해결되지 않았으며 다음 판단에는 외부 SDK·에뮬레이터 지원 정보가 필요하다. 추가 추측 우회 실험은 여기서 종료했다.

`verification/runs/nmea-03/`에 입력·전후 SDK 원본·전체 logcat·시계·공급자별 분석·경로 세션 한계를 보존했다. 최종 screenshot에서 P/0km/h를 확인했고 장치 소유권을 반납했다. 이 실험은 실제 경로/TBT 데이터와 구간 매칭 검증의 완료를 의미하지 않는다.

## 활성 목적지 경로 시험 준비와 기존 Probe 재검토

프로젝트 루트의 `app/src/main/java/dev/pleos/naviprobe/ProbeActivity.java`는 string Intent extra `lat`/`lon`이 있으면 시작8초 후 공개 `NaviHelper.requestRoute(RouteInfo(...))`를 호출한다. 목적지는 위도37.60194/경도127.04167, 옵션은 RECOMMENDED로 기존 시험과 동일하게 재현할 수 있다. 이는 별도 진단 앱이며 제품 앱에는 경로 쓰기 권한이나 기능을 추가하지 않는다. [공식 목적지 문서](https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/destinations)는 requestRoute로 목적지 설정, cancelRoute로 취소하는 API와 결과 callback 확인을 안내한다.

중요한 해석 수정: `isRouting=false`만으로 활성 경로가 없다고 판정하면 안 된다. 루트 `test-results/callbacks-before-restart.jsonl`에는 `naviMode=ROUTE_GUIDING, isRouting=false`가 함께 기록되어 있고 nonempty routeId 및10개TBT가 존재한다. 최근 sdk-01/nmea-01~03은 SAFE_GUIDING 조건이었지만 **프로젝트의 모든 과거 시험이 SAFE_GUIDING이었던 것은 아니다.** 활성 안내 여부는 mode와 routeId/TBT 결과를 함께 확인한다. requestRoute 호출 또는 route_started 단독도 완료 증거가 아니다.

기존 루트 로그에서 ROUTE_GUIDING 최초시각은1794588770339이며 이후 위치33개는 고유좌표1개였다. 구형 `test-results/gps-inputs.jsonl`의8개 입력은2026-11-13T16:53:44.144Z~16:53:51.583Z로 그 활성 세션 끝부분과 중첩한다. 다만 구형 입력은 HHMMSS.00·빈variation을 사용했고 입력 기간도 짧아, 이번 정상형식·동기화60초 조건의 결과로 대체할 수 없다. 이 과거 기록도 '활성 경로면 반드시 위치가 움직인다'는 가정을 뒷받침하지 않는다.

새 비교조건은 정상 공식 목적지 요청 후 **ROUTE_GUIDING을 실제 확인한 시점 이후** 기존 동기화 trace를 동일하게 적용하는 것이다. 좌표 우회법을 늘리는 실험이 아니라 경로 안내 상태라는 변수 하나를 비교한다. 아래 실행 계약을 준비한 뒤 실제 수행한 결과는 active-route-01 절에 기록했다.

```sh
.test-runtime/android-sdk/platform-tools/adb -s emulator-5556 shell am force-stop --user 10 dev.pleos.naviprobe
.test-runtime/android-sdk/platform-tools/adb -s emulator-5556 shell am start --user 10 \
  -n dev.pleos.naviprobe/.ProbeActivity --es lat 37.60194 --es lon 127.04167
```

8초 요청 뒤 실제 Probe 원본에서 ROUTE_GUIDING·TBT·routeId를 확인하고 제품 MainActivity를 전경으로 돌린다. 제품 자체 로그에서도 활성mode를 확인한 뒤 다음 trace를 실행한다. preflight 로그와 설치 APK hash는 active-route-01에 따로 보존하고, 입력 도구는 비어 있는 하위trace 디렉터리에 기록한다.

```sh
python3 -B apps/road-context-helper/scripts/gps-synchronized-nmea.py \
  --adb .test-runtime/android-sdk/platform-tools/adb --serial emulator-5556 \
  --output-dir apps/road-context-helper/verification/runs/active-route-01/trace
```

실행 전 계약은 목적지가 실제로 재탐색되는지 확인하고, 목적지 안내를 시작하지 못하면 trace를 실행하지 않으며 활성조건 확보 실패로 기록하는 것이었다.

### 실행 결과: active-route-01

build-10 설치본(`4cea7504943938ee290e39da54fcc88e14b058ef6701992dae6329799caabeba`)과 기존 루트 Probe를 사용했다. Probe의 공식 requestRoute 결과로 `ROUTE_GUIDING`, 새 routeId `e97efc1e-df8e-11f1-9a9c-72e3ec72fa29`, 진입·진출을 포함한10개TBT를 받았다. 이후 제품 앱에서도 동일한 mode·routeId·TBT가 전달된 것을 확인한 다음60초 입력을 시작했다. 이때도 isRouting=false였으므로 활성 안내 판단에 그 Boolean을 사용하지 않았다.

21개 입력의 첫 점부터 마지막 점까지60.007초였고, 실제 입력 구간에서 다음 결과를 얻었다.

| 공급자/데이터 | 관측 결과 |
|---|---|
| FusedLocation |63개 callback,21개 고유좌표 |
| NaviHelper 위치 |31개 callback,1개 고유좌표. 기본 성남 위치 유지 |
| Navi 경로 mode |구간 내 모든 관측이 ROUTE_GUIDING |
| routeId |동일한 nonempty ID 유지 |
| TBT |31개 callback 모두10개 항목 |
| 차량 기어·차속 |P / 0km/h. GPS 좌표 입력만 이동 |

**활성 목적지 안내 조건에서도 Navi 위치 갱신은 해결되지 않았다.** TBT를 실제로 읽는 기능과 위치의 연속 갱신 성공을 분리해야 한다. 이 결과는 특정 에뮬레이터·목적지에서의 bounded 비교시험이며 모든 실차 동작이나 SDK 자체의 불가능을 입증하지 않는다. 제품의 TBT `routeAssociationVerified=false`도 유지한다. 목록 callback에 원래 요청의 route token이 없으므로, 이번 정상 세션이 재탐색 경쟁 상태까지 검증한 것은 아니다.

시험 후 공식 내비 화면에서 관측된 ‘안내 종료’를 선택했고 ‘어디로 갈까요?’ 화면과 제품의 SAFE_GUIDING 복귀를 확인했다. Probe를 force-stop한 뒤 제품 앱을 재시작해 실제 전경의 기본 대기 화면으로 복구했다. P/0km/h 상태를 유지하고 기기를 반납했다. `verification/runs/active-route-01/`에는 사전 활성조건 원본, 입력trace, 제품 원본, 취소 전후 UI XML, 최종 제품 전경 XML, summary를 저장했다. 이 조건까지 확인한 뒤 추가 위치 실험은 종료했으며 외부 SDK·에뮬레이터 지원 정보가 필요한 상태로 남긴다.

## 추가 조건의 분리와 AC-26 결합 시험 계획

P/0 조건의 active-route-01 이후, **D + 양의 실제 차량 속도 + ROUTE_GUIDING + GPS 이동을 결합하는 별도 시험을 한 차례 수행**했다. Navi가 차량 기어·속도를 위치 처리에 사용하는 원리는 여전히 미확인이다. 아래 표의 각 관측 범위를 구분한다.

| 실행 | 경로 mode | 기어·실제 차속 | GPS 입력 | 확인 범위 |
|---|---|---|---|---|
| console-01 |활성 경로 검증 없음 |D, 클러스터61km/h 관측 |연속 입력 없음 |실제 UX 정책·설정 제한/복귀 |
| nmea-03 |SAFE_GUIDING |P/0 |21점60초 |기본mode 위치 비교 |
| active-route-01 |ROUTE_GUIDING |P/0 |21점60초 |활성mode 위치 비교 |
| combined-drive-01 |ROUTE_GUIDING |D/2~25km/h, 양의 속도 표본 구간16.242초 |동일21점60초 |결합 시간창에서도 Navi 고정. 정속·60초 연속 주행은 미검증 |

### Cruise 문서 확인

[공식 Virtual Console 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)는 Cruise Control State의 Not ready/Ready/Longitudinal/Lateral/Full autonomous와 Cruise Target Speed의5.556~27.778m/s 범위를 명시한다.5.556m/s는 약20km/h지만 문서의 의미는 **목표 속도**이다. 실제 PERF_VEHICLE_SPEED를 그 값으로 유지하는 제어 루프나 활성화 순서를 보장하는 문구는 확인하지 못했다. 따라서 Cruise 상태·목표값을 추측하여 설정하거나20km/h 정속 시험이라고 명명하지 않는다. 이미 실제 확인한 D·ACCEL·BRAKE 공식 UI만 다음 시험의 입력 후보로 사용한다.

### 실행 전 고정한 절차

soak 종료 후 담당자가5556 소유권을 넘겨준 다음 수행하도록 계획했다. 제품 코드와 SDK 응답을 바꾸지 않고 아래 비교를 한 번의60초 trace 안에서 수행하며, 결과 디렉터리는 기존 시험과 분리한다.

1. 설치 APK hash와 버전을 기록한다. 기존 별도 Probe로 정상 목적지 요청 후 ROUTE_GUIDING·nonempty routeId·TBT를 확인한다. 제품 앱 전경과 실제입력 모드, P/0 기준 상태를 확인한다.
2. ego lite에서 공식 Console에 접속하고 D를 선택한다. 실제 gear·정책 callback을 기록한다. 동시에 기존 `gps-synchronized-nmea.py`의21점60초 입력을 시작한다.
3. 입력 시작 약10초 후 관측한 ACCEL 페달을 짧게 누르고 해제한다. 양의 차속이 **실제로 표시되는 동안** 시각·Console 속도·클러스터 속도·제품 화면·실제 SDK callback을 함께 기록한다. 시작/종료를 임의의 페달 시간으로 대신하지 않고 관측된 양의 속도 구간을 기록한다. 최소2초 간격으로 Console의 실제 속도 표시를 읽되, 샘플 사이 상태는 보장하지 않는다.
4. 가속을 계속 유지하거나 Cruise 조작으로 정속을 추정하지 않는다. 약30초 시점에 BRAKE로0 복귀를 확인한다. 나머지 입력을 끝내고 정책·위치 응답을 수집한다. 이 범위에서 양의 속도가 짧게만 관측되면 **brief positive-speed window 시험**으로 기록하며60초 연속 주행으로 표현하지 않는다.
5. BRAKE 해제·0/P 복구, 공식 내비 안내 종료·SAFE_GUIDING, Probe 종료, 제품 전경 복귀를 확인하고 기기를 반납한다. 가속/브레이크 버튼은 finally로 해제하며 실패 시에도 정차 복구를 우선한다.

### 판정과 남는 한계

입력 구간 전체의 공급자별 unique 좌표 수와 별도로, **D·양의 차속·ROUTE_GUIDING이 함께 관측된 시간창**의 Navi 좌표 변화를 집계한다. callback·TBT 개수만 증가하거나 UI 차속만 증가한 경우 위치 갱신 성공이 아니다. 그 시간창에서 고유 위치가 실제 입력 방향으로 변하면 이동 관측으로 기록하되, 지속 시간·표본 수가 부족하면 연속 위치 갱신 기준을 충족했다고 판정하지 않는다. 창 내부에서 고정이더라도 모든 속도·차량 조건에서 불가라는 결론은 내리지 않는다. 복합조건 미확보나 로그 부족도 FAIL과 별도로 INCONCLUSIVE로 남긴다.

기존 GPS trace의 위도 간격은 약16.7m/3초로 약20km/h이고 NMEA/geo fix 속도 필드는10knots(18.52km/h)이다. Console 차속은 페달 입력에 따라 변하므로 GPS의 일정한 이동·속도와 일치하지 않는다. 이 시험은 결합 입력에 대한 제한된 반응 확인이며 센서 간 속도 일관성을 갖춘 주행 재현이 아니다. 지원 문의에도 이 차이를 기록한다. AC-26의 실행·관측 범위는 증거로 채우되, 정속·연속 주행처럼 수행하지 않은 조건을 성공으로 닫지 않는다.

### 실행 결과: combined-drive-01

같은 build-10에서 공식 Probe 요청으로 `ROUTE_GUIDING`, routeId `1a2cb0c4-df94-11f1-9df4-72e3ec72fa29`, TBT10을 확인했다. 정상 동기화 입력은21점/60.004초였으며63개 명령OK,42개 checksum 일치를 확인했다. 공식 Console D·ACCEL·BRAKE만 조작했고 Cruise 설정은 바꾸지 않았다.

| 분석 구간 | NaviHelper | FusedLocation | 경로 상태 |
|---|---|---|---|
| 전체60.004초 입력 |32 callback /1고유좌표 |53 callback /21고유좌표 |ROUTE_GUIDING, TBT10 |
| D·양의 속도 표본의 첫~끝16.242초 |8 callback /1고유좌표 |13 callback /6고유좌표 |ROUTE_GUIDING, TBT10 |

양의 차속은10개 UI 표본에서2~25km/h였고 제품과 함께 촬영한 실제 클러스터는11km/h였다. 호스트·장치 시계 차이 중앙값 -422.25ms(최대 왕복72ms)를 보정해 UI 표본 시각과 SDK 원본 시각을 정렬했다. 약2초 간격 표본 사이의 연속 상태는 보장하지 않는다. GPS 이동은 약20km/h·속도 필드는18.52km/h인 반면 차량 차속은 변동했다. **이 짧은 결합 시간창에서도 Navi 위치는 기본 성남 좌표에 고정됐다.** 정속·60초 연속 양의 속도·센서 간 속도 일치 조건은 검증하지 않았으며 실차 전체의 불가능으로 확대하지 않는다.

최초 private 진단 파일 복사는 자동 승인 검토가 거부했다. 거부된 읽기를 반복하지 않고 `gps-synchronized-nmea.py --public-log-only`로 검증 앱의 공개 `Log.i` SDK 원본과 Console 표본만 수집했다. 해당 옵션은 private snapshot을 실행하지 않는다. 정확한 거부 문구·실행 명령·분석은 `verification/runs/combined-drive-01/README.md`, 원본은 `trace/logcat-raw.txt`, `trace/inputs.jsonl`, `console-samples.jsonl`에 보존했다.

종료 후0/P·페달 해제, 공식 UI 안내 종료, Probe 종료, 제품 기본 화면 전경 복구를 확인했다. 브라우저 space147과 임시 포워드를 종료하고 기기를 반납했다. 결합 조건의 제한된 실측까지 완료했으나 Navi 위치 갱신 문제는 해결되지 않았고 공식 지원 확인이 필요하다.

## 성능 환경 읽기 조사: 2026-11-14

pipeline-03의 프레임 지연을 전달받아 환경만 확인했다. 장치 조작·설정 수정·재시작·프로세스 종료·SDK cache 변경은 하지 않았다. 아래 호스트 표본은 pipeline-03과 동시 측정이 아니므로 원인 증거로 소급하지 않는다.

| 항목 | 읽기 확인 |
|---|---|
| RoadContext AVD |ARM64, vCPU4, RAM4096MB, 2560×1440/160dpi/60Hz |
| 실행 옵션 |`-no-snapshot -no-audio -gpu host -port 5556`, GUI 실행 |
| 실제 그래픽 초기화 |gfxstream, Apple M2 Pro, host GLES·Vulkan/MoltenVK, HWUI skiagl |
| VM 가속 |`emulator -accel-check`: exit0, Hypervisor.Framework OS X26.6 |
| 호스트 집계04:26:39 KST |10코어/32GiB, load9.04/7.94/7.75, CPU user60.42%+system34.32%/idle5.24%, 메모리31G사용·압축12G |

근거 파일은 `.test-runtime/avd/RoadContext.avd/{config.ini,hardware-qemu.ini,emu-launch-params.txt}`, `verification/runs/bootstrap/emulator-window.log`, `scripts/start-emulator.sh`이다. 호스트 메모리 사용량만으로 메모리 부족·지연 원인을 확정할 수 없고, 단일 CPU 표본도 시험 구간 전체를 대표하지 않는다.

[Pleos 설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)의 API34·4GB·Cold boot/Hardware 조건을 현재 설정이 충족한다. [설정 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)는 Quick boot에서 VHAL을 이용할 수 없다고 명시하므로 Cold boot를 유지해야 한다. GPU 표에는 NVIDIA/AMD 사양만 있어 Apple M2 Pro의 동급 여부를 이 문서로 판정할 수 없다.

[Android 가속 가이드](https://developer.android.com/studio/run/emulator-acceleration)상 host GPU는 보통 높은 성능을 제공하며 auto가 일반 권장값이다. 현재 하드웨어 경로가 작동하므로 software 전환을 성능 개선으로 권할 근거가 없다. CPU/RAM 상향도 현재 호스트 부하에서 개선을 보장하지 않는다. 우선 동일 host/4CPU/4GB/해상도로 빌드·다른 무거운 작업과 겹치지 않는 측정 구간을 만들고, 그 시간의 전체 CPU 집계를 함께 남기는 비교가 적절하다. 다른 사용자 에뮬레이터는 그대로 둔다. 필요하면 담당자 승인 후 별도 조건으로 `-gpu auto`를 비교할 수 있지만, 실제 선택 backend가 동일하면 독립 변수로서 의미가 작다. 이번 조사에서는 어떤 옵션도 바꾸지 않았다.

[FrameMetrics 정의](https://developer.android.com/reference/android/view/FrameMetrics)에서 UNKNOWN_DELAY는 UI 스레드가 프레임 처리를 시작할 수 있을 때까지의 지연이다. 높은 값만으로 호스트 스케줄링과 앱 메인 스레드 작업을 구분할 수 없다. TOTAL은 렌더링과 display subsystem 제출까지이며 실제 화면 표시 완료 시각과 같다고 단정하지 않는다. 따라서 환경 조사만으로 기존 지연을 무효화하거나 통과로 바꾸지 않고, 제품 observer·프레임 계측 결과와 함께 판단한다.

## SDK 공식 참고 링크

- [FusedLocation 의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)
- [FusedLocation 위치 접근](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)
- [FusedLocation 생명주기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release)
- [FusedLocation 소개·권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [NaviHelper 초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [NaviHelper 최소 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
