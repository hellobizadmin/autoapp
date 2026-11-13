# 핵심 경고 2차 적용 — FR-15

적용일: 2026-11-13. 기존 타이어·주행거리 4종에 5종을 더해 **9종의 시뮬레이션 경고**를 지원한다. 일반 `SAFETY_WARNING`은 여전히 비활성 상태다.

| 앱 내부 경고 ID | 의미 | 긴급도 | 내비 제안 |
|---|---|---|---|
| ENGINE_OIL_PRESSURE | 엔진오일 압력 경고 | 지금 바로 | 없음 |
| ENGINE_OVERHEAT | 엔진 과열 경고 | 지금 바로 | 없음 |
| BRAKE_FLUID_LOW | 브레이크액 부족 경고 | 지금 바로 | 없음 |
| ABS_WARNING | 급제동 방지 장치 경고 | 오늘 안에 | 없음 |
| BATTERY_CHARGING | 배터리 충전 계통 경고 | 지금 바로 | 없음 |

## 동작

- 디버그 데모 패널에서 신규 경고를 선택한 뒤 발생·해소할 수 있다. 한국어 문구와 라벨은 앱 assets의 경고 사전에서 읽는다.
- 주행·상태 불명에서는 한 문장만 안내하고, 정차 시 상세와 다시 듣기만 제공한다. 신규 경고에는 길 안내를 제안하지 않는다.
- 긴급도 우선 순서를 유지하면서 같은 긴급도 내 정차 필요 경고와 급격한 타이어 저하를 주행거리 부족보다 먼저 표시한다. 사전의 `priority`는 작은 값이 우선이며 같은 값이면 발생 순서다.
- 중복 경고 5회는 한 번만 안내한다. 명시적 해소 후 재발생은 다시 안내한다.
- 주유소 안내 승인 대기 중 엔진오일 경고가 도착하면 이전 음성 승인을 폐기한다. 백그라운드에서도 한 문장만 안내하고 듣기·내비는 실행하지 않는다.

## 검증

- JVM 단위 테스트 **48개 통과**: 기존 코어 13개, 경고 확대 6개, 런타임 29개. 신규 검증에는 5종 × DRIVING/UNKNOWN, PARKED 전환, 발생 순서 양방향 우선순위, 잘못된 priority, 백그라운드·승인 경합이 포함된다.
- Debug 앱·계측 APK 빌드 성공. lint 오류 0, 경고 11.
- 독립 Pleos API 34 에뮬레이터에서 **계측 2개 통과(70.379초)**: 실제 앱의 데모 패널에서 5종 선택·발생·해소, 5종의 주행/정차/상태 불명/해소 및 거리 경고보다 우선 표시되는 흐름.
- 최종 쉬운 한국어 라벨 반영 후 5종 화면 시나리오도 재실행 통과(47.375초). [최종 로그](../build/verification/phase-two/final-scenarios.txt).
- [계측 로그](../build/verification/phase-two/isolated-instrumentation.txt), [화면 모음](../build/verification/phase-two/screens-overview.png).

초기 공유 에뮬레이터 실행은 Compose 화면 트리 미발견·백그라운드 전환에 따른 대기 초과 및 프로세스 종료로 실패했다. 별도 화면 미리보기 실행도 관찰됐다. 데모 패널 테스트를 실제 MainActivity에 연결하고, 동일 공식 Pleos 시스템 이미지에 별도 임시 데이터 폴더를 둔 `emulator-5556`에서 검증했다. 기존 에뮬레이터의 데이터나 다른 작업의 소스는 되돌리지 않았다. 초기 실패 로그도 `build/verification/phase-two/`에 보존한다.

## 범위와 남은 일

- 실제 Vehicle SDK 어댑터·권한·경고 점등/점멸 의미·실차 수신은 이번 범위에 포함하지 않았다. 앱 내부 ID를 실제 SDK 상수로 간주하면 안 된다.
- 문구와 긴급도는 **차종별 취급설명서 검증 전의 데모 초안**이다. 실제 운전자용 출시 전 차량별 조건과 조치를 검토해야 한다.
- 엔진오일 압력과 오일량 부족, 충전 계통 경고와 전기차 잔량 부족, 주차 브레이크 점등과 브레이크액 부족은 서로 다르다. 이를 임의로 같은 입력으로 매핑하지 않는다.
- 기존 음성 인식 최종 발화 검증·Pleos 느린 재생 제한은 별도 과제로 유지한다. 이번 화면 테스트 통과가 이 기능들의 완료를 뜻하지 않는다.

## 실행

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# 앱과 계측 APK를 설치한 테스트용 Pleos에서 실행한다.
# 알림·마이크 권한을 설정하고, 동일 기기의 계측은 순차 실행한다.
adb -s emulator-5556 shell am instrument --user current -w -r -e timeout_msec 120000 -e class chageun.CoreWarningDemoPanelTest,chageun.WarningWalkthroughTest#phaseTwoCoreWarningScenarios kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
```
