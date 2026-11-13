# Pleos 에뮬레이터 유형별 화면 테스트

실행일: 2026-11-13. 대상: `Chageun_Pleos_2_1_0`, API 34, 차량 사용자 10.

[전체 녹화 영상(약 1분 39초)](../build/verification/walkthrough/chageun-walkthrough.mp4) · [최종 실행 로그](../build/verification/walkthrough/instrumentation-final.txt)

13개 체크포인트를 포함한 계측 시나리오 1개 통과(최종 76.585초). 앞선 녹화 실행도 통과(86.899초). 별도로 JVM 단위 테스트 40개 통과, Debug/계측 APK 빌드 및 lint 오류 0개(경고 7개).

차량 이벤트·주행 상태·내비는 **Fake 시뮬레이션**입니다. 왼쪽 Pleos 계기판의 P/500km와 앱 내부 시뮬레이션 입력은 연결돼 있지 않습니다. 영상은 첫 실행, 개별 캡처는 저장 경로를 수정한 재실행에서 확보했습니다. 화면 번호 안내용 Toast는 Pleos에서 보이지 않는 장면이 있으므로 아래 순서와 화면 링크로 구분합니다.

| 순서 | 유형 | 요구사항 | 검증 내용 | 화면 |
|---|---|---|---|---|
| 01 | 정상 대기 | FR-02 | 활성 경고 없음 | [열기](../build/verification/walkthrough/01_idle.png) |
| 02 | 주행·중복 입력 | FR-04·10 | 동일 경고 5회 입력, 한 줄 화면·상세 버튼 없음. 음성 중복 횟수는 JVM 테스트에서 검증 | [열기](../build/verification/walkthrough/02_driving.png) |
| 03 | 정차 전환 | FR-05 | 상세와 안내/다시 듣기 버튼 표시 | [열기](../build/verification/walkthrough/03_parked.png) |
| 04 | 안내 승인 | FR-07 | 실제 화면 버튼 클릭 전 내비 요청 0회, 클릭 후 Fake 요청 1회 | [열기](../build/verification/walkthrough/04_approval.png) |
| 05 | 상태 불명 | FR-06 | 상세와 버튼 숨김, 듣기 없음 | [열기](../build/verification/walkthrough/05_unknown.png) |
| 06 | 거리 70km | FR-03 | RANGE_LOW 표시 | [열기](../build/verification/walkthrough/06_range_low.png) |
| 07 | 거리 25km | FR-03 | RANGE_CRITICAL로 대체, 지금 바로 표시 | [열기](../build/verification/walkthrough/07_range_critical.png) |
| 08 | 거리 95km | FR-11 | 경고 해소 후 대기 화면 | [열기](../build/verification/walkthrough/08_clear.png) |
| 09 | 급격한 타이어 저하 | FR-03·07 | 긴급 경고, 내비 버튼 없음 | [열기](../build/verification/walkthrough/09_rapid.png) |
| 10 | 복수 경고 우선순위 | FR-05 | 타이어 저압보다 긴급 거리 경고 우선 | [열기](../build/verification/walkthrough/10_priority.png) |
| 11 | 화면 종료 후 알림 | FR-14 | 액티비티 종료 후 서비스 유지·경고 게시·알림 패널 표시 | [열기](../build/verification/walkthrough/11_background.png) |
| 12 | 앱 복귀 | FR-14 | 같은 세션의 상세 복원, 내비 자동 추가 요청 없음 | [열기](../build/verification/walkthrough/12_return.png) |
| 13 | 미지원 경고 | FR-09 | SAFETY_WARNING 무시, 대기 화면 유지 | [열기](../build/verification/walkthrough/13_unsupported.png) |

## 관찰된 제약 및 개선 항목

- 한국어 TTS를 사용할 수 없어 화면에 오류 대체 안내가 표시됩니다. 실제 음성 출력·마이크 STT·실제 내비 이동은 검증하지 않았습니다. FR-13은 기존 JVM의 가상 음성 입력 테스트 범위입니다.
- 백그라운드 알림은 실제 게시됐고 시스템 알림 패널에서 확인했습니다. 다만 Pleos 알림 패널의 본문이 어두운 배경에 낮은 대비로 표시돼 가독성 보정 및 플랫폼 스타일 확인이 필요합니다. 알림 게시 성공과 시각 품질 통과를 구분합니다.
- 재부팅 자동 복구와 알림 권한 거부는 앞선 FR-14 검증 결과이며 이번 13개 화면 시연에는 포함하지 않았습니다.
- 이번 결과는 고정된 에뮬레이터 화면 환경에서의 검증입니다. 실차·다양한 해상도·장시간 안정성을 대신하지 않습니다.

## 다시 실행하기

에뮬레이터를 실행한 상태에서 저장소 루트에서 수행합니다. 알림 허용은 테스트용 에뮬레이터에서만 설정합니다.

```sh
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell pm grant --user current kr.hellobiz.chageun android.permission.POST_NOTIFICATIONS
adb -s emulator-5554 shell am instrument --user current -w -r -e timeout_msec 120000 -e class chageun.WarningWalkthroughTest kr.hellobiz.chageun.test/androidx.test.runner.AndroidJUnitRunner
```

각 화면을 약 4초 이상 표시합니다. 실제 안내 버튼은 접근성 클릭으로 누르고 결과를 검증합니다. 실행이 끝나면 테스트가 감시 서비스와 화면을 정리하므로 평소 사용을 계속하려면 앱을 다시 엽니다.

캡처는 앱 내부 `files/walkthrough/`에 저장됩니다. `adb shell am get-current-user`로 사용자 번호를 확인한 후 아래처럼 내보낼 수 있습니다. 이 환경은 사용자 10입니다. 루트 권한은 필요 없습니다.

```sh
adb -s emulator-5554 exec-out run-as kr.hellobiz.chageun --user 10 tar -cf - files/walkthrough > captures.tar
```

영상 녹화는 별도 터미널에서 테스트 직전에 시작합니다(화면만 녹화, 음성 없음).

```sh
adb -s emulator-5554 shell screenrecord --size 1280x720 --bit-rate 4000000 --time-limit 100 /sdcard/chageun-walkthrough.mp4
adb -s emulator-5554 pull /sdcard/chageun-walkthrough.mp4 .
```
