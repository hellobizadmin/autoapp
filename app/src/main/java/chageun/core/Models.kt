/*******************************
 목   적 : 차량 경고와 화면 상태 및 출력 요청 모델 정의
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
 2026-11-13 : FR-08·FR-13 및 앱 모듈 연결 지원
*******************************/
package chageun.core

/** 안내 대상으로 처리할 차량 경고 종류입니다. */
enum class WarningId {
    TIRE_LOW, TIRE_RAPID_DROP, RANGE_LOW, RANGE_CRITICAL, SAFETY_WARNING,
    // FR-15: 앱 내부 경고 ID이며 SDK 상수 또는 실제 차량 감지 구현이 아닙니다.
    ENGINE_OIL_PRESSURE, ENGINE_OVERHEAT, BRAKE_FLUID_LOW, ABS_WARNING, BATTERY_CHARGING
}

/** FR-12, FR-15: 2차 데모에서 명시적으로 발생·해소할 수 있는 핵심 경고입니다. */
val phaseTwoWarnings: List<WarningId> = listOf(
    WarningId.ENGINE_OIL_PRESSURE, WarningId.ENGINE_OVERHEAT, WarningId.BRAKE_FLUID_LOW,
    WarningId.ABS_WARNING, WarningId.BATTERY_CHARGING
)
/** 경고 표시 우선순위를 긴급한 순서로 정의합니다. */
enum class Urgency { NOW, TODAY, NEXT }
/** 차량의 주행 여부와 상태 불명 여부를 구분합니다. */
enum class DriveState { DRIVING, PARKED, UNKNOWN }
/** 경고에서 제안할 후속 행동 종류입니다. */
enum class ActionType { NAVI_SUGGEST, NONE }

/** 차량 경고의 발생 및 해소 입력입니다. */
sealed interface VehicleEvent {
    /** 경고 발생과 문구 치환에 필요한 값을 전달합니다. */
    data class Raised(val id: WarningId, val params: Map<String, String> = emptyMap()) : VehicleEvent
    /** 해소된 경고의 식별자를 전달합니다. */
    data class Cleared(val id: WarningId) : VehicleEvent
}

/** 경고의 후속 행동과 목적지 카테고리입니다. */
data class WarningAction(val type: ActionType, val category: String? = null)
/**
 * 해석된 활성 경고의 긴급도와 안내 문구입니다.
 * positionKey·rangeKm은 화면에서 타이어 위치 다이어그램·주행 가능 거리 게이지를 그릴 때만 쓰는
 * 원본 값이며, 안내 문구는 이미 drive/park에 사람이 읽는 말로 치환되어 있습니다.
 */
data class ActiveWarning(val id: WarningId, val urgency: Urgency, val drive: String?,
                         val park: String?, val action: WarningAction,
                         val positionKey: String? = null, val rangeKm: Int? = null,
                         val priority: Int = 100)
/** 승인 후 길 안내에 사용할 장소 정보입니다. */
data class Place(val name: String, val lat: Double, val lng: Double)

// FR-04, FR-06: 주행 및 상태 불명 화면에는 상세 내용과 버튼을 담지 않습니다.
sealed interface AppUiState {
    /** 활성 경고가 없는 대기 상태입니다. */
    data object Idle : AppUiState
    /** 주행 중 한 줄로 표시할 경고 정보입니다. positionKey는 차량 그림에서 바퀴 위치를 강조할 때만 씁니다. */
    data class DrivingOverlay(val id: WarningId, val urgency: Urgency, val text: String?,
                              val positionKey: String? = null) : AppUiState
    /** 정차 중 표시할 상세 경고와 안내 버튼 상태입니다. */
    data class Detail(val warning: ActiveWarning, val showNavigation: Boolean) : AppUiState
}

// 코어가 결정한 출력 요청이며 실제 어댑터 실행은 1단계 범위에 포함하지 않습니다.
sealed interface Effect {
    /** 어댑터에 전달할 음성 출력 요청입니다. */
    data class Speak(val text: String, val slow: Boolean = false) : Effect
    /** 운전자 승인으로 생성된 목적지 설정 요청입니다. */
    data class SetDestination(val place: Place) : Effect
}
