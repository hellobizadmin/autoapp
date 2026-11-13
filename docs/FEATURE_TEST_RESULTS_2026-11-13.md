# 기능별 테스트 결과

실행: 2026-11-13 14:24~14:31 KST. 대상: 현재 작업 트리, Pleos API 34 에뮬레이터 `emulator-5554`, 사용자 10. 연결된 실제 휴대폰에는 작업하지 않았다. 기준은 `FEATURE_SPEC.md` 및 후속 `FEATURE_SPEC_v2.0.md`의 FR-01~14이다.

## 결과 요약

- JVM 단위 테스트 **40개 통과**, 실패·건너뜀 0. `--rerun-tasks`로 다시 실행했다.
- Debug 앱·계측 APK 빌드 성공. lint 오류 0, 경고 11(버전 관련 5, 16KB 정렬 3, UseKtx 3).
- 끝까지 실행된 계측 묶음은 **7개 중 5개 통과, 2개 실패**: 화면 2/3, 서비스·TTS·마이크 3/4.
- 실제 앱 walkthrough는 **13개 중 10개 체크포인트 통과 후 연결 종료**. 최종 성공 결과가 없으므로 전체 통과가 아니다.
- 차량 이벤트·주행 상태·내비는 Fake다. TTS는 실제 Pleos SDK 콜백을 검증했다. 실제 차량·목적지 변경·사용자의 실제 발화 인식은 이번 실행으로 입증하지 않았다.

## 요구사항별 결과

| 요구사항 | 이번 검증 | 결과와 한계 |
|---|---|---|
| FR-01 시작·지원 확인 | 사전 파싱, 잘못된 JSON 거부, 입력 원천 시작 실패의 오류 상태, 지원 목록 로그 | 로직 통과. 손상된 asset을 설치한 실제 오류 화면은 미검증 |
| FR-02 조용한 대기 | 가상 시간 10분 TTS 0회, 실제 대기 화면 | 통과. 실제 시간 10분 관찰과는 다름 |
| FR-03 경고·긴급도 | 100→70→25→95, 임계값·히스테리시스, 급격한 타이어 저하 | 로직 및 실제 앱 체크포인트 통과. 입력은 Fake |
| FR-04 주행 안내 | 상세·버튼 숨김, drive 안내, 중복 입력 | 로직·Compose·실제 앱 체크포인트 통과 |
| FR-05 정차 상세 | 정차 전환, 복수 경고 우선순위, 한국어 TTS 시작·완료 | 통과. SDK 완료 콜백 확인이며 이번 사용자 청취 확인은 아님 |
| FR-06 상태 불명 | 상세·버튼·듣기·내비 차단, 뒤늦은 인식 무시 | 로직·Compose·실제 앱 체크포인트 통과 |
| FR-07 승인 후 안내 | 승인 전 0회, 버튼 승인 후 Fake 내비 1회, NONE 버튼 숨김, 실패 응답 | 로직·실제 앱 클릭 통과. 별도 Compose 버튼 테스트는 재실행에서 실패하여 안정성 미충족. 실제 내비 연결은 미검증 |
| FR-08 다시 듣기 | 버튼·음성 입력당 slow 요청 1회 | 순수 로직 통과. **Pleos 느린 재생 미지원**이며 실제 어댑터가 제한 오류를 반환함을 확인 |
| FR-09 미지원 경고 | 미지원 입력 무시 | JVM 통과. walkthrough 13번은 연결 종료로 미실행 |
| FR-10 중복 억제 | 동일 경고 5회→음성 1회, 해소 후 재발생 | JVM 통과. 실제 앱 중복 입력 화면 확인 |
| FR-11 해소 | 거리 95km 해소, 다음 경고 선택, Idle 복귀 | 로직·실제 앱 체크포인트 통과 |
| FR-12 데모 패널 | DEBUG 조건, 상태·타이어·거리 제어 구현 소스 확인 | 부분 확인. 이번 실행에서는 패널 직접 조작·Release APK 숨김 계측 미검증 |
| FR-13 음성 응답 | Fake 음성 승인·재생·모호한 입력·권한 거부·상태 전환 경합 | JVM 통과. **실제 마이크 준비/취소 테스트는 5초 시간 초과로 실패**. 실제 발화 인식 미검증 |
| FR-14 백그라운드 | 액티비티 종료 후 서비스·경고 알림 유지, 동일 세션 복귀, 중지 후 부팅 수신 무시 | JVM·서비스 계측 통과. 알림 허용 상태. 실제 재부팅 복구·권한 거부 조건은 이번에 미검증 |

## 실패·중단 상세

