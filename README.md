# 차근차근

## 1. 앱 이름 · 한 줄 소개

**차근차근 — 차량 경고를 운전 상황에 맞춰 쉬운 한국어로 설명하는 앱.**

Pleos Connect(Android Automotive OS)용 데모 앱입니다. 기능 기준은
[상세 기능 명세](docs/FEATURE_SPEC_v2.0.md)이며, 아래 구현·검증 상태는 **2026-11-13 기준**입니다.

### 현재 구현과 연동 상태

| 기능 | 현재 상태 |
|---|---|
| 차량 경고·주행 상태 | Fake 어댑터로 시뮬레이션. 실제 차량 SDK 미연동 |
| 경고 해석 | 순수 Kotlin 규칙과 한국어 문구 사전 사용. LLM·자체 서버 없음 |
| 화면 | 차량 부위 표시와 대기·주행·정차 화면. 주행/상태 불명에서는 상세·행동 버튼 제한 |
| 한국어 음성 출력 | Pleos TextToSpeech 3.3.1 ON_DEVICE 연결·실제 재생 확인 |
| 한국어 음성 입력 | Pleos SpeechToText 3.3.1 ON_DEVICE 연결. 사용자 실제 “안내해줘” 최종 인식 및 승인 명령 분류 1회 확인 |
| 다시 듣기 | Pleos는 일반 속도 재생과 속도 제한 표시. 일반 Android TTS는 느린 재생 지원 |
| 내비게이션 | 승인 후 고정 예시 장소 요청을 로그로 기록. 실제 내비 목적지 변경 없음 |
| 화면 종료 후 감시 | 포그라운드 서비스의 한 문장 음성·알림 및 재부팅 복구 구현. 차량 입력은 Fake |

지원하는 시뮬레이션 경고는 **9종**입니다.

- 타이어 공기압 부족·급격한 저하
- 주행 가능 거리 부족·긴급 부족
- 엔진오일 압력·엔진 과열·브레이크액 부족·ABS·배터리 충전 계통 이상

추가 5종은 [FR-15 적용·검증 기록](docs/CORE_WARNINGS_PHASE_TWO.md)을 따릅니다. 문구·긴급도는 차종별 취급설명서 검증 전의 데모 초안이며, 차량 그림은 개념 표시입니다. 기타 `SAFETY_WARNING`은 비활성 상태입니다.

정차 상세 안내가 끝나면 짧게 음성 명령을 받습니다. 주행·상태 불명·백그라운드 전환 시 듣기를 중지하며, 모호한 입력이나 지연된 결과를 승인으로 처리하지 않습니다. 마이크 권한 거부·인식 실패 시에도 화면 버튼을 사용할 수 있습니다.

## 2. 실행 방법

### APK 빌드·설치

아래 테스트 환경을 준비한 후 저장소 루트에서 실행합니다.

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug

# Android SDK platform-tools를 PATH에 추가합니다.
adb devices
# emulator-5554는 실제 연결된 대상 serial로 바꿉니다.
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user current -W -n kr.hellobiz.chageun/chageun.MainActivity
```

설치 후 런처에서 **차근차근**을 선택해도 실행할 수 있습니다. 경고 알림을 사용하려면 알림 권한을, Pleos 음성 명령을 사용하려면 마이크 권한을 허용합니다.

### 데모 조작

1. Debug 앱을 엽니다. 초기 주행 상태는 UNKNOWN이며 경고가 없으면 조용히 대기합니다.
2. 오른쪽 위 `시뮬레이션 · 고정 목록 사용` 표시를 길게 눌러 데모 패널을 엽니다.
3. 주행 중을 선택하고 타이어 위치와 경고를 설정한 뒤 패널을 닫습니다. 짧은 안내가 나오며 상세·행동 버튼은 숨겨집니다.
4. 패널에서 정차 중으로 전환하고 닫으면 상세 안내와 다시 듣기 버튼이 표시됩니다. 내비 제안 대상 경고에는 안내 버튼도 표시됩니다.
5. **Pleos에서는** 상세 음성이 끝나고 실제 `듣는 중` 표시가 나오면 “안내해줘”, “다시 말해줘”, “괜찮아”처럼 짧게 말합니다. 초기화는 최대 15초, 듣기 요청은 최대 5초로 제한됩니다. Pleos 패널에는 텍스트 인식 대체 입력이 없습니다.
6. **일반 Android 기기에서는** Fake Listener용 디버그 텍스트 입력을 사용합니다. 미리 입력한 뒤 듣는 중에 제출하여 승인·반복·모호한 입력 거부를 확인합니다.
7. 거리 슬라이더를 100 → 70 → 25 → 95km로 바꾸면 부족 → 긴급 부족 → 해소를 확인할 수 있습니다. 추가 경고 5종도 패널에서 선택·발생·해소할 수 있습니다.

같은 활성 경고는 중복 안내하지 않습니다. 다시 시험하려면 경고를 해소한 뒤 발생시킵니다. 데모 패널은 Debug 전용입니다. 고정 목적지는 서울 시청 부근의 예시 좌표이며 실제 길 안내는 실행하지 않습니다.

### 화면 종료 후 감시 (FR-14)

앱을 한 번 열면 감시 서비스가 시작됩니다. 홈 이동·뒤로 종료·최근 앱 제거 이후에도 새 입력에 한 문장 음성과 경고 알림을 제공하며, 알림을 누르면 최신 상태의 화면으로 돌아옵니다. 상태 알림에서 감시를 중지할 수 있고 앱을 다시 열면 재개합니다.

- 백그라운드에서는 정차 상태여도 한 문장만 안내하며 마이크·내비를 실행하지 않습니다.
- 활성화된 감시는 재부팅 후 복구를 요청합니다. 강제 종료·시스템 앱 중지 이후 자동 복구나 무중단 실행은 보장하지 않습니다.
- 전체 화면 강제 팝업은 구현하지 않습니다. 알림 거부 시 경고 알림이 보이지 않을 수 있으며, 음성 엔진을 사용할 수 없으면 음성도 나오지 않습니다.
- 실제 차량 감지는 미연동입니다. `specialUse` 서비스의 실제 배포 승인 조건은 별도 확인이 필요합니다.

## 3. 테스트 환경 · 필요한 설정

| 항목 | 환경 및 설정 |
|---|---|
| 개발 도구 | JDK 21, Gradle Wrapper 8.13 (`./gradlew`) |
| Android SDK | compile/target SDK 35, 최소 API 28, platform-tools |
| SDK 경로 | 루트 `local.properties`에 `sdk.dir=/Users/<사용자>/Library/Android/sdk` 설정 |
| 주 검증 환경 | 공식 Pleos Connect 2.1.0, API 34, ARM64 에뮬레이터 |
| 검증 AVD | `Chageun_Pleos_2_1_0`, RAM 4GB, 데이터 6GB, Cold boot, `host` GPU |
| 보조 검증 환경 | SM-F971N / Android 17. 실차 연동 검증 아님 |
| Pleos 음성 | 내장 음성 서비스와 공식 TTS/STT SDK 3.3.1, ON_DEVICE·한국어 사용 |
| 일반 기기 음성 | 오프라인 한국어 Android TTS 필요. 인식은 Fake 텍스트 입력 |
| 마이크 | 앱 마이크 권한, 에뮬레이터 호스트 마이크 전달, macOS 입력 장치·마이크 권한 확인 |

### Pleos 에뮬레이터 실행

공식 이미지 경로는 `system-images/android-34/pleos_car_emulator/arm64-v8a/`입니다. 아래 AVD 이름은 검증에 사용한 로컬 이름이므로 다른 환경에서는 실제 생성한 이름으로 바꿉니다.

```sh
"$HOME/Library/Android/sdk/emulator/emulator" -avd Chageun_Pleos_2_1_0 \
  -no-snapshot-load -no-snapshot-save -no-boot-anim \
  -gpu host -no-metrics -crash-report-mode never

# 실행 후 별도 터미널에서 실제 serial을 확인합니다.
adb devices
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

Virtual Console은 <http://127.0.0.1:8088/>에서 확인합니다. 에뮬레이터나 ADB 서버를 재시작하면 포트 연결을 다시 설정합니다. 현재 앱의 차량·주행 상태는 Fake이므로 콘솔 값을 바꿔도 앱 경고에 자동 반영되지 않습니다.

검증 Mac에서는 `swiftshader_indirect` 사용 중 DrivingView 충돌과 빈 화면이 발생했고, `host` GPU로 변경한 뒤 전체 자동 계측이 통과했습니다. 다른 호스트 환경이나 장시간 안정성까지 검증한 결과는 아닙니다.

음성 입력은 에뮬레이터 Extended Controls → Microphone에서 호스트 마이크 전달을 켜고 macOS 입력 장치를 확인합니다. 상세 절차는 [한국어 TTS 구성](docs/KOREAN_TTS_SETUP.md)과 [STT 구성·실제 발화 결과](docs/KOREAN_STT_SETUP.md)를 참고하세요. 각 문서의 초기 실패 기록과 마지막 후속 검증 결과를 구분합니다.

### 테스트 실행과 최신 결과

