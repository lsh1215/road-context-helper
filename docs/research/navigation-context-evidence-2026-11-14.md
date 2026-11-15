# 합류 전 도로 맥락 안내: 문제·비교 증거·제안

기록일: 2026-11-14 · 목적: 공모전 기획·발표에서 문제 상황과 추가 제공 정보를 근거로 설명하기

## 1. 현재 주장할 수 있는 결론

카카오내비는 시험 경로에서 **합류 전에 다음 왼쪽 출구의 방향·거리와 녹색 차로를 이미 안내했다.** 입체 진출 이미지도 확인했다. 이 기능을 우리 앱만의 기능으로 소개하지 않는다.

이번 두 경로의 녹화·GPS·음성 자동 전사에서는 **진입 차로의 끝나는 위치**, **합류 직후 반대편 출구로 가기 위해 여러 차로를 건너야 하는 구조와 짧은 준비 구간**을 합류 전에 함께 설명하는 안내는 관찰되지 않았다. 이 정보가 우리 앱의 차별화 가설이다.

판정은 **지정한 모바일 앱 버전·경로·시험 조건에서의 관찰 결과**다. 카카오내비 전체 기능의 부재, Pleos 기본 내비 또는 차량용 카카오내비 대비 독점 기능, 운전자 이해도·안전성 개선이 입증됐다는 뜻은 아니다.

## 2. 운전자가 해결하려는 질문

### 사례 C: 차로 소멸형 합류

낯선 진입로에서 운전자는 “이 차로가 본선 차로로 계속 이어지는가, 앞에서 끝나는가, 어디까지 합류를 준비할 수 있는가?”를 알고 싶다. 목적지 방향 화살표만으로 이 구조를 모두 설명했다고 보지는 않는다.

정체 시 무조건 끝까지 진행하거나 다른 차량이 반드시 양보한다고 지시하는 기능을 목표로 삼지 않는다. 검수된 차로의 지속·종료 구조를 보여주고, 실제 합류 판단은 표지·차선·주변 교통을 따르도록 한다.

### 사례 B: 합류 후 가까운 반대편 진출

운전자는 본선에 합류하는 일과 곧 반대편으로 빠지는 일을 한 번에 준비해야 한다. 질문은 “다음 출구가 왼쪽인가?”에 더해 “나는 어느 쪽으로 진입하며, 출구 쪽으로 가려면 어느 정도의 차로 이동이 필요하고, 준비할 공간이 얼마나 짧은가?”다.

두 사례는 **같은 망원 합류부에 서로 다른 목적지를 설정해 분리한 시험**이다. 서로 다른 두 장소를 검증했다고 표현하지 않는다.

## 3. 실제 구간의 근거

- 공통 진입: 내부순환로 성산방향 → 강변북로 구리방향, 망원 합류부.
- C 목적지: 마포대교북단교차로. 합류 후 강변북로 본선을 유지한다.
- B 목적지: 당산역 2호선. 합류 후 양화대교 왼쪽 진출로로 이동한다.
- 2026년 7월 카카오 로드뷰에서 망원 진입부의 차로 감소 표지와 합류 전후 구조를 확인했다. 현장 측량이나 정확한 소멸 좌표 확정은 아니다.
- 2024-10-20 대한경제 보도는 서울시 관계자의 설명으로, 내부순환로에서 양화대교 남단으로 갈 때 차로 변경 가능 거리가 약250m이고 4개 차로를 건너야 하는 문제를 다룬다. **250m·4개는 해당 보도의 설명이며 이번 GPS 시험에서 새로 측정한 값이 아니다.**
- 2026-11-14 열람한 서울시 건설알림이는 관련 구조개선 공사의 준공 예정일을 2028-03-26으로 표시했다. 향후 실제 구조 변경 시 이 비교도 다시 확인해야 한다.

