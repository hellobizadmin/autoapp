/*******************************
 목   적 : 2차 핵심 경고의 안내·우선순위·안전 제한 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : FR-15 핵심 경고 5종 추가 검증
*******************************/
package chageun.core

import chageun.adapter.fake.FakeVehicleSource
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** FR-03~11, FR-15: SDK 없이 실제 사전으로 새 경고의 모든 상태를 확인합니다. */
class CoreWarningExpansionTest {
    private fun asset(name: String) = checkNotNull(javaClass.classLoader.getResource(name)).readText()
    private val dictionary = WarningDictionary.parse(asset("warning_dictionary_ko.json")).getOrThrow()
    private fun coordinator() = WarningCoordinator(dictionary, UrgencyRules.parse(asset("warning_rules.json")))

    @Test fun allFiveWarningsResolveWithoutParametersAndNeverSuggestNavigation() {
        assertEquals(5, phaseTwoWarnings.size)
        for (id in phaseTwoWarnings) {
            val warning = assertNotNull(dictionary.resolve(id, emptyMap()))
            assertEquals(if (id == WarningId.ABS_WARNING) Urgency.TODAY else Urgency.NOW, warning.urgency)
            assertEquals(ActionType.NONE, warning.action.type)
            assertEquals(1, assertNotNull(warning.drive).count { it in ".!?" })
            assertTrue(assertNotNull(warning.park).count { it in ".!?" } <= 3)
            assertFalse(warning.drive!!.contains('{'))
            assertFalse(warning.park!!.contains('{'))
            assertTrue(dictionary.text("warning_${id.name}").isNotBlank())
        }
    }

    @Test fun allFiveWarningsHonorDrivingUnknownAndParkedAndSuppressDuplicates() {
        for (id in phaseTwoWarnings) for (initial in listOf(DriveState.DRIVING, DriveState.UNKNOWN)) {
            val c = coordinator()
            c.onDriveState(initial)
            val output = (1..5).flatMap { c.onVehicleEvent(VehicleEvent.Raised(id)) }
            assertEquals(1, output.filterIsInstance<Effect.Speak>().size)
            assertEquals(id, assertIs<AppUiState.DrivingOverlay>(c.uiState).id)
            assertEquals(1, c.onDriveState(DriveState.PARKED).filterIsInstance<Effect.Speak>().size)
            assertFalse(assertIs<AppUiState.Detail>(c.uiState).showNavigation)
            assertTrue(c.approveNavigation(id, DemoPlaces.parse(asset("demo_places.json"))).isEmpty())
            assertEquals(1, c.repeatDetail().size)
            assertTrue(assertIs<Effect.Speak>(c.repeatDetail().single()).slow)
            c.onDriveState(DriveState.UNKNOWN)
            assertIs<AppUiState.DrivingOverlay>(c.uiState)
            assertTrue(c.repeatDetail().isEmpty())
            assertTrue(c.onVehicleEvent(VehicleEvent.Cleared(id)).isEmpty())
            assertEquals(AppUiState.Idle, c.uiState)
            assertEquals(1, c.onVehicleEvent(VehicleEvent.Raised(id)).size)
        }
    }

    @Test fun stopWarningsPreemptCriticalRangeRegardlessOfArrivalOrder() {
        val stopWarnings = phaseTwoWarnings - WarningId.ABS_WARNING
        for (id in stopWarnings) for (rangeFirst in listOf(true, false)) {
            val c = coordinator()
            c.onDriveState(DriveState.PARKED)
            if (rangeFirst) c.onRangeChanged(25.0)
            c.onVehicleEvent(VehicleEvent.Raised(id))
            if (!rangeFirst) c.onRangeChanged(25.0)
            assertEquals(listOf(id, WarningId.RANGE_CRITICAL), c.activeWarnings.map { it.id })
            assertEquals(id, assertIs<AppUiState.Detail>(c.uiState).warning.id)
            assertFalse(assertIs<AppUiState.Detail>(c.uiState).showNavigation)
            c.onVehicleEvent(VehicleEvent.Cleared(id))
            assertEquals(WarningId.RANGE_CRITICAL, assertIs<AppUiState.Detail>(c.uiState).warning.id)
        }
    }

    @Test fun urgencyStillComesFirstAndEqualPriorityKeepsArrivalOrder() {
        val c = coordinator()
        c.onDriveState(DriveState.PARKED)
        c.onVehicleEvent(VehicleEvent.Raised(WarningId.ABS_WARNING))
        c.onRangeChanged(25.0)
        assertEquals(WarningId.RANGE_CRITICAL, assertIs<AppUiState.Detail>(c.uiState).warning.id)
        c.onVehicleEvent(VehicleEvent.Raised(WarningId.BRAKE_FLUID_LOW))
        c.onVehicleEvent(VehicleEvent.Raised(WarningId.ENGINE_OVERHEAT))
        assertEquals(WarningId.BRAKE_FLUID_LOW, assertIs<AppUiState.Detail>(c.uiState).warning.id)
        c.onVehicleEvent(VehicleEvent.Cleared(WarningId.BRAKE_FLUID_LOW))
        assertEquals(WarningId.ENGINE_OVERHEAT, assertIs<AppUiState.Detail>(c.uiState).warning.id)
    }

    @Test fun fakeSourceExplicitlySupportsNineScenariosButNotGenericSafety() = runTest {
        val supported = FakeVehicleSource().supportedWarnings()
        assertEquals(9, supported.size)
        assertTrue(supported.containsAll(phaseTwoWarnings))
        assertFalse(WarningId.SAFETY_WARNING in supported)
        assertNull(dictionary.resolve(WarningId.SAFETY_WARNING, emptyMap()))
    }

    @Test fun invalidPriorityIsRejectedAndMissingPriorityKeepsLegacyDefault() {
        for (invalid in listOf("-1", "1.5", "invalid")) {
            val json = JsonParser.parseString(asset("warning_dictionary_ko.json")).asJsonObject
            json.getAsJsonArray("warnings")[0].asJsonObject.addProperty("priority", invalid)
            assertTrue(WarningDictionary.parse(json.toString()).isFailure)
        }
        assertEquals(100, dictionary.resolve(WarningId.RANGE_CRITICAL, mapOf("range" to "25"))!!.priority)
    }
}
