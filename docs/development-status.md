# 초행길 개발·검증 현황

기준일: 2026-11-14 / 상태: 구현·자동 검증 결과 정리, 전체 완료 아님

## 최신 UI 증분 변경: build-23

사용자가 도로 구조의 가독성과 차량 모형을 통한 이해를 요청해 RoadScreen을 개선했다. 큰 도로 면과 진입 구간 색 구분, 핵심 제목, 단계 표시, 정차 조작 위계를 적용했다. 승인된 가상 구간에만 차량 모형을 넣어 정차 재생 중9초간 한 번 도로 연결을 보여준다. 일시정지·재개·주행 제한에 따른 정지와 실제/기록 입력에서 차량 표시 금지를 검사했다. 엔진·SDK·ViewModel 로직은 바꾸지 않았다.

- build-21 UI: [ui-redesign-01](../verification/runs/ui-redesign-01/summary.json) 38개 PASS,73.566초. 실제 도식 비중64.03%.
- build-23 차량 예시: [ui-vehicle-01](../verification/runs/ui-vehicle-01/instrumentation.txt) 38개 PASS,69.536초. 화면15·반응형18·차량4·실제 조작1.
- [build-23](../verification/runs/build-23/summary.json) 빌드·lint PASS. 현재 APK는 이 후보이며 아래 build-16 전체 기준 감사는 역사적 범위로 보존한다.
- 새 차량은 가상 도로 연결 설명이다. 실제 위치·차로·안전한 합류 간격을 판단하지 않는다. 기존 사람 평가용 이미지는 새 디자인의 이해도 평가 증거로 승격하지 않는다. 실제 자료·SDK·물리 크기·사람 평가의 외부 조건은 그대로 남아 있다.

## build-16 기준 감사 기록

현재 제품은 **build-16**, 최신 권한 검사 도구는 **build-19**다. ui-08 대상 24개, pipeline-04의 1,000입력, 실제 권한 흐름 permission-flow-02가 통과했다. 원래 계획의 입력→UI 상태 timestamp 기준에 따라 **AC-24는 PASS**다. 기준 전체는 **26 PASS·3 BLOCKED·1 NOT_RUN**이며, AC-23 물리 표시 크기와 실제 자료·SDK 정합성·사람 평가가 남아 **L1/L2/L3 전체 완료는 아니다**. 이전 ui-07의 4개 실패와 permission-flow-01 실패를 보존한다.

이전 build-10에서는 native 49개·합성 30분 내구·1,000입력 p95 검사를 통과했다. 실제 SDK FusedLocation 갱신과 에뮬레이터의 주행 제한·활성 내비 동시 실행도 해당 기록 APK에서 확인했다. 이 역사적 결과를 최신 후보에 자동 승계하지 않는다. 실제 도로 자료·경로와 위치 정합성·물리 크기·사람 이해도 조건이 남아 있다.

이 보고서는 테스트가 실행된 APK·소스·데이터 범위에 대한 기록이다. 현재 소스가 변경됐다고 예전 PASS가 자동으로 승계되지는 않는다. 기계 판독 기준과 증거 해시는 [acceptance.json](../verification/acceptance.json)을 따른다.

## 최신 후보와 ui-07 실패 범위

[ui-07 원본](../verification/runs/ui-07/instrumentation.txt)의 실패 4건은 다음과 같다. 재생 버튼 검사는 입력 계측 전에 Activity가 이미 종료돼 실패했다. 같은 시간의 homescreen FATAL·재시작은 관측했지만 제품의 FATAL·ANR나 모든 실패의 단일 원인으로 확정하지 않았다. 실제 제어 commit 검사는 정차 CarUx 정책을 15초 안에 받지 못해 시작 조건에서 실패했고, 입력별 측정값 자체가 없다. 나머지 두 캡처는 `appUncertainA/B`에서 `study_surface` semantics가 5초 안에 나타나지 않았다. **host bounds·bitmap 크기 검사가 실패한 것이 아니라 해당 검사까지 도달하지 못했다.**

기기 파일 스냅샷에 남아 있는 이전 feedback·pipeline·endurance·uncertain A/B 캡처는 신규 결과로 세지 않는다. 새로 성공한 14개 캡처 PNG는 평가 자산과 SHA-256을 독립 비교해 14개 모두 같았다. 자산 manifest는 build-10 출처를 유지하며 실제 참가자는 여전히 0명이다. [실행 요약](../verification/runs/ui-07/summary.json), [14개 비교](../verification/runs/ui-07/capture-comparison.json).

[build-16](../verification/runs/build-16/summary.json)은 build-15 clean 이후 증분 빌드다. 가용 JVM 보고서 130건 가운데 app debug/release 각 7회만 재실행했고 core/data는 UP-TO-DATE였다. 이를 130건의 새 실행으로 표현하지 않는다. [build-17](../verification/runs/build-17/summary.json)은 테스트 변경이며 제품 해시는 `053418e0cbd11c11886ed7f727ffb07c831ec02e455a4b21bd15bc33caf0a294`, 테스트 해시는 `b4a02cbb0c8d6a4246f8764caa03b7bee00844abc4c44bc23480f2e1e1938607`다.

[platform-ready-01](../verification/runs/platform-ready-01/summary.json)은 실제 제품 화면, Home PID 10초 안정, 실제 CarUx known=true·제한 false/0을 확인했다. 초기 날짜 필터 오류로 얻은 빈 로그는 무효로 남기고 정정한 공개 로그를 사용했다. 준비 상태 관측은 성능 또는 이전 실패 해결의 증거가 아니다. ui-08은 결과 디렉터리를 따로 격리한 24개 대상 검사이며 100.738초, 실패 0건으로 끝났다.

## ui-08 독립 검증

[ui-08 원본](../verification/runs/ui-08/instrumentation.txt)은 24개 성공·100.738초다. 사전 계획의 권한 회귀 6개, pending 화면 fixture 2개, ViewModel 12개, feedback 1개, 실제 제어 commit 1개, 수명 2개와 일치한다. [원본 파일 12개](../verification/runs/ui-08/summary.json)의 SHA-256을 독립 확인했다.

