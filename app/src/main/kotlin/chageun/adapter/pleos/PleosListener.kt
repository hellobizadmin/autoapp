/*******************************
 목   적 : Pleos 온디바이스 한국어 단발성 마이크 인식
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-13 공식 STT 연결
*******************************/
package chageun.adapter.pleos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import chageun.adapter.Listener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/** FR-13: 마이크 동의가 있을 때만 최대 5초 인식하고 최종 결과만 반환합니다. */
class PleosListener(context: Context) : Listener {
    override val preparationTimeoutMs = 15_000L
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var requestJob: Job? = null
    val status = MutableStateFlow<String?>(null)
    val recognizing = MutableStateFlow(false)
    val inputLevel = MutableStateFlow(0.0)

    /** 초기화·인식·종료를 한 요청에 묶어 취소된 결과가 다음 요청으로 넘어가지 않게 합니다. */
    override suspend fun listenOnce(timeoutMs: Long): String? = withContext(Dispatchers.Main.immediate) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.value = "microphone_denied"
            return@withContext null
        }
        mutex.withLock {
            coroutineScope {
                requestJob = currentCoroutineContext()[Job]
                val engine = SpeechToText(appContext, Mode.ON_DEVICE)
                val ready = CompletableDeferred<Unit>()
                val result = CompletableDeferred<String?>()
                val ended = CompletableDeferred<Unit>()
                var requested = false
                val listener = object : ResultListener {
                    /** 요청 준비 이벤트는 인식 결과가 아닙니다. */
                    override fun onReady() { recognizing.value = true; Log.i("CHAGEUN", "Pleos STT microphone ready") }
                    /** 실제 마이크 인식 시작을 표시합니다. */
                    override fun onStartedRecognition() {
                        recognizing.value = true
                        Log.i("CHAGEUN", "Pleos STT microphone started ON_DEVICE ko-KR")
                    }
                    /** 중간 결과는 승인에 쓰지 않고 최종 문장만 전달합니다. */
                    override fun onUpdated(stt: String, completed: Boolean) {
                        if (completed) {
                            Log.i("CHAGEUN", "Pleos STT final received")
                            result.complete(stt.takeIf { it.isNotBlank() })
                        }
                    }
                    /** 원본 음성과 음성 구간을 저장하지 않습니다. */
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    /** 마이크 레벨을 외부 전송하거나 저장하지 않습니다. */
                    override fun onUpdatedRms(rms: Double) { inputLevel.value = rms }
                    /** 종료 확인으로 다음 요청과 이전 콜백을 분리합니다. */
                    override fun onEndedRecognition() { recognizing.value = false; ended.complete(Unit) }
                    /** 오류 시 버튼 사용을 유지하고 명령을 추측하지 않습니다. */
                    override fun onError(errorType: ErrorType) {
                        status.value = "microphone_unavailable"
                        Log.w("CHAGEUN", "Pleos STT error: $errorType")
                        result.complete(null)
                    }
                }
                try {
                    status.value = "microphone_preparing"
                    inputLevel.value = 0.0
                    withTimeout(preparationTimeoutMs) {
                        engine.initialize(object : OnInitializeListener {
                            /** 초기화가 끝나야 리스너를 등록할 수 있습니다. */
                            override fun onSuccess() { ready.complete(Unit) }
                            /** 초기화 실패 시 요청을 시작하지 않습니다. */
                            override fun onFailure(errorType: ErrorType) { ready.completeExceptionally(IllegalStateException(errorType.name)) }
                        })
                        ready.await()
                    }
                    ensureActive()
                    engine.addListener(listener)
                    engine.setLocale(Locale.KOREA)
                    status.value = null
                    withTimeoutOrNull(timeoutMs.coerceIn(1, 5_000)) {
                        requested = true
                        engine.request()
                        result.await()
                    }
                } catch (timeout: TimeoutCancellationException) {
                    status.value = "microphone_unavailable"
                    Log.w("CHAGEUN", "Pleos STT initialization timed out")
                    null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    status.value = "microphone_unavailable"
                    Log.w("CHAGEUN", "Pleos STT unavailable", error)
                    null
                } finally {
                    // FR-06·14: 취소 즉시 stop을 호출하고 마이크 자원을 항상 반환합니다.
                    if (requested) withContext(NonCancellable) {
                        runCatching { engine.stop() }
                        withTimeoutOrNull(500) { ended.await() }
                    }
                    runCatching { engine.removeListener(listener) }
                    runCatching { engine.release() }
                    recognizing.value = false
                    if (status.value == "microphone_preparing") status.value = null
                    requestJob = null
                }
            }
        }
    }

    /** 주행·상태 불명·화면 종료 전환 시 초기화와 듣기를 함께 취소합니다. */
    override fun stop() { requestJob?.cancel() }
}
