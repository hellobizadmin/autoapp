/*******************************
 목   적 : 대기·주행·정차 화면과 접근 가능한 안내 버튼
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 방향 A(규칙 내 완성도) 적용 - 긴급도 배지·액센트 바·카드·주/보조 버튼·의미 아이콘
 2026-11-13 : 좌우 분할 - 타이어 그림은 왼쪽 VehiclePanel로 이동, 주행 문구 줄바꿈 허용
 2026-11-13 : 오른쪽 화면 재디자인 - 대기 상태 요약 목록, 정차 화면 제목·본문 위계, 하단 고정 버튼
 2026-11-13 : 대기 화면에 지원 경고 9종 격자 표시
*******************************/
package chageun.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chageun.core.*
import kotlin.math.min

/** 한국어 문장이 단어(어절) 중간에서 끊기지 않도록 어절 단위로 줄을 바꿉니다. (API 33 이상에서 적용) */
private val Phrase = TextStyle(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))

/** 화면에 보여주는 지원 경고 9종입니다. (기존 4종 + FR-15 2차 5종, 비활성 SAFETY_WARNING 제외) */
private val displayedWarnings: List<WarningId> = listOf(
    WarningId.TIRE_LOW, WarningId.TIRE_RAPID_DROP, WarningId.RANGE_LOW, WarningId.RANGE_CRITICAL
) + phaseTwoWarnings

/** 경고 종류를 뜻으로 전달하는 의미 아이콘입니다. */
private fun WarningId.icon(): ImageVector = when (this) {
    WarningId.TIRE_LOW, WarningId.TIRE_RAPID_DROP -> Icons.Filled.TireRepair
    WarningId.RANGE_LOW, WarningId.RANGE_CRITICAL -> Icons.Filled.LocalGasStation
    WarningId.ENGINE_OIL_PRESSURE -> Icons.Filled.OilBarrel
    WarningId.ENGINE_OVERHEAT -> Icons.Filled.Thermostat
    WarningId.BRAKE_FLUID_LOW -> Icons.Filled.WaterDrop
    WarningId.ABS_WARNING -> Icons.Filled.DiscFull
    WarningId.BATTERY_CHARGING -> Icons.Filled.BatteryAlert
    WarningId.SAFETY_WARNING -> Icons.Filled.CarRepair
}

