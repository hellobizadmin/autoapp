/*******************************
 목   적 : 차량 및 음성·내비 어댑터 계약
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.adapter

import chageun.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** FR-01, FR-09: 지원 경고와 차량 이벤트를 제공하는 경계입니다. */
interface VehicleSource {
    /** 현재 차량에서 지원하는 경고 종류를 조회합니다. */
    suspend fun supportedWarnings(): Set<WarningId>
    val events: Flow<VehicleEvent>
    val rangeKm: Flow<Double>
}
/** FR-06: 주행 상태를 제공하는 경계입니다. */
interface DriveStateSource { val state: StateFlow<DriveState> }
/** FR-05, FR-08: 재생 완료 시 반환하고 실패 시 예외를 전달하는 음성 출력 경계입니다. */
interface Speaker {
    /** 문구를 재생하고 실제 재생이 끝날 때까지 기다립니다. */
    suspend fun speak(text: String, slow: Boolean = false)
    /** 진행 중인 재생을 즉시 중단합니다. */
    fun stop()
}
/** FR-13: 온디바이스 단발성 듣기 경계이며 실패와 시간 초과는 null입니다. */
interface Listener {
    /** FR-13: 엔진 준비 시간은 실제 마이크 듣기 제한과 분리합니다. */
    val preparationTimeoutMs: Long get() = 0L
    /** 한 번만 짧게 듣고 결과를 반환합니다. */
    suspend fun listenOnce(timeoutMs: Long = 5_000): String?
    /** 듣기와 대기 중인 인식 결과를 즉시 폐기합니다. */
    fun stop()
}
/** FR-07: 승인된 목적지만 전달받는 내비 연결 경계입니다. */
interface NaviLinker {
    /** 승인된 장소로 길 안내를 요청하고 성공 여부를 반환합니다. */
    suspend fun setDestination(place: Place): Result<Unit>
}
