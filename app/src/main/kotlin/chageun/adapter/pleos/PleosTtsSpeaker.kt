/*******************************
 목   적 : Pleos 온디바이스 한국어 경고 음성 재생
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-05·08·14 공식 TTS SDK 연결
*******************************/
package chageun.adapter.pleos

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.EventListener
import chageun.adapter.ManagedSpeaker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/** FR-05·14: 서버 등록 없이 ON_DEVICE만 사용하고 실제 완료 후 반환합니다. */
class PleosTtsSpeaker(context: Context) : ManagedSpeaker {
    private val appContext = context.applicationContext
    override val available = MutableStateFlow<Boolean?>(null)
    override val limitation = MutableStateFlow<String?>(null)
    private val mutex = Mutex()
    private var active: TextToSpeech? = null
    private var pending: CompletableDeferred<Unit>? = null
    private var closed = false

    /** FR-08: 사용자 승인에 따라 Pleos는 일반 속도로 다시 읽고 속도 제한을 명시합니다. */
    override suspend fun speak(text: String, slow: Boolean) = withContext(Dispatchers.Main.immediate) {
        check(!closed) { "Speaker closed" }
        limitation.value = if (slow) "tts_slow_unsupported" else null
        mutex.withLock {
            check(!closed) { "Speaker closed" }
            val ready = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            var requested = false
            var finished = false
            val engine = TextToSpeech(appContext, Mode.ON_DEVICE)
            active = engine
            pending = done
            val listener = object : EventListener {
                /** 준비 이벤트를 기록합니다. */
                override fun onReady() = Unit
                /** 실제 재생 시작을 검증 로그에 남깁니다. */
                override fun onStart() { Log.i("CHAGEUN", "Pleos TTS start ko-KR ON_DEVICE") }
                /** 실제 완료 후에만 상세 안내 이후 흐름을 재개합니다. */
                override fun onDone() { done.complete(Unit); Log.i("CHAGEUN", "Pleos TTS done") }
                /** 종료된 요청은 듣기 시작으로 이어지지 않습니다. */
                override fun onStop() { stopped.complete(Unit); done.cancel() }
                /** 오류는 화면의 음성 사용 불가 상태로 전파합니다. */
                override fun onError(errorType: ErrorType) { done.completeExceptionally(IllegalStateException(errorType.name)) }
                /** 음량 값은 저장하거나 외부에 전달하지 않습니다. */
                override fun onUpdatedRms(rms: Double) = Unit
            }
            try {
                engine.initialize(object : OnInitializeListener {
                    /** 초기화 후 리스너 등록이 가능해집니다. */
                    override fun onSuccess() { ready.complete(Unit) }
                    /** 초기화 실패 원인을 전달합니다. */
                    override fun onFailure(errorType: ErrorType) { ready.completeExceptionally(IllegalStateException(errorType.name)) }
                })
                withTimeout(15_000) { ready.await() }
                done.ensureActive()
                engine.addEventListener(listener)
                engine.setLocale(Locale.KOREA)
                engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                requested = true
                engine.speak(text)
                withTimeout(60_000) { done.await() }
                finished = true
                available.value = true
                limitation.value = if (slow) "tts_slow_unsupported" else null
            } catch (timeout: TimeoutCancellationException) {
                available.value = false
                throw timeout
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                available.value = false
                Log.e("CHAGEUN", "Pleos TTS failed", error)
                throw error
            } finally {
                // FR-05: 이전 중단 콜백을 소진한 뒤 다음 요청이 같은 서비스에 연결됩니다.
                if (requested && !finished) withContext(NonCancellable) {
                    runCatching { engine.stop() }
                    withTimeoutOrNull(2_000) { stopped.await() }
                }
                runCatching { engine.removeEventListener(listener) }
                runCatching { engine.release() }
                if (active === engine) { active = null; pending = null }
            }
        }
    }

    /** 상태 전환 시 재생과 완료 대기를 함께 취소합니다. */
    override fun stop() { pending?.cancel() }

    /** 서비스 종료 시 이후 요청을 거부하고 현재 재생을 멈춥니다. */
    override fun close() { closed = true; stop() }
}