실제 MainActivity의 10종 동작을 5회 반복한 50입력 중 변경 45건은 실제 상태·관찰 scope·중복되지 않은 record ID·콜백 이후 시각을 대조했다. callback→필요한 composition 완료의 최대값은 재계산 **90.577125ms**이며 기록 누락은 0건이다. 바뀌지 않는 5입력은 새 화면 피드백이 있었다고 세지 않는다. 별도 재생/정지 20입력의 semantics 문구 관측 최대값은 **233.661626ms**였다. 두 방식의 종료 지점은 다르며 물리 픽셀 표시 시간을 측정한 것이 아니다. [제어 원본](../verification/runs/ui-08/device-files/verification-device/actual-control-composition.json), [문구 원본](../verification/runs/ui-08/device-files/verification-device/actual-button-feedback.json).

실제 SDK 연결·해제 20회는 각 구독 2→0, 종료 후 위치 콜백 0건이었다. Activity 재생성 10회는 정지한 합성 예고 상태를 보존했다. 권한 gate·SavedState 복구 회귀와 pending 화면은 검증했으나 실제 OS 권한 팝업·결과 지연, 장시간 대기 표시 지연, 주행 제한 전환 지연은 이번 실행에 포함되지 않았다. 이 실행만 있던 당시에는 AC-24 전체를 NOT_RUN으로 유지했다. 후속 실제 권한 검사를 합친 최종 판정은 아래와 같다. ui-07의 4개 실패 원본은 보존하며 이번 대상 검사를 69개 전체 재실행으로 세지 않는다. 후속 pipeline-04 결과는 아래처럼 합성 next 입력 범위로 별도 판정했다.

## pipeline-04 실제 composition 연결 검사

build-16 제품과 build-17 테스트 APK의 보관 파일 해시를 독립 확인했다. 사전 정의한 1회 실행은 **1,000입력·125사이클**, 142.145초로 끝났다. 실제 코드가 만든 outer/nested composition 기록을 고정 8단계 기대값의 제목·본문·단계·거리·도식·출처와 대조했다. 변경 입력 **750건 모두 250ms 이내**, 최대 **194.22575ms**, p95 **51.156333ms**였다. 대응 기록 1,250개는 중복 사용이 없고 누락 scope·기록 drop은 0건이다. 바뀌지 않는 250입력은 새 commit을 주장하지 않는다. [독립 요약](../verification/runs/pipeline-04/summary.json), [원본](../verification/runs/pipeline-04/device-files/verification-device/synthetic-replay-pipeline-performance.json).

기존 의미 트리 관측 기준도 전체 p95 **149.265ms**, 변경 입력 p95 **162.687708ms**로 통과했다. 그러나 전체 트리 수집 종료까지는 최대 **363.776083ms**, 250ms 초과 **4건**이 남는다. renderer composition 완료와 테스트가 전체 semantics 복사를 마친 시점은 다르다. 창 frame 940개 중 total 250ms 초과가 3개 있으나 특정 기대 상태의 픽셀 표시 시각으로 연결하지 않는다. 동일 상태를 기다려 유리한 관측을 고르는 재시도는 없고 원래 실패 기록도 보존한다.

1,000개 기록의 단계·위치·콜백 수락·의미 검사 오류 0건과 시간 계산을 대조했고, 변경 750건의 실제 composition 상태는 원본으로 다시 검사했다. 전체 semantics 트리 원본은 보관하지 않으므로 모든 text/bounds assertion을 사후에 새로 실행한 것처럼 표현하지 않는다. 해당 부분은 실행 중 독립 oracle과 성공한 계측 종료에 근거한다. 이 결과는 **합성 next 경로**이며 실제 SDK 경로·지리 투영·실제 도로·물리 픽셀 표시와 전체 AC-24를 증명하지 않는다. 이전 계측/제품 버전 대비 속도 개선률로 보고하지 않는다.

## 실제 권한 흐름의 실패와 복원

[permission-flow-01](../verification/runs/permission-flow-01/summary.json)은 제품 build-16·테스트 build-18로 사전에 정한 **1회 실행**, 22.125초, **FAIL**이다. 실제 권한을 거절한 상태에서 앱 버튼 콜백은 한 번 호출돼 `pending=true`가 됐고 실제 OS 권한창도 나타났다. 그러나 앱의 pending 상태를 담은 composition 기록은 0개라 `ACTUAL_PENDING_COMPOSITION_MISSING`으로 실패했다. 누락을 0ms나 PASS로 바꾸지 않았다. [계측 원본](../verification/runs/permission-flow-01/device-files/verification-device/real-permission-flow.json), [실제 OS 화면](../verification/runs/permission-flow-01/os-dialog.png).

OS의 허용 조작 후 실제 권한과 앱 초기설정 상태는 복원됐다. native oracle은 실제 입력 출처·권한 버튼 제거·사용 가능한 시연/테마 버튼을 확인했다. composition 2개는 모두 허용 후 `setup=false/pending=false` 상태였고 기록 drop은 0개다. Android 사용자 10의 권한은 최종 `granted=true, USER_SET`으로 실행 전 상태와 같다. 조작 직후 [package-restored](../verification/runs/permission-flow-01/package-restored.txt)는 아직 false였으며, 이를 삭제하지 않고 이후 [package-final](../verification/runs/permission-flow-01/package-final.txt)의 true와 구분한다.

OS 대화상자가 앱 위에 표시된 관측은 앱 pending 렌더가 생략될 수 있다는 해석과 맞지만, 원인·정확한 표시 순서를 증명하지 않는다. 이 실패만으로 제품 고장을 확정하거나 OS 권한 요청에 인위적인 지연을 넣을 근거는 없다. 실제 권한창의 표시 마감이나 물리 픽셀 지연도 측정하지 않았다. 이 실행 당시 AC-24는 미완료였고 이 실제 pending 부분 검사는 명시적으로 FAIL이다. 실제 권한 부여·앱 복원이 성공한 사실로 실패한 지연 기준을 덮지 않는다.

후속 코드 리뷰에서 **시험 도구의 동기화 문제**가 확인됐다. 권한 대기 중 Handler/SystemClock만 사용해 Compose 테스트 시계를 진행하지 않았고, 허용 뒤 UI assertion 시점에야 동기화했을 가능성이 있다. 따라서 누락을 OS나 제품 원인으로 단정하지 않는다. 제품 지연을 추가하지 않고 테스트 동기화만 수정한 build-19로 사전 정의 1회 재검증을 마쳤다. 기존 permission-flow-01 FAIL과 원본은 유지한다.

## 최종 AC-24 판정과 실제 권한 재검증

