/*******************************
 목   적 : 디버그 전용 차량 및 음성 입력 제어 패널
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import chageun.adapter.fake.*
import chageun.adapter.Listener
import chageun.core.*

/** FR-12: 디버그 빌드에서만 열리는 시뮬레이션 입력 패널입니다. */
@Composable
fun DemoPanel(dictionary: WarningDictionary, vehicle: FakeVehicleSource, drive: FakeDriveStateSource,
              listener: Listener, navi: LogNaviLinker, listening: Boolean, onClose: () -> Unit) {
    var position by remember { mutableStateOf(dictionary.positions().keys.first()) }
    var range by remember { mutableFloatStateOf(vehicle.rangeKm.value.toFloat()) }
    var voice by remember { mutableStateOf("") }
    var fail by remember { mutableStateOf(navi.shouldFail) }
    var extraWarning by remember { mutableStateOf(phaseTwoWarnings.first()) }
    AlertDialog(onDismissRequest = onClose, title = { Text(dictionary.text("demo")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    DriveState.entries.forEach { state ->
                        TextButton(onClick = { drive.set(state) }, modifier = Modifier.testTag("set_${state.name}")) { Text(dictionary.text(state.name)) }
                    }
                }
                dictionary.positions().forEach { (key, label) ->
                    FilterChip(selected = position == key, onClick = { position = key }, label = { Text(label) })
                }
                Button(onClick = { vehicle.emit(VehicleEvent.Raised(WarningId.TIRE_LOW, mapOf("position" to position))) }, modifier = Modifier.testTag("raise_tire")) { Text(dictionary.text("raise_tire")) }
                TextButton(onClick = { vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_LOW)) }) { Text(dictionary.text("clear_tire")) }
                Button(onClick = { vehicle.emit(VehicleEvent.Raised(WarningId.TIRE_RAPID_DROP, mapOf("position" to position))) }) { Text(dictionary.text("raise_rapid")) }
                TextButton(onClick = { vehicle.emit(VehicleEvent.Cleared(WarningId.TIRE_RAPID_DROP)) }) { Text(dictionary.text("clear_rapid")) }
                Text(dictionary.text("safety_unavailable"))
                // FR-12, FR-15: 실제 SDK 감지가 아닌 명시적 시뮬레이션 입력입니다.
                Text(dictionary.text("phase_two_demo"))
                phaseTwoWarnings.forEach { id ->
                    FilterChip(selected = extraWarning == id, onClick = { extraWarning = id },
                        modifier = Modifier.testTag("select_${id.name}"),
                        label = { Text(dictionary.text("warning_${id.name}")) })
                }
                Button(onClick = { vehicle.emit(VehicleEvent.Raised(extraWarning)) },
                    modifier = Modifier.testTag("raise_extra")) {
                    Text(dictionary.text("raise_selected"))
                }
                TextButton(onClick = { vehicle.emit(VehicleEvent.Cleared(extraWarning)) },
                    modifier = Modifier.testTag("clear_extra")) {
                    Text(dictionary.text("clear_selected"))
                }
                Text(dictionary.text("range", mapOf("range" to range.toInt().toString())))
                Slider(value = range, onValueChange = { range = it; vehicle.setRange(it.toDouble()) }, valueRange = 0f..150f, steps = 149)
                if (listener is FakeListener) {
                OutlinedTextField(value = voice, onValueChange = { voice = it }, label = { Text(dictionary.text("voice_input")) }, modifier = Modifier.testTag("voice_input"))
                Button(enabled = listening, onClick = { listener.submit(voice); voice = "" }, modifier = Modifier.testTag("submit_voice")) { Text(dictionary.text("submit_voice")) }
                } else Text(dictionary.text("microphone_real"))
                Row {
                    Checkbox(checked = fail, onCheckedChange = { fail = it; navi.shouldFail = it })
                    Text(dictionary.text("navi_failure"))
                }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text(dictionary.text("close_panel")) } })
}
