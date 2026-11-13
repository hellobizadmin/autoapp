# 미해결 항목 수정 및 재검증 — 2026-11-13

## 결과
단위 테스트 **50개**, 자동 Android 계측 **12개**, 사용자 실제 발화 계측 **1개** 통과. 빌드 성공. lint 오류 0개·경고 8개(기존 16KB 정렬 경고 3건 제거). 실제 차량 및 내비게이션은 Fake이며, 음성 인식 성공을 실제 차량 길 안내 성공으로 표현하지 않는다.

## 변경
| 이전 문제 | 조치 | 검증 |
|---|---|---|
| 기본 경고 흐름 5초 시간 초과 | 아래 GPU 환경 수정 적용. 대기 시간을 늘리거나 검증을 생략하지 않음 | 13개 체크포인트 통과, 63.4초 |
| DrivingView libUnreal 충돌·빈 화면 | 동일 AVD를 `-gpu host`로 콜드 부팅. Apple M3 Pro 렌더러 사용. AVD config.ini 기본값도 host로 저장 | 왼쪽 3D 차량 화면 정상 표시, 전체 자동 테스트 동안 DrivingView/libUnreal 충돌 재발 없음 |
| 실제 발화 최종 결과 미반환 | PleosListener 초기화 최대 15초와 듣기 요청 최대 5초를 분리. WarningSession 외부 제한도 준비 시간을 반영. 실제 마이크 상태로 듣는 중 표시 | 준비 지연/주행 취소 회귀 테스트 2개 추가. 사용자 실제 “안내해줘” 발화가 최종 ACCEPT_NAVI로 인식됨 |
| 느린 다시 듣기 미지원 | 사용자가 일반 속도 재생을 승인. Pleos는 실제 일반 속도 재생 완료까지 수행하고 속도 제한 표시 | TTS 2개 테스트에서 재생·중단·재시도·다시 듣기 검증 |
| JNA 16KB 정렬 경고 | JNA 5.18.1 Android AAR 직접 의존성 적용 | 전체 빌드 및 음성 SDK 회귀 성공, Aligned16KB 경고 0건 |

## 안전 조건
초기화 중 주행 전환도 즉시 취소한다. 중간 인식 결과를 승인으로 쓰지 않는다. 외부 제한은 준비 시간을 최대 15초까지만 추가하며 무기한 대기하지 않는다. Fake Listener는 기존 5초 제한을 유지한다. SDK 공개 API 외의 속도 제어를 추측하거나 음성을 외부 서버로 보내지 않는다. 모든 앱 안내 문구는 사전에서 읽는다.

## 증거
`build/verification/fixes-20261113/`
- `build.log`, `live-test-build.log`: 빌드·단위 테스트
- `suite-results.json`, `01-*.txt` ~ `09-*.txt`: 자동 계측 9개 그룹/12회 전부 PASS
- `live-microphone.txt`: 실제 발화 계측 OK (1 test), 10.164초
- `speech-events.txt`: 최종 STT 콜백 및 ACCEPT_NAVI (원본 음성/인식 문장 저장 없음)
- `host-gpu.png`: 앱과 Pleos 왼쪽 3D 차량 화면
- `emulator-host.log`, `crash-after-walkthrough.log`: 렌더러 및 시스템 로그
- `avd-config-before.ini`: 변경 전 AVD 설정 백업

실제 발화 시험에는 준비 시점을 알려주는 짧은 시작음을 테스트 코드에 추가했다. 해당 계측은 최종 인식과 명령 분류를 확인하며 실제 내비 SDK를 호출하지 않는다. 버튼 승인과 Fake 내비 전달은 기본 경고 흐름 및 단위 테스트에서 별도 확인했다.

## 공식 근거
- [JNA 변경 기록](https://github.com/java-native-access/jna/blob/master/CHANGES.md): 5.16/5.17 Android 16KB 수정, 5.18 교착 수정. 이번 적용 5.18.1.
- [JNA Android 사용](https://github.com/java-native-access/jna/blob/master/www/FrequentlyAskedQuestions.md): Android AAR 의존성.
- [Pleos STT 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners): completed=true인 최종 콜백만 사용.
- [Pleos 에뮬레이터 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator): 그래픽 요구사항 및 Cold boot. host 옵션은 설치된 Android Emulator `-help-gpu`에서 확인.

## 검증 한계
한 번의 실제 명령 성공이 모든 화자·소음 조건의 인식 품질을 보장하지 않는다. 느린 재생 API 자체가 생긴 것이 아니라 사용자가 일반 속도 다시 듣기로 요구사항을 조정했다. 16KB 전용 기기에서 전체 앱 실행은 아직 별도 검증하지 않았다. 장시간 에뮬레이터 안정성과 이미지 전체 시스템 서비스의 무오류까지 보증하지 않는다. 차량·주행 상태·내비 실제 SDK 연동은 이번 수정 범위 밖이다.
