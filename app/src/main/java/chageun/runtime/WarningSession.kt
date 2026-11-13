/*******************************
 목   적 : 어댑터 실행과 음성 듣기 세션 안전 제어
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.runtime

import chageun.adapter.*
import chageun.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** FR-01, FR-13: 화면, 듣기 여부 및 오류 상태의 불변 스냅샷입니다. */
data class SessionState(val screen: AppUiState = AppUiState.Idle, val listening: Boolean = false, val failed: Boolean = false,
                        val foreground: Boolean = true)

/** FR-01~13: 단일 디스패처에서 코어와 어댑터를 연결하고 오래된 비동기 응답을 폐기합니다. */
class WarningSession(
    private val coordinator: WarningCoordinator,
    private val dictionary: WarningDictionary,
    private val commands: VoiceCommands,
    private val places: Map<String, List<Place>>,
    private val vehicle: VehicleSource,
    private val drive: DriveStateSource,
    private val speaker: Speaker,
    private val listener: Listener,
    private val navi: NaviLinker,
    parentScope: CoroutineScope,
    private val log: (String) -> Unit = {},
    initialForeground: Boolean = true,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val mutable = MutableStateFlow(SessionState(foreground = initialForeground))
    val state: StateFlow<SessionState> = mutable.asStateFlow()
    private var interaction: Job? = null
    private var generation = 0L
    private var started = false
    private var supported = emptySet<WarningId>()
    private var foreground = initialForeground

    /** FR-01, FR-09: 지원 목록을 먼저 확인한 후 차량 입력 수집을 시작합니다. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                supported = vehicle.supportedWarnings()
                log("CHAGEUN supported: $supported")
                launch {
                    try { drive.state.collect { updateDrive(if (foreground) it else DriveState.UNKNOWN) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { updateDrive(DriveState.UNKNOWN); log("CHAGEUN drive unavailable") }
                }
                launch {
                    try {
                        vehicle.events.collect { event ->
                            val id = when (event) { is VehicleEvent.Raised -> event.id; is VehicleEvent.Cleared -> event.id }
                            if (id in supported) {
                                log("CHAGEUN event: $event")
                                apply(coordinator.onVehicleEvent(event))
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { log("CHAGEUN vehicle unavailable: ${error.javaClass.simpleName}") }
                }
                launch {
                    try {
                        vehicle.rangeKm.collect { km ->
                            if (WarningId.RANGE_LOW in supported && WarningId.RANGE_CRITICAL in supported && km.isFinite() && km >= 0)
                                apply(coordinator.onRangeChanged(km))
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { log("CHAGEUN range unavailable: ${error.javaClass.simpleName}") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = SessionState(failed = true, foreground = foreground); log("CHAGEUN startup failed") }
        }
    }

    /** FR-06, FR-14: 화면을 닫아도 감시는 유지하며 새 경고는 안전한 한 문장으로 안내합니다. */
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        mutable.value = mutable.value.copy(foreground = value)
        updateDrive(if (value) drive.state.value else DriveState.UNKNOWN)
        if (!value) cancelInteraction()
    }

    /** FR-06, FR-13: 주행 전환은 재생과 듣기 취소보다 먼저 코어에 반영합니다. */
    private fun updateDrive(value: DriveState) {
        log("CHAGEUN drive: $value")
        apply(coordinator.onDriveState(value))
    }

    /** FR-07: 화면에 표시된 경고의 명시적인 버튼 승인을 처리합니다. */
    fun approve() {
        if (!foreground || drive.state.value != DriveState.PARKED) return
        val detail = coordinator.uiState as? AppUiState.Detail ?: return
        apply(coordinator.approveNavigation(detail.warning.id, places))
    }

    /** FR-08: 현재 정차 상세 문구를 한 번 천천히 다시 재생합니다. */
    fun repeat() {
        if (foreground && drive.state.value == DriveState.PARKED) apply(coordinator.repeatDetail())
    }

    /** 변경된 화면과 요청을 반영하며 취소된 작업의 결과는 다음 세션에 적용하지 않습니다. */
    private fun apply(effects: List<Effect>) {
        val next = coordinator.uiState
        val changed = mutable.value.screen != next
        if (changed || effects.isNotEmpty()) cancelInteraction()
        mutable.value = mutable.value.copy(screen = next)
        if (effects.isEmpty()) return
        val token = generation
        interaction = scope.launch {
            for (effect in effects) {
                if (token != generation) return@launch
                when (effect) {
                    is Effect.Speak -> {
                        // FR-14: 백그라운드에서는 상세 음성·마이크 없이 drive 문장만 허용합니다.
                        if (!foreground && next !is AppUiState.DrivingOverlay) continue
                        val completed = speakSafely(effect.text, effect.slow)
                        if (completed && !effect.slow && next is AppUiState.Detail && token == generation)
                            listenAfterDetail(token, next.warning.id)
                    }
                    is Effect.SetDestination -> {
                        if (!foreground) continue
                        log("CHAGEUN navigation request: ${effect.place.name}")
                        val result = try { navi.setDestination(effect.place) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { Result.failure(error) }
                        if (token == generation && coordinator.driveState == DriveState.PARKED) {
                            val detail = coordinator.uiState as? AppUiState.Detail
                            val text = if (result.isSuccess) dictionary.text("navigation_success", mapOf("category" to detail?.warning?.action?.category.orEmpty()))
                                else dictionary.text("navigation_failure")
                            speakSafely(text)
                        }
                    }
                }
            }
        }
    }

    /** FR-13: 상세 재생 완료 후 단 한 번 듣고 현재 세션의 인식 결과만 실행합니다. */
    private suspend fun listenAfterDetail(token: Long, id: WarningId) {
        if (coordinator.driveState != DriveState.PARKED || drive.state.value != DriveState.PARKED || !foreground) return
        mutable.value = mutable.value.copy(listening = true)
        // FR-13: SDK 초기화 때문에 사용자의 5초 발화 시간이 줄어들지 않게 합니다.
        val text = try { withTimeoutOrNull(5_000 + listener.preparationTimeoutMs.coerceIn(0, 15_000)) { listener.listenOnce(5_000) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { log("CHAGEUN listener unavailable"); null }
        finally { if (token == generation) { runCatching { listener.stop() }; mutable.value = mutable.value.copy(listening = false) } }
        val detail = coordinator.uiState as? AppUiState.Detail ?: return
        if (token != generation || coordinator.driveState != DriveState.PARKED || detail.warning.id != id || drive.state.value != DriveState.PARKED || !foreground) return
        when (commands.match(text)) {
            VoiceCommand.REPEAT -> repeat()
            VoiceCommand.ACCEPT_NAVI -> approve()
            VoiceCommand.DISMISS -> {
                val effects = coordinator.dismissDetail()
                // 다음 경고가 있으면 그 안내를 우선하며 닫기 응답으로 듣기 루프를 만들지 않습니다.
                if (effects.isNotEmpty()) apply(effects)
                else {
                    apply(emptyList())
                    val dismissToken = generation
                    interaction = scope.launch { if (dismissToken == generation) speakSafely(commands.dismissResponse) }
                }
            }
            null -> Unit
        }
    }

    /** 음성 오류는 화면과 버튼 사용을 유지하면서 해당 안내만 중단합니다. */
    private suspend fun speakSafely(text: String, slow: Boolean = false): Boolean = try {
        log("CHAGEUN TTS slow=$slow")
        withTimeout(60_000) { speaker.speak(text, slow) }
        true
    } catch (cancelled: TimeoutCancellationException) { runCatching { speaker.stop() }; false }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { log("CHAGEUN speaker unavailable"); false }

    /** FR-13: 세대 번호 갱신으로 지연된 재생 완료 및 인식 결과를 무효화합니다. */
    private fun cancelInteraction() {
        generation++
        interaction?.cancel()
        interaction = null
        runCatching { speaker.stop() }.onFailure { log("CHAGEUN speaker stop failed") }
        runCatching { listener.stop() }.onFailure { log("CHAGEUN listener stop failed") }
        mutable.value = mutable.value.copy(listening = false)
    }

    /** 감시 서비스 종료 시 모든 입력 수집과 음성 작업을 정리합니다. */
    fun close() { cancelInteraction(); scope.cancel() }
}
