# 차근차근 (차량 경고 통역 도우미) 기능 상세 명세서

> 문서 버전: v0.4 (2026-11-13) — FR-15 핵심 경고 2차 확대 추가
> 용도: AI 코딩 도우미와 개발자가 이 문서만 읽고 구현할 수 있도록 작성한 명세
> 앱 이름은 임시명입니다. 확정되면 `APP_NAME`만 바꿉니다.

---

## 0. AI 도우미를 위한 작업 규칙 (먼저 읽기)

1. **Pleos SDK의 클래스명, 메서드명, Gradle 의존성을 추측해서 쓰지 않는다.** 공식 문서 또는 SDK 샘플에서 확인한 것만 쓴다. 확인 전에는 `// TODO(verify-sdk):` 주석과 인터페이스 뒤에 숨긴다 (5장 참고).
2. **안내 문구를 코드에 직접 쓰지 않는다.** 모든 문구는 `assets/warning_dictionary_ko.json`에서 읽는다.
3. **LLM을 사용하지 않는다.** 경고 해석은 규칙 기반이다.
4. **운전자 승인 없이 내비게이션 목적지를 바꾸지 않는다.**
5. **주행 상태를 확인할 수 없으면 안전한 쪽으로 동작한다** (fail-closed: 상세 화면 숨김, 음성 한 문장만).
6. 이 문서의 요구사항 ID(`FR-xx`)를 커밋 메시지와 코드 주석에 남긴다.
7. 범위 밖(9장) 기능은 만들지 않는다.
8. **화면 디자인과 UI 변경 시 [Pleos Connect UI Design Guide](https://document.pleos.ai/en/docs/connect/guide/docs-design/Introduction/intro)를 공식 참고 기준으로 사용한다.** 적용 항목과 검증 기준은 8장에 명시한다. 미확인 하위 문서의 수치나 컴포넌트 규칙은 추측하지 않는다.

---

## 1. 제품 개요

| 항목 | 내용 |
|---|---|
| 한 줄 정의 | 계기판 경고가 뜨면 뜻과 해야 할 일을 쉬운 말로 알려주고, 승인하면 정비소·주유소로 길 안내를 연결하는 차량용 앱 |
| 대상 사용자 | 차량 지식이 많지 않은 50~60대 운전자 |
| 해결하는 문제 | "경고는 떴는데 무엇을 해야 할지 모른다" |
| 플랫폼 | Pleos Connect (Android Automotive OS), 앱 카테고리 Business 또는 Mobility Services (미정) |
| 데모 차량 | 내연기관 (`vehicle_type = GASOLINE`) |
| 개발 환경 | Android Studio, Kotlin, minSdk 28 이상, Gradle 8.0 이상, Pleos Connect Emulator (Cold boot 필수) |

### 핵심 원칙

- **평소엔 조용히**: 이상이 없으면 말하지 않는다.
- **쉬운 말**: 전문 용어(TPMS, kPa 등) 금지.
- **긴급도 먼저**: 무엇이 급한지부터 알려준다.
- **행동까지 연결**: 안내 → 승인 → 목적지 설정.
- **안전 우선**: 주행 중엔 한 문장 음성만.

---

## 2. 용어 정의

| 용어 | 정의 |
|---|---|
| 경고(Warning) | 앱이 안내 대상으로 삼는 차량 상태 이상. 이 문서에서는 `TIRE_LOW`, `TIRE_RAPID_DROP`, `RANGE_LOW`, `RANGE_CRITICAL`, `SAFETY_WARNING` |
| 긴급도(Urgency) | `NOW`(지금 바로), `TODAY`(오늘 안에), `NEXT`(다음 점검 때) |
| 주행 상태(DriveState) | `DRIVING`, `PARKED`, `UNKNOWN` |
| drive 문구 | 주행 중 음성으로만 말하는 한 문장 |
| park 문구 | 정차 중 화면과 음성으로 주는 상세 안내 |
| 행동(Action) | 경고 후 제안하는 다음 행동. `NAVI_SUGGEST`(목적지 제안), `NONE` |
| 활성 경고 | 감지되었고 아직 해소되지 않은 경고 |

---

## 3. 데모 범위 (1차 제출: 2026-11-15 23:59)

반드시 동작해야 하는 흐름 하나:

```
경고 감지 → 긴급도 분류 → (주행 중) 음성 한 문장
         → (정차 후) 상세 화면 + 음성 → [안내] 승인 → 내비 목적지 설정
```

| 경고 | 데모 포함 | 비고 |
|---|---|---|
| TIRE_LOW | 필수 | 주인공 시나리오 |
| RANGE_LOW / RANGE_CRITICAL | 필수 | 주행 가능 거리 기반 |
| TIRE_RAPID_DROP | 선택 | 시간 남으면 |
| SAFETY_WARNING | 선택 | Safety API 경고 코드 확인 후 |

---

## 4. 아키텍처

```
┌──────────────────────────────────────────────────────────┐
│ UI 계층 (Activity / Compose)                              │
│  - IdleScreen      : 평소 화면 (활성 경고 없음)            │
│  - DetailScreen    : 정차 중 경고 상세 + 버튼 2개          │
│  - DrivingOverlay  : 주행 중 최소 표시 (텍스트 1줄)        │
└───────────────▲──────────────────────────────────────────┘
                │ StateFlow<AppUiState>
┌───────────────┴──────────────────────────────────────────┐
│ WarningCoordinator (핵심 로직, 순수 Kotlin)                │
│  - 경고 이벤트 + 주행 상태 → 무엇을 말하고 보여줄지 결정    │
│  - 중복 안내 방지, 해소 처리                               │
└──▲──────────────▲──────────────▲──────────────▲──────────┘
   │              │              │              │
┌──┴─────────┐ ┌──┴─────────┐ ┌──┴─────────┐ ┌──┴──────────┐
│VehicleSource│ │DriveState  │ │Speaker     │ │NaviLinker   │
│(경고 원천)   │ │Source      │ │(TTS)+      │ │(NaviHelper) │
│             │ │            │ │Listener    │ │             │
│             │ │            │ │(STT 온디바이스)│ │             │
└──▲─────────┘ └──▲─────────┘ └────────────┘ └─────────────┘
   │              │
   │   ┌──────────┴─────────┐
   └───┤ WarningDictionary  │ ← assets/warning_dictionary_ko.json
       └────────────────────┘
```

### 계층 규칙

- `WarningCoordinator`, `WarningDictionary`, `UrgencyRules`는 **Android/SDK 의존성 없는 순수 Kotlin**으로 작성하고 단위 테스트한다.
- SDK 호출은 4개 어댑터(`VehicleSource`, `DriveStateSource`, `Speaker`, `NaviLinker`) 안에만 둔다.
- 각 어댑터에는 **Fake 구현**을 함께 만든다 (에뮬레이터 값 주입이 안 될 때 데모용).

---

## 5. 어댑터 인터페이스 (SDK는 이 뒤에 숨긴다)

```kotlin
// 경고 원천
interface VehicleSource {
    /** 이 차량에서 지원되는 경고 종류. 미지원 항목은 기능을 끈다. (FR-09) */
    suspend fun supportedWarnings(): Set<WarningId>
    /** 경고 발생/해소 이벤트 스트림 */
    val events: Flow<VehicleEvent>
}

sealed interface VehicleEvent {
    data class Raised(val id: WarningId, val params: Map<String, String>) : VehicleEvent
    data class Cleared(val id: WarningId) : VehicleEvent
}

// 주행 상태
interface DriveStateSource {
    val state: StateFlow<DriveState>   // DRIVING, PARKED, UNKNOWN
}

// 음성 출력
interface Speaker {
    suspend fun speak(text: String, slow: Boolean = false)
    fun stop()
}

// 짧은 음성 응답 (FR-13) - ON_DEVICE 모드 고정
interface Listener {
    /** 한 번 짧게 듣고 인식 문장을 돌려준다. 실패·시간초과면 null. */
    suspend fun listenOnce(timeoutMs: Long = 5_000): String?
    fun stop()
}

// 내비 연결
interface NaviLinker {
    /** 운전자 승인 후에만 호출한다. (FR-07) */
    suspend fun setDestination(place: Place): Result<Unit>
}

data class Place(val name: String, val lat: Double, val lng: Double)
```

### 구현 매핑 (SDK 확인 상태)

| 어댑터 | 실제 구현 후보 | 확인 상태 | Fake 동작 |
|---|---|---|---|
| VehicleSource (타이어) | Vehicle SDK `Tire` 그룹 + `checkXXXCapability` | **미확인**: 클래스명·이벤트 방식 | 버튼/adb로 경고 발생 |
| VehicleSource (주행 가능 거리) | AAOS 차량 속성 `RANGE_REMAINING` | 지원 표시 확인, 코드 **미확인** | 값 슬라이더 |
| VehicleSource (안전 경고) | Vehicle SDK `Safety` 그룹 | **미확인**: 경고 코드 목록 | 선택 |
| DriveStateSource | `CarUxRestrictions` 우선, 보조로 Vehicle SDK `Brake`(주차 브레이크) | **미확인** | 토글 버튼 |
| Speaker | Pleos `TextToSpeech` 3.3.1, ON_DEVICE / ko-KR | 공식 API 및 에뮬레이터 한국어 재생 확인. 속도 변경 미지원 | 일반 기기는 Android 기본 TTS |
| Listener | Pleos `SpeechToText` 3.3.1, `Mode.ON_DEVICE` | 실제 어댑터·권한·취소 연결. 실제 발화 최종 인식은 검증 중 ([기록](KOREAN_STT_SETUP.md)) | 일반 기기는 데모 패널 텍스트 입력 |
| NaviLinker | Pleos `NaviHelper` SDK `requestRoute` | API 이름 확인, 파라미터 **미확인** | 로그 + 토스트 |

> **TODO(verify-sdk)**: 위 "미확인" 항목은 공식 Project Setup 문서와 샘플 앱으로 확인한 뒤 채운다. 확인 전에는 Fake로 개발을 진행한다.

---

## 6. 데이터

### 6.1 경고 문구 사전

파일: `app/src/main/assets/warning_dictionary_ko.json` (별도 제공, v0.2)

주요 필드:

| 필드 | 설명 |
|---|---|
| `_meta.vehicle_type` | `GASOLINE` 또는 `EV`. `fuel_words`에서 "주유/주유소" 또는 "충전/충전소" 선택 |
| `warnings[].id` | 경고 ID |
| `warnings[].urgency` | `NOW` / `TODAY` / `NEXT` / `BY_CODE` |
| `warnings[].drive` | 주행 중 한 문장 (템플릿) |
| `warnings[].park` | 정차 중 상세 문장 (템플릿) |
| `warnings[].action` | `{type, category, confirm_required}` |
| `positions` | 타이어 위치 → 쉬운 말 ("운전석 쪽 뒤") |
| `fallback.unsupported` / `fallback.unknown_state` | 예외 문구 |

### 6.2 템플릿 변수

| 변수 | 값 출처 | 예 |
|---|---|---|
| `{position}` | `positions` 매핑 | 운전석 쪽 뒤 |
| `{range}` | 주행 가능 거리 (km, 정수 반올림) | 28 |
| `{fuel}` | `fuel_words[vehicle_type].fuel` | 주유 |
| `{station}` | `fuel_words[vehicle_type].station` | 주유소 |
| `{warning_name}` 등 | Safety 경고 코드 매핑 (추후) | - |

치환되지 않은 `{변수}`가 남으면 그 문구를 말하지 않고 로그를 남긴다.

### 6.3 임계값 (데모용 가정, 설정 파일로 분리)

| 이름 | 값 | 의미 |
|---|---|---|
| `RANGE_LOW_KM` | 80 | 미만이면 RANGE_LOW |
| `RANGE_CRITICAL_KM` | 30 | 미만이면 RANGE_CRITICAL (LOW 대체) |
| `RANGE_HYSTERESIS_KM` | 10 | 해소 판정 여유 (예: 90km 이상이 되면 LOW 해소) |
| `TIRE_RAPID_DROP` | 미정 | 급격한 저하 기준 |

### 6.4 데모용 장소 목록

NaviHelper에 장소 검색 기능이 확인되지 않아, 데모는 고정 목록을 쓴다.

파일: `app/src/main/assets/demo_places.json`

```json
{
  "정비소": [{ "name": "데모 정비소", "lat": 0.0, "lng": 0.0 }],
  "주유소": [{ "name": "데모 주유소", "lat": 0.0, "lng": 0.0 }]
}
```

> 좌표는 에뮬레이터 현재 위치 근처로 채운다. 데모 영상에 "고정 목록 사용"을 명시한다.

---

## 7. 기능 요구사항

### FR-01 앱 시작과 지원 기능 확인
- 앱 시작 시 사전을 로드하고 `VehicleSource.supportedWarnings()`를 호출한다.
- 미지원 경고는 감시하지 않는다.
- **수용 기준**: 사전 파싱 실패 시 앱이 죽지 않고 오류 화면을 보여준다. 지원 목록이 로그에 남는다.

### FR-02 평소엔 조용히
- 활성 경고가 없으면 아무 말도 하지 않는다.
- IdleScreen에 "지금은 확인할 경고가 없어요" 한 줄만 표시한다.
- **수용 기준**: 경고 없는 상태로 10분 실행 시 TTS 호출 0회.

### FR-03 경고 감지와 긴급도 결정
- `VehicleEvent.Raised` 수신 시 사전에서 항목을 찾고 긴급도를 정한다.
- 주행 가능 거리는 값 변화로 판정: 30 미만 → `RANGE_CRITICAL`, 80 미만 → `RANGE_LOW`. CRITICAL이 활성화되면 LOW는 대체된다.
- **수용 기준**: 거리 100 → 70 → 25 → 95로 바뀌면 LOW 발생 → CRITICAL로 대체 → 모두 해소 순서로 처리된다.

### FR-04 주행 중 안내 (DRIVING)
- `drive` 문구 한 문장만 음성으로 말한다.
- 화면은 DrivingOverlay에 같은 문장을 텍스트 1줄로만 표시하고, **버튼과 상세 내용을 보여주지 않는다.**
- **수용 기준**: DRIVING 상태에서 DetailScreen과 버튼이 표시되지 않는다.

### FR-05 정차 후 상세 안내 (PARKED)
- 주행 중 발생한 활성 경고가 있으면, PARKED로 바뀔 때 DetailScreen을 띄우고 `park` 문구를 말한다.
- 정차 중 새 경고가 오면 바로 DetailScreen을 띄운다.
- 활성 경고가 여러 개면 **긴급도 순서(NOW > TODAY > NEXT)**로 하나씩 보여준다.
- **수용 기준**: DRIVING 중 TIRE_LOW 발생 → PARKED 전환 시 상세 화면과 park 문구 음성이 나온다.

### FR-06 상태 불명 처리 (UNKNOWN, fail-closed)
- 주행 상태를 확인할 수 없으면 DRIVING과 똑같이 동작한다.
- 앱 시작 직후 상태가 아직 오지 않았을 때도 UNKNOWN으로 취급한다.
- **수용 기준**: DriveState가 UNKNOWN이면 DetailScreen과 버튼이 표시되지 않는다.

### FR-07 행동 연결 (승인 후에만)
- DetailScreen에 `action.type == NAVI_SUGGEST`이면 **[안내]** 버튼을 보여준다.
- 버튼을 누르면 `demo_places.json`에서 해당 카테고리 첫 장소로 `NaviLinker.setDestination()`을 호출한다.
- 성공 시 "정비소로 안내를 시작할게요." 음성, 실패 시 "길 안내를 시작하지 못했어요." 음성.
- `action.type == NONE`이면 [안내] 버튼을 숨기고 park 문구만 보여준다.
- **수용 기준**: 버튼을 누르기 전에는 `setDestination` 호출 0회.

### FR-08 다시 듣기

> 사용자 승인 변경(2026-11-13): Pleos TTS 3.3.1은 속도 조절 API가 없어 일반 속도로 다시 듣기를 제공한다. 속도 제한을 화면에 표시하며 느린 재생으로 표현하지 않는다. 일반 Android TTS는 기존 slow=true 동작을 유지한다.
- DetailScreen의 **[다시 듣기]** 버튼을 누르면 같은 park 문구를 천천히(`slow = true`) 다시 말한다.
- **수용 기준**: 버튼 1회당 TTS 1회.

### FR-13 짧은 음성 응답 (1차 제출 필수)
- **SpeechToText SDK를 `ON_DEVICE` 모드로 고정**한다. 서버 인식(HYBRID, SERVER)을 쓰지 않는다. 앱 등록과 네트워크가 필요 없고, 음성을 외부로 보내지 않는다.
- **듣기 시작 조건**: PARKED 상태에서 DetailScreen의 park 문구를 다 말한 직후, 한 번만 짧게 듣는다. 화면에 "듣는 중" 표시를 한다.
- **DRIVING / UNKNOWN 상태에서는 듣지 않는다** (FR-06과 동일한 fail-closed). 듣는 중 상태가 바뀌면 즉시 중지한다.
- **인식 문장 → 동작 매핑** (공백 제거 후 포함 여부로 판정):

| 동작 | 인식 키워드 예 |
|---|---|
| 다시 듣기 (FR-08과 동일) | "다시", "뭐라고", "한번더" |
| 안내 승인 (FR-07과 동일) | "안내", "그래", "응", "좋아", "가자" |
| 닫기 | "괜찮아", "아니", "나중에", "됐어" |

- 키워드가 둘 이상 겹치거나 아무것도 없으면 **아무 동작도 하지 않고** 버튼을 기다린다. 승인으로 추측하지 않는다.
- 인식 실패, 시간 초과, 권한 거부 시 조용히 듣기를 끝내고 버튼으로만 동작한다.
- 키워드 목록은 코드에 쓰지 않고 `assets/voice_commands_ko.json`에서 읽는다.
- **수용 기준**:
  - "다시 말해줘" 인식 → park 문구를 천천히 1회 재생
  - "안내해줘" 인식 → `setDestination` 1회 호출 (FR-07 승인과 같은 경로)
  - "응 아니" 같은 모호한 입력 → `setDestination` 0회
  - DRIVING 상태에서 듣기 시작 0회

### FR-09 미지원 항목 처리
- 지원되지 않는 경고는 감시하지 않고, 사용자에게 매번 알리지 않는다.
- 설정/정보 화면(선택)에서만 `fallback.unsupported`를 보여준다.

### FR-10 중복 안내 방지
- 같은 경고는 **해소되기 전까지 다시 말하지 않는다.**
- 예외: DRIVING → PARKED 전환 시 상세 안내 1회 (FR-05).
- 앱 재시작 시 활성 경고는 다시 안내해도 된다.
- **수용 기준**: TIRE_LOW 이벤트가 연속 5회 와도 drive 문구는 1회만 나온다.

### FR-11 경고 해소
- `VehicleEvent.Cleared` 수신 시 활성 경고에서 제거한다.
- DetailScreen에 표시 중이면 닫고 다음 경고 또는 IdleScreen으로 간다.
- 해소 안내 음성은 1차 제출 범위 밖이다.

### FR-12 데모 제어 패널 (디버그 빌드 전용)
- Fake 어댑터를 쓸 때 화면 구석에 숨김 패널을 둔다.
  - 주행 상태 토글: DRIVING / PARKED / UNKNOWN
  - 경고 발생/해소 버튼: TIRE_LOW(위치 선택), TIRE_RAPID_DROP, SAFETY
  - 주행 가능 거리 슬라이더 (0~150km)
  - 음성 응답 대체 입력: 텍스트를 입력하면 Listener 인식 결과로 전달 (FR-13 시뮬레이션)
- 릴리스 빌드에서는 보이지 않는다. 데모 영상에서 이 패널을 쓰면 "시뮬레이션"이라고 표시한다.

### FR-14 화면 종료 후 경고 감시 및 자동 안내 (사용자 추가 요청)

- 액티비티와 별개인 `WarningMonitorService`가 입력 구독과 TTS를 소유한다. 처음 앱을 열면 감시를 시작하고, 홈 이동·뒤로 종료·최근 앱에서 제거 이후에도 서비스를 유지한다.
- 화면이 없는 상태의 경고는 자동으로 **drive 한 문장 음성과 경고 알림**을 제공한다. 안전을 위해 실제 주행 상태가 PARKED여도 백그라운드에서는 UNKNOWN 규칙으로 처리한다. 마이크·상세 음성·내비 변경은 실행하지 않는다.
- 알림 탭으로 앱을 열면 최신 주행 상태에 따라 상세 또는 한 줄 화면을 표시한다. 정차 상세 안내 및 짧은 듣기는 FR-05·13을 따른다.
- 서비스는 `specialUse` 포그라운드 서비스와 상시 상태 알림을 사용한다. 경고 알림에는 drive 문구만 표시하고, 미해소 중복을 억제하며 해소 또는 화면 복귀 시 제거한다.
- 감시를 한 번 시작한 경우 `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`에서 재개한다. `START_STICKY`로 메모리 회수 후 시스템 재생성을 요청하지만 즉시 복구나 무중단 실행을 보장하지 않는다. 재시작 시 실제 어댑터는 차량의 현재 경고 상태를 다시 조회해야 한다(현재 Fake라 미검증).
- 상태 알림의 **감시 중지**는 서비스와 음성을 종료하고 재부팅 재개 설정을 해제한다. 이후 앱을 직접 열면 다시 시작한다. 차량 경고 이력은 저장하지 않는다.
- Android 13 이상 알림 권한을 요청한다. 거부 시 화면에 상태를 표시하고 감시·사용 가능한 TTS는 유지하지만 경고 알림 노출은 보장하지 않는다. TTS가 없으면 음성 역시 불가능하다.
- **플랫폼 경계**: 일반 앱의 백그라운드 Activity 강제 실행은 제한되므로 전체 화면 자동 팝업을 구현했다고 간주하지 않는다. Pleos에서 무조작 화면 활성화까지 필요하면 OEM/플랫폼의 공식 허용 경로 확인과 별도 연동이 필요하다. 강제 종료·시스템의 앱 중지·백그라운드 제한을 우회하지 않는다. 설치 후 한 번도 열지 않은 앱은 감시하지 않는다.
- **현재 구현 범위**: 차량 이벤트·주행 상태는 Fake. 서비스 구조와 자동 안내를 구현했어도 실제 차량 경고 감지는 아직 연결되지 않았다. 알림에 시뮬레이션 상태를 명시한다. `specialUse` 적합성 및 배포 승인 여부는 Pleos 배포 정책에 별도 확인한다.

**수용 기준**

1. 화면을 종료한 뒤 TIRE_LOW 입력 5회 → drive 음성 1회, 상세/듣기/내비 호출 없음.
2. 화면 없이 시작해 PARKED 입력이 있어도 drive 한 문장만 안내.
3. 백그라운드 거리 100→70→25→95 → LOW→CRITICAL→Idle, 안내는 발생 단계별 1회.
4. 앱 재열기 → 기존 세션을 공유하며 PARKED에서는 상세, UNKNOWN/DRIVING에서는 한 줄만 표시. 구독 중복 없음.
5. 실제 Android에서 액티비티 파괴 후 서비스 유지 및 새 경고 알림 게시 확인. 알림 탭 목적지는 앱 진입뿐이며 내비 승인과 분리.
6. 감시 중지 후 입력 미처리, 재부팅 수신으로 자동 재개하지 않음. 활성화 상태의 실제 재부팅 복구는 에뮬레이터 별도 검증.
7. 알림 권한 거부·TTS 미설치·강제 종료·플랫폼 제한은 성공으로 보고하지 않고 제약을 표시/기록.

**공식 실행 정책 근거 (확인: 2026-11-13)**

- [Foreground service types — specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Foreground service background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Background Activity starts](https://developer.android.com/guide/components/activities/background-starts)
- [Android 15 부팅 수신기의 FGS 제한](https://developer.android.com/about/versions/15/behavior-changes-15)
- [Android 15 강제 종료 상태](https://developer.android.com/about/versions/15/behavior-changes-all)


---

### FR-15 핵심 경고 2차 확대 (사용자 추가 요청)

- 95가지 계기판 표시를 모두 활성화하지 않고 내연기관 데모에 적합한 경고 5종을 추가한다. 기존 4종과 합쳐 시뮬레이션 지원 경고는 9종이다.
- `ENGINE_OIL_PRESSURE`(엔진오일 압력), `ENGINE_OVERHEAT`(엔진 과열), `BRAKE_FLUID_LOW`(브레이크액 부족), `ABS_WARNING`(급제동 방지 장치), `BATTERY_CHARGING`(배터리 충전 계통)을 지원한다. 이 ID는 앱 내부 모델이며 SDK API/상수 명칭을 뜻하지 않는다.
- 신규 경고는 `FakeVehicleSource`와 디버그 패널에서 명시적으로 발생·해소한다. 숫자 센서값의 임계값이나 점등·점멸 의미를 추측하지 않는다. 실제 Vehicle SDK·권한·차종별 의미 확인은 별도 단계이다.
- ABS 단독 경고는 TODAY, 나머지 4종은 NOW로 분류한다. 신규 5종의 action은 NONE이며 운전자 승인·음성 응답으로도 내비 목적지를 설정하지 않는다.
- 긴급도 NOW > TODAY > NEXT를 유지하고, 같은 긴급도에서는 사전의 `priority`(작은 값 우선, 생략 시 100)를 적용한다. 정차 필요 경고 4종과 기존 급격한 타이어 저하는 10으로 두어 주행거리 긴급 경고(100)보다 먼저 안내한다. 같은 긴급도·priority는 발생 순서를 유지한다.
- 신규 경고의 문구·긴급도는 차종별 취급설명서 검증 전의 데모 초안이다. 엔진오일 압력을 오일량 부족으로, 충전 계통 경고를 전기차 잔량 부족으로, 주차 브레이크 점등을 브레이크액 부족으로 간주하지 않는다.
- 모든 안내·데모 라벨은 `assets/warning_dictionary_ko.json`에서 읽는다. 기존 화면을 재사용하며 차량 그림은 개념 표시로 취급하고 실제 차종별 부품 위치를 보증하지 않는다.
- FR-04~11·13·14의 주행/상태 불명 제한, 중복 억제, 명시적 해소, 승인 경합 방지, 백그라운드 제한을 그대로 적용한다. 기타 `SAFETY_WARNING`은 비활성 상태를 유지한다.

**수용 기준**

1. 신규 5종 각각 DRIVING/UNKNOWN에서는 한 문장·버튼 없음, PARKED에서는 상세·다시 듣기만 표시한다.
2. 같은 경고 5회 입력은 한 번만 안내하고, 해소 후에는 대기로 돌아가며 재발생 안내를 허용한다.
3. 주행거리 25km 경고와 정차 필요 경고가 동시에 있으면 입력 순서와 관계없이 정차 필요 경고가 우선한다.
4. 주유소 안내 음성 승인 대기 중 정차 필요 경고가 도착하면 이전 승인을 폐기하고 내비를 호출하지 않는다.
5. 디버그 패널에서 5종을 선택·발생·해소할 수 있고, 지원 목록은 명시된 9종만 포함한다.
6. 실제 차량 경고 감지·차종별 문구 인증·실차 검증 완료로 표현하지 않는다.

---

## 8. 화면 명세

### 공식 디자인 참고 기준

- **기준 사이트**: [Pleos Connect UI Design Guide — Introduction](https://document.pleos.ai/en/docs/connect/guide/docs-design/Introduction/intro)
- **내용 확인일**: 2026-11-13
- **적용 대상**: IdleScreen, DrivingOverlay, DetailScreen의 레이아웃·색상·타이포그래피·아이콘·버튼·피드백과 화면 크기 대응. 후속 디자인 및 UI 수정 시 이 사이트와 해당 항목의 공식 하위 가이드를 확인한다.

다음은 위 Introduction 페이지에서 확인한 기준과 이 앱의 적용 방향이다. 표의 적용 방향은 프로젝트 요구사항이며, 현재 구현의 준수 검증이 완료되었다는 의미는 아니다.

| 공식 가이드의 기준 | 차근차근 적용 및 검증 기준 |
|---|---|
| 주행 중 시각·인지 부하 최소화, 영상·움직이는 이미지·자동 스크롤 텍스트 금지 | FR-04·06에 따라 DRIVING/UNKNOWN에서는 한 문장 음성과 한 줄 표시만 제공하고 상세 내용과 버튼은 숨긴다. 긴 문구를 자동 스크롤로 처리하지 않는다. |
| 정보는 1–2초 안에 파악 가능하게 구성 | 긴급도와 해야 할 행동을 우선 표시하고 불필요한 장식과 정보를 제외한다. |
| 한 손으로 가능한 단순한 조작, 일관된 용어·아이콘·상호작용 | 정차 상세 화면의 안내·다시 듣기 버튼을 일관되게 배치한다. 일반 가이드의 터치 허용을 근거로 주행 중 버튼을 추가하지 않는다. |
| 텍스트·아이콘 등의 대비는 최소 4.5:1 | 배경 대비를 측정하고, 긴급도는 색과 글자 라벨을 함께 사용한다. |
| 문장 또는 문단은 최대 3줄, 짧고 간결한 문구 | 기존 한 화면 최대 3문장 규칙과 함께 적용한다. 영어·일본어에 명시된 글자 수 제한을 한국어 규칙으로 임의 환산하지 않는다. |
| 주요 정보 글자 높이 최소 4mm, 일반 텍스트 최소 3mm | 아래의 32sp 이상 규칙도 유지한다. sp와 실제 mm를 동일시하지 않고 대상 차량 디스플레이의 밀도·크기와 시청 거리에서 가독성을 확인한다. |
| 사용자 입력 후 시스템 응답 0.25초 이내, 지연 시 처리 중임을 표시 | 버튼 입력의 시각적 피드백과 비동기 처리 상태를 검증한다. 10장의 경고 수신 후 TTS 시작 목표와는 별도로 측정한다. |
| 주야간 가독성 및 다양한 해상도·화면 비율 대응 | 실제 Pleos 화면에서 밝기·대비, 문구 잘림, 버튼 접근성을 확인한다. 휴대폰 화면 검증만으로 차량 화면 검증을 대체하지 않는다. |

**적용 범위와 우선순위**

- 공식 디자인 가이드와 본 명세의 안전·접근성 조건을 함께 충족한다. 충돌이 확인되면 관련 근거와 조정안을 기록하며, FR-04·06의 안전 제한을 임의로 완화하지 않는다.
- 가이드에서 소개하는 Gleo AI, 다국어, OTA 등의 내용은 그 자체로 이 앱의 구현 요구사항을 추가하지 않는다. 0장의 LLM 금지, FR-13의 ON_DEVICE STT, 9장의 범위 제외를 유지한다.
- 디자인 문서는 SDK API 확인 근거를 대신하지 않는다. 실제 SDK 연동은 별도의 공식 개발 문서와 샘플을 확인한다.
- 색상·타이포그래피·아이콘·디스플레이 배치의 세부 변경 시 관련 하위 가이드 URL과 확인 내용을 작업 기록에 남긴다. 디자인 리소스는 공식 배포처의 이용 조건을 확인한 뒤 사용한다.

### 공통 디자인 규칙 (중장년 배려)

| 항목 | 규칙 |
|---|---|
| 본문 글자 | 32sp 이상 |
| 버튼 | 최대 2개, 높이 88dp 이상, 글자 28sp 이상 |
| 긴급도 색 | NOW = 빨강, TODAY = 주황, NEXT = 회색 (색만으로 구분하지 말고 글자 라벨 병기) |
| 문장 | 한 화면 최대 3문장 |
| 아이콘 | 장식용 금지, 의미 있는 것만 |

### 8.1 IdleScreen
```
┌──────────────────────────────┐
│  차근차근                      │
│                              │
│  지금은 확인할 경고가 없어요     │
│                              │
└──────────────────────────────┘
```

### 8.2 DrivingOverlay (DRIVING / UNKNOWN)
```
┌──────────────────────────────┐
│ [오늘 안에] 운전석 쪽 뒤 타이어   │
│ 바람이 부족해요. 오늘 안에       │
│ 점검받으세요.                  │
└──────────────────────────────┘
  (버튼 없음, 터치 반응 없음)
```

### 8.3 DetailScreen (PARKED)
```
┌──────────────────────────────┐
│ ● 오늘 안에                    │
│                              │
│ 운전석 쪽 뒤 타이어 공기압이      │
│ 낮습니다. 오늘 안에 정비소나      │
│ 주유소에서 공기를 채우세요.      │
│                              │
│ ┌────────────┐ ┌───────────┐ │
│ │ 정비소 안내  │ │ 다시 듣기   │ │
│ └────────────┘ └───────────┘ │
└──────────────────────────────┘
```
- 버튼 라벨: `"{category} 안내"` (예: 정비소 안내, 주유소 안내)
- 화면 문구에서 질문형 마지막 문장("안내할까요?")은 음성으로만 말하고 화면에는 생략 가능

---

## 9. 범위 밖 (만들지 않는다)

- 로그인, 회원 관리, 서버
- LLM 기반 문구 생성
- 실제 장소 검색, 정비소 예약
- 가족 알림, 정기 점검 안내
- 경고 이력 저장
- 해소 안내 음성
- 다국어

---

## 10. 비기능 요구사항

| 항목 | 요구 |
|---|---|
| 반응 시간 | 경고 이벤트 수신 후 1초 안에 TTS 시작 (목표, 미측정) |
| 안정성 | 어댑터 오류 시 앱이 죽지 않고 해당 기능만 끈다 |
| 개인정보 | 자체 서버 없음. 위치·차량 데이터와 음성을 외부로 전송하지 않는다 (STT는 ON_DEVICE 고정) |
| 권한 | 마이크 권한(`RECORD_AUDIO`)이 거부되어도 앱은 버튼으로 정상 동작한다 |
| 로그 | 태그 `CHAGEUN`으로 이벤트, 상태 전환, TTS 호출, 내비 호출 기록 |
| 테스트 | `WarningCoordinator` 단위 테스트로 FR-03, 04, 05, 06, 07, 10 수용 기준 검증 |

---

## 11. 상태 전이 (WarningCoordinator)

```
입력: VehicleEvent, DriveState 변화, 사용자 버튼
출력: AppUiState(screen, activeWarning), speak 요청, navi 요청

[Idle] --Raised & DRIVING/UNKNOWN--> speak(drive) --> [PendingDetail]
[Idle] --Raised & PARKED----------> speak(park)  --> [Detail]
[PendingDetail] --DriveState=PARKED--> speak(park) --> [Detail]
[Detail] --DriveState=DRIVING/UNKNOWN--> [PendingDetail] (말하지 않음)
[Detail] --[안내] 버튼--> navi.setDestination --> [Detail]
[Detail] --[다시 듣기]--> speak(park, slow) --> [Detail]
[Detail] --park 문구 재생 끝 & PARKED--> listenOnce --> 키워드 매핑(FR-13) --> 해당 동작 또는 대기
[Listening] --DriveState=DRIVING/UNKNOWN--> listener.stop --> [PendingDetail]
[*] --Cleared(마지막 경고)--> [Idle]
```

---

## 12. 권장 파일 구조

```
app/src/main/
├── assets/
│   ├── warning_dictionary_ko.json
│   ├── voice_commands_ko.json
│   └── demo_places.json
└── java/.../chageun/
    ├── MainActivity.kt
    ├── ui/        IdleScreen.kt, DetailScreen.kt, DrivingOverlay.kt, DemoPanel.kt
    ├── core/      WarningCoordinator.kt, WarningDictionary.kt, UrgencyRules.kt, Models.kt
    ├── adapter/   VehicleSource.kt, DriveStateSource.kt, Speaker.kt, NaviLinker.kt
    ├── adapter/pleos/  (SDK 확인 후 실제 구현)
    └── adapter/fake/   FakeVehicleSource.kt, FakeDriveStateSource.kt, AndroidTtsSpeaker.kt, LogNaviLinker.kt
app/src/test/.../core/  WarningCoordinatorTest.kt
```

---

## 13. 구현 순서 (AI 도우미 작업 단위)

1. **순수 로직**: `Models`, `WarningDictionary`(JSON 파싱, 템플릿 치환), `UrgencyRules`, `WarningCoordinator` + 단위 테스트
2. **Fake 어댑터 + 데모 패널**로 에뮬레이터에서 전체 흐름 동작
3. **화면 3종** (Compose)
3a. **FR-14 추가 요청**: 화면 종료 후 감시 서비스·자동 한 문장/알림·부팅 복구 및 검증. 전체 화면 강제 실행과 실제 차량 SDK 연결은 별도 확인.
3b. **FR-15 사용자 승인 핵심 경고 2차 확대**: 사전·순수 로직·Fake 입력·기존 화면·검증에 한정한다. 실제 차량 어댑터 교체는 포함하지 않는다.
4. **실제 어댑터 교체** (SDK 확인된 것부터): Speaker → NaviLinker → DriveStateSource → VehicleSource
5. **데모 영상 촬영** (14장 스크립트)

> 4단계에서 확인이 안 된 어댑터는 Fake로 남기고, 영상과 기획서에 시뮬레이션으로 명시한다.

---

## 14. 60초 데모 스크립트

심사용 촬영 대본은 [DEMO_SCRIPT.md](DEMO_SCRIPT.md)를 따른다. 현재 영상에서는 차량·주행 상태가 Fake이며, 실제 한국어 TTS·마이크 STT·내비 목적지 설정을 완료했다고 표현하지 않는다. 발표자 나레이션은 앱 음성 출력과 구분한다.

[기존 13개 체크포인트 검증 자료](EMULATOR_TEST_RESULTS.md)는 보조 증거로 사용한다. 새 60초 영상 제작 완료를 의미하지 않는다.

---

## 15. 미확인 사항 체크리스트

- [x] Vehicle SDK 2.3.2 Gradle 좌표·초기화 진입점 및 공식 배포 파일 확인 ([확인 기록](SDK_INTEGRATION_STATUS.md)); 앱 런타임 연동·호환성은 미검증
- [ ] Tire 그룹에서 위치별 저압 경고를 받는 방법
- [ ] Safety 그룹 경고 코드 목록
- [ ] RANGE_REMAINING 읽기 코드와 에뮬레이터 값 변경 가능 여부
- [ ] 주행/정차 판정: CarUxRestrictions 사용 가능 여부
- [x] TextToSpeech SDK 초기화와 한국어 음성 ([구성 기록](KOREAN_TTS_SETUP.md))
- [ ] NaviHelper `requestRoute` 파라미터 형식
- [ ] SpeechToText ON_DEVICE 모드의 한국어 인식 품질, 에뮬레이터 마이크 설정
- [x] TextToSpeech 재생 종료 콜백 확인 (마이크 STT 연동과는 별개)
- [ ] Pleos Virtual Console에서 타이어·경고 값 주입 가능 여부
- [ ] 앱 카테고리(Business / Mobility Services) 결정
- [ ] 안내 문구와 긴급도를 차량 취급설명서 기준으로 검증


## 16. 심사 대응 및 사용자 검증

- [심사 대응 설명서](JUDGING_BRIEF.md): 네 가지 평가 관점별 가치·증거·보완 조건, 대상 사용자 및 사업 가설.
- [사용자 검증 계획](USER_VALIDATION.md): 인터뷰·정차 비교 과제와 지표 정의. 조사 미실시이며 사용자 수·유지율·효과를 실적으로 표시하지 않는다.
- 현재 SDK·음성·내비 미연동 사실을 제출 자료와 동일하게 유지한다. 계획은 검증 결과를 대체하지 않는다.
