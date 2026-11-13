/*******************************
 목   적 : 화면 종료 후 서비스 감시와 알림의 실제 Android 동작 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-14 서비스 수명 테스트 추가
*******************************/
package chageun

import android.app.NotificationManager
import android.content.Intent
import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import chageun.core.*
import chageun.monitoring.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** FR-14: 실제 서비스가 액티비티 종료와 독립적으로 동작하는지 확인합니다. */
class WarningMonitorServiceTest {
    /** 화면 파괴 후 새 경고를 주입하고 재실행 시 같은 세션의 상세를 확인합니다. */
    @Test fun destroyedActivityKeepsMonitorAndPostsWarning() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WarningApplication
        WarningMonitorService.preferences(context).edit().putBoolean("notification_requested", true).commit()
        var activity: Activity? = null
        try {
            activity = launchActivity(app)
            val runtime = withTimeout(10_000) { app.runtime.first { it != null }!! }
            withContext(Dispatchers.Main) { runtime.drive.set(DriveState.PARKED) }
            withContext(Dispatchers.Main) { activity?.finishAndRemoveTask() }; activity = null
            withTimeout(5_000) { runtime.session.state.first { !it.foreground } }
            assertSame(runtime, app.runtime.value)
            withContext(Dispatchers.Main) {
                runtime.vehicle.emit(VehicleEvent.Raised(WarningId.TIRE_LOW, mapOf("position" to "REAR_LEFT")))
            }
            val state = withTimeout(5_000) { runtime.session.state.first { it.screen is AppUiState.DrivingOverlay } }
            assertFalse(state.listening)
            val notifications = context.getSystemService(NotificationManager::class.java)
            if (notifications.areNotificationsEnabled()) {
                withTimeout(5_000) {
                    while (notifications.activeNotifications.none { it.id == WarningMonitorService.WARNING_ID }) delay(50)
                }
                val alert = notifications.activeNotifications.first { it.id == WarningMonitorService.WARNING_ID }.notification
                assertEquals((state.screen as AppUiState.DrivingOverlay).text, alert.extras.getCharSequence("android.text").toString())
                assertNotNull(alert.contentIntent)
            }
            activity = launchActivity(app)
            withTimeout(5_000) { runtime.session.state.first { it.foreground && it.screen is AppUiState.Detail } }
            assertSame(runtime, app.runtime.value)
            assertTrue(runtime.navi.destinations.isEmpty())
            // 감시 중지는 저장되어 다음 부팅 수신으로도 다시 켜지지 않아야 합니다.
            context.startService(Intent(context, WarningMonitorService::class.java).setAction(WarningMonitorService.STOP))
            withTimeout(5_000) { app.runtime.first { it == null } }
            assertFalse(WarningMonitorService.preferences(context).getBoolean(WarningMonitorService.ENABLED, true))
            withContext(Dispatchers.Main) { MonitorBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED)) }
            assertNull(app.runtime.value)
        } finally {
            withContext(Dispatchers.Main) { activity?.finishAndRemoveTask() }
            context.stopService(Intent(context, WarningMonitorService::class.java))
        }
    }
    /** Pleos 다중 창에서 동기 시작 대기 대신 실제 RESUMED 콜백을 제한 시간 내 기다립니다. */
    private suspend fun launchActivity(app: WarningApplication): Activity {
        val resumed = CompletableDeferred<Activity>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            /** 대상 화면의 재개 완료를 통지합니다. */
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) resumed.complete(activity) }
            /** 생성은 재개 완료로 간주하지 않습니다. */
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            /** 시작은 재개 완료로 간주하지 않습니다. */
            override fun onActivityStarted(activity: Activity) = Unit
            /** 일시 정지에는 대기 결과를 변경하지 않습니다. */
            override fun onActivityPaused(activity: Activity) = Unit
            /** 중지는 세션의 foreground 상태로 검증합니다. */
            override fun onActivityStopped(activity: Activity) = Unit
            /** 저장 상태는 테스트에 필요하지 않습니다. */
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            /** 화면 파괴 후에도 서비스 유지 여부를 별도로 검증합니다. */
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        return withContext(Dispatchers.Main) {
            app.registerActivityLifecycleCallbacks(callbacks)
            try {
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                withTimeout(10_000) { resumed.await() }
            } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
        }
    }

}