[permission-flow-02](../verification/runs/permission-flow-02/summary.json)는 22.508초, 1개 검사 PASS다. 실제 권한 거절 상태에서 제공된 버튼을 한 번 호출했고, 첫 pending outer composition의 시각을 독립 재계산한 결과 **120.800584ms**였다. pending 기록 2개, 허용 후 기록 4개, drop·오류 0건이다. 실제 OS 대화상자는 XML에서 확인됐고, 허용 뒤 앱 복원 oracle과 사용자 10의 `granted=true, USER_SET` 복원도 확인했다. 제품 APK는 그대로이며 테스트의 Compose 동기화만 정상화했다. permission-flow-01의 실패를 삭제하거나 수치 개선률로 비교하지 않는다.

실제 OS 문구는 ‘초행길에서 알 수 없는 작업 실행하도록 허용하시겠습니까?’였다. 이는 플랫폼의 custom permission 표시 한계로 기록한다. 실제 요청한 권한은 `pleos.car.permission.FUSED_LOCATION`이며 다른 권한이나 시스템 문구를 바꾸지 않았다. [실제 XML](../verification/runs/permission-flow-02/os-dialog.xml), [최종 권한](../verification/runs/permission-flow-02/package-final.txt).

원래 [실행 계획 AC-24](road-context-helper-execution-plan.md)는 입력 이벤트와 **UI 상태 갱신 timestamp**로 250ms 피드백을 측정하고, 긴 처리에는 대기 상태와 중복 방지를 요구한다. ui-08의 제공 버튼 45개 변경, pipeline-04의 next 750개 변경, 실제 권한 대기 1회가 모두 이 기준을 충족했다. ui-08의 중복 gate·SavedState 회귀 6개와 pending 버튼 비활성화 fixture 2개가 중복 방지를 뒷받침한다. 따라서 지원 에뮬레이터의 제공 입력 범위에서 **AC-24 PASS**로 판정한다. 물리 픽셀 마감·주행 정책 전환·숨겨진 버튼을 새 필수 조건으로 추가하지 않는다. 물리 글자 크기는 원래 AC-23의 별도 미검증 조건이다.

## 완료된 실행과 증거

| 실행 | 확인 결과 | 증명하지 않는 것 |
|---|---|---|
| build-10 | 앱·계측 APK 빌드 성공, 소스 manifest와 실제 코드/config 일치 확인 | 에뮬레이터 실행·실차 동작 |
| domain-04 | 고정 소스의 JVM 123개 테스트, 실패·오류 0건 | 실제 도로 데이터의 정확성·차량 UI·실제 주행 |
| domain-04 성능·결정론 | 123개에 포함된 2개 테스트 성공 | SDK 전송·지도 투영·Android UI·실차 성능 |
| sdk-01 | FusedLocation으로 주입한 21개 위치를 순서대로 관측 | NaviHelper 위치 문제 해결·현재 차로 인식 |
| ui-06 | build-10 후보의 native 테스트 49개 성공, 178.592초 | 실제 도로·모든 물리 디스플레이·차량 외부 센서 정확도 |
| soak-01 | build-10 실제시간 1,809,043ms, 75사이클·600판정 성공 | 실제 도로 안내·일반적인 메모리 누수 부재 |
| pipeline-02 | 동일 제품 APK, v2 계측 1,000개 p95 172.263875ms·변경750개 p95 183.715084ms | 모든 입력의 최대 지연≤250ms·실제 SDK 경로·제품 속도 개선률 |
| pipeline-study-01 | 17개 중 이미지 준비 16개 성공, 연결 성능 1개 실패 | 이미지가 준비됐다고 실제 사용자 평가를 수행한 것은 아님 |
| combined-drive-01 | build-10에서 D·양의 차속·활성 내비·제품 전경 유지 관측 | 60초 연속 주행·중앙 지도 동시 표시·실차 동작 |
| console-01 | build-07 후보에서 실제 CarUx 주행 제한 수신, 앱 표시 유지·설정 숨김·정차 복원 | 최신 APK의 재시험·실차·활성 내비 경로와 동시 사용 |

domain-04의 123개는 엔진 32, 복구 회귀 14, 독립 fixture runner 1, 지리 투영 15, 성능·결정론 2, 실시간 조정 로직 21, 도로 카탈로그 31, 진단 writer 7개다. fixture runner 한 테스트 안에서 독립 시나리오 18개를 확인하므로 이를 JVM 테스트 18개로 다시 더하지 않는다. 과거 domain-03 107개와 performance-01 2개를 최신 123개에 중복 합산하지 않는다.

ui-06은 미지원 SDK·권한 거절·연결 오류·통과 상태의 작은 창 검사를 포함해 49개 native 테스트가 모두 통과했다. 이 실행의 build-10 APK SHA-256은 `4cea7504943938ee290e39da54fcc88e14b058ef6701992dae6329799caabeba`다. ui-04의 34개, ui-05의 42개는 이전 후보의 실행 기록이며 최신 49개에 합산하지 않는다.

근거: [build-10 소스](../verification/runs/build-10/source.json), [ui-06 결과와 APK 해시](../verification/runs/ui-06/summary.json), [domain-04 결과](../verification/runs/domain-04/summary.json), [SDK 결과](../verification/runs/sdk-01/summary.json), [Console 실제 주행 정책 결과](../verification/runs/console-01/summary.json).

## SDK 위치 검증의 정확한 범위

AC-19는 sdk-01에 기록된 APK의 **Pleos FusedLocation 에뮬레이터 위치 갱신**에 한정해 PASS다. 입력은 공식 Android emulator `geo fix` 경로이며 앱이 받은 SDK 콜백을 바꾸지 않았다.

- 서로 다른 입력 21개, 실제 입력 기간 60.0094초.
- FusedLocation 92개 콜백에서 22개 서로 다른 좌표 관측. 초기 위치를 제외하고 주입한 21개가 순서·진행 방향과 일치했다.
- 원본 재계산 좌표 오차 p95 약 0.185325m.
- 호스트·에뮬레이터 시각을 정렬한 수신 지연 p95 553.5ms. 초기 초 단위 시각 측정의 불확실성 1초를 추가한 보수값은 1,553.5ms다.
- 같은 실행에서 NaviHelper 좌표는 1개로 고정됐다. 이후 올바른 체크섬의 NMEA 입력 실험도 NaviHelper 위치 갱신으로 이어지지 않았다.

