/*******************************
 목   적 : 경고 조정 로직과 기능 수용 기준 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
*******************************/
package chageun.core

import kotlin.test.*

/** 코어 상태 전이와 요구사항별 수용 기준을 검증합니다. */
class WarningCoordinatorTest {
    /** 테스트에서 사용하는 실제 assets 파일 내용을 읽습니다. */
    private fun asset(name: String) = checkNotNull(javaClass.classLoader.getResource(name)).readText()
    private val dictionary = WarningDictionary.parse(asset("warning_dictionary_ko.json")).getOrThrow()
    private val rules = UrgencyRules.parse(asset("warning_rules.json"))
    private val coordinator = WarningCoordinator(dictionary, rules)
    private val tire = VehicleEvent.Raised(WarningId.TIRE_LOW, mapOf("position" to "REAR_LEFT"))
    /** 사전에서 해당 경고의 예상 문구와 행동을 조회합니다. */
    private fun expected(id: WarningId, params: Map<String, String>) = checkNotNull(dictionary.resolve(id, params))

    /** FR-03: 거리 변화에 따른 LOW 발생, CRITICAL 대체 및 전체 해소를 검증합니다. */
    @Test fun `FR-03 range 100 70 25 95 raises replaces and clears`() {
        assertTrue(coordinator.onRangeChanged(100.0).isEmpty())
        assertEquals(AppUiState.Idle, coordinator.uiState)
        assertEquals(1, coordinator.onRangeChanged(70.0).size)
        assertEquals(listOf(WarningId.RANGE_LOW), coordinator.activeWarnings.map { it.id })
        assertEquals(Urgency.TODAY, coordinator.activeWarnings.single().urgency)
        assertEquals(1, coordinator.onRangeChanged(25.0).size)
        assertEquals(listOf(WarningId.RANGE_CRITICAL), coordinator.activeWarnings.map { it.id })
        assertEquals(Urgency.NOW, coordinator.activeWarnings.single().urgency)
        assertTrue(coordinator.onRangeChanged(95.0).isEmpty())
        assertTrue(coordinator.activeWarnings.isEmpty())
        assertEquals(AppUiState.Idle, coordinator.uiState)
    }

    /** FR-04: 주행 중 상세 화면 없이 한 문장만 안내하는지 검증합니다. */
    @Test fun `FR-04 driving exposes only overlay and a single drive utterance`() {
        coordinator.onDriveState(DriveState.DRIVING)
        val effects = coordinator.onVehicleEvent(tire)
        val ui = assertIs<AppUiState.DrivingOverlay>(coordinator.uiState)
        assertEquals(expected(tire.id, tire.params).drive, ui.text)
        assertEquals(listOf(Effect.Speak(checkNotNull(ui.text))), effects)
        assertEquals(1, checkNotNull(ui.text).count { it in ".!?" })
        assertFalse(coordinator.uiState is AppUiState.Detail)
    }

    /** FR-05: 주행 중 발생한 경고를 정차 후 상세 안내하는지 검증합니다. */
    @Test fun `FR-05 driving warning becomes spoken detail when parked`() {
        coordinator.onDriveState(DriveState.DRIVING)
        coordinator.onVehicleEvent(tire)
        val effects = coordinator.onDriveState(DriveState.PARKED)
        val ui = assertIs<AppUiState.Detail>(coordinator.uiState)
        assertEquals(expected(tire.id, tire.params).park, ui.warning.park)
        assertEquals(listOf(Effect.Speak(checkNotNull(ui.warning.park))), effects)
        assertTrue(ui.showNavigation)
        assertTrue(coordinator.onDriveState(DriveState.PARKED).isEmpty())
    }

    /** FR-05: 정차 중 즉시 안내와 긴급도별 표시 순서를 검증합니다. */
    @Test fun `FR-05 parked new warning is immediate and NOW precedes TODAY then NEXT`() {
        // 모든 우선순위를 검증하기 위해 테스트 사전의 타이어 경고만 NEXT로 바꿉니다.
        val fixture = com.google.gson.JsonParser.parseString(asset("warning_dictionary_ko.json")).asJsonObject
        fixture.getAsJsonArray("warnings").first { it.asJsonObject["id"].asString == "TIRE_LOW" }
            .asJsonObject.addProperty("urgency", "NEXT")
        val c = WarningCoordinator(WarningDictionary.parse(fixture.toString()).getOrThrow(), rules)
        c.onDriveState(DriveState.PARKED)
        assertEquals(1, c.onVehicleEvent(tire).size)
        assertIs<AppUiState.Detail>(c.uiState)
        c.onRangeChanged(70.0)
        c.onVehicleEvent(VehicleEvent.Raised(WarningId.TIRE_RAPID_DROP, tire.params))
        assertEquals(listOf(Urgency.NOW, Urgency.TODAY, Urgency.NEXT), c.activeWarnings.map { it.urgency })
        assertEquals(WarningId.TIRE_RAPID_DROP, assertIs<AppUiState.Detail>(c.uiState).warning.id)
        c.onVehicleEvent(VehicleEvent.Cleared(WarningId.TIRE_RAPID_DROP))
        assertEquals(WarningId.RANGE_LOW, assertIs<AppUiState.Detail>(c.uiState).warning.id)
        c.onVehicleEvent(VehicleEvent.Cleared(WarningId.RANGE_LOW))
        assertEquals(WarningId.TIRE_LOW, assertIs<AppUiState.Detail>(c.uiState).warning.id)
    }

