/*******************************
 목   적 : 서비스 감시 세션과 Compose 화면 연결
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : FR-14 서비스 세션 연결
*******************************/
package chageun

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import chageun.monitoring.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chageun.app.BuildConfig
import chageun.core.*
import chageun.ui.*

/** FR-01, FR-12: 순수 코어와 데모 어댑터를 조립하며 실제 차량 SDK는 사용하지 않습니다. */
class MainActivity : ComponentActivity() {
    private val app get() = application as WarningApplication
    private var notificationsAllowed by mutableStateOf(true)
    private var microphoneAllowed by mutableStateOf(false)
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        microphoneAllowed = it
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
        requestMicrophoneOnce()
    }

    /** 사전 파일과 어댑터를 준비하고 파싱 실패 시 오류 화면을 표시합니다. */
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dictionary = runCatching { WarningDictionary.parse(asset("warning_dictionary_ko.json"), ::log).getOrThrow() }.getOrNull()
        val fallback = runCatching { com.google.gson.JsonParser.parseString(asset("warning_dictionary_ko.json")).asJsonObject.getAsJsonObject("ui")["loading_error"].asString }
            .getOrDefault("⚠")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ChageunColors.Primary, background = ChageunColors.Background)) {
                var panel by remember { mutableStateOf(false) }
                val runtime = app.runtime.collectAsState().value
                val initialized = runtime?.session
                val status = app.status.collectAsState().value
                val state = initialized?.state?.collectAsState()?.value
                val microphoneStatus = runtime?.microphone?.status?.collectAsState()?.value
                val microphoneRecognizing = runtime?.microphone?.recognizing?.collectAsState()?.value
                val ttsLimitation = runtime?.speaker?.limitation?.collectAsState()?.value
                val ttsAvailable = runtime?.speaker?.available?.collectAsState()?.value
                Column(Modifier.fillMaxSize().background(ChageunColors.Background).windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp)) {
                    if (dictionary == null || state?.failed == true) {
                        Text(fallback, fontSize = 32.sp, color = ChageunColors.TextPrimary, modifier = Modifier.testTag("error"))
                    } else if (initialized == null) {
                        Text(dictionary.text(status), fontSize = 32.sp, color = ChageunColors.TextPrimary)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dictionary.text("app_name"), fontSize = 32.sp, color = ChageunColors.TextPrimary, fontWeight = FontWeight.Bold)
                            Text(dictionary.text("demo"), fontSize = 20.sp, color = ChageunColors.TextSecondary,
                                modifier = if (BuildConfig.DEBUG) Modifier.testTag("demo_toggle").combinedClickable(onClick = {}, onLongClick = { panel = true }) else Modifier)
                        }
                        if (!notificationsAllowed) Text(dictionary.text("notifications_denied"), fontSize = 20.sp, color = ChageunColors.Warn)
                        if (runtime.microphone != null && !microphoneAllowed) Text(dictionary.text("microphone_denied"), fontSize = 20.sp, color = ChageunColors.Warn)
                        else microphoneStatus?.let { Text(dictionary.text(it), fontSize = 20.sp, color = ChageunColors.Warn) }
                        ttsLimitation?.let { Text(dictionary.text(it), fontSize = 20.sp, color = ChageunColors.Warn) }
                        if (ttsAvailable == false) Text(dictionary.text("tts_unavailable"), fontSize = 20.sp, color = ChageunColors.Warn)
                        Spacer(Modifier.height(32.dp))
                        Box(Modifier.weight(1f)) {
                            val screen = state?.screen ?: AppUiState.Idle
                            // 왼쪽 차량 그림(경고 부위 강조) + 오른쪽 안내 화면
                            SplitWarningLayout(screen, dictionary) {
                                when (screen) {
                                    AppUiState.Idle -> IdleScreen(dictionary)
                                    is AppUiState.DrivingOverlay -> DrivingOverlay(screen, dictionary)
                                    is AppUiState.Detail -> DetailScreen(screen, state?.listening == true && (microphoneRecognizing ?: true), dictionary, initialized::approve, initialized::repeat)
                                }
                            }
                        }
                        if (BuildConfig.DEBUG && panel) DemoPanel(dictionary, runtime.vehicle, runtime.drive, runtime.listener, runtime.navi, state?.listening == true) { panel = false }
                    }
                }
            }
        }
    }

    /** assets 문구와 설정을 UTF-8 문자열로 읽습니다. */
    private fun asset(name: String): String = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    /** 차량 및 음성 이벤트를 지정된 로그 태그로 기록합니다. */
    private fun log(message: String) { Log.i("CHAGEUN", message) }
    /** FR-14: 보이는 화면에서 감시를 시작하고 알림 권한을 한 번 요청합니다. */
    override fun onStart() {
        super.onStart()
        app.screenVisible(true)
        WarningMonitorService.preferences(this).edit().putBoolean(WarningMonitorService.ENABLED, true).apply()
        WarningMonitorService.start(this)
        notificationsAllowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val prefs = WarningMonitorService.preferences(this)
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed && !prefs.getBoolean("notification_requested", false)) {
            prefs.edit().putBoolean("notification_requested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else requestMicrophoneOnce()
    }

    /** FR-13: 화면에서 한 번만 동의를 요청하며 거부해도 버튼 사용을 유지합니다. */
    private fun requestMicrophoneOnce() {
        microphoneAllowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val prefs = WarningMonitorService.preferences(this)
        if (runCatching { packageManager.getPackageInfo("ai.pleos.playground.caas", 0) }.isSuccess && !microphoneAllowed && !prefs.getBoolean("microphone_requested", false)) {
            prefs.edit().putBoolean("microphone_requested", true).apply()
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** FR-13: 설정에서 변경한 마이크 권한을 복귀 시 반영합니다. */
    override fun onResume() { super.onResume(); microphoneAllowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }

    /** FR-14: 화면만 분리하며 서비스의 차량 감시와 새 경고 음성은 유지합니다. */
    override fun onStop() { app.screenVisible(false); super.onStop() }
}
