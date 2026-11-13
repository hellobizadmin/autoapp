# 차근차근

## 1. 앱 이름 · 한 줄 소개

**차근차근 — 차량 경고를 운전 상황에 맞춰 쉬운 한국어로 설명하는 앱.**

차량 경고를 쉬운 말로 안내하는 Pleos Connect용 데모 앱입니다. 기능 기준은
[`docs/FEATURE_SPEC_v2.0.md`](docs/FEATURE_SPEC_v2.0.md)입니다.

### 현재 구현

- FR-15 핵심 경고 2차: 기존 타이어·거리 4종 + 엔진오일 압력·과열·브레이크액 부족·ABS·배터리 충전 이상 5종 = **9종 시뮬레이션**. 데모 패널에서 선택·발생·해소하며 정차 필요 경고는 거리 부족보다 우선합니다. 신규 5종은 내비를 제안하지 않습니다. 문구·긴급도는 차종별 취급설명서 검증 전의 데모 초안입니다. [적용·검증 결과](docs/CORE_WARNINGS_PHASE_TWO.md).

- Android 의존성 없는 Kotlin 코어와 경고 사전, 거리 임계값 및 해소 여유값 처리
- 대기·주행·정차 Compose 화면, 승인 후 목적지 요청, 느린 다시 듣기
- FR-13 음성 명령 사전 판정, 상세 재생 완료 후 5초 단발성 듣기, 모호한 입력 거부
- 주행·상태 불명·백그라운드 전환 시 듣기 중지 및 지연된 결과 무효화
- Fake 차량·주행과 로그 내비 어댑터, Pleos 마이크 STT 및 일반 기기용 Fake Listener, 디버그 전용 데모 패널
- Pleos 공식 온디바이스 한국어 음성 및 일반 기기용 Android 오프라인 TTS 어댑터

Pleos 한국어 음성은 공식 TextToSpeech SDK의 ON_DEVICE 모드로 연결했습니다. 차량·내비 SDK는 아직 연결하지 않았습니다. STT SDK는 연결했으나 실제 발화의 최종 명령 인식은 추가 검증 중입니다. Pleos에서는 마이크 STT를 사용하고 일반 기기에서는 디버그 텍스트 입력을 사용합니다. 내비는 실제 목적지 변경 없이 요청을 기록합니다.
화면에 `시뮬레이션 · 고정 목록 사용`을 표시합니다. SDK 클래스와 호출 방식은
공식 문서를 확인한 뒤 교체해야 합니다.

## 2. 실행 방법

아래 테스트 환경을 준비한 후 저장소 루트에서 실행합니다.

```sh
./gradlew --no-daemon :app:assembleDebug

# Android SDK platform-tools를 PATH에 추가하고 연결된 기기를 확인합니다.
adb devices
# emulator-5554는 실제 확인한 대상 serial로 바꿉니다.
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user current -W -n kr.hellobiz.chageun/chageun.MainActivity
```

설치 후 런처에서 **차근차근**을 선택해도 실행할 수 있습니다.

### 데모 조작

1. Debug APK를 설치하고 앱을 엽니다. 초기 상태는 UNKNOWN이며 경고가 없으면 조용히 대기합니다.
2. 오른쪽 위 `시뮬레이션 · 고정 목록 사용`을 길게 눌러 데모 패널을 엽니다.
3. 주행 중을 선택하고 타이어 위치를 고른 후 경고를 발생시킵니다. 패널을 닫으면 한 줄 안내만 표시됩니다.
4. 패널에서 정차 중으로 전환하고 닫으면 상세 안내와 두 개의 버튼이 표시됩니다.
5. 상세 TTS 재생이 끝나면 5초 동안 한 번만 듣습니다. 데모 패널의 텍스트 입력으로 인식 결과를 전달합니다.
   미리 입력해 두었다가 `듣는 중`에 제출할 수 있습니다. `안내해줘`, `다시 말해줘`, `응 아니`로 각각 승인·반복·모호한 입력 거부를 확인합니다.
