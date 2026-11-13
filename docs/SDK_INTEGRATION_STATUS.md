# Pleos SDK 연동 확인 기록

> 후속 음성 구성: Pleos 공식 온디바이스 한국어 재생과 사용자 청취를 확인했습니다. 아래 기존 시연·검증 설명의 음성 미연동 상태는 이전 시점 기록입니다. 현재 상태와 속도 조절 제한은 [한국어 음성 구성](KOREAN_TTS_SETUP.md)을 참고하세요.


확인일: 2026-11-13. 공식 문서 검색 결과와 공식 Maven 배포 AAR의 공개 시그니처·권한 annotation을 확인했다. 앱 실행 시 SDK 연결 성공이나 차량 데이터 수신을 확인한 문서는 아니다. 제품 의존성·권한·Fake 입력은 이번 확인만으로 교체하지 않았다.

## 확보한 근거

- [Vehicle Project Setup](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup): `ai.pleos.playground:Vehicle:2.3.2` 및 공식 Maven 저장소 확인.
- [초기화·도메인 객체](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object): `Vehicle(context)`, `initialize()`, 도메인 조회, 비동기 작업 완료 후 `release()` 순서 확인.
- [콜백](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/callback): 등록·해제와 오류 전달 방식 확인.
- [공식 샘플](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app): 제품에서는 필요한 최소 권한만 선언하며 샘플의 전체 권한을 복사하지 않음.
- 실제 다운로드: `https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar` (HTTP 200).
- AAR SHA-256: `e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937`.
- `classes.jar`를 `javap`으로 확인. 타이어 값의 단위·의미 및 현재 에뮬레이터와 SDK 버전 호환성은 아직 확정하지 못했다.

## 실제 바이너리에서 확인한 사항

| 대상 | 확인한 공개 API 또는 annotation | 구현 전 해소할 사항 |
|---|---|---|
| 진입점 | `ai.pleos.playground.vehicle.Vehicle`, `initialize()`, `getTire()`, `getOdometer()`, `release()` | 서비스 연결 완료·오류·재연결 동작의 계측 검증 |
| 저압 조회 | `Tire.getLowTirePressure(WheelArea, onComplete, onFailed)`, Float 콜백, `pleos.car.permission.CAR_SAFETY` | Float를 불리언 경고로 추측하지 않음. 값의 의미·단위·null 및 오류 조건 확인 |
| 압력 조회·구독 | `getTirePressure`, `registerTirePressure`, `unregisterTirePressure`에 `pleos.car.permission.PRIVATE_CAR_SAFETY` 요구 표시 | 일반 제출 앱에 대한 권한 부여 가능 여부와 공개 대체 경로 확인. 임의로 private 권한 추가 안 함 |
| 주행 가능 거리 | `Odometer.getRangeRemainingDistance`가 deprecated, Google `RANGE_REMAINING` 사용 안내 | AAOS API의 단위·지원 여부·읽기 권한·Pleos 값 주입 경로 확인 후 별도 어댑터 구현 |

SDK 다운로드가 가능하다는 사실은 차량 데이터 권한이 허용됐거나 에뮬레이터 2.1.0에서 SDK 2.3.2가 호환된다는 증거가 아니다.

## 최소 연동 완료 조건

1. 공개적으로 사용 가능한 경고 데이터와 단위·지원 조회·권한을 공식 Reference에서 확정한다.
2. 어댑터에서 실제 초기값과 변경 콜백을 수신하고 해제·오류·서비스 재연결을 검증한다.
3. 지원하지 않는 기능은 비활성화한다. 통신 실패를 정상값 또는 경고 해소로 바꾸지 않는다.
4. 차량 상태가 없으면 UNKNOWN을 유지한다. 속도 0만으로 PARKED를 추정하지 않는다.
5. 실제 입력이 코어 경고와 화면으로 연결되는 로그와 영상을 확보한다. Fake 시연과 구분한다.
6. SDK 연결만으로 TTS·STT·내비를 완료 처리하지 않고 각각 별도로 검증한다.

## Pleos에 확인할 질문 초안 — 미발송

- Vehicle 2.3.2와 Pleos Connect Emulator 2.1.0의 지원 조합은 무엇인가요?
- `getLowTirePressure` Float의 의미·단위·지원 불가 값과 변경 구독의 공개 경로는 무엇인가요?
- `PRIVATE_CAR_SAFETY`가 필요한 압력 구독을 공모전 일반 앱이 사용할 수 있나요? 공개 API 대안이 있나요?
- `RANGE_REMAINING` 읽기와 Virtual Console 주입에 필요한 정확한 권한·지원 조건은 무엇인가요?
- 주행/정차를 안전하게 구분하는 권장 공개 API와 데이터 연결 해제 시의 동작은 무엇인가요?
- 경고 감시용 포그라운드 서비스의 배포 조건과 OEM 화면 활성화 허용 경로가 있나요?

이 질문은 확인용 초안이며 외부에 발송하지 않았다. 공식 문서에서 답을 확인하면 근거와 함께 갱신한다.

## 테스트 정보와 인증정보 확인 (2026-11-13)

현재 `Chageun_Pleos_2_1_0`의 실행 이미지는 Pleos Connect 2.1.0이며 빌드 유형은 `user`다. 제공받은 테스트 DID 설정 절차의 사전 확인에서 `adb root`는 production build 제한으로 거부됐고, `su`와 `/data/vendor/vsomeip/vhal_fifo` 경로는 없었다. DID 쓰기와 재부팅은 수행하지 않았다. 연결된 실제 휴대폰에는 명령을 실행하지 않았다.

[공식 프로젝트 등록 문서](https://document.pleos.ai/en/docs/pleos-only/create-my-project)는 Emulator 2.1.0부터 테스트 CRN이 필요 없다고 명시한다. 제공된 속성 주입 절차를 현재 이미지에 강제로 맞추지 않는다. Project ID는 App Market 심사 요청 시 사용하며, Client ID/Secret은 서버 연결을 요구하는 SDK 등록에 사용한다. 현재 앱의 ON_DEVICE 음성 경로에는 등록이 필요 없다([TTS](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/app-registration), [STT](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration)). 제공된 실제 인증값은 소스·문서·작업 기록에 저장하지 않았다.
