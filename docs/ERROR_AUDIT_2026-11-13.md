# 전체 테스트 및 미해결 오류 점검 — 2026-11-13

> 후속 수정 완료: [수정·재검증 결과](ISSUE_FIXES_2026-11-13.md). 아래는 수정 전 점검 기록입니다.

## 결론
전체 자동 테스트를 실행했다. JVM 48개 통과. Android 계측 12회는 최초 9회 통과/3회 실패였고, 테스트 코드 오류 2개를 수정한 뒤 해당 화면 테스트 3개 재실행이 모두 통과하여 최종 항목별 결과는 11회 통과/1회 미해결이다. 동일 테스트의 알림 권한 허용/거부 실행은 별도 케이스로 센다. 사용자 실제 발화 테스트는 이번 실행에서 재실시하지 않았으며 앞선 최종 인식 실패가 미해결이다. 전체 기능 정상 판정은 불가하다.

## 실행 및 증거
- `./gradlew --no-daemon testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --rerun-tasks`: 성공, 82개 작업 실행. 단위 테스트 48개(6+13+29), 실패 0.
- 테스트 코드 수정 후 `testDebugUnitTest :app:assembleDebugAndroidTest` 재실행 성공.
- lint: 오류 0개, 경고 11개. 16KB 정렬 경고 3건은 같은 JNA x86_64 라이브러리에 대해 보고됨. 기타 target API/의존성 버전/SharedPreferences KTX 경고.
- 에뮬레이터: Pleos 2.1.0, emulator-5554, 사용자 10. 차량/주행/내비 입력은 Fake, 음성은 실제 Pleos 서비스.
- 별도 emulator-5556 비교 실행은 QEMU 스레드 멈춤 뒤 호스트 프로세스 종료 코드 139로 중단. 이후 기존 인스턴스 하나만 사용해 전체 자동 테스트 완료. 이 중단은 앱 테스트 통과/실패 개수에 포함하지 않음.
- 실행 로그: `build/verification/error-audit-20261113/`. 최초 결과 `main/suite-results.json`, 수정 후 `screens-after-fix.txt`. 이전 결과를 덮어쓰지 않음.
- 동시 UI 작업이 있는 저장소이며 이번 결과는 설치한 APK 기준이다. APK 해시는 `apk-sha256.txt`. 다른 작업자의 제품 UI 변경은 수정하지 않음.

## 항목별 결과
| 항목 | 개수 | 결과 |
|---|---:|---|
| 화면 상태 제한·버튼 클릭·내비 버튼 숨김 | 3 | 수정 후 통과 |
| 5종 데모 패널 선택·발생·해소 | 1 | 통과 |
| 기존 경고 13개 체크포인트 전체 흐름 | 1 | 실패: 정상 대기/주행 경고 이후 5초 시간 초과 |
| 추가 경고 5종 및 우선순위 화면 | 1 | 통과 |
| 백그라운드 감시: 알림 허용/거부 | 2 | 모두 통과 |
| 실제 한국어 TTS 재생/중단/재시도 | 2 | 통과 |
| 마이크 취소·시간 제한 | 1 | 통과 |
| 마이크 권한 거부 | 1 | 통과 |

추가 수동 시스템 검증: 감시 활성화 설정을 확인하고 실제 adb reboot 수행. 사용자 10의 부팅 후 앱을 수동 실행하지 않고 `WarningMonitorService`의 `isForeground=true` 확인. 부팅 초기에는 서비스가 없었으나 이후 자동 시작됨. 증거: `boot-enabled-before.txt`, `after-reboot-service.txt`, `after-reboot-service-final.txt`, `boot-final-logcat.txt`. 실제 차량 경고 재조회나 무중단 복구까지 검증한 것은 아님.

## 확인하고 수정한 테스트 오류
최근 정차 버튼이 하단 고정 배치로 변경됐는데 `WarningScreensTest`는 버튼에 `performScrollTo()`를 호출했다. 실제 실패는 `Semantic Node has no parent layout with a Scroll SemanticsAction`이었다. 해당 호출을 `assertIsDisplayed()` 후 클릭으로 수정했다. 내비 호출 전 0회/클릭 후 1회, 다시 듣기 1회, 주행·UNKNOWN 버튼 숨김 검증은 유지. 제품 코드는 변경하지 않음. 수정 후 화면 테스트 3개 통과(17.825초).

## 미해결 사항
1. **기본 경고 전체 흐름 시간 초과**: `main/03-basic-walkthrough.txt`. 대기와 주행 경고 체크포인트 후 정차 단계에서 5초 대기 실패. 전체 승인·거리·복귀 흐름을 이번 실행의 통과로 볼 수 없음. 어떤 대기 조건이 실패했는지 현재 스택만으로 확정되지 않아 추가 계측 필요.
2. **Pleos 시스템/에뮬레이터 불안정**: 기존 인스턴스에서도 DrivingView의 `libUnreal.so` 네이티브 충돌 및 강제 액티비티 종료가 반복. `main/runtime-logcat.txt`. 격리 인스턴스는 QEMU 스레드 hang 뒤 exit 139. `isolated-emulator.log`. 화면 실패와 같은 기간에 발생했으나 모든 앱 시간 초과의 직접 원인으로 확정하지 않음.
3. **실제 발화 최종 결과 미반환**: 앞선 사용자 참여 테스트에서 마이크 신호 8회는 감지됐지만 최종 문자열 미반환. `build/verification/full-emulator-20261113/live-microphone.txt`, `live-microphone-signals.txt`. 이번에는 마이크 제어 자동 테스트만 재검증. 코드상 PleosListener 초기화·요청·최종 결과가 같은 5초 제한을 공유하고 WarningSession에도 5초 제한이 있어 실제 듣기 시간이 줄어들 수 있음. 원인 후보이며 아직 수정·실제 발화 재검증하지 않음.
4. **느린 다시 듣기 미충족**: PleosTtsSpeaker는 slow=true에서 제한 상태를 표시하고 UnsupportedOperationException을 발생시킴. 일반 음성 출력 통과가 FR-08 충족을 뜻하지 않음.
5. **16KB 페이지 크기 호환성 경고**: JNA 5.13.0의 x86_64/libjnidispatch.so 정렬 경고. 현재 ARM64 에뮬레이터 충돌의 원인으로 확인된 것은 아니며 별도 호환성 조치·검증 필요.

## 범위 밖 또는 미검증
실제 Vehicle/DriveState SDK, 실제 내비 목적지 설정은 아직 Fake. 신규 경고 문구의 차종별 인증, 10분 무경고 실시간 대기, 경고 수신 후 1초 내 음성 시작의 정량 측정, 실차 테스트는 이번 통과 판정에 포함하지 않는다. 사용자의 실발화 재참여 없이 실제 음성 인식 성공을 주장하지 않는다.
