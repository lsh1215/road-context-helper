# 독립 저장소 구성

이 폴더(`road-context-helper/`) 자체가 Git 저장소 루트다. 상위 `Pleos App/`나 다른 앱을 함께 올리지 않는다. Gradle Wrapper와 앱별 계획서는 이 저장소에 포함된다.

## 새 PC에서 빌드

JDK 17과 Android SDK를 설치하고 `ANDROID_HOME`을 설정한다. 또는 Gradle 프로젝트 루트의 `local.properties`에 `sdk.dir`을 설정한다. 렛츠 플레오는 `car/local.properties`를 사용한다. 첫 실행에는 Gradle 및 공식 Maven 의존성 다운로드를 위한 인터넷이 필요하다.

```sh
./gradlew :app:assembleDebug :core:test
```

## GitHub 연결

GitHub에 이 앱 전용의 빈 저장소를 만든 뒤 이 폴더에서 실행한다. URL은 본인의 실제 저장소로 바꾼다.

```sh
git status --short
git add .
git diff --cached --stat
git commit -m "Initial app implementation"
git remote add origin https://github.com/OWNER/road-context-helper.git
git push -u origin main
git rev-parse HEAD
```

마지막 명령은 커밋 후 제출할 해시를 출력한다. 현재 정리는 로컬 Git 초기화까지이며 원격 생성·커밋·push는 수행하지 않았다.

## 포함과 제외

소스·테스트·빌드 설정·Gradle Wrapper·계획 문서는 포함한다. 빌드 결과, APK, 로컬 SDK 경로, AVD, 캐시, 비밀 설정, 서명키, 임시 로그와 대용량 시험 원본은 `.gitignore`로 제외한다. 제외 파일은 삭제하지 않는다. 실제 배포 APK는 GitHub Release나 별도 제출 첨부로 제공할 수 있다.

`demos/`에는 승인된 응봉·망원 두 사례의 전체/핵심 MP4 총 4개를 포함한다. `verification/runs/`와 `verification/archived-demos/`는 로컬에만 있다. 옛 실행 기록의 문서 링크는 로컬 검증 환경을 전제로 하며, 새 clone에 원본 로그가 포함된다는 뜻이 아니다. 시연 스크립트는 Pleos 이미지로 만든 전용 RoadContext AVD와 지정된 gRPC 설정이 필요하다. `replay-mangwon-comparison.py`는 제외된 과거 검증 GPS 원본도 필요하다. 앱 빌드에는 이 원본이 필요 없다.

## 독립 구성 검증 (2026-11-15)

`git ls-files --others --exclude-standard`로 선정된 파일만 `/tmp`의 별도 앱 폴더로 복사해 빌드 성공을 확인했다. 원래 상위 작업 폴더의 소스는 사용하지 않았으며 로컬 JDK 17·Android SDK·이미 다운로드된 Gradle/Maven 캐시를 사용했다. 추가로 75개 core 단위 테스트를 통과했다. `.gitignore`의 비밀 설정·로컬 상태·산출물 제외 규칙도 검사했다.