    /** FR-06: 초기 및 상태 불명 시 상세 화면과 내비 요청을 차단하는지 검증합니다. */
    @Test fun `FR-06 unknown including startup uses drive only and rejects navigation`() {
        assertEquals(DriveState.UNKNOWN, coordinator.driveState)
        assertEquals(listOf(Effect.Speak(checkNotNull(expected(tire.id, tire.params).drive))),
            coordinator.onVehicleEvent(tire))
        assertIs<AppUiState.DrivingOverlay>(coordinator.uiState)
        coordinator.onDriveState(DriveState.PARKED)
        assertTrue(coordinator.onDriveState(DriveState.UNKNOWN).isEmpty())
        assertIs<AppUiState.DrivingOverlay>(coordinator.uiState)
        assertTrue(coordinator.approveNavigation(tire.id, places()).isEmpty())
    }

    /** 승인 시 첫 장소를 선택하는지 확인할 장소 목록을 만듭니다. */
    private fun places(): Map<String, List<Place>> = mapOf(
        checkNotNull(expected(tire.id, tire.params).action.category) to
            listOf(Place("first", 37.0, 127.0), Place("second", 38.0, 128.0)))

    /** FR-07: 승인 전에는 요청이 없고 승인 후 첫 장소를 선택하는지 검증합니다. */
    @Test fun `FR-07 destination requests are zero before approval and first place after approval`() {
        val effects = mutableListOf<Effect>()
        effects += coordinator.onVehicleEvent(tire)
        effects += coordinator.onDriveState(DriveState.DRIVING)
        effects += coordinator.onDriveState(DriveState.PARKED)
        effects += coordinator.onVehicleEvent(tire)
        assertEquals(0, effects.filterIsInstance<Effect.SetDestination>().size)
        assertEquals(listOf(Effect.SetDestination(places().values.single().first())),
            coordinator.approveNavigation(tire.id, places()))
        assertTrue(coordinator.approveNavigation(WarningId.RANGE_LOW, places()).isEmpty())
        assertTrue(coordinator.approveNavigation(tire.id, emptyMap()).isEmpty())
        coordinator.onVehicleEvent(VehicleEvent.Cleared(tire.id))
        assertTrue(coordinator.approveNavigation(tire.id, places()).isEmpty())
    }

    /** FR-07: 후속 행동이 없으면 안내 버튼과 내비 요청이 없는지 검증합니다. */
    @Test fun `FR-07 NONE action has no navigation button or destination request`() {
        coordinator.onDriveState(DriveState.PARKED)
        coordinator.onVehicleEvent(VehicleEvent.Raised(WarningId.TIRE_RAPID_DROP, tire.params))
        assertFalse(assertIs<AppUiState.Detail>(coordinator.uiState).showNavigation)
        assertTrue(coordinator.approveNavigation(WarningId.TIRE_RAPID_DROP, places()).isEmpty())
    }

    /** FR-10: 연속 경고를 한 번만 안내하고 해소 후에는 다시 안내하는지 검증합니다. */
    @Test fun `FR-10 five duplicate events speak once and clear permits new announcement`() {
        coordinator.onDriveState(DriveState.DRIVING)
        assertEquals(1, (1..5).flatMap { coordinator.onVehicleEvent(tire) }.filterIsInstance<Effect.Speak>().size)
        assertEquals(1, coordinator.onDriveState(DriveState.PARKED).size)
        assertTrue(coordinator.onDriveState(DriveState.DRIVING).isEmpty())
        assertTrue(coordinator.onDriveState(DriveState.PARKED).isEmpty())
        assertTrue(coordinator.onVehicleEvent(tire).isEmpty())
        assertTrue(coordinator.onVehicleEvent(VehicleEvent.Cleared(tire.id)).isEmpty())
        assertEquals(1, coordinator.onVehicleEvent(tire).size)
    }

