/*******************************
 목   적 : 차량·주행·음성 입력과 내비 시뮬레이션
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.adapter.fake

import chageun.adapter.*
import chageun.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull

// TODO(verify-sdk): 공식 문서 확인 후 실제 차량·주행·STT·내비 어댑터로 교체합니다.
/** FR-12: 지원되는 데모 차량 이벤트를 직접 주입합니다. */
class FakeVehicleSource : VehicleSource {
    private val eventFlow = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 16)
    private val rangeFlow = MutableStateFlow(100.0)
    override val events = eventFlow.asSharedFlow()
    override val rangeKm = rangeFlow.asStateFlow()
    /** FR-15: 구현한 데모 경고만 명시하며 미래 enum 추가가 자동 활성화되지 않게 합니다. */
    override suspend fun supportedWarnings() = setOf(
        WarningId.TIRE_LOW, WarningId.TIRE_RAPID_DROP, WarningId.RANGE_LOW, WarningId.RANGE_CRITICAL
    ) + phaseTwoWarnings
    /** 디버그 패널의 발생 및 해소 입력을 전달합니다. */
    fun emit(event: VehicleEvent) { check(eventFlow.tryEmit(event)) }
    /** 디버그 슬라이더의 거리 값을 전달합니다. */
    fun setRange(km: Double) { require(km.isFinite() && km >= 0); rangeFlow.value = km }
}
/** FR-06, FR-12: 초기 UNKNOWN 상태와 데모 상태 전환을 제공합니다. */
class FakeDriveStateSource : DriveStateSource {
    private val mutable = MutableStateFlow(DriveState.UNKNOWN)
    override val state = mutable.asStateFlow()
    /** 디버그 입력으로 주행 상태를 변경합니다. */
    fun set(state: DriveState) { mutable.value = state }
}
/** FR-13: 활성 듣기 중에만 텍스트 입력을 수락하며 미리 입력한 결과를 저장하지 않습니다. */
class FakeListener : Listener {
    private var pending: CompletableDeferred<String?>? = null
    /** 제한 시간 동안 단일 텍스트 인식을 기다립니다. */
    override suspend fun listenOnce(timeoutMs: Long): String? {
        stop()
        val request = CompletableDeferred<String?>()
        pending = request
        return try { withTimeoutOrNull(timeoutMs) { request.await() } }
        finally { if (pending === request) pending = null }
    }
    /** 현재 듣기 세션에만 결과를 제출합니다. */
    fun submit(text: String): Boolean = pending?.complete(text) ?: false
    /** 듣기 종료와 함께 기존 입력 세션을 폐기합니다. */
    override fun stop() { pending?.complete(null); pending = null }
}
/** FR-07: 실제 내비 변경 없이 승인된 목적지를 기록합니다. */
class LogNaviLinker(private val log: (String) -> Unit = {}) : NaviLinker {
    val destinations = mutableListOf<Place>()
    var shouldFail = false
    /** 데모 목적지 요청을 기록하고 설정된 성공 또는 실패 결과를 반환합니다. */
    override suspend fun setDestination(place: Place): Result<Unit> {
        destinations += place
        log("CHAGEUN simulated navigation: ${place.name}")
        return if (shouldFail) Result.failure(IllegalStateException("simulated failure")) else Result.success(Unit)
    }
}