이 수치는 인위적으로 주입한 에뮬레이터 좌표의 전달 결과다. 실차 GPS 오차나 차로 판별 정확도가 아니다. FusedLocation의 confidence 필드를 미터 단위 정확도로 해석할 근거도 확보되지 않았다. 따라서 실제 지도 매칭에 필요한 오차 범위를 임의로 생성하지 않는다. [원본 입력](../verification/runs/sdk-01/gps-inputs.jsonl), [SDK 원본](../verification/runs/sdk-01/diagnostics.jsonl), [시계 정렬](../verification/runs/sdk-01/clock-alignment.json), [NMEA 재시험](../verification/runs/nmea-02/summary.json).

## 구현·검증·수정 루프에서 바뀐 부분

| 발견된 문제 | 수정 방향 | 재검증 상태 |
|---|---|---|
| 동일 측정을 다시 수신하면 순서 역전으로 판단 | 센서 내용과 수신 시각을 분리하고 측정 freshness 유지 | 엔진 회귀 테스트 통과 |
| title 등 텍스트에 `hasVisualOverflow` 발생 | 실제 문단·노드 크기 로그를 확보하고 텍스트 폭 측정 수정 | ui-01 실패를 보존하고 ui-03 15개 재실행 성공 |
| 진단 파일 I/O 오류가 메인 콜백을 중단시킬 수 있음 | 128건 bounded queue, 단일 worker, 2MB 회전, 실패 격리 | writer JVM 7개 테스트 성공 |
| 지원 도로만 비교하면 주변 미지원 도로를 놓칠 수 있음 | 실제 자료에 경쟁 도로와 검수 범위 계약 추가 | 카탈로그·투영·조정 로직 테스트 성공; 실제 자료 자체는 없음 |
| 실제 지도 도식과 합성 도식 출처가 섞일 수 있음 | source·승인 여부·segment ID 확인, 미승인 실제 도식 차단 | ui-06의 UI·ViewModel 회귀 통과 |
| Activity 중단 시 실제 안내 카드가 남을 수 있음 | 실제 모드는 카드·예약 상태 제거, 합성 모드는 정지된 시연 상태 보존 | 실제 Activity 재생성 10회 통과 |

수정 후 테스트 통과는 해당 결함의 재현 범위에 대한 증거다. 모든 도로에서 오류가 없다는 결론으로 확대하지 않는다.

## UI 검증 상태

ui-03은 native Compose로 실제 에뮬레이터 host 1682×1210dp와 1200×900dp 설계 fixture를 검사했다. 기본·확대 글자와 상태별 출처·도식·버튼·콜백을 확인했다. 실제 창과 강제 설계 크기는 로그에서 별도로 표시했다. 물리 크기(mm)는 에뮬레이터 dp 검사로 인증하지 않았다.

ui-01의 8개 실패는 숨기지 않고 보존한다. ui-03에서 해당 실패가 재현되지 않았으므로 AC-22/26을 과거 실패 상태로 그대로 두지 않고, **해결된 부분과 남은 검사**로 나눴다. 당시에는 전체 기준이 미완료였다. 특히 `settingsAllowed=false`를 주입한 화면 테스트는 실제 차량이 주행 중일 때 운영체제가 앱을 표시하도록 허용한다는 증거가 아니다.

ui-06에서 49개 성공을 확인했다. 실제 SDK 연결·해제 20회 모두 활성 client 구독 2개→0개, 오류 없음, 종료 후 2.1초 동안 위치 콜백 없음이었다. 실제 Activity 재생성 10회에서 정지된 합성 시연 상태를 보존했다. 이 검증은 프로세스 사망 후 복구나 SDK 서버 내부 listener 수를 증명하지 않는다.

AC-22는 선언한 에뮬레이터 화면 범위에서 PASS다. 실제 host 1682×1210dp와 가상 600×900·1200×600·1682×1210dp, 밝은/어두운 테마, 글자 배율 1.0/1.3을 검사했다. 600×900dp·1.3배의 미지원·권한·오류·통과 화면 캡처 4개를 독립적으로 다시 열어 핵심 글자가 잘리거나 겹치지 않음을 확인했다. 이 작은 화면은 큰 실제 창 내부의 가상 레이아웃이며 별도 물리 화면으로 세지 않는다.

AC-24의 재생/정지 부분 검사는 실제 OnClick 진입부터 재구성된 semantics 문구 관측까지 20회 모두 250ms 이내다. 이 실행 당시에는 전체 입력 피드백 기준이 NOT_RUN이었으며 이 20회만으로 승격하지 않았다. 최대값은 100.961208ms이며 테스트 동기화·assertion 비용을 포함한다. 콜백 이전 입력 전달 대기와 물리 픽셀 표시 시간을 측정한 결과는 아니다. 준비 50회 후 상태 제출→Compose commit 1,000회의 p95는 6.073708ms였다. 이 값은 SDK·엔진·투영 시간을 포함하지 않으므로 엔진 수치와 단순 합산하지 않는다. [계측 원본](../verification/runs/ui-06/captures/files/verification-device/)

console-01은 공식 Pleos Virtual Console의 D·가속 조작이 실제 에뮬레이터 CarUx 상태에 반영되는 경로로 검사했다. 계기판 61km/h에서 앱 표시가 유지되고 `requiresDistractionOptimization=true`, 제한 flags=80, 설정 버튼 숨김을 확인했다. 0/P로 복원하자 제한 false/0과 버튼이 돌아왔다. 앱에 제한 값을 직접 주입한 결과가 아니다. 이 실행의 APK는 **build-07**이며 최신 build-10으로 바꿔 적지 않는다. 실제 차량이나 활성 내비 경로와의 동시 사용은 별도 검증이다. [Console 원본과 한계](../verification/runs/console-01/summary.json)

## 추가 결합 시험과 성능 실패

combined-drive-01에서는 build-10 앱이 전경인 상태로 D·양의 차속·내장 내비 안내의 결합을 확인했다. 양의 차속 표본 10개는 모두 D, 2~25km/h였으며 최초~최종 간격은 16.242초였다. 이 시간창의 원본에서 ROUTE_GUIDING 2회, 10개 항목의 TBT 8회, 실제 CarUx 제한 true/80을 확인했다. 11km/h 화면에는 클러스터의 내비 회전 안내와 제품 화면이 함께 보이고 제품 설정 버튼은 숨겨져 있었다. 이 관측 형태를 AC-26 PASS로 판정했다. 중앙 화면에 지도와 앱을 동시에 표시하거나 음성을 지속하는지, 표본 사이 모든 순간이 주행 상태인지까지 확인한 것은 아니다. [결합 시험](../verification/runs/combined-drive-01/README.md), [실제 화면](../verification/runs/combined-drive-01/positive-device.png).

