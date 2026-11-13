/*******************************
 목   적 : 실제 Android 화면의 주행 제한과 정차 버튼 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import chageun.core.*
import chageun.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** FR-04, FR-05, FR-06, FR-07, FR-08: 실제 Compose 트리에서 화면과 버튼을 검증합니다. */
class WarningScreensTest {
    @get:Rule val compose = createComposeRule()
    /** 앱에 포함된 실제 사전을 로드합니다. */
    private fun dictionary(): WarningDictionary {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return WarningDictionary.parse(context.assets.open("warning_dictionary_ko.json").bufferedReader().use { it.readText() }).getOrThrow()
    }

    /** FR-04, FR-06: 주행 및 상태 불명 전환 시 상세 내용과 버튼이 사라집니다. */
    @Test fun drivingAndUnknownHideDetailAndButtons() {
        val d = dictionary()
        val coordinator = WarningCoordinator(d, UrgencyRules(80.0, 30.0, 10.0))
        coordinator.onDriveState(DriveState.PARKED)
        coordinator.onVehicleEvent(VehicleEvent.Raised(WarningId.TIRE_LOW, mapOf("position" to "REAR_LEFT")))
        val state = mutableStateOf(coordinator.uiState)
        compose.setContent {
            MaterialTheme {
                when (val screen = state.value) {
                    is AppUiState.Detail -> DetailScreen(screen, false, d, {}, {})
                    is AppUiState.DrivingOverlay -> DrivingOverlay(screen, d)
                    AppUiState.Idle -> IdleScreen(d)
                }
            }
        }
        compose.onNodeWithTag("detail").assertExists()
        for (drive in listOf(DriveState.DRIVING, DriveState.UNKNOWN)) {
            compose.runOnIdle { coordinator.onDriveState(drive); state.value = coordinator.uiState }
            compose.onNodeWithTag("driving").assertIsDisplayed()
            compose.onNodeWithTag("detail").assertDoesNotExist()
            compose.onNodeWithTag("navigate").assertDoesNotExist()
            compose.onNodeWithTag("repeat").assertDoesNotExist()
        }
    }

    /** FR-05, FR-07, FR-08: 정차 상세 버튼은 각 사용자 입력을 한 번만 전달합니다. */
    @Test fun parkedDetailButtonsDispatchOneAction() {
        val d = dictionary()
        val warning = d.resolve(WarningId.TIRE_LOW, mapOf("position" to "REAR_LEFT"))!!
        var navigation = 0
        var repeat = 0
        compose.setContent { MaterialTheme { DetailScreen(AppUiState.Detail(warning, true), true, d, { navigation++ }, { repeat++ }) } }
        assertEquals(0, navigation)
        // FR-05·07·08: 하단 고정 버튼은 스크롤 없이 표시되고 클릭되어야 합니다.
        compose.onNodeWithTag("navigate").assertIsDisplayed().performClick()
        compose.onNodeWithTag("repeat").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, navigation); assertEquals(1, repeat) }
    }

    /** FR-07: 후속 행동이 없는 경고에는 길 안내 버튼이 없습니다. */
    @Test fun noneActionHidesNavigation() {
        val d = dictionary()
        val warning = d.resolve(WarningId.TIRE_RAPID_DROP, mapOf("position" to "REAR_LEFT"))!!
        compose.setContent { MaterialTheme { DetailScreen(AppUiState.Detail(warning, false), false, d, {}, {}) } }
        compose.onNodeWithTag("navigate").assertDoesNotExist()
        compose.onNodeWithTag("repeat").assertIsDisplayed()
    }
}
