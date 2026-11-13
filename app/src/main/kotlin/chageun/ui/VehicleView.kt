/*******************************
 목   적 : 화면 왼쪽 차량 윗면 그림과 경고 부위 강조
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안 (좌우 분할 레이아웃 - 왼쪽 차량, 오른쪽 안내)
 2026-11-13 : FR-15 엔진·충전 경고는 엔진룸, 브레이크·ABS 경고는 네 바퀴 강조
 2026-11-13 : 차량 그림을 실제 세단 윗면처럼 개선 (곡선 차체·금속 광택·유리·필러·램프·그림자)
*******************************/
package chageun.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chageun.core.*

/** 차량 그림에서 강조할 부위입니다. */
sealed interface VehiclePart {
    /** 특정 바퀴(positionKey가 없으면 네 바퀴 모두)를 강조합니다. */
    data class Tire(val positionKey: String?) : VehiclePart
    /** 연료(주행 가능 거리)를 강조합니다. */
    data object Fuel : VehiclePart
    /** 엔진룸(차량 앞쪽 보닛)을 강조합니다. labelKey는 이름표 문구, icon은 엔진룸 위 표시입니다. */
    data class EngineBay(val labelKey: String, val icon: ImageVector) : VehiclePart
    /** 제동 계통 경고로 네 바퀴(브레이크가 있는 곳)를 강조합니다. */
    data class Brakes(val labelKey: String) : VehiclePart
    /** 특정 부위를 알 수 없는 안전 경고로 차체 전체를 강조합니다. */
    data object Body : VehiclePart
}

/** 경고 종류에서 강조할 차량 부위를 고릅니다. */
fun vehiclePartOf(id: WarningId, positionKey: String?): VehiclePart = when (id) {
    WarningId.TIRE_LOW, WarningId.TIRE_RAPID_DROP -> VehiclePart.Tire(positionKey)
    WarningId.RANGE_LOW, WarningId.RANGE_CRITICAL -> VehiclePart.Fuel
    // FR-15: 차종마다 다른 부품의 정확한 위치(예: 배터리·브레이크액 통)는 추측하지 않고,
    // 차종과 무관하게 맞는 "계통 구역"만 강조합니다. (사전 vehicle_type = GASOLINE, 앞 엔진 기준)
    // 충전 경고는 배터리 자체보다 엔진이 돌리는 충전 장치(발전기) 문제이므로 엔진룸으로 둡니다.
    WarningId.ENGINE_OIL_PRESSURE -> VehiclePart.EngineBay("part_engine_oil", Icons.Filled.OilBarrel)
    WarningId.ENGINE_OVERHEAT -> VehiclePart.EngineBay("part_engine_heat", Icons.Filled.Thermostat)
    WarningId.BATTERY_CHARGING -> VehiclePart.EngineBay("part_charging", Icons.Filled.BatteryAlert)
    WarningId.BRAKE_FLUID_LOW -> VehiclePart.Brakes("part_brake_fluid")
    WarningId.ABS_WARNING -> VehiclePart.Brakes("part_abs")
    WarningId.SAFETY_WARNING -> VehiclePart.Body
}

/** 화면 상태에서 강조할 부위와 긴급도를 꺼냅니다. 대기 화면이면 null입니다. */
private fun AppUiState.focus(): Pair<VehiclePart, Urgency>? = when (this) {
    AppUiState.Idle -> null
    is AppUiState.DrivingOverlay -> vehiclePartOf(id, positionKey) to urgency
    is AppUiState.Detail -> vehiclePartOf(warning.id, warning.positionKey) to warning.urgency
}

/**
 * 좌우 분할 레이아웃입니다. 왼쪽은 차량 그림, 오른쪽은 기존 안내 화면(대기·주행·정차)을 그대로 둡니다.
 * 글을 읽기 전에 "차의 어디가 문제인지"를 그림으로 먼저 알 수 있게 하는 것이 목적입니다.
 */