같은 결합 시간창에서도 Navi 좌표는 8개 콜백·고유 좌표 1개였고 Fused는 13개 콜백·6개 고유 좌표였다. 활성 경로와 주행 조건을 추가해도 이번 Navi 위치 고정 현상은 해결되지 않았다. 원인이나 실차의 지원 여부는 확정하지 않았다. 공식 지원 문의용 [재현 보고서](./sdk-support-repro.md)는 로컬 초안이며 발송·게시하지 않았다.

pipeline-study-01의 원본 1,000개 시간을 재계산한 전체 p95는 **258.107708ms**, 화면 변경이 있는 750개의 p95는 **267.495667ms**였다. 250ms를 넘긴 표본은 62개이며 목표 실패를 보존한다. 예상 상태별 건수는 모두 맞아 기능 결과와 성능 실패를 구분한다. 측정에는 테스트 동기화·검사 비용이 포함되므로 제품 렌더링 자체가 느리다는 원인을 아직 확정하지 않는다. 250ms 목표를 바꾸지 않고 원인을 분리한 뒤 재검증한다. [실패 계측](../verification/runs/pipeline-study-01/instrumentation.txt), [1,000개 원본](../verification/runs/pipeline-study-01/synthetic-replay-pipeline-performance.json).

다음 계측안은 여러 검사 뒤에 시계를 멈추던 방식에서, 전체 unmerged semantics 트리를 한 번 읽은 직후 시계를 멈추고 독립 기대값 검사를 뒤에 수행하는 방식으로 원인을 분리한다. 입력 1,000개·변경 상태 750개·250ms 기준은 유지한다. 종료 지점이 달라지는 만큼 이전 수치 대비 제품 속도가 개선됐다고 직접 비교하지 않으며, 기존 실패 기록을 유지한다. pipeline-02 재실행은 1개 테스트 성공, 152.421초로 종료됐다. 원본 1,000개 index·사이클·단계와 750개 변경 상태를 독립 재검산했고, 기대값 오류 0건·거부된 콜백 0건이었다. 전체 p95는 172.263875ms, 변경 상태 p95는 183.715084ms여서 AC-29의 L1 합성 제품 경로 목표를 충족한다. 제품 APK는 동일하고 테스트 APK만 build-13이므로 이를 제품 자체의 속도 개선으로 보고하지 않는다. [v2 원본](../verification/runs/pipeline-02/synthetic-replay-pipeline-performance.json).

다만 최대 관측 지연은 2357.489043ms이고 250ms 초과는 16건이다. 1,000건의 모델 상태 반영은 최대 78.235667ms였으며 긴 관측 지연의 원인이 렌더링인지 동기화·수집 지연인지 확정되지 않았다. AC-24의 기존 재생/정지 20회 최대 100.961208ms 결과는 그 표본 범위에서 유지한다. 다른 버튼의 이번 관측까지 포함해 모든 입력이 항상 250ms 이내에 보인다고 확대하지 않는다. p95 통과와 최대 지연 보장은 다른 조건이다.

자극 화면 16개는 UX 에이전트의 직접 시각 검토를 마쳤고, manifest의 SHA-256·이미지 크기를 독립 대조해 모두 일치했다. 자산은 READY지만 실제 참가자는 0명, 응답은 0/40이다. 에이전트의 자산 검수를 사람의 이해도 평가로 세지 않는다.

노출 도구의 이미지 검증·로딩·전체 화면 표시·잘못된 노출 무효화·빈 응답 폼 전환은 성공했다. 그러나 2초 노출은 최초 2173.7ms, 측정 수정 후 2138.6ms로 허용범위 1900~2100ms를 두 번 모두 벗어났다. 타이밍 preflight는 **FAIL_ENVIRONMENT_CONSTRAINT**다. visible 페이지에서 RAF 간격 약16~33ms와 약1000ms가 섞여 관측됐지만 브라우저·OS·자동화 중 원인은 확정하지 않았다. 실제 사람 평가 전 동일 컴퓨터·브라우저·창 조건에서 타이밍을 바로잡고 모든 시도를 보존한 재검증이 필요하다. [기술 검사 원본과 요약](../verification/human-study/technical-smoke-summary.json), [평가 프로토콜](../verification/human-study/protocol.md).

## 30분 내구 시험 독립 검토

soak-01의 최종 JSON과 원본 진단을 독립 대조했다. 실제 경과 1,809,043ms 동안 75사이클·600판정 모두 `대기→대기→예고→예고→접근→접근→접근→통과` 순서와 일치했다. 각 사이클의 누적 예고 횟수는 `0,0,1,1,1,1,1,1`로 중복이 없었으며 마지막 도식은 제거됐다. 각 단계의 위치·화면 상태·도식 표시를 모두 비교했고, 진단과 화면 상태 기록의 시각 차이는 0~84ms였다. 단계 간격은 최소 3,001ms, 최대 3,329ms였다. [최종 기록](../verification/runs/soak-01/endurance-final.json), [원본 판정](../verification/runs/soak-01/diagnostics.jsonl), [계측 종료](../verification/runs/soak-01/instrumentation.txt).

실행 중 앱 crash/ANR를 관측하지 않았다. crash buffer의 Fatal signal 18건은 시험 시작 03:25:09보다 앞선 03:00:54~03:01:04의 플랫폼 프로세스 기록이다. 이를 숨기지 않고 실행 구간·프로세스로 구분했다. runtime buffer의 처음 65초는 남아 있지 않아, 전체 실행의 연속성은 600개 판정·정상 종료와 함께 판단했다. 유효한 메모리 표본 29개 모두 동일 PID였고 PSS는 75,016~84,726KiB, 마지막 유효값은 80,650KiB였다. 계측 종료 후 마지막 수집에서 프로세스가 없는 것은 메모리 표본으로 세지 않았다. 이 결과로 모든 상황에서 메모리 누수가 없다고 주장하지 않는다.

AC-28은 이 30분 시험과 ui-06의 실제 SDK 연결/해제 20회·Activity 재생성 10회를 합쳐 명시한 에뮬레이터 범위에서 PASS다. 실제 도로를 이용한 복구는 AC-21의 별도 미완료 범위다.

## G0~G7 인계표

build-15의 clean build·JVM·lint는 완료됐으나 ui-07은 4건 실패했다. 후속 build-16 제품/build-17 테스트 후보의 ui-08 대상 24개와 pipeline-04 연결 검사는 성공했다. 아래 기록된 PASS는 명시한 이전 실행의 증거이고 새 후보의 모든 검사가 끝났다는 뜻이 아니다. 전체 수준의 승격은 표의 미완료 게이트가 해소된 뒤 판정한다.

