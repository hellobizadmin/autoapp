# Pleos 한국어 마이크 인식 연결

구현일: 2026-11-13. **SDK 연결·권한·취소 검증 및 사용자 실제 발화 “안내해줘”의 최종 명령 인식 1회 통과**이다. 아래 초기 실패 기록은 보존하며 최신 결과는 마지막 절을 따른다.

## 현재 연결

- 공식 `ai.pleos.playground:SpeechToText:3.3.1`, `Mode.ON_DEVICE`, `Locale.KOREA` 사용.
- Pleos 서비스가 있으면 `PleosListener`, 일반 기기에서는 기존 FakeListener를 사용한다. Pleos 데모 패널은 텍스트 인식 대체 입력을 표시하지 않는다.
- `STT_SERVICE`를 선언하고 `RECORD_AUDIO` 동의를 앱 화면에서 요청한다. 거부 시 SDK를 호출하지 않고 버튼 사용을 유지한다. 재허용은 Android 앱 권한 설정에서 가능하다.
- 정차 상세 TTS 완료 후 한 번만 최대 5초 요청한다. 중간 결과는 무시하고 `completed=true`인 최종 문장만 기존 `voice_commands_ko.json` 규칙으로 판단한다.
- 주행·UNKNOWN·백그라운드 전환, 취소, 오류, 시간 초과 시 stop·리스너 해제·release로 마이크 자원을 정리한다. 무응답과 모호한 문장은 내비 승인으로 취급하지 않는다.
- 원본 음성이나 인식 문장을 앱 파일에 저장하지 않는다. 서버 인식·앱 등록·LLM은 사용하지 않는다.

## 에뮬레이터 설정

대상은 `Chageun_Pleos_2_1_0` / Pleos Connect 2.1.0 / 사용자 10이다. Android Emulator의 공식 gRPC `setMicrophoneState(realAudioEnabled=true)`로 호스트 마이크 전달을 켜고 조회로 확인했다. Extended Controls → Microphone에서도 같은 설정을 확인할 수 있다.

사용자는 Mac 마이크 권한이 허용되어 있음을 확인했다. Mac의 출력은 EarPods, 입력은 내장 마이크였으므로 입력을 EarPods 마이크로 변경하고 시스템 설정의 선택 상태를 확인했다. 다른 마이크를 사용할 때는 Mac 사운드 입력 설정도 맞춘다.

## 실행 방법

1. 최신 Debug APK 설치 후 차근차근을 열고 마이크 권한을 허용한다.
2. 데모 패널에서 정차를 선택하고 타이어 경고를 발생시킨 뒤 패널을 닫는다.
3. 상세 안내 음성이 끝나고 듣는 중 표시가 나타나면 짧게 “안내해줘” 또는 “괜찮아”라고 말한다.
4. 새로 시험하려면 기존 경고를 해소한 뒤 다시 발생시킨다. 같은 경고 반복 입력만으로는 듣기를 다시 시작하지 않는다.
5. “다시 말해줘”의 판정 경로는 연결돼 있지만 Pleos TTS의 느린 재생은 별도 미지원이다. 실제 내비는 여전히 로그 요청이며 차량 입력은 Fake다.

## 검증 결과 및 남은 문제

- JVM 단위 테스트 40개, Debug·계측 APK 빌드 성공. lint 오류 0개·경고 11개.
- 권한 거부 시 인식 시작 없이 null 반환: 계측 1개 통과 (`build/verification/stt/denied.txt`).
- 실제 SDK 마이크 준비 후 취소 및 시간 제한: 계측 1개 통과, 재부팅 후에도 단독 재실행 통과 (`cancellation-final.txt`, `cancellation-reboot.txt`).
- 호스트 마이크 입력의 비영(0이 아닌) 신호는 관찰했지만, 실제 발화에서 최종 결과는 받지 못했다. `live-cued.txt`, `live-earpods.txt`는 실패 기록이다. 성공으로 집계하지 않는다.
- 재부팅 직후 준비 지연 및 계측 프로세스 중단이 발생했다. 합성 음성을 에뮬레이터 마이크에 주입하는 진단도 에뮬레이터 종료로 완료되지 않았다. 이것을 실제 사용자 발화 검증으로 대체하지 않는다.
- 마이크 전달 설정과 앱 실행을 복구했으며 실제 발화 인식 품질·최종 결과 수신은 추가 검증이 필요하다. 인식 실패 중에도 화면 버튼은 사용할 수 있다.

## 테스트 재현

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s emulator-5554 shell am instrument --user 10 -w -r \
  -e class chageun.PleosMicrophoneTest#microphoneStopsAndTimesOut \
  kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
# 수동 발화 필요: 안내 음성이 끝난 뒤 5초 안에 명령을 말합니다.
adb -s emulator-5554 shell am instrument --user 10 -w -r -e liveMic true \
  -e class chageun.PleosMicrophoneTest#liveMicrophoneCommand \
  kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
```

수동 발화 테스트는 `liveMic=true`인 경우에만 실행한다. 일반 자동 테스트에서 건너뛴 결과를 실제 음성 인식 통과로 계산하지 않는다. 권한 거부 테스트는 RECORD_AUDIO가 거부된 상태에서 별도로 실행한다.

## 공식 근거

- [SDK 의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk)
- [초기화·요청 순서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)
- [STT 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)
- [최종 결과 콜백](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)
- [한국어 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)
- [에뮬레이터 마이크 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)

## 후속 수정 및 실제 발화 성공 (2026-11-13)

초기화 최대 15초와 요청 후 듣기 최대 5초를 분리하고 세션의 외부 제한에도 준비 시간을 반영했다. 준비 중에는 사전의 준비 문구를, 실제 인식 준비 콜백 이후에는 듣는 중 표시를 사용한다. 주행·UNKNOWN·백그라운드 취소 정책은 유지한다. 준비 지연 뒤 승인과 준비 중 주행 취소 회귀 테스트가 통과했다.

호스트 GPU 환경에서 사용자 준비 확인 후 안내 음성 및 테스트 시작음 다음에 실제 발화를 받았다. 최종 콜백과 `ACCEPT_NAVI` 분류 확인, 계측 `OK (1 test)`, 10.164초. 증거: `build/verification/fixes-20261113/live-microphone.txt`, `speech-events.txt`. 원본 음성·인식 문장은 파일로 저장하지 않았다. 마이크 연결은 테스트 후 종료했다. 실제 차량 내비 실행은 검증하지 않았고, 한 번의 명령 인식 성공이 다양한 화자/소음 환경의 인식률을 보장하지 않는다.