@Composable
fun SplitWarningLayout(screen: AppUiState, dictionary: WarningDictionary, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        VehiclePanel(screen, dictionary, Modifier.weight(0.4f).fillMaxHeight())
        Box(Modifier.weight(0.6f).fillMaxHeight()) { content() }
    }
}

/** 왼쪽 패널: 차량 윗면 그림과 강조 부위 이름표입니다. */
@Composable
fun VehiclePanel(screen: AppUiState, dictionary: WarningDictionary, modifier: Modifier = Modifier) {
    val focus = screen.focus()
    val accent = focus?.second?.accent()
    Surface(color = ChageunColors.Surface, shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.5f) ?: ChageunColors.SurfaceBorder),
        modifier = modifier.testTag("vehicle_panel")) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(dictionary.text("vehicle_front"), color = ChageunColors.TextSecondary, fontSize = 22.sp)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                VehicleTopView(focus?.first, accent ?: ChageunColors.Ok,
                    // 칸이 좁거나 낮아도 잘리지 않도록 가로·세로 중 맞는 쪽에 비율을 맞춥니다.
                    Modifier.aspectRatio(0.52f))
            }
            if (focus != null && accent != null) {
                PartLabel(partLabel(focus.first, dictionary), focus.first, accent, focus.second.container())
            }
        }
    }
}

/** 강조 부위를 글로도 적어 색만으로 구분하지 않게 합니다. */
private fun partLabel(part: VehiclePart, dictionary: WarningDictionary): String = when (part) {
    is VehiclePart.Tire -> part.positionKey?.let { dictionary.positions()[it] }
        ?.let { dictionary.text("part_tire", mapOf("position" to it)) } ?: dictionary.text("part_tire_any")
    VehiclePart.Fuel -> dictionary.text("part_fuel")
    is VehiclePart.EngineBay -> dictionary.text(part.labelKey)
    is VehiclePart.Brakes -> dictionary.text(part.labelKey)
    VehiclePart.Body -> dictionary.text("part_safety")
}