| 게이트 | 현재 판정 | 완료된 근거 | 남은 조건 |
|---|---|---|---|
| G0 범위·계약 | PASS | [원래 실행 계획](road-context-helper-execution-plan.md), [기준·출처·증거 계약](../verification/acceptance.json), [시나리오 인덱스](../verification/scenarios/index.json) | 기준 축소 없이 새 관측 결과를 같은 계약에 반영 |
| G1 초기세팅 | PASS — 빌드·기동 범위 | [build-15 clean](../verification/runs/build-15/build.txt), [build-16 증분 빌드](../verification/runs/build-16/summary.json), [새 제품의 실제 기동·정차 정책](../verification/runs/platform-ready-01/summary.json) | 기동 성공은 전체 native 성공이나 성능 통과가 아님 |
| G2 도로·로직 | PASS — 기록된 합성 계약 범위 | [domain-04 JVM 123개](../verification/runs/domain-04/summary.json), [독립 fixture 검사](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.AcceptanceFixtureTest.xml) | build-15 JVM 회귀130회·고유123개 성공. 실제 자료는 G4의 별도 조건 |
| G3 UX·화면 | NOT_RUN — 전체 미완료 | [ui-06 49개](../verification/runs/ui-06/summary.json), [실제 주행·활성 내비](../verification/runs/combined-drive-01/summary.json) | AC-23 대상 표시 장치의 물리 크기 미확정. AC-24는 ui-08·pipeline-04·permission-flow-02로 PASS |
| G4 실시간 통합 | BLOCKED | [Fused 위치 갱신](../verification/runs/sdk-01/summary.json), [Navi 고정 재현](../verification/runs/combined-drive-01/summary.json) | 승인 실제 구간, 경로·위치 정합성·미터 오차 근거, 실제 접근→예고→통과 3회 |
| G5 신뢰성 | PASS — 기록된 시험 범위 | build-10의 [30분 내구](../verification/runs/soak-01/summary.json), ui-06의 SDK 20회·Activity 10회, [p95 진단](../verification/runs/pipeline-03/summary.json) | ui-08 최신 수명·구독, pipeline-04 연결, 권한 회귀 완료. 30분 soak는 build-10 기록이며 새 30분 실행으로 주장하지 않음 |
| G6 독립 재현 | NOT_RUN — 최종 승격 대기 | 독립 oracle·원본 재계산·코드 리뷰·해시 검사를 [기준별로 기록](../verification/acceptance.json) | 고정 후보 최종 검사 및 필수 미해결 조건 0개. 개별 검토 통과를 전체 승격으로 대체하지 않음 |
| G7 사용자 이해 | BLOCKED | [자산·절차](../verification/human-study/README.md), [타이밍 실패 시 시작 차단](../verification/human-study/tests/preflight-browser-smoke.json) | 유효한 실제 노출 환경과 최소 5명 응답·독립 채점. 현재 참가자 0명 |

## 원래 R01~R12 시나리오와 증거 연결

원래 제품 계획의 시나리오를 확장된 AC 기준으로 연결한다. 합성 로직의 성공은 동일한 실제 도로 장면을 시험했다는 뜻이 아니다. 음성을 구현하지 않았으므로 음성 취소를 검사했다고 기재하지 않는다.

| 원래 ID·상황 | 연결 AC | 현재 결과와 근거 |
|---|---|---|
| R01 승인 구간·올바른 방향 | AC-01·25 | 합성 정상 접근·도식 PASS: [fixture](../verification/scenarios/normal-approach.json), [독립 실행](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.AcceptanceFixtureTest.xml), [native 화면](../verification/runs/ui-06/summary.json). 실제 승인 구간은 G4 BLOCKED |
| R02 반대 방향 | AC-02 | 합성 억제 PASS: [fixture](../verification/scenarios/reverse-direction.json), [엔진 결과](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.GuidanceEngineTest.xml) |
| R03 고가·평행 도로 | AC-03·16 | 복수 후보 억제 PASS: [fixture](../verification/scenarios/ambiguous-road.json), [투영 검사](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.GeoProjectorTest.xml). 실제 고가 도로 관측 아님 |
| R04 진입 차로 아닌 본선 통과 | AC-03·04·16 | 현재 차로 미확정 표현·후보 모호성/미지원 도로 억제의 합성 검사 PASS: [조정 로직](../verification/runs/domain-04/data-TEST-dev.pleos.roadcontext.data.LiveGuidanceCoordinatorTest.xml), [native UI](../verification/runs/ui-06/summary.json). 실제 본선 구간 시험 NOT_RUN |
| R05 GPS 점프·오래된 입력 | AC-05·17 | 합성 억제·시간 경계 PASS: [좌표 점프](../verification/scenarios/coordinate-jump.json), [오래된 입력](../verification/scenarios/stale-input.json), [독립 실행](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.AcceptanceFixtureTest.xml) |
| R06 같은 위치 반복 | AC-06 | 100회 반복 예고 증가 없음 PASS: [fixture](../verification/scenarios/repeated-callback.json), [엔진 결과](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.GuidanceEngineTest.xml). 음성 기능은 구현하지 않음 |
| R07 예고 후 재경로 | AC-07·14 | 이전 카드·세대 폐기 합성 검사 PASS: [fixture](../verification/scenarios/route-change.json), [복구 검사](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.AvailabilityRecoveryTest.xml). 실제 경로 카드 통합은 G4 미완료 |
| R08 합류 끝에서 앱 시작 | AC-08 | 늦은 예고 억제 PASS: [fixture](../verification/scenarios/late-start.json), [독립 실행](../verification/runs/domain-04/core-TEST-dev.pleos.roadcontext.core.AcceptanceFixtureTest.xml) |
| R09 자료 누락·검수 만료 | AC-09·12·18 | 비활성화·안내 보류 PASS: [만료 fixture](../verification/scenarios/expired-evidence.json), [구간 비활성화](../verification/scenarios/segment-disabled.json), [자료 로더](../verification/runs/domain-04/data-TEST-dev.pleos.roadcontext.data.CatalogLoaderTest.xml) |
| R10 좌표계·끊긴 topology | AC-10·16 | 로딩 거부 PASS: [잘못된 topology](../verification/scenarios/invalid-topology.json), [자료 로더](../verification/runs/domain-04/data-TEST-dev.pleos.roadcontext.data.CatalogLoaderTest.xml) |
| R11 실제 SDK 이동 | AC-19·21 | Fused 위치 전달 부분 PASS: [sdk-01](../verification/runs/sdk-01/summary.json). 실제 SDK 위치→접근 단계 연결은 BLOCKED이며 [활성·주행 조건의 Navi 위치 고정](../verification/runs/combined-drive-01/summary.json) 보존 |
| R12 실제 사용자 구간 | AC-20·21 | NOT_RUN: 용산역→월곡역의 정확한 해당 구간·구조 미특정. [자료 조사](./decisions/road-data.md)에서 확보한 샘플을 원래 구간 검증으로 대체하지 않음 |