6. 거리 슬라이더를 100 → 70 → 25 → 95로 바꾸면 LOW → CRITICAL → 해소가 됩니다.

로컬 한국어 TTS 음성이 없으면 음성 사용 불가를 표시하고 화면과 버튼은 유지합니다.
재생에 실패한 경우 듣기를 시작하지 않습니다. 데모 목적지는 서울 시청 부근의 예시 좌표이며,
실제 내비 연결 전에 에뮬레이터 위치에 맞춰 `demo_places.json`을 수정해야 합니다.

## 3. 테스트 환경 · 필요한 설정

| 항목 | 환경 및 설정 |
|---|---|
| 개발 도구 | JDK 21, Gradle Wrapper 8.13 (`./gradlew` 사용) |
| Android SDK | compile/target SDK 35, 최소 API 28, platform-tools 설치 |
| SDK 경로 | 저장소 루트 `local.properties`에 `sdk.dir=/Users/<사용자>/Library/Android/sdk` 설정 |
| 주 검증 환경 | 공식 Pleos Connect 2.1.0, API 34, ARM64 에뮬레이터 |
| AVD 설정 | `Chageun_Pleos_2_1_0`, RAM 4GB, 데이터 6GB, Cold boot, software GPU |
| 보조 화면 검증 | SM-F971N / Android 17 (실차 연동 검증 아님) |
| 알림 | 경고 알림을 보려면 앱 알림 권한 허용 |
| 음성 출력 | Pleos는 내장 음성 서비스를 공식 SDK로 사용. 일반 기기는 오프라인 한국어 Android TTS 필요 |
| 음성 입력 | Pleos ON_DEVICE STT 연결, 마이크 권한 필요. 실제 발화 최종 인식은 검증 중 |

### 테스트 실행 및 결과

```sh
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# 에뮬레이터 또는 테스트 기기를 연결한 뒤 화면·서비스 계측 테스트 실행
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

코어와 어댑터 통합 테스트는 루트 JVM 프로젝트에서 실행합니다. Android 앱은 `:app`이며,
코어 소스는 `app/src/main/java/chageun`, Android 전용 소스는 `app/src/main/kotlin/chageun`에
분리되어 있습니다.

2026-11-13 검증 기록:

- JVM 단위 테스트 **40개 통과**: 코어 13개, 세션 27개.
- Debug·계측 APK 빌드 성공, Android lint 오류 0개·경고 7개.
- Pleos에서 **13개 체크포인트를 포함한 화면 시나리오 1개를 2회 실행하여 통과**.
- 화면 종료 후 감시 서비스 테스트: 알림 허용/거부 각각 1개 통과. 작업 제거 후 서비스 유지 및 재부팅 복구 확인.
- SM-F971N에서 Compose 화면 테스트 3개 통과. Pleos의 기존 화면 테스트는 일괄 2/3 통과 후 실패한 1개 단독 재실행 통과하여, 연속 실행 안정성 확인이 남아 있습니다.
- 실제 차량 SDK, 실제 마이크 인식, 실제 내비 목적지 설정은 미검증입니다. 한국어 음성은 후속 구성에서 재생을 확인했습니다.
- Pleos 시스템 알림 본문의 대비 개선이 남아 있습니다.

[유형별 테스트 결과·화면·영상](docs/EMULATOR_TEST_RESULTS.md)을 참고하세요.
영상·스크린샷·실행 로그는 로컬 `build/verification/` 산출물이므로 저장소만 복제하면 포함되지 않습니다.

### Pleos 에뮬레이터 실행

2026-11-13 공간 확보 후 공식 42dot Pleos 이미지로 전용 AVD를 구성했습니다.
기존 `Pleos_Connect_v2.1.0` AVD는 이름과 달리 Google Automotive API 35 이미지를 참조하므로
보존하되 이 프로젝트 검증에는 사용하지 않습니다.

- 사용할 AVD: `Chageun_Pleos_2_1_0`
- 시스템 이미지: `system-images/android-34/pleos_car_emulator/arm64-v8a/`
- 확인한 빌드: `RELEASE.release_connect_v2.1.0 test-keys`
- 설정: RAM 4GB, 데이터 6GB, Cold boot
- 최신 재검증(2026-11-13): `swiftshader_indirect`에서 DrivingView 네이티브 충돌과 화면 시간 초과가 반복되어 `host`로 변경했습니다. Apple M3 Pro 렌더러에서 전체 계측 12개가 통과했고 AVD 기본 설정도 host로 저장했습니다. 과거 host 종료 기록은 있었으므로 장시간 안정성까지 보증하지 않습니다.

```sh
"$HOME/Library/Android/sdk/emulator/emulator" -avd Chageun_Pleos_2_1_0 \
  -no-snapshot-load -no-snapshot-save -no-boot-anim \
  -gpu host -no-metrics -crash-report-mode never

