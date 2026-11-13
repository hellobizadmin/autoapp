# 차근차근 (차량 경고 통역 도우미) 기능 상세 명세서

> 문서 버전: v0.1 (2026-11-13)
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
│(경고 원천)   │ │Source      │ │(TTS)       │ │(NaviHelper) │
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
| Speaker | Pleos `TextToSpeech` SDK | 기능 존재 확인, 코드 **미확인** | Android 기본 TTS |
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
- DetailScreen의 **[다시 듣기]** 버튼을 누르면 같은 park 문구를 천천히(`slow = true`) 다시 말한다.
- (선택) SpeechToText로 "다시 말해줘"를 인식하면 같은 동작. 1차 제출에서는 생략 가능.
- **수용 기준**: 버튼 1회당 TTS 1회.

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
- 릴리스 빌드에서는 보이지 않는다. 데모 영상에서 이 패널을 쓰면 "시뮬레이션"이라고 표시한다.

---

## 8. 화면 명세

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
| 개인정보 | 위치·차량 데이터를 외부로 전송하지 않는다 (서버 없음) |
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
[*] --Cleared(마지막 경고)--> [Idle]
```

---

## 12. 권장 파일 구조

```
app/src/main/
├── assets/
│   ├── warning_dictionary_ko.json
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
4. **실제 어댑터 교체** (SDK 확인된 것부터): Speaker → NaviLinker → DriveStateSource → VehicleSource
5. **데모 영상 촬영** (14장 스크립트)

> 4단계에서 확인이 안 된 어댑터는 Fake로 남기고, 영상과 기획서에 시뮬레이션으로 명시한다.

---

## 14. 60초 데모 스크립트

| 시간 | 장면 | 실제/시뮬레이션 표시 |
|---|---|---|
| 0~5초 | IdleScreen, "평소엔 조용합니다" 자막 | 실제 |
| 5~15초 | 주행 중 TIRE_LOW 발생 → 한 문장 음성, 버튼 없음 | 경고·주행 상태: 에뮬레이터 또는 데모 패널 |
| 15~30초 | 정차 전환 → 상세 화면 + park 문구 음성 | 화면·음성: 실제 |
| 30~40초 | [정비소 안내] → 내비 목적지 설정 | 목적지: 고정 목록 |
| 40~55초 | 주행 가능 거리 25km → "지금 바로" 안내 → 주유소 안내 | 거리 값: 에뮬레이터 또는 데모 패널 |
| 55~60초 | "경고가 떴을 때, 무엇을 해야 할지까지" 마무리 자막 | - |

---

## 15. 미확인 사항 체크리스트

- [ ] Vehicle SDK Gradle 의존성과 초기화 코드 (Project Setup 문서)
- [ ] Tire 그룹에서 위치별 저압 경고를 받는 방법
- [ ] Safety 그룹 경고 코드 목록
- [ ] RANGE_REMAINING 읽기 코드와 에뮬레이터 값 변경 가능 여부
- [ ] 주행/정차 판정: CarUxRestrictions 사용 가능 여부
- [ ] TextToSpeech SDK 초기화와 한국어 음성
- [ ] NaviHelper `requestRoute` 파라미터 형식
- [ ] Pleos Virtual Console에서 타이어·경고 값 주입 가능 여부
- [ ] 앱 카테고리(Business / Mobility Services) 결정
- [ ] 안내 문구와 긴급도를 차량 취급설명서 기준으로 검증