실패·수정·재검증의 원본 연결은 [이슈 색인](../verification/issues/index.md)을 따른다. 제출용 별도 영상은 필수로 추가하지 않으며, 원래 계획의 ‘영상 또는 화면 캡처’ 요구는 이미 보관한 native 캡처로 인계한다.

## 남은 게이트

G2의 합성 L1 기준은 실제 도로 자료가 없다는 이유로 미검증 처리하지 않았다. AC-01~18, AC-25, AC-30은 각 기대 결과에 해당하는 엔진·조정 로직·native 회귀·수명 증거를 대조해 정의된 L1 범위에서 PASS로 판정했다. AC-12의 미지원 SDK·권한·연결 실패 표시도 ui-06에서 보완 검증했다. AC-22는 명시한 지원 화면 범위로 판정했다. AC-24는 이후 제공 버튼·next·실제 권한 대기·중복 guard를 모두 연결해 원래 기준대로 PASS 판정했다. 실제 자료·실시간 통합은 AC-20/21에서 별도로 차단한다.

| 게이트·기준 | 상태 | 이유와 다음 작업 |
|---|---|---|
| AC-19 SDK 위치 갱신 | 기록된 범위 PASS | Fused 에뮬레이터 공급자만; 소스 변경 시 재시험 |
| AC-20 실제 도로 자료 | BLOCKED | NGII 인증 불가. 별도 공식 GITC 샘플은 확보했으나 합류 구조·방향·최신성·coverage·이용조건 미승인 |
| AC-21 실제 위치→도로→안내 | BLOCKED | 실제 카탈로그 없음, 경로·위치 일치와 오차 범위 미검증 |
| AC-22 다양한 화면 | 선언한 에뮬레이터 범위 PASS | 실제 host·가상 화면·테마·글자 확대 검사 성공. 물리 표시 장치 인증은 아님 |
| AC-23 대비·터치·물리 크기 | 부분 검증 | dp 검사와 실제 물리 크기 판단은 별개 |
| AC-24 입력 피드백 | PASS — 지원 에뮬레이터 | ui-08 제공 버튼·pipeline-04 next·permission-flow-02 대기·중복 guard. 물리 픽셀 보장으로 확대하지 않음 |
| AC-26 native UI·주행 표시 | 관측한 에뮬레이터 사용 형태 PASS | build-10 실제 주행 제한·활성 내비와 제품 전경 유지 확인. 중앙 지도 동시 표시·음성은 미검증 |
| AC-27 실제 사용자 이해 | BLOCKED | 자산16 READY·사람0명. 노출 타이밍 preflight도 실패해 동일 환경 재검증 필요 |
| AC-28 내구·수명 | 명시한 에뮬레이터 범위 PASS | 실제시간 30분·75사이클·600판정, 재생성 10회·SDK 연결/해제 20회 성공 |
| AC-29 성능 | L1 합성 제품 경로 PASS | v2 1,000건 p95 172.263875ms, 변경750건183.715084ms. v1실패·최대2357.489043ms 보존. 실제 SDK 경로는 미검증 |
| AC-30 결정론 | L1 범위 PASS | 최신 고정 입력 1,000개×3회 결정 해시 동일, 독립 18개 fixture 통과 |

G4 실시간 통합은 Fused 위치가 움직인다는 사실만으로 통과하지 않는다. 실제 구간 0개인 현재 카탈로그를 유지하고, 사용자가 로그인할 수 있을 때 NGII 원자료 반입·검수부터 이어 간다. 로그인 없이 확보한 GITC 공식 샘플도 있지만 합류 구간 승인 요건이 충족되지 않았으므로 카탈로그에 넣지 않았다. 원자료가 들어오더라도 주변 경쟁 도로 범위·방향·합류 구조·출처·이용 조건을 검수해야 한다. [도로 자료 조사와 인계](./decisions/road-data.md)

## 증거 유효성과 다음 순서

domain-04의 123개 JVM 결과는 build-09 소스에 해당한다. 이후 앱의 미지원 상태 처리를 보완했으며, build-10 소스 manifest와 실제 코드/config의 일치 및 ui-06 native 49개 성공을 확인했다. domain-04가 검증한 엔진·투영·카탈로그 범위와 ui-06이 검증한 앱 범위를 구분한다. console-01 주행 정책 결과는 build-07 범위로 보존한다.

합성 시연의 엔진·native UI·수명·30분 내구·연결 성능은 명시한 실행 범위에서 통과했다. 현재 제품 build-16은 고정되어 있고 후속 테스트 도구 build-19까지 검증했다. 입력 피드백은 원래 UI 상태 timestamp 기준으로 PASS이며, 외부 자료·SDK 정합성·물리 표시·사람 평가가 남는다. 물리 픽셀의 일반적인 최악 지연 보장을 새 요구사항으로 추가하지 않는다.

pipeline-02까지 수행한 독립 증거 감사에서 acceptance의 증거 경로·해시 217개, fixture 해시 22개에 불일치가 없었다. build-13의 코드·설정 41개 경로(gradle.properties 포함)는 현 파일과 일치했고 이 보고서의 로컬 링크도 유효했다. 당시 결과는 PASS 25개, BLOCKED 3개, NOT_RUN 2개였고, 최종 결과는 PASS 26개·BLOCKED 3개(AC-20/21/27)·NOT_RUN 1개(AC-23)다. AC-24의 부분 PASS를 전체 기준 PASS로 표시했던 상태는 원래 계획에 맞춰 정정했다. 이 숫자를 전체 제품 완료율이나 실도로 성공률로 해석하지 않는다.

## 원래 계획에 따른 전체 완료 감사