# adb devices에서 실제 에뮬레이터 serial을 확인한 뒤 지정합니다.
adb -s emulator-5554 forward tcp:8088 tcp:8088
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user current -W -n kr.hellobiz.chageun/chageun.MainActivity
```

Virtual Console 주소는 <http://127.0.0.1:8088/>입니다. 에뮬레이터를 종료하거나 ADB 서버를
재시작하면 포트 연결이 사라질 수 있으므로 다시 설정합니다. 콘솔 자체는 이미지에 포함되어
있어 별도 서버 설치가 필요하지 않았습니다.

공식 설치·설정 근거:

- [Pleos 에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Cold boot 및 마이크 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Virtual Console 연결](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

현재 앱은 Fake 차량·내비 어댑터와 Pleos 음성 어댑터를 사용합니다. 콘솔의 값을 바꿔도 앱 경고에
자동 반영되지 않습니다. 차량·내비 실제 어댑터 구현과 추가 발화 품질 검증은 후속 작업입니다. 에뮬레이터 설치 성공은 실제 차량·마이크·내비 연동 완료를 의미하지 않습니다.

### 화면 종료 후 감시 (FR-14)

앱을 한 번 열면 포그라운드 감시 서비스가 시작됩니다. 화면을 닫아도 새 경고에 짧은 음성과 알림을 제공하고, 알림을 누르면 최신 상태의 화면으로 돌아옵니다. 감시 상태 알림에서 중지할 수 있으며 다시 앱을 열면 재개합니다. 활성화된 감시는 재부팅 후 복구를 요청합니다.

- 현재 차량 원천은 **Fake**입니다. 실제 차량 감지는 공식 SDK 어댑터 연결 후 검증해야 합니다.
- 백그라운드에서는 정차 상태여도 한 문장만 안내하며 STT·내비는 실행하지 않습니다.
- Android 실행 제한에 따라 **화면 강제 팝업은 구현하지 않습니다**. Pleos의 공식 OEM 허용 경로는 미확인입니다.
- 강제 종료/시스템 앱 중지 뒤 자동 복구, 설치 직후 첫 실행 전 감시, 시스템에 의한 무중단 실행은 보장하지 않습니다.
- 알림 거부 시 경고 알림이 보이지 않으며, 한국어 TTS가 없으면 음성도 나오지 않습니다. 이 경우에도 권한 제한을 우회하지 않습니다.
- `specialUse` 서비스는 데모 구현입니다. 실제 배포 전 Pleos 서비스 정책과 승인 조건을 확인해야 합니다.

수용 기준과 공식 정책 링크는 `docs/FEATURE_SPEC_v2.0.md`의 FR-14에 있습니다.

FR-14 검증(2026-11-13): JVM 테스트 40개, Debug/계측 APK 빌드, lint 오류 0(버전·스타일 경고 7). Pleos 서비스 테스트는 알림 허용/거부 각각 1개 통과했습니다. 작업 제거 후 서비스 유지와 화면 없는 BOOT_COMPLETED 자동 복구를 확인했습니다. 계측 테스트는 Pleos 다중 창의 동기 ActivityScenario 시작 대기를 피해 실제 RESUMED 콜백을 기다립니다. 근거는 `build/verification/background/`에 보관합니다. 한국어 TTS 미설치로 실제 음성 출력은 검증하지 못했습니다.

## 4. APK 위치 또는 Release 링크

| 항목 | 내용 |
|---|---|
| 데모 설치 APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 생성 명령 | `./gradlew --no-daemon :app:assembleDebug` |
| 앱 ID / 버전 | `kr.hellobiz.chageun` / `0.2.0-demo` |
| 배포 형태 | 로컬 Debug APK. 이 README에서는 별도 Release 다운로드 링크를 제공하지 않습니다. |

위 APK는 빌드 후 생성되는 파일이며 Git 저장소에 포함되지 않습니다. 제출 시에는 생성한
`app-debug.apk`를 별도 첨부하거나 업로드한 실제 Release 링크를 함께 제공해야 합니다.
화면 테스트용 `app-debug-androidTest.apk`는 앱 설치본이 아닙니다.

## 한국어 음성

[설정·사용 방법과 검증 범위](docs/KOREAN_TTS_SETUP.md)를 참고하세요. Pleos에서는 일반 경고 음성을 재생하며, 느린 다시 듣기는 SDK 속도 조절 미지원으로 제한 안내를 표시합니다. 기존 영상·제출 ZIP은 음성 연동 이전 자료입니다.

## 관련 문서

- [상세 기능 명세 v2.0](docs/FEATURE_SPEC_v2.0.md)
- [에뮬레이터 테스트 결과](docs/EMULATOR_TEST_RESULTS.md)
- [작업 기록](WORKLOG.md)
- [Pleos 공식 디자인 가이드](https://document.pleos.ai/en/docs/connect/guide/docs-design/Introduction/intro)

## 심사 대응 자료

- [네 가지 심사 기준별 설명·사업 가설](docs/JUDGING_BRIEF.md)
- [사용자 인터뷰·사용성 검증 질문지](docs/USER_VALIDATION.md) — 조사 미실시
- [현재 구현 기준 60초 시연 대본](docs/DEMO_SCRIPT.md) — 새 영상 촬영용
- [공식 SDK 확인 기록과 연동 과제](docs/SDK_INTEGRATION_STATUS.md)

제출 검토 묶음은 로컬 `build/submission/chageun-review.zip`에 생성합니다. APK, 기존 검증 영상·화면,
문서와 파일별 SHA-256을 함께 포함하며, 공개 Release 게시 또는 제출 완료를 의미하지 않습니다.

## 한국어 마이크 인식

[STT 연결 및 테스트 안내](docs/KOREAN_STT_SETUP.md): 공식 온디바이스 SDK 연결과 권한·취소 검증을 완료했습니다. 실제 발화의 최종 명령 인식은 아직 성공 확인 전이며 버튼 사용을 유지합니다.

### 미해결 항목 수정 및 최신 검증 (2026-11-13)

단위 테스트 50개·자동 계측 12개 통과. 사용자 실제 발화 “안내해줘”의 최종 인식 및 ACCEPT_NAVI 분류 1회 통과. 초기화/듣기 시간 분리, 호스트 GPU 전환, JNA 5.18.1로 16KB 정렬 경고 제거. 사용자 승인에 따라 Pleos 다시 듣기는 일반 속도로 실제 재생하고 제한을 표시합니다. 실제 차량·내비는 여전히 Fake입니다. [수정 보고서](docs/ISSUE_FIXES_2026-11-13.md)를 확인하세요.
