/*******************************
 목   적 : 로컬 한국어 음성 재생과 실제 완료 통지
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.adapter.fake

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import chageun.adapter.ManagedSpeaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** FR-05, FR-08, FR-13: 데모용 Android TTS이며 네트워크 음성은 선택하지 않습니다. */
class AndroidTtsSpeaker(context: Context) : ManagedSpeaker {
    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    override val available = MutableStateFlow<Boolean?>(null)
    override val limitation = MutableStateFlow<String?>(null)
    private lateinit var engine: TextToSpeech

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val voice = if (status == TextToSpeech.SUCCESS) engine.voices?.firstOrNull {
                it.locale.language == "ko" && !it.isNetworkConnectionRequired
            } else null
            if (voice == null) {
                available.value = false
                ready.completeExceptionally(IllegalStateException("Offline Korean TTS unavailable"))
            } else {
                engine.voice = voice
                available.value = true
                ready.complete(Unit)
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            /** 재생 시작은 듣기 시작으로 취급하지 않습니다. */
            override fun onStart(utteranceId: String?) = Unit
            /** 실제 재생 완료만 대기 중인 요청을 성공 처리합니다. */
            override fun onDone(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(Unit) } }
            /** 재생 실패는 듣기를 시작하지 않도록 예외로 전달합니다. */
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.completeExceptionally(IllegalStateException("TTS failed")) }
            }
        })
    }

    /** 초기화와 실제 재생 완료를 기다리며 느린 재생 속도를 지원합니다. */
    override suspend fun speak(text: String, slow: Boolean) {
        withTimeout(5_000) { ready.await() }
        val id = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Unit>()
        pending[id] = completion
        try {
            engine.setSpeechRate(if (slow) 0.75f else 0.95f)
            check(engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS)
            completion.await()
        } finally { pending.remove(id) }
    }

    /** 주행 상태 변경 시 음성을 즉시 멈추고 완료 대기 요청을 취소합니다. */
    override fun stop() {
        engine.stop()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    /** 액티비티 종료 시 TTS 엔진 자원을 반환합니다. */
    override fun close() { stop(); engine.shutdown() }
}