```sh
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# 대상 기기 연결 후 화면·서비스 계측 실행
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

코어·세션 단위 테스트는 루트 JVM 프로젝트에서 실행합니다. 코어 소스는 `app/src/main/java/chageun`, Android 전용 소스는 `app/src/main/kotlin/chageun`이며 Android 앱 모듈은 `:app`입니다.

| 검증 | 최신 기록 (2026-11-13) |
|---|---|
| JVM 단위 테스트 | **50개 통과**: 코어 19개·세션 31개. APK 생성 시 재실행 통과 |
| 빌드 | Debug·계측 APK 성공. 최신 설치용 Debug APK 서명 검증 성공 |
| 자동 Android 계측 | 환경·권한 조건을 나누어 실행한 **12개 통과** |
| 실제 사용자 발화 | “안내해줘” 최종 인식·`ACCEPT_NAVI` 분류 **1회 통과**. 실제 내비 실행 검증은 아님 |
| Android lint | 오류 0개·경고 8개. JNA 5.18.1 적용 후 기존 16KB 정렬 경고 3건 제거 |
| 백그라운드 감시 | 알림 허용·거부, 화면 종료 후 서비스 유지, 실제 재부팅 후 복구 확인 |

계측 12개 결과는 권한 허용·거부 등 조건별 실행을 합산한 기록입니다. 위 `connectedDebugAndroidTest` 한 번으로 동일한 조건을 모두 재현했다는 뜻은 아닙니다. 수동 발화 테스트는 별도 `liveMic=true` 설정과 실제 발화가 필요하며 자동 실행에서 건너뛴 결과를 성공으로 계산하지 않습니다.

최신 근거는 [미해결 항목 수정 및 재검증](docs/ISSUE_FIXES_2026-11-13.md)에 있습니다. 이전 [화면 검증 기록](docs/EMULATOR_TEST_RESULTS.md)과 [오류 점검 기록](docs/ERROR_AUDIT_2026-11-13.md)은 당시 상태를 보존합니다. 상세 로그·캡처는 로컬 `build/verification/`에 있으며 저장소 복제에 포함되지 않습니다.

실제 차량·주행 상태·내비 SDK 연결, 다양한 화자·소음 조건의 인식 품질, 장시간 안정성, 16KB 페이지 기기의 전체 실행은 추가 검증 대상입니다.

## 4. APK · 시연 영상

| 항목 | 위치 및 상태 |
|---|---|
| 제출용 Release APK | [송우진_차근차근_release.apk](apk/송우진_차근차근_release.apk) |
| 개발·시뮬레이션용 Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 생성 명령 | `./gradlew --no-daemon :app:assembleDebug` |
| 앱 ID / 버전 | `kr.hellobiz.chageun` / `0.2.0-demo` (versionCode 1) |
| 배포 형태 | 제출용 키로 서명한 Release APK를 저장소 `apk/`에서 제공 |
| 최신 시연 영상 | [실제 앱 화면·한국어 TTS 시연](deliverables/chageun-app-demo-20261113.mp4) — 약 64초 |
| 영상 미리보기 | [대표 화면](deliverables/chageun-app-demo-preview.png) |

제출 파일명은 `송우진_차근차근_release.apk`입니다. `apk/`의 제출본은 Git 등록 대상이며 `app/build/`의 중간 산출물은 제외합니다. `app-debug-androidTest.apk`는 계측용입니다.

Release는 `BuildConfig.DEBUG=false`이며 명세에 따라 시뮬레이션 제어 패널이 숨겨집니다. 실제 차량 입력은 아직 Fake이므로 심사자가 경고를 직접 발생시키는 조작은 Debug APK에서 가능하며, 제출용 Release와 함께 시연 영상을 참고해야 합니다. Release는 기존 Debug와 서명이 달라 덮어쓰기 설치할 수 없습니다. 기존 앱 제거 시 앱 데이터가 삭제되므로 설치 대상을 확인하세요.

제출용 개인 키와 비밀번호는 로컬 `.signing/`에만 보관하고 Git에서 제외합니다. 같은 앱의 후속 업데이트에 필요하므로 별도로 안전하게 백업해야 합니다. 현재 체크아웃에서 동일 서명으로 재생성하는 명령은 다음과 같습니다.

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleRelease
# SDK 경로·build-tools 버전은 설치 환경에 맞춥니다.
"$HOME/Library/Android/sdk/build-tools/35.0.0/apksigner" sign \
  --ks .signing/chageun-release.p12 --ks-key-alias chageun-release \
  --ks-pass file:.signing/release-password.txt \
  --out "apk/송우진_차근차근_release.apk" \
  app/build/outputs/apk/release/app-release-unsigned.apk
"$HOME/Library/Android/sdk/build-tools/35.0.0/apksigner" verify \
  "apk/송우진_차근차근_release.apk"
```

최신 영상은 실제 앱 화면·TTS·버튼 조작을 담고 있으며 차량·주행·내비는 시뮬레이션으로 표시합니다. 사용자 실제 발화는 이 영상에 포함되지 않습니다. 이전 `build/submission/chageun-review.zip`과 기존 99초 영상은 후속 음성 연동·수정 전 자료이므로 최신 제출본으로 사용하지 않습니다. 새 제출 패키지 구성과 공모전 제출은 별도 작업입니다.

## 관련 문서

- [상세 기능 명세](docs/FEATURE_SPEC_v2.0.md)
- [최신 수정·검증 결과](docs/ISSUE_FIXES_2026-11-13.md)
- [공식 SDK 확인 기록·남은 연동 과제](docs/SDK_INTEGRATION_STATUS.md)
- [심사 대응 설명서·사업 가설](docs/JUDGING_BRIEF.md)
- [사용자 인터뷰·사용성 검증 계획](docs/USER_VALIDATION.md) — 조사 미실시
- [시연 대본](docs/DEMO_SCRIPT.md)
- [작업 기록](WORKLOG.md)
- [Pleos 공식 UI Design Guide](https://document.pleos.ai/en/docs/connect/guide/docs-design/Introduction/intro)