    /** FR-03: 임계값 경계와 해소 여유값에 따른 반복 안내 억제를 검증합니다. */
    @Test fun `FR-03 range boundaries and hysteresis prevent repeated threshold announcements`() {
        assertNull(rules.rangeWarning(80.0, null))
        assertEquals(WarningId.RANGE_LOW, rules.rangeWarning(30.0, null))
        assertEquals(WarningId.RANGE_CRITICAL, rules.rangeWarning(29.9, null))
        assertEquals(WarningId.RANGE_CRITICAL, rules.rangeWarning(39.9, WarningId.RANGE_CRITICAL))
        assertEquals(WarningId.RANGE_LOW, rules.rangeWarning(40.0, WarningId.RANGE_CRITICAL))
        assertEquals(WarningId.RANGE_LOW, rules.rangeWarning(89.9, WarningId.RANGE_LOW))
        assertNull(rules.rangeWarning(90.0, WarningId.RANGE_LOW))
        coordinator.onRangeChanged(70.0)
        assertTrue(coordinator.onRangeChanged(65.0).isEmpty())
        assertFailsWith<IllegalArgumentException> { coordinator.onRangeChanged(Double.NaN) }
        assertEquals(1, coordinator.activeWarnings.size)
    }

    /** FR-10: 정차 중 안내한 경고가 주행 전환 후 반복되지 않는지 검증합니다. */
    @Test fun `FR-10 warning first spoken parked stays silent after driving and duplicate events`() {
        coordinator.onDriveState(DriveState.PARKED)
        assertEquals(1, coordinator.onVehicleEvent(tire).size)
        assertTrue(coordinator.onDriveState(DriveState.DRIVING).isEmpty())
        assertTrue(coordinator.onVehicleEvent(tire).isEmpty())
        assertTrue(coordinator.onDriveState(DriveState.UNKNOWN).isEmpty())
        assertTrue(coordinator.onDriveState(DriveState.PARKED).isEmpty())
    }

    /** 미해결 변수가 있으면 음성을 억제하고 로그를 남기는지 검증합니다. */
    @Test fun `unresolved placeholders suppress speech and leave a log`() {
        val logs = mutableListOf<String>()
        val d = WarningDictionary.parse(asset("warning_dictionary_ko.json"), logs::add).getOrThrow()
        val c = WarningCoordinator(d, rules)
        assertTrue(c.onVehicleEvent(VehicleEvent.Raised(tire.id)).isEmpty())
        assertTrue(logs.any { it.startsWith("CHAGEUN") })
        assertTrue(c.onDriveState(DriveState.PARKED).isEmpty())
        assertFalse(assertIs<AppUiState.Detail>(c.uiState).showNavigation)
        assertEquals(1, c.onVehicleEvent(tire).size)
    }

    /** 사전 파싱, 거리 반올림 및 차량 종류별 문구 치환을 검증합니다. */
    @Test fun `dictionary parses assets rounds range and resolves vehicle fuel words`() {
        for (vehicle in listOf("GASOLINE", "EV")) {
            val d = WarningDictionary.parse(asset("warning_dictionary_ko.json").replace("\"vehicle_type\": \"GASOLINE\"",
                "\"vehicle_type\": \"$vehicle\"")).getOrThrow()
            val warning = checkNotNull(d.resolve(WarningId.RANGE_LOW, mapOf("range" to "28.7")))
            assertTrue(checkNotNull(warning.drive).contains("29"))
            assertFalse(checkNotNull(warning.park).contains('{'))
            assertFalse(checkNotNull(warning.action.category).contains('{'))
        }
        assertTrue(WarningDictionary.parse("invalid").isFailure)
        assertNull(dictionary.resolve(WarningId.SAFETY_WARNING, emptyMap()))
    }

    /** FR-05: 대기 중인 낮은 긴급도 경고는 선택된 후 안내하는지 검증합니다. */
    @Test fun `FR-05 queued lower urgency detail is spoken only when selected`() {
        coordinator.onDriveState(DriveState.PARKED)
        coordinator.onRangeChanged(25.0)
        assertTrue(coordinator.onVehicleEvent(tire).isEmpty())
        assertEquals(1, coordinator.onVehicleEvent(VehicleEvent.Cleared(WarningId.RANGE_CRITICAL)).size)
        assertEquals(tire.id, assertIs<AppUiState.Detail>(coordinator.uiState).warning.id)
    }
}