출처: [카카오 로드뷰](https://map.kakao.com/link/roadview/37.55268282953703,126.89970173363042), [서울시 관계자 설명 보도](https://www.dnews.co.kr/uhtml/view.jsp?idxno=202410171444339690565&section=S1N12), [서울시 건설알림이](https://cis.seoul.go.kr/TotalAlimi_new/PopInfo.action?cmd=info1&menu=1&pjt_cd=1112024112098).

![망원 합류부 차로 감소 표지](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/mangwon-lane-end-before.png>)

그림1. 시험 구간의 2026년7월 로드뷰. 오른쪽 차로 감소 표지와 합류 전 구조. 이미지의 촬영 월과 사이트 표시는 유지한다.

## 4. 시험 조건과 신뢰 범위

| 항목 | 기록 |
|---|---|
| 앱 | 모바일 카카오내비4.52.1 / versionCode45202 / com.locnall.KimGiSa |
| 환경 | Android16 API36 Google Play arm64 에뮬레이터 KakaoComparison36, emulator-5562 |
| 음성 | 기본 음성 여성·국문. 설정 화면 보존 |
| 위치 입력 | 실제 도로 중심선 좌표를 emulator gRPC setGps와 체크섬 NMEA로 입력. 앱 화면의 주행 단계 강제 전환은 하지 않음 |
| C 한국어 재시험 | case-c-ko1: 114초, 서로 다른 좌표115개 |
| B 한국어 재시험 | case-b-ko2: 140초, 서로 다른 좌표141개 |
| 입력 확인 | 두 실행의 위치 getGps readback이 입력과 일치. 이 값은 실차 위치 정확도 증명이 아님 |
| 화면 확인 | 전체 녹화 보존, 주요 지점 캡처, C139장·B160장을1초 간격 OCR 보조 점검 |
| 음성 확인 | 에뮬레이터 출력 녹음과 자동 전사. 사람이 전 구간 청취한 기록은 아님 |
| 주행 조건 | 좌표 재생15m/s. 화면 속도27/54km/h 교대 관찰. 정체·주변 차량은 통제하지 않음 |

스크린샷 파일명의 before-200m 등은 **OSM 연결 노드까지의 입력 기준 거리**다. 정확한 차로 끝까지의 실측 거리나 카카오내비가 인식한 현재 위치와 같다고 가정하지 않는다. C 최대 캡처 지연3.327초, B1.331초이며 표시 시점을 정밀한 반응속도 측정에 사용하지 않는다.

동영상은 화면만 담고 음성은 별도 WAV다. WAV는 수신 패킷을 이어 붙였으므로 WAV 재생 초와 화면 녹화 초를 직접 맞추면 안 된다. 음성의 실제 발생 시점은 패킷 timestamp와 utterances.json을 사용한다.

초기 영문 음성 실행과 성산 연결로 후보(case-a)는 보존하되 최종 두 사례의 판정에서 제외했다. 성산 후보는 목표 진입로의 차로 소멸을 확정하지 못했다. 이전 kakao-comparison-02의 다른 방향 시험을 이번 두 상황의 증거로 대신 쓰지 않는다.

## 5. 기존 안내에서 실제 확인한 것

| 질문 | C: 본선 유지 | B: 양화대교 진출 |
|---|---|---|
| 일반 경로·다음 진출 정보가 있는가 | 확인 | 확인 |
| 합류 전에 다음 왼쪽 출구를 알려주는가 | 목적지 경로상 해당 진출을 이용하지 않음 | 확인. 왼쪽 출구 거리와 녹색 차로 음성 |
| 입체 진출 그림이 나오는가 | 해당 양화대교 진출을 이용하지 않음 | 확인. 합류 부근에서 이미 표시 |
| 진입 차로의 소멸 위치·남은 합류 구간을 설명하는가 | 이번 기록에서 관찰되지 않음 | 이번 기록에서 관찰되지 않음 |
| 합류부터 반대편 진출까지의 차로 이동 부담을 함께 설명하는가 | 해당 상황 아님 | 이번 기록에서 관찰되지 않음 |
| 구체적인 횡단 차로 수·짧은 이동 가능 구간을 설명하는가 | 해당 상황 아님 | 이번 기록에서 관찰되지 않음 |

### C 관찰

합류 전에는 일반 경로와 본선 진행을 위한 차로 화살표가 표시됐다. 차로의 끝이나 남은 합류 공간을 별도로 설명하는 문구는 관찰되지 않았다. 합류 후에는 일반 위험 표시와 사고 다발 구간으로 해석되는 음성이 기록됐다. 자동 전사에는 일부 오인식이 있으므로 다듬은 문장을 원문 인용으로 제시하지 않는다.

![카카오내비 본선 유지 경로 합류 전](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-c-ko1/before-200m.png>)

그림2. C의 before-200m 캡처. 이름의 거리는 입력 연결 노드 기준이다. 이 한 장만으로 안내 부재를 판단한 것이 아니며 전체 녹화·음성 기록을 함께 보존했다.

### B 관찰

합류 전에 다음 음성이 자동 전사됐다. 숫자와 정확한 발음은 첨부 음성을 사람이 확인한 후 발표 자막으로 확정한다.

> 1km 앞, 양화대교, 방면, 왼쪽에, 도시고속도로 출구입니다.
> 녹색 차로로 주행하세요.

후속 음성도 같은 취지이며 자동 전사상 거리는600m다. 음성 시작과 가장 가까운 GPS 입력을 대조하면 각각 연결 노드 약585m 전, 약135m 전이다. 두 거리는 **출구까지의 앱 안내 거리**와 **합류 노드까지의 시험 거리**로 기준이 다르다. 이 수치를 빼서 새 도로 거리를 산출하지 않는다.

![카카오내비 양화대교 진출 입체 안내](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko2/before-30m.png>)

그림3. B의 before-30m 캡처. 카카오내비가 왼쪽 진출 입체 그림까지 이미 제공한다는 긍정 증거다. 이 화면을 빼고 경쟁 기능을 약하게 보이게 편집하지 않는다.

이 안내만으로도 다음 출구 방향은 미리 알 수 있다. 이번 기록에서 추가로 관찰되지 않은 내용은 **오른쪽 진입과 왼쪽 진출 사이의 연속 구조, 차로 횡단 부담, 사용할 수 있는 구간이 짧은 이유를 묶은 설명**이다.

## 6. 우리 방법: 무엇을 추가하며 무엇이 구현됐는가

| 추가하려는 정보 | 제안하는 표현 | 현재 증거의 상태 |
|---|---|---|
| 진입 차로 지속·종료 | 연결 도로를 크게 보여주고 차로 끝을 강조 | 가상 MERGE 도식과 차량 모형 구현 |
| 합류와 다음 진출의 연결 | 진입 쪽부터 출구 쪽까지 한 그림으로 보여주기 | 가상 SHORT_EXIT 시나리오 구현 |
| 여러 차로 이동 부담 | 전방을 향한 차량 모형이 차로를 단계적으로 이동하는 설명 | 가상 애니메이션 구현. 실제 차로 측위·주행 지시가 아님 |
| 필요한 구간에만 표시 | 접근 시 안내, 통과 시 숨김 | 별도 양화대교 북단→강변북로 난지방향 GPS 시험에서 Pleos Fused 콜백 기반 전환 기록 있음 |
| 이번 망원 구간에 같은 구조·방향으로 적용 | 검수된 실제 진입·본선·진출 구조와 경로를 연결 | **이번 기록에는 동일 구간의 우리 앱 주행 결과 없음** |
| 운전자에게 더 유용한가 | 기본 내비만 사용한 경우와 보조 안내를 함께 사용한 경우 비교 | 미검증. 선호·준비 시간·오류 감소 수치 없음 |

![우리 앱 가상 도로 구조 설명 프로토타입](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/cluster-gallery-16/short-exit.png>)

그림4. 우리 앱의 기존 build-34 가상 시연 화면. **왼쪽 합류→오른쪽 진출,300m,4개 본선 차로**를 사용하는 설명 예시다. 실제 시험한 망원→양화대교의 **오른쪽 진입→왼쪽 진출** 구조와 다르다.300m는 시연 값이며 보도상의250m와 혼용하지 않는다. 사진의 Y자 분기 메뉴는 이후 build-35에서 제거되어 현재 구성과도 차이가 있다. 이 사진은 구현 방식의 증거로만 사용하고 동일 도로 비교의 ‘개선 후’로 쓰지 않는다.

현재 소스의 MERGE·SHORT_EXIT 문구와 기존 SDK 시험의 범위도 아래 증거 목록에 연결했다. 클러스터 스타일이나 차량 크기 자체를 차별화 근거로 삼지 않는다.

## 7. 발표 순서와 사용할 문장

| 순서 | 보여줄 자료 | 설명할 핵심 |
|---|---|---|
| 0–20초 | 실제 도로 사진·출구 연결 개요 | 처음 온 운전자는 차로가 끝나는 지점과 합류 직후의 반대편 진출을 한 번에 예상하기 어렵다 |
| 20–50초 | B의 합류 전 전체 흐름과 음성 | 기존 내비가 출구 방향·거리·녹색 차로를 이미 안내한다는 점을 먼저 보여준다 |
| 50–70초 | C·B의 관찰표 | 이번 조건에서는 차로 종료 구조와 합류 후 이동 부담을 묶은 설명을 관찰하지 못했다 |
| 70–100초 | 우리 앱 구조 예시 | 합류 전에 진입부터 다음 출구까지의 관계를 한 화면으로 설명하는 방식을 제안한다. 현재는 가상 예시임을 표시 |
| 100–120초 | 검증 범위와 다음 비교 기준 | 동일 실제 구간 적용 및 운전자 이해도 비교는 후속 검증으로 명시 |

발표용 핵심 문장:

> “카카오내비는 이 구간에서 다음 왼쪽 출구와 녹색 차로를 미리 안내했습니다. 저희가 확인하려는 것은 그 다음 단계입니다. 진입 차로가 어디서 끝나는지, 합류한 뒤 반대편 출구까지 어떤 준비가 필요한지를 합류 전에 하나의 도로 구조로 설명하려고 합니다. 이번 가상 주행에서는 이 통합 설명을 관찰하지 못했고, 이를 보완하는 표현을 프로토타입으로 구현했습니다.”

현재 허용할 표현은 “이 버전·경로의 시험에서 관찰되지 않았다”, “추가 가치를 검증 중이다”다. “카카오내비는 합류 전에 출구를 안 알려준다”, “Pleos에 없는 기능이다”, “최초다”, “사고가 줄었다”는 이번 자료로 뒷받침되지 않는다.

예상 질문: **“이미 추천 차로와 다음 출구 안내가 되지 않나요?”**

답변: “맞습니다. 그 기능은 이번 시험에서도 확인했고 영상에 포함했습니다. 저희의 비교 항목은 진입 차로의 끝과 합류 후 반대편 진출까지의 이동 부담을 함께 설명하는지입니다. 해당 조건에서 그 설명을 관찰하지 못했습니다. 이 설명이 운전자 이해에 실제로 도움이 되는지는 별도 비교가 필요합니다.”

예상 질문: **“같은 도로에서 우리 앱이 더 잘한 건가요?”**

답변: “현재 자료는 기존 앱 관찰과 우리 앱의 표현 프로토타입입니다. 같은 실제 도로를 양쪽 앱으로 완주한 직접 비교나 우월성 검증은 아직 아닙니다.”

## 8. ‘기존 안내 → 우리 방법’ 직접 비교를 완성할 기준

다음 결과가 있어야 동일 구간의 개선 시연으로 교체한다. 이 항목들은 후속 작업 기준이며 현재 통과한 결과가 아니다.

1. **도로 구조:** 진입 방향·연결 차로·소멸 지점·진출 방향과 차선 변경 제약을 최신 자료로 검수한다. 횡단 차로 수와 사용 가능 거리는 출처를 붙이고 불명확하면 숫자를 표시하지 않는다.
2. **같은 입력:** C와 B의 경로, 접근 방향, 목적지, 좌표 재생을 우리 앱에도 적용한다. 제안 경로와 현재 운전자 차로를 구분한다. 현재 차로를 모르면 특정 차로에 있다고 단정하지 않는다.
3. **예고 시점:** 두 앱의 입력 로그에 공통 사건 기준을 둔다. 합류 전부터 각 앱이 어떤 정보를 언제 제공했는지 기록하고, 단순 화면 전환으로 대신하지 않는다.
4. **정보 차이:** C에서는 차로 종료 구조, B에서는 합류와 반대편 진출의 관계가 합류 전에 표현되어야 한다. 방향 화살표만 다르게 그린 화면은 충족으로 세지 않는다.
5. **동작 경계:** 통과·반대 방향·경로 이탈·오래된 위치에서 안내가 적절히 사라지는지 기록한다. 위치 전달 성공과 도로 정보 정확성을 별도로 판정한다.
6. **공정한 제시:** 기존 앱의 음성·입체 안내가 모두 보이는 구간을 포함한다. 우리 앱에도 가상 GPS·구조 예시 여부를 표시한다. 별도 녹화는 ‘동시 주행’처럼 편집하지 않는다.
7. **효용 검증:** 운전자 평가 전에 질문·순서·성공 기준을 등록한다. ‘차로가 끝나는지’, ‘합류 후 출구가 어느 쪽인지’, ‘왜 미리 준비해야 하는지’의 정확한 이해와 인지 시점을 측정한다. 결과 전에는 개선율을 제시하지 않는다.

## 9. 증거 목록과 보관

| ID | 파일 | 사용 범위 |
|---|---|---|
| E01 | [시험 조건·결과 JSON](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/comparison-evidence.json>) | 이 문서의 기초 관찰 기록 |
| E02 | [C 화면 녹화](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-c-ko-context.mp4>) | 무음 화면 원본 |
| E03 | [C 음성 원본](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-c-ko-audio.wav>) | 패킷 결합 WAV |
| E04 | [C 자동 전사](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-c-transcript.txt>) | 오인식 포함 원문 |
| E05 | [C GPS 로그](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-c-ko1/gps.jsonl>) | 115좌표 |
| E06 | [B 화면 녹화](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko-context.mp4>) | 무음 화면 원본 |
| E07 | [B 음성 원본](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko-audio.wav>) | 패킷 결합 WAV |
| E08 | [B 자동 전사](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko-transcript.txt>) | 거리·색상 안내 확인 |
| E09 | [B 합류 전 첫 음성](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko-audio-utterance-01.wav>) | 연결 노드 약585m 전 |
| E10 | [B 합류 전 후속 음성](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko-audio-utterance-02.wav>) | 연결 노드 약135m 전 |
| E11 | [B 음성·위치 대응](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko-audio-utterances.json>) | 패킷 시각과 근접 GPS |
| E12 | [B GPS 로그](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/case-b-ko2/gps.jsonl>) | 141좌표 |
| E13 | [한국어 음성 설정](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/korean-voice-setting.png>) | 최종 두 실행의 설정 |
| E14 | [우리 앱 현재 시나리오 코드](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/app/src/main/java/dev/pleos/roadcontext/ClusterScenario.kt>) | 현재 작업 파일; 해시로 기록 시점 구분 |
| E15 | [가상 데모 출처 설명](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/cluster-gallery-16/README.md>) | build-34 역사적 그림 |
| E16 | [별도 Pleos SDK 위치 시험](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-comparison-02/comparison-result.json>) | 이번 망원 구간 직접 비교가 아님 |
| E17 | [도로 구조 자료·SDK 시험 범위](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/docs/decisions/sdk-gps-road-replay.md>) | 별도 양화대교 난지방향 시험 |
| E18 | [사람 평가 프로토콜](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/human-study/protocol.md>) | 평가 설계 참고; 실참가자 결과로 인용 금지 |

[증거 파일 해시 목록](</Users/leesanghun/My_Project/agent-engineering/Pleos App/apps/road-context-helper/verification/runs/kakao-context-03/evidence-manifest-2026-11-14.json>)는 선택한 원본·로그·스크립트의 SHA-256과 크기를 담는다. 문서를 작성한 시점의 변경 감지용 스냅샷이며 독립기관의 원본 인증은 아니다. 기존 기록은 덮어쓰지 않고 새 재시험은 새 run 이름으로 남긴다.

화면 원본의 표지·버전·가상 시연 표시를 잘라 사실관계를 바꾸지 않는다. 출처·촬영 시점과 시험 조건은 화면 설명 또는 각주에 유지한다. 음성과 화면을 결합할 때는 패킷 timestamp로 동기화를 검증하고, 자동 전사만을 듣고 확인한 정확한 인용으로 바꾸지 않는다.

## 10. 재실행 인수인계

저장된 replay-procedure.py는 당시 실행 스크립트 스냅샷이다. 이 Mac의 workspace·SDK 경로와 /tmp/kakao-gps-control.mjs를 참조한다. 함께 보존한 gps-control.mjs는 그 임시 파일의 사본이며 인증 토큰 값은 포함하지 않는다. 실행 시 전용 에뮬레이터 discovery 파일에서 로컬 제어 토큰을 읽는다. 스냅샷은 다른 컴퓨터에서 바로 실행되는 설치 패키지가 아니다.

전제: KakaoComparison36/emulator-5562/gRPC8563 실행, 카카오내비 로그인, 한국어 기본 음성, 목적지 경로 선택. 최초 준비 때 --prime-moving으로 방향을 정합시킨 후 실제 길안내가 시작됐는지 확인한다. 다른 에뮬레이터는 사용하지 않는다.

당시 좌표 재생 인자:

- C: `c --start 150 --speed 15 --run ko1` (경로 끝 약 1,850m).
- B: `b --start 150 --end 2250 --speed 15 --run ko2`.

위 표기는 당시 실행 인자다. 재실행은 **ko1/ko2 대신 새로운 run 이름**을 쓴다. 음성·화면 출력도 새 이름을 사용한다. GPS readback, 실제 지도 이동, 합류 전 안내, 통과 후 안내를 모두 확인해야 하며 좌표 주입만 성공한 실행은 비교 완료로 세지 않는다.
