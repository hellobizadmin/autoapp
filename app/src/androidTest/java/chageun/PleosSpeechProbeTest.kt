/*******************************
 목   적 : Pleos 온디바이스 한국어 음성의 실제 재생 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-05 공식 음성 SDK 검증 추가
*******************************/
package chageun

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import chageun.monitoring.WarningRuntime
import chageun.adapter.pleos.PleosTtsSpeaker
import chageun.core.AppUiState
import kotlinx.coroutines.flow.first
import java.util.Locale

/** FR-05: 실제 엔진의 초기화·시작·완료 콜백을 확인합니다. */
class PleosSpeechProbeTest {
    /** Pleos 서비스가 없는 일반 휴대폰에서는 이 전용 검증을 실행하지 않습니다. */
    @Before fun requirePleosService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(runCatching { context.packageManager.getPackageInfo("ai.pleos.playground.caas", 0) }.isSuccess)
    }

    /** FR-05·08·14: 앱 실행 경로의 음성 선택·백그라운드 안내·취소·재시도를 확인합니다. */
    @Test fun runtimeUsesKoreanSpeechAndStopsSafely() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = withContext(Dispatchers.Main) { WarningRuntime(context, false).also { it.session.start() } }
        try {
            assertTrue(runtime.speaker is PleosTtsSpeaker)
            withContext(Dispatchers.Main) { runtime.vehicle.setRange(25.0) }
            withTimeout(60_000) { runtime.speaker.available.first { it == true } }
            val screen = runtime.session.state.value.screen as AppUiState.DrivingOverlay
            val spokenText = requireNotNull(screen.text)
            assertFalse(runtime.session.state.value.listening)
            assertTrue(runtime.navi.destinations.isEmpty())
            // FR-08: 일반 속도 다시 듣기는 실제 완료되어야 하고 속도 제한은 표시합니다.
            withTimeout(60_000) { runtime.speaker.speak(spokenText, true) }
            assertEquals(true, runtime.speaker.available.value)
            assertEquals("tts_slow_unsupported", runtime.speaker.limitation.value)
            val playback = async { runtime.speaker.speak(spokenText) }
            delay(500)
            withContext(Dispatchers.Main) { runtime.speaker.stop() }
            assertTrue(runCatching { playback.await() }.exceptionOrNull() is CancellationException)
            withTimeout(60_000) { runtime.speaker.speak(spokenText) }
            assertNull(runtime.speaker.limitation.value)
            assertEquals(true, runtime.speaker.available.value)
        } finally { withContext(Dispatchers.Main) { runtime.close() } }
    }

    /** 외부 서버 없이 사전의 한국어 안내를 한 번 재생합니다. */
    @Test fun koreanOnDevicePlayback() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val ready = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val engine = withContext(Dispatchers.Main) { TextToSpeech(target, Mode.ON_DEVICE) }
        val listener = object : EventListener {
            /** 준비 신호를 기록합니다. */
            override fun onReady() { Log.i("CHAGEUN_TTS_PROBE", "ready") }
            /** 실제 재생 시작을 기록합니다. */
            override fun onStart() { started.complete(Unit); Log.i("CHAGEUN_TTS_PROBE", "start") }
            /** 합성 음성의 재생 완료를 기록합니다. */
            override fun onDone() { done.complete(Unit); Log.i("CHAGEUN_TTS_PROBE", "done") }
            /** 음량 이벤트를 기록합니다. */
            override fun onUpdatedRms(rms: Double) { if (rms > 0) Log.i("CHAGEUN_TTS_PROBE", "rms=$rms") }
            /** 예상하지 않은 중단은 실패로 처리합니다. */
            override fun onStop() { done.completeExceptionally(IllegalStateException("stopped")) }
            /** 엔진 오류를 성공으로 숨기지 않습니다. */
            override fun onError(errorType: ErrorType) { done.completeExceptionally(IllegalStateException(errorType.name)); Log.e("CHAGEUN_TTS_PROBE", errorType.name) }
        }
        try {
            withContext(Dispatchers.Main) {
                engine.initialize(object : OnInitializeListener {
                    /** 초기화 완료 후에만 합성을 요청합니다. */
                    override fun onSuccess() { ready.complete(Unit) }
                    /** 초기화 오류를 전달합니다. */
                    override fun onFailure(errorType: ErrorType) { ready.completeExceptionally(IllegalStateException(errorType.name)) }
                })
            }
            withTimeout(15_000) { ready.await() }
            withContext(Dispatchers.Main) { engine.addEventListener(listener) }
            val json = org.json.JSONObject(target.assets.open("warning_dictionary_ko.json").bufferedReader().use { it.readText() })
            val warnings = json.getJSONArray("warnings")
            val text = (0 until warnings.length()).map { warnings.getJSONObject(it) }.first { it.getString("id") == "RANGE_CRITICAL" }.getString("drive")
                .replace("{range}", "25").replace("{station}", json.getJSONObject("fuel_words").getJSONObject("GASOLINE").getString("station"))
            check(!text.contains("{"))
            withContext(Dispatchers.Main) { engine.setLocale(Locale.KOREA); engine.speak(text) }
            withTimeout(45_000) { done.await() }
            check(started.isCompleted) { "No playback start callback" }
        } finally {
            withContext(Dispatchers.Main) { runCatching { engine.removeEventListener(listener) }; runCatching { engine.stop() }; engine.release() }
        }
    }
}