원래 계획은 ‘부분 성공은 PASS가 아니다’라고 정하고, L1에도 G0·G1·G2·G3·G5·G6을 모두 요구한다. 따라서 **현재 L1 전체도 완료로 선언하지 않는다.** 합성 입력으로 핵심 경험을 시연할 수 있고 여러 자동 검사가 성공했다는 사실은 유지하지만, G3의 AC-23이 미완료이고 G6의 모든 필수 조건을 충족한 최종 승격도 남아 있다. L2는 승인된 실도로·실시간 정합성, L3는 그 조건에 더해 실제 사람 평가를 요구한다.

필요한 로컬 구현·계측 보완을 마쳤다. build-15 clean·필수 JVM·lint, 후속 동일 제품의 native 수명·제어·연결 검사, 실제 권한 동기화 수정 재검증까지 끝났다. 새 요건이나 성공 사례를 고르기 위한 반복 검사는 추가하지 않는다.

남은 원래 조건은 승인 가능한 실제 합류 자료의 이용조건·방향·최신성·주변 도로 범위, Navi/Fused 정합성과 confidence 단위 근거, 대상 표시 장치의 물리 글자 크기, 유효한 평가 환경과 최소 5명의 실제 참가자다. 실제 자료·SDK 답변·대상 장치·사람 응답을 로컬 합성값으로 대체할 수 없다. 동일한 외부 차단이 세 작업 턴 이상 지속됐으며, 현재 담당 범위에 해결하지 않은 구체적인 로컬 결함은 없다. 최종 인계 정리 후 목표 BLOCKED 판정이 적절하다. 완료 판정은 아니다.

사람 평가 도구의 추가 검증에서는 중립 자극 3회가 2180.5ms·2041.3ms·2042.3ms로 기록됐다. 원본 callback 시각으로 각각 재계산했고 환경 sidecar와 일치했다. 첫 시행이 허용범위를 넘으므로 전체 preflight는 FAIL, 평가 시작 버튼은 차단됐으며 응답은 0건이다. 사유 없는 재시도 금지·환경 변경 시 무효화 등을 포함한 gate 9개와 채점 무결성 10개 순수 테스트도 독립 재실행에 성공했다. 시험 도구의 차단 기능은 검증됐지만 실제 사람을 평가할 환경의 타이밍은 여전히 통과하지 못했다.

## 이전 pipeline-03 진단 기록

사전에 1회로 정한 pipeline-03은 제품 코드를 바꾸지 않고 fetch/copy 시간과 Android Window FrameMetrics를 추가했다. 입력 1,000개 p95는 169.19075ms, 변경 상태 750개 p95는 180.613958ms다. 최대 1172.140292ms와 250ms 초과 14건을 보존했다. 모델 반영 최대는 39.841791ms, semantics fetch 최대는 1156.206083ms, 트리 복사 최대는 7.249583ms였다. [원본 진단](../verification/runs/pipeline-03/synthetic-replay-pipeline-performance.json).

Window 프레임 943개 중 9개는 total이 250ms를 넘었고 최대는 1049.649454ms였다. 단순히 검증 스레드의 반복 검사 비용뿐이라고 설명할 수 없다. 945개 시계 쌍의 산술을 독립 확인했고 offset 구간의 공통 교집합은 -584~792ns, 최대 읽기 불확실성은 665167ns였다. 로컬 offset 범위로 대조한 긴 관측 14건 중 13건은 frame 구간과 시간상 겹쳤으며 5건은 250ms 초과 frame과 겹쳤다. 다만 warm-up·reset 프레임도 포함되고 frame이 어떤 모델 상태를 그렸는지 식별되지 않으므로 개별 입력의 화면 반영 원인이나 물리 표시 시각을 확정하지 않는다.

이 진단 당시에는 AC-24 전체가 NOT_RUN이었다. 이후 실제 composition 관찰·제공 버튼·권한 대기 검사를 완료한 최종 판정은 앞의 AC-24 항목을 따른다.

진행 중인 작업을 제외하고, pipeline-03까지 연결한 기존 증거 227개 경로와 SHA-256을 확인했다. 불일치 0건이다. 이는 당시 기록된 실행의 무결성 확인이다. 최종 감사에서는 증거 참조 332개·로컬 링크 119개를 확인해 누락·해시 불일치·깨진 링크가 없었다.

| 남은 요건 | 지금 자율적으로 할 작업 | 완료에 필요한 외부 조건 |
|---|---|---|
| AC-24 전체 피드백 | 완료: 실제 composition·제공 버튼·권한 대기·중복 guard | 별도 외부 조건 없음 |
| G1·G6 최종 재현 | clean build·test·lint·native 검사, 새 APK와 증거 고정 | 다른 필수 게이트의 완료 전 전체 승격은 불가 |
| AC-20 실제 자료 | 확보한 자료의 불충족 근거와 후속 절차 보존 | 합류 topology·방향·최신성·이용조건·주변 도로 범위를 승인할 원자료 |
| AC-21 실제 연결 | 안전한 억제 유지·SDK 재현 자료 보존 | 실제 자료 및 위치·경로 정합성·미터 단위 오차 근거 |
| AC-23 물리 적합 | 계산 가능한 색 대비·dp 검사 결과와 가정 보존 | 대상 화면의 물리 크기와 실제 글자 높이 측정 |
| AC-27 사용자 이해 | 타이밍 미달 시 시작 차단 유지 | 같은 평가 환경의 타이밍 통과, 최소 5명 참여·독립 채점 |

## build-15 clean 검증 완료

원본 build.txt에서 app·core·data·integration의 clean task와 `BUILD SUCCESSFUL in 3m 17s`를 확인했다. XML testcase를 독립 계수한 결과 실행 130회·고유 123개이며, 차이 7개는 debug/release 양쪽에서 실행한 같은 writer 테스트다. 실패·오류 0건이다. lint는 오류 0건·경고 8건(의존성 버전 6, target API 1, data extraction 1)이며 경고를 숨기지 않는다. [빌드 원본](../verification/runs/build-15/build.txt), [결과·보고서 목록](../verification/runs/build-15/summary.json).

compiled-inputs의 소스·설정·리소스 48개 해시가 현 파일과 일치했고 APK 사본의 SHA-256은 `e57af973ed6daab4e1ac36f306fb4b497b9918a2d3b6484cadd575f86a5cb254`다. 변경 중인 문서는 코드 식별자에서 제외했다. [컴파일 입력](../verification/runs/build-15/compiled-inputs.json). 이 후보의 ui-07은 이후 65 성공·4 실패로 종료됐으며 후속 build-16 대상 검사는 별도 기록했다. build-10 native 결과를 build-15 실행으로 바꿔 적지 않는다.
