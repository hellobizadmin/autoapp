/*******************************
 목   적 : 핵심 경고 선택·발생·해소 데모 패널 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-12·15 추가
*******************************/
package chageun

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import chageun.adapter.fake.*
import chageun.core.*
import chageun.monitoring.*
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** 새 경고를 실제 패널 조작으로 선택하고 해당 이벤트가 전달되는지 확인합니다. */
class CoreWarningDemoPanelTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun selectRaiseAndClearEachCoreWarning() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WarningApplication
        context.stopService(Intent(context, WarningMonitorService::class.java))
        withTimeout(5_000) { app.runtime.first { it == null } }
        var activity: Activity? = null
        var collector: Job? = null
        try {
            activity = launchActivity(app)
            val runtime = withTimeout(10_000) { app.runtime.first { it != null }!! }
            val events = mutableListOf<VehicleEvent>()
            collector = launch(Dispatchers.Main.immediate) { runtime.vehicle.events.collect { events += it } }
            compose.onNodeWithTag("demo_toggle").performTouchInput { longClick() }
            phaseTwoWarnings.forEach { id ->
                compose.onNodeWithTag("select_${id.name}").performScrollTo().performClick()
                compose.onNodeWithTag("raise_extra").performScrollTo().performClick()
                compose.onNodeWithTag("clear_extra").performScrollTo().performClick()
            }
            compose.runOnIdle {
                assertEquals(phaseTwoWarnings.flatMap { listOf(VehicleEvent.Raised(it), VehicleEvent.Cleared(it)) }, events)
            }
        } finally {
            collector?.cancel()
            withContext(Dispatchers.Main) { activity?.finishAndRemoveTask() }
            context.stopService(Intent(context, WarningMonitorService::class.java))
        }
    }

    /** Pleos에서 일반 ComponentActivity 대신 실제 앱 화면의 재개 완료를 기다립니다. */
    private suspend fun launchActivity(app: WarningApplication): Activity = withContext(Dispatchers.Main) {
        val resumed = CompletableDeferred<Activity>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) resumed.complete(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            withTimeout(10_000) { resumed.await() }
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }
}
