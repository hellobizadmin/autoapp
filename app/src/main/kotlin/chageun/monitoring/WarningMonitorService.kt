/*******************************
 목   적 : 화면 종료 후 경고 감지와 자동 음성·알림 제공
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-14 포그라운드 서비스 추가
*******************************/
package chageun.monitoring

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import chageun.MainActivity
import chageun.app.R
import chageun.core.AppUiState
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** FR-14: 화면이 없어도 감시하며 시스템 알림을 통해서만 화면 진입을 제공합니다. */
class WarningMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as WarningApplication
    private val notifications get() = getSystemService(NotificationManager::class.java)
    private var labels: Map<String, String> = emptyMap()

    /** 감시 시작 전 알림을 게시하여 포그라운드 서비스 시작 기한을 준수합니다. */
    override fun onCreate() {
        super.onCreate()
        labels = runCatching {
            JsonParser.parseString(asset(this, "warning_dictionary_ko.json")).asJsonObject
                .getAsJsonObject("ui").entrySet().associate { it.key to it.value.asString }
        }.getOrDefault(emptyMap())
        notifications.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, text("monitor_channel"), NotificationManager.IMPORTANCE_LOW))
        notifications.createNotificationChannel(NotificationChannel(WARNING_CHANNEL, text("warning_channel"), NotificationManager.IMPORTANCE_HIGH))
        try {
            val notice = notification(MONITOR_CHANNEL, text("monitor_demo")).setOngoing(true)
                .addAction(Notification.Action.Builder(null, text("monitor_stop"), PendingIntent.getService(this, 1,
                    Intent(this, WarningMonitorService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
                .build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(MONITOR_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(MONITOR_ID, notice)
            val runtime = app.startMonitoring()
            scope.launch {
                runtime.session.state.map { state ->
                    if (!state.foreground) state.screen as? AppUiState.DrivingOverlay else null
                }.distinctUntilChanged().collect { warning ->
                    if (warning?.text == null) notifications.cancel(WARNING_ID)
                    else {
                        // 알림 거부 시에도 음성과 감시는 유지합니다. 화면 강제 실행은 하지 않습니다.
                        runCatching { notifications.notify(WARNING_ID, notification(WARNING_CHANNEL, warning.text!!)
                            .setCategory(Notification.CATEGORY_EVENT).setOnlyAlertOnce(true).setAutoCancel(true).build()) }
                            .onFailure { monitorLog("CHAGEUN warning notification unavailable") }
                    }
                }
            }
            monitorLog("CHAGEUN monitor started (fake vehicle source)")
        } catch (error: Exception) {
            monitorLog("CHAGEUN monitor startup failed: ${error.javaClass.simpleName}")
            app.status.value = "monitor_error"
            stopSelf()
        }
    }

    /** 사용자 중지는 저장하며 시스템 메모리 회수에는 재시작을 요청합니다. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            preferences(this).edit().putBoolean(ENABLED, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /** 화면은 애플리케이션의 StateFlow를 사용하므로 바인더를 노출하지 않습니다. */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 서비스 종료 시 경고 알림과 감시 자원을 함께 해제합니다. */
    override fun onDestroy() {
        scope.cancel()
        val failed = app.status.value == "monitor_error"
        app.stopMonitoring()
        if (failed) app.status.value = "monitor_error"
        notifications.cancel(WARNING_ID)
        super.onDestroy()
    }

    /** 사용자 탭으로만 화면을 열며 내비 승인 동작은 포함하지 않습니다. */
    private fun notification(channel: String, message: String): Notification.Builder = Notification.Builder(this, channel)
        .setSmallIcon(R.drawable.ic_warning).setContentTitle(text("app_name")).setContentText(message)
        .setStyle(Notification.BigTextStyle().bigText(message))
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

    /** 사전 파싱 실패 시에도 알림 생성은 안전한 기호로 처리합니다. */
    private fun text(key: String): String = labels[key] ?: "⚠"

    /** 서비스 시작과 재부팅 복구에 공유하는 식별자입니다. */
    companion object {
        const val MONITOR_CHANNEL = "warning_monitor"
        const val WARNING_CHANNEL = "vehicle_warning"
        const val MONITOR_ID = 1400
        const val WARNING_ID = 1401
        const val STOP = "chageun.monitor.STOP"
        const val ENABLED = "enabled"
        /** 재부팅 시 감시 재개 여부만 저장하며 차량 경고 이력은 저장하지 않습니다. */
        fun preferences(context: Context): android.content.SharedPreferences = context.getSharedPreferences("warning_monitor", Context.MODE_PRIVATE)
        /** 허용된 진입점에서 서비스를 시작하고 플랫폼 거부를 화면 상태로 알립니다. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, WarningMonitorService::class.java))
            true
        } catch (error: Exception) {
            (context.applicationContext as WarningApplication).status.value = "monitor_error"
            monitorLog("CHAGEUN monitor start rejected: ${error.javaClass.simpleName}")
            false
        }
    }
}