1. `PleosMicrophoneTest.microphoneStopsAndTimesOut`: `TimeoutCancellationException: Timed out waiting for 5000 ms`. 시작 21:29:09.845, microphone ready 로그 21:29:15.021(기기 시각)로 준비가 약 5.18초 뒤 기록됐다. 초기화 지연과 테스트 제한 시간 경합 가능성이 있으나 정확한 원인은 확정하지 않았다. 취소·재시도 검증까지 도달하지 못했다.
2. `WarningScreensTest.parkedDetailButtonsDispatchOneAction`: 최종 묶음에서 `No compose hierarchies found in the app`, 64행 `performScrollTo` 실패. 앞선 부분 실행에서는 같은 테스트가 통과했다. 실제 앱 walkthrough의 안내 버튼 클릭도 통과했지만, 이를 근거로 계측 실패를 삭제하거나 안정성 통과로 처리하지 않는다.
3. 초기 두 실행은 `start instr`에 의한 대상 앱 프로세스 종료로 중단됐다. 당시 별도 `liveMicrophoneCommand` 계측 프로세스가 확인됐다. 요청 주체는 확인되지 않았다. 사용자는 다른 테스트를 실행하지 않았다고 응답했다.
4. walkthrough는 01~10까지 성공 상태를 출력한 뒤 adb가 종료 코드 255로 끝났다. 이후 기기 목록에서 에뮬레이터가 사라졌다. 11~13 및 최종 결과는 미확인이다. 에뮬레이터 종료 원인은 확정하지 않았다.

앱 구현·테스트 소스 수정 없이 검증했다. 실패를 통과시키기 위한 타임아웃 변경이나 SDK 교체는 수행하지 않았다.

## 증거 및 재현

- [빌드 로그](../build/verification/feature-tests/build.log)
- [코어 13개](../build/verification/feature-tests/TEST-chageun.core.WarningCoordinatorTest.xml), [세션 27개](../build/verification/feature-tests/TEST-chageun.runtime.WarningSessionTest.xml)
- [최종 화면 계측](../build/verification/feature-tests/screens-final.txt)
- [서비스·TTS·마이크 계측](../build/verification/feature-tests/service-speech-mic.txt), [음성 로그](../build/verification/feature-tests/speech-log.txt)
- [실제 앱 10개 체크포인트](../build/verification/feature-tests/walkthrough.txt)
- [초기 중단 근거](../build/verification/feature-tests/test-process-log.txt), [lint 결과](../build/verification/feature-tests/lint-results-debug.xml)

검증 APK SHA-256: `382cd36eaead917592b6973f63609d2f1aea648b86952b46b92039603a5dbfdf`.

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --rerun-tasks
adb -s emulator-5554 shell am instrument --user current -w -r -e timeout_msec 120000 -e class chageun.WarningScreensTest kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument --user current -w -r -e timeout_msec 120000 -e class chageun.WarningMonitorServiceTest,chageun.PleosSpeechProbeTest,chageun.PleosMicrophoneTest#microphoneStopsAndTimesOut kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument --user current -w -r -e timeout_msec 120000 -e class chageun.WarningWalkthroughTest kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
```

계측 명령은 동시에 실행하지 않는다. 이번 테스트의 마이크·알림 권한은 사용자 10에서 허용 상태였다. 결과 로그는 빌드 디렉터리에 보존했으며 저장소 커밋·배포는 수행하지 않았다.

## 후속 테스트 영상 촬영 (2026-11-13)

[자막 포함 테스트 영상 — 1분 26초](../build/verification/test-video/chageun-feature-test-20261113.mp4) · [녹화 원본](../build/verification/test-video/raw.mp4) · [계측 결과](../build/verification/test-video/instrumentation.txt)

기존 Pleos AVD를 다시 실행하고 최신 Debug/계측 APK를 설치해 13개 체크포인트 시나리오를 촬영했다. `OK (1 test)`, 75.064초로 전체 시나리오 통과를 확인했다. 이전에 중단된 11~13번도 이번 실행에서는 통과했다. 앞선 Compose·마이크 계측 실패는 이번 촬영으로 해결된 것으로 보지 않는다.

영상은 1280×880, H.264 MP4, 약 86.25초이며 무음이다. 원본 화면에 상·하단 자막과 마지막 검증 범위 설명을 추가했고 원래 화면 영역은 가리지 않았다. 차량·주행·내비 시뮬레이션 표시를 유지했다. 실제 TTS·발화 인식·실차·내비 연동의 증거 영상이 아니다. 정차·주행·거리·알림·마지막 설명 프레임을 확인하고 전체 디코딩 오류 0을 확인했다. 필수 단위 테스트와 APK 빌드는 성공했으며 이번 호출은 UP-TO-DATE였다.
