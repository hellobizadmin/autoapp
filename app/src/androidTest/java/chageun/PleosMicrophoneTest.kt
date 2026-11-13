/*******************************
 목   적 : Pleos 마이크 인식의 권한·취소·실제 발화 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-13 마이크 계측 테스트 추가
*******************************/
package chageun

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import chageun.adapter.pleos.PleosListener
import chageun.core.VoiceCommands
import chageun.core.WarningDictionary
import chageun.core.WarningId
import chageun.adapter.pleos.PleosTtsSpeaker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

/** FR-13: 실제 Pleos 서비스를 사용하며 수동 발화 테스트는 명시적으로만 실행합니다. */
class PleosMicrophoneTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 권한 거부 시 SDK 요청과 마이크 시작 없이 null을 반환합니다. */
    @Test fun deniedPermissionDoesNotListen() = runBlocking {
        assumeTrue(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        val listener = PleosListener(context)
        assertNull(listener.listenOnce(5_000))
        assertFalse(listener.recognizing.value)
        assertEquals("microphone_denied", listener.status.value)
    }

    /** 실제 마이크 시작 후 취소하면 인식 자원과 지연 결과를 폐기합니다. */
    @Test fun microphoneStopsAndTimesOut() = runBlocking {
        assumeTrue(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val listener = PleosListener(context)
        try {
            val result = async { listener.listenOnce(5_000) }
            withTimeout(listener.preparationTimeoutMs + 5_000) { listener.recognizing.first { it } }
            withContext(Dispatchers.Main) { listener.stop() }
            assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
            assertFalse(listener.recognizing.value)
            listener.listenOnce(1_000)
            assertFalse(listener.recognizing.value)
        } finally { withContext(Dispatchers.Main) { listener.stop() } }
    }

    /** Mac 마이크로 말한 명령을 실제 STT와 사전 판정까지 전달합니다. */
    @Test fun liveMicrophoneCommand() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveMic") == "true")
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val listener = PleosListener(context)
        val commands = VoiceCommands.parse(context.assets.open("voice_commands_ko.json").bufferedReader().use { it.readText() })
        try {
            val dictionary = WarningDictionary.parse(context.assets.open("warning_dictionary_ko.json").bufferedReader().use { it.readText() }).getOrThrow()
            val prompt = requireNotNull(dictionary.resolve(WarningId.RANGE_CRITICAL, mapOf("range" to "25")))
            val speaker = PleosTtsSpeaker(context)
            try { speaker.speak(requireNotNull(prompt.park)) } finally { withContext(Dispatchers.Main) { speaker.close() } }
            val levelJob = launch { listener.inputLevel.collect { if (it > 0) Log.i("CHAGEUN_STT_TEST", "Microphone signal detected") } }
            // 준비 시간이 발화 시간을 잠식하지 않도록 실제 준비 콜백 후 시작음을 냅니다.
            val cueJob = launch {
                listener.recognizing.first { it }
                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
                try { tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150); delay(200) }
                finally { tone.release() }
            }
            val text = try { listener.listenOnce(5_000) } finally { levelJob.cancel(); cueJob.cancelAndJoin() }
            assertNotNull("No final microphone recognition result", text)
            val command = commands.match(text!!)
            Log.i("CHAGEUN_STT_TEST", "Recognized command=$command")
            assertNotNull("Speech was not a known unambiguous command", command)
        } finally { withContext(Dispatchers.Main) { listener.stop() } }
    }
}
