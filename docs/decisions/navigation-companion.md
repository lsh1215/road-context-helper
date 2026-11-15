# 기본 내비와 도로 맥락 안내의 함께 표시

확인일: 2026-11-14. NaviHelper 3.0.5 / FusedLocation 1.1.3. 공개 문서, 설치 AAR의 공개 메서드, RoadContext 에뮬레이터(Android 14/API34)를 대조했다.

## 판정

| 방식 | 확인 결과 |
| --- | --- |
| 기본 내비와 우리 앱을 나란히 표시 | 공식 지원 + 에뮬레이터 실측 성공 |
| 나란히 열린 우리 창 안에서 접근 구간에만 구조 안내 | 구현 가능한 구조. 실제 도로·경로 정합성은 기존 미해결 |
| 숨겨진 앱이 합류 때 내비 위에 자동 카드 표시 | 공개 SDK 보장 확인 못함. 백그라운드 수신·표시 권한·알림 정책 별도 검증 필요 |
| 기본 내비 지도 안에 분홍 경로·차량 그림 삽입 | NaviHelper 3.0.5 외부 공개 API에서 확인 못함 |

## 실제 화면 검증

전용 emulator-5556/user10에서 기존 앱(build25)을 실행한 상태로 OS 창 바를 조작했다. 앱 코드나 OS 설정/특권 권한을 바꾸지 않았다. 우리 앱은 Rect(853,90–1694,1300), 기본 내비는 Rect(1718,90–2559,1300)로 각 841×1210 영역에 동시에 visible=true였다. Driving view는 별도 유지됐다.

[화면](../../verification/runs/navigation-companion-01/split-preview-final.png), [원본 창 상태](../../verification/runs/navigation-companion-01/split-preview-activities.txt), [결과](../../verification/runs/navigation-companion-01/summary.json).

캡처의 우리 안내는 가상 구간이며 오른쪽 내비의 목적지/경로와 연결되지 않았다. 내비의 TBT·목적지 UI는 표시됐지만 지도 바탕은 빈 상태여서 지도 정상 렌더나 실제 경로 안내 성공까지 주장하지 않는다. 정차 상태의 함께 표시만 검증했다.

## SDK와 구현 경계

- 멀티 윈도우는 Pleos OS의 기능이다. 사용자가 창 바/제스처로 배치할 수 있으며 앱이 자동으로 내비 옆 자리를 확보하는 공개 메서드는 이번에 확인하지 못했다.
- NAVI_CUSTOM_MAP 권한은 requestNavigationScreen에 대응한다. Foreground, SearchPlace, POIDetailPopup, OverviewRoute 등 내비가 정한 화면 요청이며 우리 Compose 카드/차량 그림 삽입 API가 아니다.
- FusedLocation의 addProximityAlert(request,listener)는 좌표·반경·ENTER/EXIT를 받는다. 화면 알림이 아니라 앱에 전달하는 위치 이벤트다. 가까운 평행도로, 반대 방향, 고가/하부도로를 구별하려면 경로·방향·도로 구조를 추가 검증해야 한다. 이 API의 이벤트 실측은 이번 범위에 없다.
- 현재 앱은 onStop에서 SDK 읽기를 정리한다. 숨겨진 앱에서 계속 감지하는 서비스나 알림/오버레이는 구현되어 있지 않다.
- Android 백그라운드 Activity 실행은 제한된다. 일반 알림·heads-up은 별도 검토 후보이며 AOSP의 지원을 Pleos에서의 표시 보장으로 바꾸어 해석하지 않는다.
- 주행 중 화면은 CarUxRestrictions와 앱 카테고리/지역 정책을 따른다. 정차용 차량 이동 시연이 주행 중 허용된다는 뜻이 아니다.

## 제품 방향 제안

내비를 계속 표시하면서 옆 창의 보조 앱에서 평소에는 간결한 대기 상태, 검증된 구간 접근 시에만 도로 구조와 짧은 설명, 통과 후 대기로 돌아가는 방식이 현재 근거가 가장 확실하다. 이 방식은 창 자체를 자동으로 숨기는 기능이 아니다. 평소 내비 전체 화면을 유지하다가 합류 때만 겹쳐 띄우는 경험이 필수라면, 그 배경 수신·알림 표시의 별도 프로토타입 검증을 먼저 통과해야 한다.

## 공식 근거

- [앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)
- [Window Policy](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window)
- [NaviHelper 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [FusedLocation 기능](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)
- [Android background launch](https://developer.android.com/guide/components/activities/secure-bal)
- [AOSP 알림 설정](https://source.android.com/docs/automotive/hmi/notifications/notifications)
- [설치 AAR의 공개 API와 해시](./navigation-companion-sdk-api.txt)
