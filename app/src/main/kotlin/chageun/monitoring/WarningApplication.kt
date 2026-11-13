/*******************************
 목   적 : 화면과 독립적인 차량 경고 감시 수명 관리
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-14 백그라운드 감시 추가
*******************************/
package chageun.monitoring

import android.app.Application
import android.content.Context
import android.util.Log
import chageun.adapter.fake.*
import chageun.adapter.ManagedSpeaker
import chageun.adapter.Listener
import chageun.adapter.pleos.PleosListener
import chageun.adapter.pleos.PleosTtsSpeaker
import chageun.core.*
import chageun.runtime.WarningSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** FR-14: 서비스만 감시 자원을 생성·해제하고 화면은 같은 세션을 구독합니다. */
class WarningApplication : Application() {
    private val mutableRuntime = MutableStateFlow<WarningRuntime?>(null)
    val runtime = mutableRuntime.asStateFlow()
    val status = MutableStateFlow("monitor_starting")
    private var visibleScreens = 0

    /** 화면 수를 반영하여 화면 교체 중에도 감시 세션을 중복 생성하지 않습니다. */
    fun screenVisible(visible: Boolean) {
        visibleScreens = (visibleScreens + if (visible) 1 else -1).coerceAtLeast(0)
        mutableRuntime.value?.session?.setForeground(visibleScreens > 0)
    }

    /** 포그라운드 서비스 시작 후 한 번만 어댑터와 수집 작업을 만듭니다. */
    fun startMonitoring(): WarningRuntime {
        mutableRuntime.value?.let { return it }
        return WarningRuntime(this, visibleScreens > 0).also {
            mutableRuntime.value = it
            it.session.start()
            status.value = "monitor_demo"
        }
    }

    /** 명시적 중지 또는 서비스 종료 시 음성 엔진과 코루틴을 정리합니다. */
    fun stopMonitoring() {
        mutableRuntime.value?.close()
        mutableRuntime.value = null
        status.value = "monitor_stopped"
    }
}

/** FR-01, FR-14: 액티비티 참조 없이 어댑터를 보유하는 서비스 실행 단위입니다. */
class WarningRuntime(context: Context, foreground: Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val dictionary = WarningDictionary.parse(asset(context, "warning_dictionary_ko.json"), ::monitorLog).getOrThrow()
    // TODO(verify-sdk): 공식 Vehicle/DriveState SDK 구독 및 재연결 시 현재 경고 조회로 교체합니다.
    val vehicle = FakeVehicleSource()
    val drive = FakeDriveStateSource()
    val hasPleosService = runCatching { context.packageManager.getPackageInfo("ai.pleos.playground.caas", 0) }.isSuccess
    val microphone = if (hasPleosService) PleosListener(context) else null
    val listener: Listener = microphone ?: FakeListener()
    val navi = LogNaviLinker(::monitorLog)
    private val commands = VoiceCommands.parse(asset(context, "voice_commands_ko.json"))
    private val places = DemoPlaces.parse(asset(context, "demo_places.json"))
    private val coordinator = WarningCoordinator(dictionary, UrgencyRules.parse(asset(context, "warning_rules.json")))
    // FR-05: 공식 서비스가 설치된 Pleos는 온디바이스 SDK, 일반 기기는 Android TTS를 사용합니다.
    val speaker: ManagedSpeaker = if (hasPleosService) PleosTtsSpeaker(context) else AndroidTtsSpeaker(context)
    val session = WarningSession(coordinator, dictionary, commands, places, vehicle, drive, speaker, listener,
        navi, scope, ::monitorLog, initialForeground = foreground)

    /** 감시 종료 시 입력과 출력의 모든 자원을 반환합니다. */
    fun close() { session.close(); speaker.close(); scope.cancel() }
}

/** 사전 및 설정을 UTF-8로 읽습니다. */
internal fun asset(context: Context, name: String): String =
    context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

/** 공통 태그에 감시 수명과 어댑터 상태를 기록합니다. */
internal fun monitorLog(message: String) { Log.i("CHAGEUN", message) }
