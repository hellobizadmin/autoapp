/*******************************
 목   적 : 음성·차량 어댑터 통합 상태 전이와 안전 조건 검증
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.runtime

import chageun.adapter.*
import chageun.adapter.fake.*
import chageun.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** FR-01~13: 가상 시간과 제어 가능한 어댑터로 비동기 완료 및 취소를 검증합니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class WarningSessionTest {
    /** 실제 assets 사전을 테스트 자원으로 읽습니다. */
    private fun asset(name: String) = checkNotNull(javaClass.classLoader.getResource(name)).readText()

    /** 실제 완료를 테스트가 직접 통지하는 음성 출력 대역입니다. */
    private class TestSpeaker : Speaker {
        val calls = mutableListOf<Pair<String, Boolean>>()
        var pending: CompletableDeferred<Unit>? = null
        var fail = false
        var stopped = 0
        /** 완료 신호 전까지 반환하지 않아 조기 듣기를 검출합니다. */
        override suspend fun speak(text: String, slow: Boolean) {
            calls += text to slow
            if (fail) error("speaker failure")
            val request = CompletableDeferred<Unit>()
            pending = request
            request.await()
        }
        /** 재생 중지 횟수를 기록하며 취소된 완료도 별도로 검증할 수 있게 유지합니다. */
        override fun stop() { stopped++ }
    }

    /** 단일 테스트에 필요한 실제 코어와 Fake 어댑터 묶음입니다. */
    private inner class Fixture(scope: CoroutineScope, customListener: Listener? = null, customVehicle: VehicleSource? = null, foreground: Boolean = true) {
        val dictionary = WarningDictionary.parse(asset("warning_dictionary_ko.json")).getOrThrow()
        val vehicle = FakeVehicleSource()
        val drive = FakeDriveStateSource()
        val speaker = TestSpeaker()
        val listener = FakeListener()
        val navi = LogNaviLinker()
        val logs = mutableListOf<String>()
        val session = WarningSession(WarningCoordinator(dictionary, UrgencyRules.parse(asset("warning_rules.json"))),
            dictionary, VoiceCommands.parse(asset("voice_commands_ko.json")), DemoPlaces.parse(asset("demo_places.json")),
            customVehicle ?: vehicle, drive, speaker, customListener ?: listener, navi, scope, logs::add, initialForeground = foreground)
        /** 타이어 경고 입력을 전달합니다. */
        fun raise() { vehicle.emit(VehicleEvent.Raised(WarningId.TIRE_LOW, mapOf("position" to "REAR_LEFT"))) }
        /** 현재 음성 재생의 정상 완료를 통지합니다. */
        fun completeSpeech() { checkNotNull(speaker.pending).complete(Unit) }
    }

    /** FR-15: 새 경고 5종도 백그라운드에서 한 번만 말하고 듣기·내비를 시작하지 않습니다. */
    @Test fun phaseTwoWarningsRemainSafeInBackground() = runTest {
        val f = Fixture(backgroundScope, foreground = false)
        f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent()
        for (id in phaseTwoWarnings) {
            val before = f.speaker.calls.size
            repeat(5) { f.vehicle.emit(VehicleEvent.Raised(id)); runCurrent() }
            assertEquals(before + 1, f.speaker.calls.size)
            assertEquals(id, assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen).id)
            assertFalse(f.session.state.value.listening)
            assertTrue(f.navi.destinations.isEmpty())
            f.vehicle.emit(VehicleEvent.Cleared(id)); runCurrent()
        }
        f.session.close()
    }

    /** FR-15: 주유소 승인 대기 중 정차 경고가 오면 이전 음성 승인을 폐기합니다. */
    @Test fun stopWarningRejectsPendingRangeVoiceApproval() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent()
        f.vehicle.setRange(25.0); runCurrent(); f.completeSpeech(); runCurrent()
        assertTrue(f.session.state.value.listening)
        f.vehicle.emit(VehicleEvent.Raised(WarningId.ENGINE_OIL_PRESSURE)); runCurrent()
        assertFalse(f.listener.submit("안내해줘")); runCurrent()
        f.session.approve(); runCurrent()
        assertTrue(f.navi.destinations.isEmpty())
        assertEquals(WarningId.ENGINE_OIL_PRESSURE, assertIs<AppUiState.Detail>(f.session.state.value.screen).warning.id)
        f.session.close()
    }

    /** FR-02: 경고 없이 10분 실행해도 음성 및 내비 호출이 없습니다. */
    @Test fun idleForTenMinutesIsSilent() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        advanceTimeBy(600_000); runCurrent()
        assertTrue(f.speaker.calls.isEmpty()); assertTrue(f.navi.destinations.isEmpty())
        assertFalse(f.session.state.value.listening)
        assertTrue(f.logs.any { it.contains("supported") })
    }

    /** FR-13: 상세 문구의 실제 재생 완료 전에는 듣지 않습니다. */
    @Test fun listeningWaitsForSpeechCompletionAndTimesOutOnce() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent()
        assertFalse(f.session.state.value.listening)
        assertFalse(f.listener.submit("안내해줘"))
        f.completeSpeech(); runCurrent(); assertTrue(f.session.state.value.listening)
        advanceTimeBy(5_001); runCurrent()
        assertFalse(f.session.state.value.listening); assertTrue(f.navi.destinations.isEmpty())
        advanceTimeBy(20_000); runCurrent(); assertEquals(1, f.speaker.calls.size)
    }

    /** FR-08, FR-13: 다시 말해 달라는 인식은 느린 재생 한 번이며 듣기를 반복하지 않습니다. */
    @Test fun voiceRepeatSpeaksSlowlyOnceWithoutRelistening() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        assertTrue(f.listener.submit("다시 말해줘")); runCurrent()
        assertEquals(2, f.speaker.calls.size); assertTrue(f.speaker.calls.last().second)
        assertEquals(f.speaker.calls.first().first, f.speaker.calls.last().first)
        f.completeSpeech(); runCurrent(); assertFalse(f.session.state.value.listening)
        assertTrue(f.navi.destinations.isEmpty())
    }

    /** FR-07, FR-13: 음성 승인은 버튼과 같은 목적지 경로를 정확히 한 번 호출합니다. */
    @Test fun voiceApprovalNavigatesOnce() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        assertTrue(f.navi.destinations.isEmpty())
        assertTrue(f.listener.submit("안내해줘")); runCurrent()
        assertEquals(1, f.navi.destinations.size)
        assertFalse(f.listener.submit("안내해줘")); runCurrent(); assertEquals(1, f.navi.destinations.size)
        assertEquals(f.dictionary.text("navigation_success", mapOf("category" to f.dictionary.resolve(WarningId.TIRE_LOW, mapOf("position" to "REAR_LEFT"))!!.action.category!!)), f.speaker.calls.last().first)
        f.completeSpeech(); runCurrent(); assertFalse(f.session.state.value.listening)
    }

    /** FR-13: 승인과 거절이 겹치는 인식은 내비를 호출하지 않습니다. */
    @Test fun ambiguousVoiceDoesNothing() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        f.listener.submit("응 아니"); runCurrent()
        assertTrue(f.navi.destinations.isEmpty()); assertEquals(1, f.speaker.calls.size)
        assertFalse(f.session.state.value.listening)
    }

    /** FR-04, FR-06, FR-13: 주행 및 초기 상태 불명에서는 듣기를 시작하지 않습니다. */
    @Test fun drivingAndUnknownNeverListen() = runTest {
        for (state in listOf(DriveState.UNKNOWN, DriveState.DRIVING)) {
            val f = Fixture(backgroundScope); f.session.start(); runCurrent()
            f.drive.set(state); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
            assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen)
            assertFalse(f.session.state.value.listening); assertFalse(f.listener.submit("안내해줘"))
            f.session.approve(); runCurrent(); assertTrue(f.navi.destinations.isEmpty()); f.session.close()
        }
    }

    /** FR-06, FR-13: 듣는 중 주행 또는 상태 불명으로 바뀌면 즉시 종료합니다. */
    @Test fun stateChangeCancelsListeningAndRejectsLateInput() = runTest {
        for (state in listOf(DriveState.DRIVING, DriveState.UNKNOWN)) {
            val f = Fixture(backgroundScope); f.session.start(); runCurrent()
            f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
            assertTrue(f.session.state.value.listening)
            f.drive.set(state); runCurrent()
            assertFalse(f.session.state.value.listening); assertFalse(f.listener.submit("안내해줘"))
            assertTrue(f.navi.destinations.isEmpty()); assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen)
            f.session.close()
        }
    }

    /** FR-13: 주행 전환 후 늦게 도착한 음성 재생 완료가 듣기를 시작하지 않습니다. */
    @Test fun lateSpeechCompletionCannotListenAfterDriving() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent()
        val oldCompletion = f.speaker.pending!!
        f.drive.set(DriveState.DRIVING); runCurrent(); oldCompletion.complete(Unit); runCurrent()
        assertFalse(f.session.state.value.listening); assertTrue(f.navi.destinations.isEmpty())
    }

    /** FR-11, FR-13: 경고 해소는 듣기와 늦은 승인을 함께 무효화합니다. */
    @Test fun clearedWarningCancelsListening() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        f.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)); runCurrent()
        assertEquals(AppUiState.Idle, f.session.state.value.screen)
        assertFalse(f.listener.submit("안내해줘")); assertFalse(f.session.state.value.listening)
    }

    /** FR-13: 닫기는 경고 해소를 위조하지 않으며 중복 이벤트로 다시 열리지 않습니다. */
    @Test fun dismissHidesUntilClearAndReraise() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        f.listener.submit("나중에"); runCurrent(); assertEquals(AppUiState.Idle, f.session.state.value.screen)
        f.raise(); runCurrent(); assertEquals(AppUiState.Idle, f.session.state.value.screen)
        f.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)); runCurrent(); f.raise(); runCurrent()
        assertIs<AppUiState.Detail>(f.session.state.value.screen)
    }

    /** FR-07: 내비 오류는 오류 문구로 안내하고 상세 화면을 유지합니다. */
    @Test fun navigationFailureKeepsDetailAndSpeaksFailure() = runTest {
        val f = Fixture(backgroundScope); f.navi.shouldFail = true; f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent()
        f.session.approve(); runCurrent()
        assertEquals(1, f.navi.destinations.size)
        assertEquals(f.dictionary.text("navigation_failure"), f.speaker.calls.last().first)
        assertIs<AppUiState.Detail>(f.session.state.value.screen)
    }

    /** FR-08: 버튼 한 번은 느린 TTS 한 번에 대응합니다. */
    @Test fun repeatButtonProducesOneSlowCall() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent()
        val before = f.speaker.calls.size
        f.session.repeat(); runCurrent()
        assertEquals(before + 1, f.speaker.calls.size); assertTrue(f.speaker.calls.last().second)
    }

    /** FR-13: 권한 거부나 인식 오류가 나도 버튼 승인 경로는 유지됩니다. */
    @Test fun deniedListenerStillAllowsButtons() = runTest {
        val denied = object : Listener {
            /** 권한 거부 상황을 재현합니다. */
            override suspend fun listenOnce(timeoutMs: Long): String? = throw SecurityException("denied")
            /** 테스트 대역에는 해제할 자원이 없습니다. */
            override fun stop() = Unit
        }
        val f = Fixture(backgroundScope, customListener = denied); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        assertFalse(f.session.state.value.listening)
        f.session.approve(); runCurrent(); assertEquals(1, f.navi.destinations.size)
    }

    /** FR-13: 준비 지연 6초 후의 정상 명령이 이전 5초 외부 제한으로 폐기되지 않습니다. */
    @Test fun microphonePreparationDoesNotConsumeListeningBudget() = runTest {
        val delayed = object : Listener {
            override val preparationTimeoutMs = 15_000L
            override suspend fun listenOnce(timeoutMs: Long): String? {
                delay(6_000) // 엔진 준비
                delay(2_000) // 사용자 발화
                return "안내해줘"
            }
            override fun stop() = Unit
        }
        val f = Fixture(backgroundScope, customListener = delayed)
        f.session.start(); runCurrent(); f.drive.set(DriveState.PARKED); runCurrent()
        f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        advanceTimeBy(8_001); runCurrent()
        assertEquals(1, f.navi.destinations.size)
        f.session.close()
    }

    /** FR-06·13: 준비 중 주행 전환도 취소되며 늦은 명령은 승인되지 않습니다. */
    @Test fun drivingCancelsMicrophonePreparation() = runTest {
        var cancelled = false
        val delayed = object : Listener {
            override val preparationTimeoutMs = 15_000L
            override suspend fun listenOnce(timeoutMs: Long): String? {
                try { delay(6_000); return "안내해줘" }
                finally { cancelled = true }
            }
            override fun stop() = Unit
        }
        val f = Fixture(backgroundScope, customListener = delayed)
        f.session.start(); runCurrent(); f.drive.set(DriveState.PARKED); runCurrent()
        f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        advanceTimeBy(1_000); f.drive.set(DriveState.DRIVING); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertTrue(cancelled); assertFalse(f.session.state.value.listening)
        assertTrue(f.navi.destinations.isEmpty())
        f.session.close()
    }

    /** FR-09: 미지원 Safety 경고는 음성이나 화면을 발생시키지 않습니다. */
    @Test fun unsupportedWarningIsIgnored() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.vehicle.emit(VehicleEvent.Raised(WarningId.SAFETY_WARNING)); runCurrent()
        assertTrue(f.speaker.calls.isEmpty()); assertEquals(AppUiState.Idle, f.session.state.value.screen)
    }

    /** FR-01: 지원 목록 조회 실패는 앱 종료 대신 오류 상태가 됩니다. */
    @Test fun sourceStartupFailureBecomesErrorState() = runTest {
        val broken = object : VehicleSource {
            override val events = emptyFlow<VehicleEvent>()
            override val rangeKm = emptyFlow<Double>()
            /** 초기화 실패를 재현합니다. */
            override suspend fun supportedWarnings(): Set<WarningId> = error("unavailable")
        }
        val f = Fixture(backgroundScope, customVehicle = broken); f.session.start(); runCurrent()
        assertTrue(f.session.state.value.failed); assertTrue(f.speaker.calls.isEmpty())
    }

    /** FR-13: TTS 실패 후 완료를 추정하여 듣기를 시작하지 않습니다. */
    @Test fun speechFailureDoesNotStartListening() = runTest {
        val f = Fixture(backgroundScope); f.speaker.fail = true; f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent()
        assertFalse(f.session.state.value.listening); assertIs<AppUiState.Detail>(f.session.state.value.screen)
    }

    /** FR-06: 백그라운드에서는 듣기와 상세 표시를 안전하게 제한합니다. */
    @Test fun backgroundCancelsInteraction() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        f.session.setForeground(false); runCurrent()
        assertFalse(f.session.state.value.listening); assertFalse(f.listener.submit("안내해줘"))
        assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen)
        f.session.setForeground(true); runCurrent(); assertIs<AppUiState.Detail>(f.session.state.value.screen)
    }

    /** FR-13: 파일 키워드의 공백 정규화와 동작 간 모호성 판정을 검증합니다. */
    @Test fun voiceDictionaryMatchingUsesAssets() {
        val commands = VoiceCommands.parse(asset("voice_commands_ko.json"))
        assertEquals(VoiceCommand.REPEAT, commands.match("한 번 더"))
        assertEquals(VoiceCommand.ACCEPT_NAVI, commands.match("안내해줘"))
        assertEquals(VoiceCommand.DISMISS, commands.match("괜찮아"))
        assertNull(commands.match("응 아니")); assertNull(commands.match("무슨 소리")); assertNull(commands.match(null))
        assertEquals(VoiceCommand.REPEAT, commands.match("다시 한번더"))
        assertFails { VoiceCommands.parse(asset("voice_commands_ko.json").replace("ON_DEVICE", "SERVER")) }
    }
    /** FR-13: 취소를 무시하는 어댑터의 늦은 인식 결과도 새 화면에 적용하지 않습니다. */
    @Test fun uncancellableLateRecognitionIsRejected() = runTest {
        val response = CompletableDeferred<String?>()
        val late = object : Listener {
            /** 취소 이후에 도착하는 외부 SDK 콜백을 재현합니다. */
            override suspend fun listenOnce(timeoutMs: Long): String? = withContext(NonCancellable) { response.await() }
            /** 테스트에서 늦은 결과를 직접 전달하기 위해 중지를 무시합니다. */
            override fun stop() = Unit
        }
        val f = Fixture(backgroundScope, customListener = late); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        f.drive.set(DriveState.DRIVING); runCurrent(); response.complete("안내해줘"); runCurrent()
        assertTrue(f.navi.destinations.isEmpty()); assertFalse(f.session.state.value.listening)
    }

    /** FR-07: 주행 상태 수집 전에 버튼 입력이 와도 최신 주행 상태로 승인을 차단합니다. */
    @Test fun approvalCannotRaceDrivingStateCollection() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent()
        f.drive.set(DriveState.DRIVING)
        f.session.approve(); runCurrent()
        assertTrue(f.navi.destinations.isEmpty())
    }

    /** FR-13: 긴급 경고로 교체되면 이전 경고를 향한 인식 결과를 폐기합니다. */
    @Test fun higherPriorityWarningCancelsPreviousVoiceApproval() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        f.vehicle.setRange(25.0); runCurrent()
        assertFalse(f.listener.submit("안내해줘")); assertTrue(f.navi.destinations.isEmpty())
        assertEquals(WarningId.RANGE_CRITICAL, (f.session.state.value.screen as AppUiState.Detail).warning.id)
        f.completeSpeech(); runCurrent(); assertTrue(f.session.state.value.listening)
    }

    /** FR-07, FR-13: 행동이 없는 경고는 음성으로 승인해도 내비를 호출하지 않습니다. */
    @Test fun voiceCannotNavigateNoneAction() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent()
        f.vehicle.emit(VehicleEvent.Raised(WarningId.TIRE_RAPID_DROP, mapOf("position" to "REAR_LEFT"))); runCurrent()
        f.completeSpeech(); runCurrent(); f.listener.submit("안내해줘"); runCurrent()
        assertTrue(f.navi.destinations.isEmpty())
    }

    /** FR-14: 화면 종료 후에도 새 경고는 한 문장으로 한 번만 자동 안내합니다. */
    @Test fun backgroundWarningSpeaksOnceWithoutListeningOrNavigation() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.drive.set(DriveState.PARKED); runCurrent(); f.session.setForeground(false); runCurrent()
        repeat(5) { f.raise(); runCurrent() }
        val screen = assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen)
        assertEquals(listOf(screen.text to false), f.speaker.calls)
        f.completeSpeech(); runCurrent()
        assertFalse(f.session.state.value.foreground); assertFalse(f.session.state.value.listening)
        f.session.approve(); f.session.repeat(); runCurrent()
        assertTrue(f.navi.destinations.isEmpty()); assertEquals(1, f.speaker.calls.size)
        f.session.setForeground(true); runCurrent()
        assertIs<AppUiState.Detail>(f.session.state.value.screen)
        assertEquals(2, f.speaker.calls.size)
        f.completeSpeech(); runCurrent(); assertTrue(f.session.state.value.listening)
    }

    /** FR-14: 화면이 없어도 거리 경고의 대체와 해소를 처리합니다. */
    @Test fun backgroundRangeEscalationAndClearRemainActive() = runTest {
        val f = Fixture(backgroundScope); f.session.setForeground(false); f.session.start(); runCurrent()
        f.vehicle.setRange(70.0); runCurrent()
        assertEquals(WarningId.RANGE_LOW, assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen).id)
        f.vehicle.setRange(25.0); runCurrent()
        assertEquals(WarningId.RANGE_CRITICAL, assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen).id)
        assertEquals(2, f.speaker.calls.size)
        f.vehicle.setRange(95.0); runCurrent()
        assertEquals(AppUiState.Idle, f.session.state.value.screen)
        assertFalse(f.session.state.value.listening)
    }

    /** FR-14: 화면 재연결로 구독이 중복되거나 같은 안내가 반복되지 않습니다. */
    @Test fun repeatedStartAndScreenReattachmentKeepSingleSession() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); f.session.start(); runCurrent()
        f.raise(); runCurrent(); f.completeSpeech(); runCurrent()
        repeat(3) { f.session.setForeground(false); f.session.setForeground(true); runCurrent() }
        f.raise(); runCurrent()
        assertEquals(1, f.speaker.calls.size)
        f.session.setForeground(false); runCurrent()
        f.vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)); runCurrent(); f.raise(); runCurrent()
        assertEquals(2, f.speaker.calls.size)
    }

    /** FR-14: 서비스가 종료되면 이후 입력은 더 이상 안내하지 않습니다. */
    @Test fun stoppedMonitorDoesNotConsumeWarnings() = runTest {
        val f = Fixture(backgroundScope); f.session.start(); runCurrent()
        f.session.setForeground(false); f.session.close()
        f.raise(); f.vehicle.setRange(25.0); runCurrent()
        assertTrue(f.speaker.calls.isEmpty()); assertEquals(AppUiState.Idle, f.session.state.value.screen)
    }

    /** FR-14: 부팅처럼 화면 없이 시작해도 처음부터 UNKNOWN 규칙으로 안내합니다. */
    @Test fun coldBackgroundStartUsesShortWarningEvenWhenParked() = runTest {
        val f = Fixture(backgroundScope, foreground = false)
        f.drive.set(DriveState.PARKED); f.session.start(); runCurrent()
        f.raise(); runCurrent()
        val screen = assertIs<AppUiState.DrivingOverlay>(f.session.state.value.screen)
        assertEquals(screen.text, f.speaker.calls.single().first)
        f.completeSpeech(); runCurrent()
        assertFalse(f.session.state.value.foreground); assertFalse(f.session.state.value.listening)
    }

}
