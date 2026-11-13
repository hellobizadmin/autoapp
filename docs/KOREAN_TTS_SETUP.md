# Pleos 에뮬레이터 한국어 음성 구성

구성일: 2026-11-13. 대상: `Chageun_Pleos_2_1_0`, Pleos Connect 2.1.0 / API 34 / ARM64, 사용자 10.

## 구성 방식

별도 비공식 TTS APK나 Google 계정을 설치하지 않고, 이미지에 포함된 `ai.pleos.playground.caas` 음성 서비스(4.2.10-coral-aosp)를 공식 TextToSpeech SDK 3.3.1로 사용한다. 일반 Android TTS 설정에 기본 엔진이 없다는 사실만으로 Pleos 음성 엔진도 없다고 판단해서는 안 된다.

- `Mode.ON_DEVICE`와 `Locale.KOREA`를 명시한다. 서버 모드·서버 앱 등록은 사용하지 않는다.
- `pleos.car.permission.TTS_SERVICE` 권한과 서비스 패키지 조회를 선언한다.
- SDK 초기화 완료 후 이벤트 리스너를 등록하고 음성을 요청한다.
- 실제 재생 완료 콜백이 와야 성공 처리한다. 상태 변경 시 재생을 중단하며, 중단 콜백을 정리한 뒤 다음 요청을 실행한다.
- Pleos 서비스가 없는 일반 기기는 기존 Android 오프라인 한국어 TTS를 사용한다.
- 문구는 기존 `warning_dictionary_ko.json`에서 가져온다. 차량·주행·마이크·내비 입력은 이번 음성 연결로 바뀌지 않는다.

## 사용 방법

최신 Debug APK를 설치한 뒤 차근차근을 연다. 오른쪽 위 시뮬레이션 표시를 길게 눌러 데모 패널에서 주행 중과 타이어 경고를 선택한다. 패널을 닫으면 짧은 한국어 안내를 확인할 수 있다. 정차로 전환하면 상세 안내를 재생한다. 같은 활성 경고를 반복 주입하면 중복 안내하지 않으므로 다시 시험하려면 경고를 해소한 뒤 발생시킨다.

Mac 출력 장치의 음소거를 해제하고 에뮬레이터 음량을 확인한다. 2026-11-13 사용자로부터 실제 한국어 안내가 들린다는 응답을 받았다. 엔진의 시작·음량·완료 콜백도 확인했다.

## 검증 결과

- JVM 단위 테스트 40개 통과, Debug·계측 APK 빌드 성공.
- 한국어 SDK 단독 재생 테스트 1개 통과(6.394초).
- 앱 런타임 경로의 백그라운드 경고 재생·취소 후 재생·느린 재생 제한 테스트 1개 통과(12.203초, 단독 실행).
- 사용자 실제 청취 확인. 초기 일괄 실행은 프로세스 중단으로 실패하여 단독 실행으로 검증했다.
- lint 오류 0개·경고 10개. 이전 7개에 SDK 전이 의존성의 16KB 정렬 경고 3건 추가.

## 제약

- SDK 3.3.1의 공개 API에는 재생 속도 설정이 없다. 2026-11-13 사용자 승인에 따라 Pleos의 다시 듣기는 일반 속도로 재생하고 속도 제한을 표시한다. 일반 Android TTS의 기존 느린 재생은 유지한다.
- 후속 작업에서 Pleos STT SDK를 연결했다. 실제 발화의 최종 명령 인식은 검증 중이며 [STT 구성](KOREAN_STT_SETUP.md)에 상태를 기록한다.
- 기존 99초 시연 영상과 제출 검토 ZIP은 이 음성 연결 이전 산출물이다. 최신 음성이 반영된 영상·제출 패키지로 간주하지 않는다.
- lint에 SDK 전이 의존성 JNA의 x86_64 네이티브 라이브러리 16KB 정렬 경고가 추가된다. 현재 ARM64/API 34 에뮬레이터 재생 검증과 향후 배포 호환성 검증을 구분한다.

## 재현 명령

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument --user 10 -w -r \
  -e class chageun.PleosSpeechProbeTest#koreanOnDevicePlayback \
  kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument --user 10 -w -r \
  -e class chageun.PleosSpeechProbeTest#runtimeUsesKoreanSpeechAndStopsSafely \
  kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
```

serial·사용자 번호는 실제 기기에 맞춘다. 증거 파일은 `build/verification/tts/`에 보관한다. 검사 완료 상태는 작업 기록과 최종 실행 로그를 함께 확인한다.

## 공식 근거

- [SDK 의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk)
- [온디바이스 모드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [초기화·종료](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release)
- [한국어 Locale](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale)
- [재생 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)

## 최신 회귀 결과 (2026-11-13)

일반 속도 다시 듣기 실제 재생 완료를 포함한 TTS 계측 2개 통과. JNA 5.18.1 Android AAR로 전이 의존성을 올려 기존 16KB 정렬 경고 3건이 제거됐다. lint 오류 0개·경고 8개. 실제 16KB 페이지 기기 전체 검증과는 구분한다.
