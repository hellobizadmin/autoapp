/*******************************
 목   적 : 에뮬레이터에서 유형별 경고 테스트 진행 화면 시연 및 캡처
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-02~14 시각적 테스트 시나리오 추가
*******************************/
package chageun

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.test.platform.app.InstrumentationRegistry
import chageun.core.*
import chageun.monitoring.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 실제 앱을 표시하면서 Fake 차량 입력을 전달하고 상태·화면·알림을 검증합니다. */
class WarningWalkthroughTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as WarningApplication
    private val output get() = File(context.filesDir, "walkthrough").apply { mkdirs() }
    private lateinit var runtime: WarningRuntime

    /** FR-02~15: 실제 안내 음성이 끝날 때까지 유지하는 제출용 시연 촬영입니다. */
    @Test fun submissionVideoScenario() = runBlocking {
        WarningMonitorService.preferences(context).edit().putBoolean("notification_requested", true).commit()
        context.stopService(Intent(context, WarningMonitorService::class.java))
        withTimeout(5_000) { app.runtime.first { it == null } }
        var activity: Activity? = null
        fun chapter(name: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nDEMO|$name\n") })
        suspend fun spokenDetail() {
            // 듣기 세션은 실제 park TTS 완료 후에만 시작됩니다. 실패를 지연으로 숨기지 않습니다.
            withTimeout(35_000) { runtime.session.state.first { it.listening } }
            delay(700)
        }
        try {
            activity = launchActivity()
            runtime = withTimeout(10_000) { app.runtime.first { it != null }!! }
            awaitScreen { it == AppUiState.Idle }
            chapter("idle"); delay(4_000)

            chapter("driving")
            mutate { runtime.drive.set(DriveState.DRIVING) }; delay(200)
            repeat(5) { mutate { tire() }; delay(100) }
            awaitScreen { it is AppUiState.DrivingOverlay }
            expectText(runtime.dictionary.text("repeat"), false)
            withTimeout(30_000) { runtime.speaker.available.first { it == true } }
            delay(1_000)

            chapter("parked")
            mutate { runtime.drive.set(DriveState.PARKED) }
            awaitScreen { it is AppUiState.Detail }
            spokenDetail()

            chapter("repeat")
            clickText(runtime.dictionary.text("repeat"))
            delay(12_000)
            assertEquals("tts_slow_unsupported", runtime.speaker.limitation.value)

            chapter("approval")
            val detail = runtime.session.state.value.screen as AppUiState.Detail
            assertTrue(runtime.navi.destinations.isEmpty())
            clickText(runtime.dictionary.text("navigate", mapOf("category" to detail.warning.action.category.orEmpty())))
            withTimeout(5_000) { while (runtime.navi.destinations.size != 1) delay(50) }
            delay(5_000)

            chapter("range")
            mutate { runtime.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)); runtime.vehicle.setRange(25.0) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.RANGE_CRITICAL }
            spokenDetail()

            chapter("priority")
            mutate { runtime.vehicle.emit(VehicleEvent.Raised(WarningId.ENGINE_OIL_PRESSURE)) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.ENGINE_OIL_PRESSURE && !it.showNavigation }
            spokenDetail()
            assertEquals(1, runtime.navi.destinations.size)

            chapter("clear")
            mutate { runtime.vehicle.setRange(95.0); runtime.vehicle.emit(VehicleEvent.Cleared(WarningId.ENGINE_OIL_PRESSURE)) }
            awaitScreen { it == AppUiState.Idle }
            delay(4_000)
            chapter("end")
        } finally {
            mutate { activity?.finishAndRemoveTask() }
            context.stopService(Intent(context, WarningMonitorService::class.java))
        }
    }

    /** FR-15: 추가 경고 5종의 실제 화면과 정차 필요 경고 우선 표시를 확인합니다. */
    @Test fun phaseTwoCoreWarningScenarios() = runBlocking {
        WarningMonitorService.preferences(context).edit().putBoolean("notification_requested", true).commit()
        context.stopService(Intent(context, WarningMonitorService::class.java))
        withTimeout(5_000) { app.runtime.first { it == null } }
        var activity: Activity? = null
        try {
            activity = launchActivity()
            runtime = withTimeout(10_000) { app.runtime.first { it != null }!! }
            for ((index, id) in phaseTwoWarnings.withIndex()) {
                mutate { runtime.drive.set(DriveState.DRIVING) }
                delay(200)
                repeat(5) { mutate { runtime.vehicle.emit(VehicleEvent.Raised(id)) } }
                awaitScreen { it is AppUiState.DrivingOverlay && it.id == id }
                expectText(runtime.dictionary.text("repeat"), present = false)
                assertFalse(runtime.session.state.value.listening)
                mutate { runtime.drive.set(DriveState.PARKED) }
                awaitScreen { it is AppUiState.Detail && it.warning.id == id && !it.showNavigation }
                expectText(runtime.dictionary.text("repeat"))
                capture("phase_two_${index + 1}_${id.name}", "warning_${id.name}")
                assertTrue(runtime.navi.destinations.isEmpty())
                mutate { runtime.drive.set(DriveState.UNKNOWN) }
                awaitScreen { it is AppUiState.DrivingOverlay && it.id == id }
                expectText(runtime.dictionary.text("repeat"), present = false)
                mutate { runtime.vehicle.emit(VehicleEvent.Cleared(id)) }
                awaitScreen { it == AppUiState.Idle }
            }
            mutate { runtime.drive.set(DriveState.PARKED); runtime.vehicle.setRange(25.0) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.RANGE_CRITICAL }
            mutate { runtime.vehicle.emit(VehicleEvent.Raised(WarningId.ENGINE_OIL_PRESSURE)) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.ENGINE_OIL_PRESSURE && !it.showNavigation }
            capture("phase_two_priority", "warning_ENGINE_OIL_PRESSURE")
            mutate { runtime.session.approve() }
            assertTrue(runtime.navi.destinations.isEmpty())
        } finally {
            mutate { activity?.finishAndRemoveTask() }
            context.stopService(Intent(context, WarningMonitorService::class.java))
        }
    }

    /** FR-02~14: 각 화면을 읽을 수 있게 유지하며 유형별 결과와 실제 화면을 저장합니다. */
    @Test fun visibleWarningScenarios() = runBlocking {
        WarningMonitorService.preferences(context).edit().putBoolean("notification_requested", true).commit()
        context.stopService(Intent(context, WarningMonitorService::class.java))
        withTimeout(5_000) { app.runtime.first { it == null } }
        var activity: Activity? = null
        try {
            activity = launchActivity()
            runtime = withTimeout(10_000) { app.runtime.first { it != null }!! }
            awaitScreen { it == AppUiState.Idle }
            expectText(runtime.dictionary.text("idle"))
            capture("01_idle", "test_idle")

            mutate { runtime.drive.set(DriveState.DRIVING) }
            delay(200)
            repeat(5) { mutate { tire() }; delay(100) }
            awaitScreen { it is AppUiState.DrivingOverlay && it.id == WarningId.TIRE_LOW }
            expectText(runtime.dictionary.text("repeat"), present = false)
            assertTrue(runtime.navi.destinations.isEmpty())
            capture("02_driving", "test_driving")

            mutate { runtime.drive.set(DriveState.PARKED) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.TIRE_LOW }
            expectText(runtime.dictionary.text("repeat"))
            capture("03_parked", "test_parked")
            val detail = runtime.session.state.value.screen as AppUiState.Detail
            val navigationLabel = runtime.dictionary.text("navigate", mapOf("category" to detail.warning.action.category.orEmpty()))
            assertTrue(runtime.navi.destinations.isEmpty())
            clickText(navigationLabel)
            withTimeout(5_000) { while (runtime.navi.destinations.size != 1) delay(50) }
            capture("04_approval", "test_approval")

            mutate { runtime.drive.set(DriveState.UNKNOWN) }
            awaitScreen { it is AppUiState.DrivingOverlay }
            expectText(runtime.dictionary.text("repeat"), present = false)
            expectText(navigationLabel, present = false)
            assertFalse(runtime.session.state.value.listening)
            capture("05_unknown", "test_unknown")

            mutate { runtime.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)); runtime.drive.set(DriveState.PARKED); runtime.vehicle.setRange(70.0) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.RANGE_LOW }
            capture("06_range_low", "test_range_low")
            mutate { runtime.vehicle.setRange(25.0) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.RANGE_CRITICAL && it.warning.urgency == Urgency.NOW }
            capture("07_range_critical", "test_range_critical")
            mutate { runtime.vehicle.setRange(95.0) }
            awaitScreen { it == AppUiState.Idle }
            capture("08_clear", "test_clear")

            mutate { tire(WarningId.TIRE_RAPID_DROP) }
            awaitScreen { it is AppUiState.Detail && !it.showNavigation && it.warning.urgency == Urgency.NOW }
            expectText(navigationLabel, present = false)
            capture("09_rapid", "test_rapid")
            mutate { runtime.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_RAPID_DROP)); tire(); runtime.vehicle.setRange(25.0) }
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.RANGE_CRITICAL }
            capture("10_priority", "test_priority")

            mutate { runtime.vehicle.setRange(95.0); runtime.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)) }
            awaitScreen { it == AppUiState.Idle }
            mutate { activity?.finishAndRemoveTask() }; activity = null
            withTimeout(5_000) { runtime.session.state.first { !it.foreground } }
            mutate { tire() }
            awaitScreen { it is AppUiState.DrivingOverlay }
            assertFalse(runtime.session.state.value.listening)
            assertSame(runtime, app.runtime.value)
            val manager = context.getSystemService(NotificationManager::class.java)
            assertTrue("Notification permission required for visible walkthrough", manager.areNotificationsEnabled())
            withTimeout(5_000) { while (manager.activeNotifications.none { it.id == WarningMonitorService.WARNING_ID }) delay(50) }
            shell("cmd statusbar expand-notifications")
            capture("11_background", "test_background")
            shell("cmd statusbar collapse")

            activity = launchActivity()
            awaitScreen { it is AppUiState.Detail && it.warning.id == WarningId.TIRE_LOW }
            assertSame(runtime, app.runtime.value)
            assertEquals(1, runtime.navi.destinations.size)
            capture("12_return", "test_return")
            mutate { runtime.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)); runtime.vehicle.emit(VehicleEvent.Raised(WarningId.SAFETY_WARNING)) }
            awaitScreen { it == AppUiState.Idle }
            capture("13_unsupported", "test_unsupported")
            File(output, "result.txt").writeText("PASS: 13 visible checkpoints. Vehicle and navigation simulated. Korean TTS available=${runtime.speaker.available.value}. Microphone recognition and real navigation not tested.\n")
        } finally {
            shell("cmd statusbar collapse")
            // 테스트가 서비스나 이전 경고를 남기지 않도록 정리합니다.
            mutate { activity?.finishAndRemoveTask() }
            context.stopService(Intent(context, WarningMonitorService::class.java))
        }
    }

    /** 메인 디스패처에서 실제 세션 입력을 순차 전달합니다. */
    private suspend fun mutate(action: () -> Unit) = withContext(Dispatchers.Main) { action() }
    /** 타이어 위치를 포함한 경고를 주입합니다. */
    private fun tire(id: WarningId = WarningId.TIRE_LOW) {
        runtime.vehicle.emit(VehicleEvent.Raised(id, mapOf("position" to "REAR_LEFT")))
    }
    /** 상태 전이를 제한 시간 내 기다립니다. */
    private suspend fun awaitScreen(predicate: (AppUiState) -> Boolean) {
        withTimeout(5_000) { runtime.session.state.first { predicate(it.screen) } }
        delay(500)
    }
    /** 화면 트리에서 정확한 문구 노드를 찾습니다. */
    private fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) findText(node.getChild(index), text)?.let { return it }
        return null
    }
    /** 실제 접근성 화면에서 문구의 표시 또는 숨김을 검증합니다. */
    private suspend fun expectText(text: String, present: Boolean = true) {
        withTimeout(5_000) {
            while ((findText(instrumentation.uiAutomation.rootInActiveWindow, text) != null) != present) delay(100)
        }
    }
    /** 실제 화면의 버튼을 접근성 클릭하여 사용자 승인 경로를 검증합니다. */
    private suspend fun clickText(text: String) {
        expectText(text)
        var node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Visible button must accept click", node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }
    /** 유형 표시 후 화면을 캡처하고 사용자가 읽을 수 있게 잠시 유지합니다. */
    private suspend fun capture(name: String, label: String) {
        val message = runtime.dictionary.text(label)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$name: $message\n") })
        mutate { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        delay(1_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        delay(3_000)
    }
    /** 테스트 전용 시스템 알림 패널 명령의 완료를 기다립니다. */
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    /** Pleos 다중 창의 실제 RESUMED 콜백으로 화면 시작을 확인합니다. */
    private suspend fun launchActivity(): Activity {
        val resumed = CompletableDeferred<Activity>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            /** 대상 화면이 재개되면 대기를 끝냅니다. */
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) resumed.complete(activity) }
            /** 생성 단계는 완료로 처리하지 않습니다. */
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            /** 시작 단계는 완료로 처리하지 않습니다. */
            override fun onActivityStarted(activity: Activity) = Unit
            /** 일시 정지를 무시합니다. */
            override fun onActivityPaused(activity: Activity) = Unit
            /** 중지는 세션 상태에서 검증합니다. */
            override fun onActivityStopped(activity: Activity) = Unit
            /** 상태 저장은 변경하지 않습니다. */
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            /** 화면 종료는 세션 상태에서 검증합니다. */
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
