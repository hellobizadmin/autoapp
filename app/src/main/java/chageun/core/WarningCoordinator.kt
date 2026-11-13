/*******************************
 목   적 : 차량 경고와 주행 상태에 따른 안내 흐름 관리
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
 2026-11-13 : FR-08·FR-13 및 앱 모듈 연결 지원
*******************************/
package chageun.core

/** 입력을 순차 처리하여 화면 상태와 출력 요청을 결정하는 순수 Kotlin 코어입니다. */
class WarningCoordinator(private val dictionary: WarningDictionary, private val rules: UrgencyRules) {
    /** 활성 경고와 주행 및 정차 안내 여부를 보관합니다. */
    private data class Entry(var warning: ActiveWarning, var driveSpoken: Boolean = false,
                             var parkSpoken: Boolean = false, var dismissed: Boolean = false)
    private val active = linkedMapOf<WarningId, Entry>()
    var driveState = DriveState.UNKNOWN // FR-06: 시작부터 상태 불명으로 두어 안전하게 제한합니다.
        private set
    var uiState: AppUiState = AppUiState.Idle
        private set
    /** 활성 경고를 긴급도 순서로 조회합니다. */
    private val warningOrder = compareBy<ActiveWarning> { it.urgency }.thenBy { it.priority }
    val activeWarnings: List<ActiveWarning> get() = active.values.map { it.warning }.sortedWith(warningOrder)

    /** FR-03, FR-10: 발생 및 해소 이벤트를 반영하고 중복 안내를 억제합니다. */
    fun onVehicleEvent(event: VehicleEvent): List<Effect> {
        when (event) {
            is VehicleEvent.Raised -> {
                val warning = dictionary.resolve(event.id, event.params) ?: return emptyList()
                if (event.id == WarningId.RANGE_CRITICAL) active.remove(WarningId.RANGE_LOW)
                if (event.id == WarningId.RANGE_LOW && WarningId.RANGE_CRITICAL in active) return emptyList()
                active[event.id]?.warning = warning
                if (event.id !in active) active[event.id] = Entry(warning)
            }
            is VehicleEvent.Cleared -> active.remove(event.id)
        }
        return present()
    }

    /** FR-04, FR-05, FR-06: 주행 상태에 맞춰 화면과 음성 안내를 갱신합니다. */
    fun onDriveState(state: DriveState): List<Effect> {
        val wasParked = driveState == DriveState.PARKED
        driveState = state
        // FR-05, FR-10: 활성 경고당 상세 안내는 한 번만 하며 상세 화면을 나갈 때는 말하지 않습니다.
        return present(silent = wasParked && state != DriveState.PARKED)
    }

    /** FR-03: 거리 변화에 따라 경고를 발생시키거나 대체 및 해소합니다. */
    fun onRangeChanged(km: Double): List<Effect> {
        val previous = active.keys.firstOrNull { it == WarningId.RANGE_LOW || it == WarningId.RANGE_CRITICAL }
        val next = rules.rangeWarning(km, previous)
        if (previous != next) previous?.let { active.remove(it) }
        if (next != null) return onVehicleEvent(VehicleEvent.Raised(next, mapOf("range" to km.toString())))
        return present()
    }

    // FR-07: 현재 표시 중인 경고에 대한 명시적 승인 후에만 내비 요청을 만듭니다.
    // 호출자가 demo_places.json에서 읽은 카테고리별 장소 목록을 전달합니다(2단계).
    fun approveNavigation(id: WarningId, places: Map<String, List<Place>>): List<Effect> {
        val detail = uiState as? AppUiState.Detail ?: return emptyList()
        if (detail.warning.id != id || !detail.showNavigation) return emptyList()
        val place = places[detail.warning.action.category]?.firstOrNull() ?: return emptyList()
        return listOf(Effect.SetDestination(place))
    }

    /** FR-08: 정차 상세 화면의 문구를 버튼이나 음성 요청당 한 번 천천히 안내합니다. */
    fun repeatDetail(): List<Effect> {
        val detail = uiState as? AppUiState.Detail ?: return emptyList()
        return detail.warning.park?.let { listOf(Effect.Speak(it, slow = true)) } ?: emptyList()
    }

    /** FR-13: 닫은 경고는 해소로 처리하지 않고 이번 활성 기간 동안 화면에서 제외합니다. */
    fun dismissDetail(): List<Effect> {
        val detail = uiState as? AppUiState.Detail ?: return emptyList()
        active[detail.warning.id]?.dismissed = true
        return present()
    }

    /** 가장 긴급한 경고를 표시하고 필요한 경우에만 음성 요청을 만듭니다. */
    private fun present(silent: Boolean = false): List<Effect> {
        // FR-15: 정차가 필요한 NOW 경고가 거리 부족 안내보다 먼저 표시됩니다.
        val entry = active.values.filterNot { it.dismissed }
            .minWithOrNull(compareBy(warningOrder) { it.warning })
        if (entry == null) { uiState = AppUiState.Idle; return emptyList() }
        val warning = entry.warning
        val parked = driveState == DriveState.PARKED
        uiState = if (parked) AppUiState.Detail(warning,
            warning.park != null && warning.action.type == ActionType.NAVI_SUGGEST)
        else AppUiState.DrivingOverlay(warning.id, warning.urgency, warning.drive, warning.positionKey)
        if (silent || (if (parked) entry.parkSpoken else entry.driveSpoken)) return emptyList()
        val text = (if (parked) warning.park else warning.drive) ?: return emptyList()
        if (parked) entry.parkSpoken = true
        // FR-10: 상세 안내를 마친 경고는 주행 중 다시 안내하지 않습니다.
        entry.driveSpoken = true
        return listOf(Effect.Speak(text))
    }
}