/** 강조 부위 이름표입니다. */
@Composable
private fun PartLabel(text: String, part: VehiclePart, accent: Color, container: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(container)
        .padding(horizontal = 24.dp, vertical = 12.dp).testTag("vehicle_part_label"),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(when (part) {
                VehiclePart.Fuel -> Icons.Filled.LocalGasStation
                is VehiclePart.EngineBay -> part.icon
                else -> Icons.Filled.Warning
            },
            contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = accent, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// 차량 그림 전용 색입니다. 짙은 네이비 금속 도장처럼 보이도록 가장자리는 어둡게, 가운데는 밝게 둡니다.
private val PaintEdge = Color(0xFF16233A)
private val PaintMid = Color(0xFF2E4466)
private val PaintHighlight = Color(0xFF4A6690)
private val CarOutline = Color(0xFF5A7398)
private val GlassDark = Color(0xFF070E1A)
private val GlassLight = Color(0xFF1B2B45)
private val CarTire = Color(0xFF05090F)
private val HeadLamp = Color(0xFFE6EEF8)
private val TailLamp = Color(0xFFC23A48)
private val Crease = Color(0x33FFFFFF)

/**
 * 차량 윗면 그림입니다. 위쪽이 차량 앞이며, 한국 기준 운전석(왼쪽)을 화면 왼쪽에 그대로 둡니다.
 * 실제 세단 비율(전폭:전장 약 0.38)과 곡선 차체, 유리·필러·램프를 그려 "내 차"로 읽히게 하되,
 * 강조 대상(바퀴·엔진룸·연료·차체)의 위치는 그대로 유지합니다. part가 null이면 아무 부위도 강조하지 않습니다.
 */
@Composable
fun VehicleTopView(part: VehiclePart?, accent: Color, modifier: Modifier = Modifier) {
    val fuelPainter = rememberVectorPainter(Icons.Filled.LocalGasStation)
    val warnPainter = rememberVectorPainter(Icons.Filled.Warning)
    val bayPainter = rememberVectorPainter((part as? VehiclePart.EngineBay)?.icon ?: Icons.Filled.Warning)
    Canvas(modifier.testTag("vehicle_view")) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val stroke = w * 0.012f
        val body = carBodyPath(w, h)
        val bodyLeft = cx - w * 0.335f
        val bodyRight = cx + w * 0.335f

        // 바닥 그림자: 차가 화면 위에 떠 있지 않고 놓여 있게 보이도록 겹겹이 퍼지는 그림자를 깝니다.
        for (i in 4 downTo 1) {
            drawRoundRect(Color.Black.copy(alpha = 0.07f),
                topLeft = Offset(bodyLeft - w * 0.012f * i, h * 0.03f - h * 0.004f * i + h * 0.01f),
                size = Size(w * 0.67f + w * 0.024f * i, h * 0.945f + h * 0.008f * i),
                cornerRadius = CornerRadius(w * 0.22f, h * 0.08f))
        }

        // 바퀴: 윗면에서는 차체 밖으로 살짝만 보입니다. 차체보다 먼저 그려 안쪽은 가려지게 합니다.
        val wheelW = w * 0.10f
        val wheelH = h * 0.13f
        val wheels = mapOf(
            "FRONT_LEFT" to Offset(bodyLeft + w * 0.025f, h * 0.215f),
            "FRONT_RIGHT" to Offset(bodyRight - w * 0.025f, h * 0.215f),
            "REAR_LEFT" to Offset(bodyLeft + w * 0.025f, h * 0.775f),
            "REAR_RIGHT" to Offset(bodyRight - w * 0.025f, h * 0.775f),
        )
        val tirePart = part as? VehiclePart.Tire
        val hitWheels = wheels.filterKeys { key ->
            part is VehiclePart.Brakes || (tirePart != null && (tirePart.positionKey == null || tirePart.positionKey == key))
        }
        wheels.values.forEach { c ->
            drawRoundRect(CarTire, topLeft = Offset(c.x - wheelW / 2, c.y - wheelH / 2), size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(wheelW * 0.3f))
        }

        // 차체: 가로 그라데이션으로 둥근 금속 판의 광택을 냅니다.
        val bodyHit = part == VehiclePart.Body
        if (bodyHit) {
            drawPath(body, accent.copy(alpha = 0.12f), style = Stroke(w * 0.10f))
            drawPath(body, accent.copy(alpha = 0.20f), style = Stroke(w * 0.05f))
        }
        drawPath(body, Brush.horizontalGradient(
            0f to PaintEdge, 0.22f to PaintMid, 0.5f to PaintHighlight, 0.78f to PaintMid, 1f to PaintEdge,
            startX = bodyLeft, endX = bodyRight))

        // 엔진룸 경고: 차체 모양을 따라 앞유리 앞쪽 보닛만 칠합니다.
        if (part is VehiclePart.EngineBay) {
            clipPath(body) {
                drawRect(accent.copy(alpha = 0.45f), topLeft = Offset(0f, 0f), size = Size(w, h * 0.305f))
            }
        }

        // 보닛 주름선과 트렁크 경계선
        drawPath(Path().apply {
            moveTo(cx - w * 0.17f, h * 0.075f)
            quadraticBezierTo(cx - w * 0.13f, h * 0.19f, cx - w * 0.15f, h * 0.30f)
            moveTo(cx + w * 0.17f, h * 0.075f)
            quadraticBezierTo(cx + w * 0.13f, h * 0.19f, cx + w * 0.15f, h * 0.30f)
        }, Crease, style = Stroke(stroke * 0.7f))
        drawPath(Path().apply {
            moveTo(cx - w * 0.27f, h * 0.835f)
            quadraticBezierTo(cx, h * 0.815f, cx + w * 0.27f, h * 0.835f)
        }, Crease, style = Stroke(stroke * 0.7f))

        // 앞유리: 앞쪽으로 둥글게 휘어진 곡면 유리
        val windshield = Path().apply {
            moveTo(cx - w * 0.275f, h * 0.325f)
            quadraticBezierTo(cx, h * 0.285f, cx + w * 0.275f, h * 0.325f)
            lineTo(cx + w * 0.235f, h * 0.415f)
            quadraticBezierTo(cx, h * 0.40f, cx - w * 0.235f, h * 0.415f)
            close()
        }
        drawPath(windshield, Brush.verticalGradient(listOf(GlassLight, GlassDark), startY = h * 0.29f, endY = h * 0.415f))

        // 지붕과 파노라마 선루프
        val roofTop = h * 0.415f
        val roofBottom = h * 0.665f
        drawRoundRect(Brush.horizontalGradient(listOf(PaintMid, PaintHighlight, PaintMid),
            startX = cx - w * 0.235f, endX = cx + w * 0.235f),
            topLeft = Offset(cx - w * 0.235f, roofTop), size = Size(w * 0.47f, roofBottom - roofTop),
            cornerRadius = CornerRadius(w * 0.04f))
        drawRoundRect(Brush.verticalGradient(listOf(GlassLight, GlassDark), startY = h * 0.44f, endY = h * 0.62f),
            topLeft = Offset(cx - w * 0.175f, h * 0.44f), size = Size(w * 0.35f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.04f))

        // 옆유리(양쪽)와 B필러
        for (sign in listOf(-1f, 1f)) {
            drawPath(Path().apply {
                moveTo(cx + sign * w * 0.245f, h * 0.425f)
                lineTo(cx + sign * w * 0.29f, h * 0.445f)
                lineTo(cx + sign * w * 0.29f, h * 0.645f)
                lineTo(cx + sign * w * 0.245f, h * 0.67f)
                close()
            }, GlassDark)
            drawLine(PaintMid, Offset(cx + sign * w * 0.24f, h * 0.545f), Offset(cx + sign * w * 0.295f, h * 0.545f),
                strokeWidth = stroke * 1.6f)
        }

        // 뒷유리
        drawPath(Path().apply {
            moveTo(cx - w * 0.235f, h * 0.67f)
            quadraticBezierTo(cx, h * 0.68f, cx + w * 0.235f, h * 0.67f)
            lineTo(cx + w * 0.255f, h * 0.745f)
            quadraticBezierTo(cx, h * 0.765f, cx - w * 0.255f, h * 0.745f)
            close()
        }, Brush.verticalGradient(listOf(GlassDark, GlassLight), startY = h * 0.67f, endY = h * 0.765f))

        // 사이드미러
        for (sign in listOf(-1f, 1f)) {
            drawPath(Path().apply {
                moveTo(cx + sign * w * 0.315f, h * 0.33f)
                lineTo(cx + sign * w * 0.40f, h * 0.345f)
                quadraticBezierTo(cx + sign * w * 0.415f, h * 0.365f, cx + sign * w * 0.39f, h * 0.372f)
                lineTo(cx + sign * w * 0.315f, h * 0.365f)
                close()
            }, PaintMid)
        }

        // 헤드램프(앞 모서리를 따라 비스듬한 LED)와 테일램프(뒤쪽 가로 일체형)
        for (sign in listOf(-1f, 1f)) {
            drawPath(Path().apply {
                moveTo(cx + sign * w * 0.10f, h * 0.040f)
                quadraticBezierTo(cx + sign * w * 0.23f, h * 0.042f, cx + sign * w * 0.30f, h * 0.080f)
            }, HeadLamp.copy(alpha = 0.25f), style = Stroke(stroke * 3.2f))
            drawPath(Path().apply {
                moveTo(cx + sign * w * 0.10f, h * 0.040f)
                quadraticBezierTo(cx + sign * w * 0.23f, h * 0.042f, cx + sign * w * 0.30f, h * 0.080f)
            }, HeadLamp, style = Stroke(stroke * 1.2f))
        }
        drawPath(Path().apply {
            moveTo(cx - w * 0.29f, h * 0.935f)
            quadraticBezierTo(cx, h * 0.972f, cx + w * 0.29f, h * 0.935f)
        }, TailLamp, style = Stroke(stroke * 1.3f))

        // 차체 윤곽선
        drawPath(body, if (bodyHit) accent else CarOutline, style = Stroke(if (bodyHit) stroke * 2.2f else stroke * 0.8f))

        // 강조된 바퀴: 차체 위에 다시 그려 가려지지 않게 하고, 빛 번짐으로 위치를 먼저 보이게 합니다.
        hitWheels.values.forEach { c ->
            drawCircle(accent.copy(alpha = 0.12f), radius = wheelH * 1.25f, center = c)
            drawCircle(accent.copy(alpha = 0.20f), radius = wheelH * 0.85f, center = c)
            drawRoundRect(accent, topLeft = Offset(c.x - wheelW / 2, c.y - wheelH / 2), size = Size(wheelW, wheelH),
                cornerRadius = CornerRadius(wheelW * 0.3f))
        }

        // 엔진룸 경고 아이콘
        if (part is VehiclePart.EngineBay) {
            val c = Offset(cx, h * 0.17f)
            val r = w * 0.14f
            drawCircle(accent, radius = r, center = c)
            drawIcon(bayPainter, c, r * 1.2f, CarTire)
        }
        // 연료 경고: 차체 가운데(연료 탱크가 있는 뒷좌석 아래쪽)에 주유 아이콘을 강조합니다.
        if (part == VehiclePart.Fuel) {
            val c = Offset(cx, h * 0.58f)
            val r = w * 0.15f
            drawCircle(accent.copy(alpha = 0.18f), radius = r * 1.5f, center = c)
            drawCircle(accent, radius = r, center = c)
            drawIcon(fuelPainter, c, r * 1.2f, CarTire)
        }
        if (bodyHit) {
            val c = Offset(cx, h * 0.54f)
            val r = w * 0.15f
            drawCircle(accent, radius = r, center = c)
            drawIcon(warnPainter, c, r * 1.2f, CarTire)
        }
    }
}

/** 세단 윗면 윤곽(앞 범퍼 → 앞 펜더 → 도어 허리 → 뒤 펜더 → 뒤 범퍼)을 좌우 대칭 곡선으로 만듭니다. */
private fun carBodyPath(w: Float, h: Float): Path {
    val cx = w * 0.5f
    return Path().apply {
        moveTo(cx, h * 0.030f)
        cubicTo(cx + w * 0.16f, h * 0.030f, cx + w * 0.29f, h * 0.040f, cx + w * 0.310f, h * 0.090f)
        cubicTo(cx + w * 0.335f, h * 0.140f, cx + w * 0.340f, h * 0.200f, cx + w * 0.335f, h * 0.300f)
        cubicTo(cx + w * 0.325f, h * 0.400f, cx + w * 0.325f, h * 0.600f, cx + w * 0.335f, h * 0.700f)
        cubicTo(cx + w * 0.345f, h * 0.800f, cx + w * 0.335f, h * 0.890f, cx + w * 0.300f, h * 0.940f)
        cubicTo(cx + w * 0.270f, h * 0.970f, cx + w * 0.150f, h * 0.972f, cx, h * 0.972f)
        cubicTo(cx - w * 0.150f, h * 0.972f, cx - w * 0.270f, h * 0.970f, cx - w * 0.300f, h * 0.940f)
        cubicTo(cx - w * 0.335f, h * 0.890f, cx - w * 0.345f, h * 0.800f, cx - w * 0.335f, h * 0.700f)
        cubicTo(cx - w * 0.325f, h * 0.600f, cx - w * 0.325f, h * 0.400f, cx - w * 0.335f, h * 0.300f)
        cubicTo(cx - w * 0.340f, h * 0.200f, cx - w * 0.335f, h * 0.140f, cx - w * 0.310f, h * 0.090f)
        cubicTo(cx - w * 0.290f, h * 0.040f, cx - w * 0.160f, h * 0.030f, cx, h * 0.030f)
        close()
    }
}

/** 벡터 아이콘을 중심점 기준으로 그립니다. */
private fun DrawScope.drawIcon(painter: androidx.compose.ui.graphics.vector.VectorPainter, center: Offset, side: Float, tint: Color) {
    translate(center.x - side / 2, center.y - side / 2) {
        with(painter) { draw(Size(side, side), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)) }
    }
}