/** FR-02: 활성 경고가 없을 때 조용히 보여주는 대기 화면입니다. 살피는 경고 9종을 함께 보여 안심을 줍니다. */
@Composable
fun IdleScreen(dictionary: WarningDictionary) {
    Column(Modifier.fillMaxSize().testTag("idle").padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center) {
        StatusPill(dictionary.text("idle_watching"), ChageunColors.Listening)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // "지금은 괜찮다"를 전달하는 의미 아이콘
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ChageunColors.Ok,
                modifier = Modifier.size(56.dp).testTag("idle_ok"))
            Spacer(Modifier.width(20.dp))
            Text(dictionary.text("idle"), color = ChageunColors.TextPrimary, fontSize = 46.sp,
                fontWeight = FontWeight.Bold, lineHeight = 58.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(dictionary.text("idle_sub"), color = ChageunColors.TextSecondary, fontSize = 32.sp, lineHeight = 46.sp, style = Phrase)
        Spacer(Modifier.height(32.dp))
        Surface(color = ChageunColors.Surface, shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, ChageunColors.SurfaceBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 카드 머리: 몇 종을 살피는지 + 전체 상태(색만으로 구분하지 않도록 글자 병기)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dictionary.text("idle_list_title", mapOf("count" to displayedWarnings.size.toString())),
                        color = ChageunColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Check, contentDescription = null, tint = ChageunColors.Ok, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(dictionary.text("idle_all_clear"), color = ChageunColors.Ok, fontSize = 28.sp,
                        fontWeight = FontWeight.Medium)
                }
                displayedWarnings.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { id ->
                            WarningTile(id, dictionary.text("item_${id.name}"), Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

/** 대기 화면 격자의 경고 한 칸입니다. 의미 아이콘 + 이름 + 경고 없음 체크를 보여줍니다. */
@Composable
private fun WarningTile(id: WarningId, label: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(ChageunColors.Background.copy(alpha = 0.55f))
        .padding(horizontal = 16.dp, vertical = 16.dp).testTag("idle_item_${id.name}"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(ChageunColors.SurfaceBorder),
            contentAlignment = Alignment.Center) {
            Icon(id.icon(), contentDescription = null, tint = ChageunColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = ChageunColors.TextPrimary, fontSize = 26.sp, lineHeight = 34.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = Phrase, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Check, contentDescription = null, tint = ChageunColors.Ok, modifier = Modifier.size(26.dp))
    }
}

/** 작은 상태 알약(점 + 글자)입니다. */
@Composable
private fun StatusPill(text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
        .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(text, color = color, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

/** FR-04, FR-06: 주행 및 상태 불명에서는 상세 내용과 버튼 없이 긴급도와 한 문장만 표시합니다. */
@Composable
fun DrivingOverlay(state: AppUiState.DrivingOverlay, dictionary: WarningDictionary) {
    Box(Modifier.fillMaxSize().testTag("driving"), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // 긴급도 색 세로 막대로 문장을 한 덩어리로 묶어 한눈에 읽히게 합니다.
            Box(Modifier.width(8.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(state.urgency.accent()))
            Spacer(Modifier.width(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                UrgencyBadge(state.urgency, dictionary, labelSize = 30.sp, iconSize = 32.dp)
                Text(state.text.orEmpty(), color = ChageunColors.TextPrimary, fontSize = 42.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 58.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, style = Phrase)
            }
        }
    }
}

/** FR-05, FR-07, FR-08, FR-13: 정차 상세 화면과 최대 두 개의 큰 버튼을 표시합니다. */
@Composable
fun DetailScreen(state: AppUiState.Detail, listening: Boolean, dictionary: WarningDictionary,
                 onNavigate: () -> Unit, onRepeat: () -> Unit) {
    val warning = state.warning
    val urgency = warning.urgency
    val title = runCatching { dictionary.text("title_${warning.id.name}") }.getOrNull()
    Column(Modifier.fillMaxSize().testTag("detail")) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.height(8.dp))
            UrgencyBadge(urgency, dictionary, labelSize = 28.sp, iconSize = 32.dp)
            title?.let {
                Text(it, color = ChageunColors.TextPrimary, fontSize = 48.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 60.sp, style = Phrase, modifier = Modifier.testTag("detail_title"))
            }
            Box(Modifier.width(64.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(urgency.accent()))
            Text(screenText(warning.park.orEmpty(), state.showNavigation), color = ChageunColors.TextBody,
                fontSize = 34.sp, lineHeight = 52.sp, style = Phrase)
            // 주행 가능 거리는 숫자로도 안내되지만, 얼마나 남았는지 길이로 한 번 더 보여주는 보조 막대입니다.
            warning.rangeKm?.takeIf { warning.id == WarningId.RANGE_LOW || warning.id == WarningId.RANGE_CRITICAL }
                ?.let { RangeGauge(it, urgency.accent(), dictionary) }
            if (listening) ListeningIndicator(dictionary)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.showNavigation) {
                Button(onClick = onNavigate, shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChageunColors.Primary,
                        contentColor = ChageunColors.OnPrimary),
                    modifier = Modifier.weight(1f).heightIn(min = 96.dp).testTag("navigate")) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(dictionary.text("navigate", mapOf("category" to warning.action.category.orEmpty())),
                        fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (warning.park != null) {
                OutlinedButton(onClick = onRepeat, shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, ChageunColors.SurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ChageunColors.Surface,
                        contentColor = ChageunColors.TextPrimary),
                    modifier = Modifier.weight(1f).heightIn(min = 96.dp).testTag("repeat")) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(dictionary.text("repeat"), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * 8.3 명세: 안내 버튼이 있으면 마지막 질문 문장("…안내해 드릴까요?")은 음성으로만 말하고 화면에서는 뺍니다.
 * 버튼이 같은 질문을 대신하므로 화면 문장 수를 줄여 읽기 부담을 낮춥니다.
 */
private fun screenText(park: String, showNavigation: Boolean): String {
    if (!showNavigation || !park.trimEnd().endsWith("?")) return park
    val cut = park.trimEnd().dropLast(1).lastIndexOfAny(charArrayOf('.', '!', '?'))
    return if (cut > 0) park.substring(0, cut + 1) else park
}

/** 긴급도를 의미 아이콘+글자 라벨 배지로 표시합니다. (색만으로 구분하지 않도록 라벨 병기) */
@Composable
private fun UrgencyBadge(urgency: Urgency, dictionary: WarningDictionary, labelSize: TextUnit, iconSize: Dp) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(urgency.container())
        .padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(urgency.icon(), contentDescription = null, tint = urgency.accent(), modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(12.dp))
        Text(dictionary.text(urgency.name), color = urgency.accent(), fontSize = labelSize,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 주행 가능 거리를 숫자와 길이로 함께 보여주는 막대 게이지입니다. 150km를 데모용 최대 눈금으로 둡니다. */
@Composable
private fun RangeGauge(rangeKm: Int, accent: Color, dictionary: WarningDictionary) {
    val fraction = min(rangeKm / 150f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(dictionary.text("range_left_label"), color = ChageunColors.TextSecondary, fontSize = 28.sp,
                modifier = Modifier.weight(1f))
            Text(dictionary.text("range_left", mapOf("range" to rangeKm.toString())), color = accent,
                fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
            .background(ChageunColors.SurfaceBorder).testTag("range_gauge")) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceAtLeast(0.04f))
                .clip(RoundedCornerShape(14.dp)).background(accent))
        }
    }
}

/** FR-13: 짧은 음성 응답을 듣는 중임을 표시합니다. */
@Composable
private fun ListeningIndicator(dictionary: WarningDictionary) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(ChageunColors.Listening.copy(alpha = 0.12f))
        .padding(horizontal = 22.dp, vertical = 12.dp).testTag("listening"),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = ChageunColors.Listening,
            modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(dictionary.text("listening"), color = ChageunColors.Listening, fontSize = 30.sp)
    }
}
