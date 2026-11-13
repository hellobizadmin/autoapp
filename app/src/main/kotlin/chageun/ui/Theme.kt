/*******************************
 목   적 : 화면 전체에서 공유하는 색 팔레트와 긴급도 색 규칙
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안 (FEATURE_SPEC 8장 대비·긴급도 색+라벨 규칙을 한곳에서 관리)
*******************************/
package chageun.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import chageun.core.Urgency

/**
 * 인라인 hex를 한곳으로 모은 팔레트입니다.
 * FEATURE_SPEC 8장의 대비(4.5:1 이상)와 "색만으로 구분하지 않기" 규칙을 지키기 위해
 * 긴급도는 강조색과 배지 배경을 쌍으로 두고, 화면에서는 항상 글자 라벨을 함께 보여줍니다.
 */
object ChageunColors {
    val Background = Color(0xFF0B1321)      // 바탕 네이비
    val Surface = Color(0xFF16233A)         // 상세 카드 배경 (바탕에서 살짝 떠 보이게)
    val SurfaceBorder = Color(0xFF2A3A54)   // 카드·외곽 버튼 테두리
    val TextPrimary = Color(0xFFF2F6FC)     // 본문 글자
    val TextSecondary = Color(0xFFAEBCD2)   // 보조 글자 (앱 이름 옆, 대기 안내)
    val TextBody = Color(0xFFD9E2EE)        // 정차 화면 본문 (제목보다 한 단계 낮은 밝기, 대비 4.5:1 이상)
    val Primary = Color(0xFF6FA8FF)         // 주 행동(안내) 버튼 배경
    val OnPrimary = Color(0xFF07101F)       // 주 버튼 위 글자
    val Listening = Color(0xFF78E0C6)       // 듣는 중 표시
    val Ok = Color(0xFF7ED99A)              // 대기 화면 정상 표시
    val Warn = Color(0xFFFFCB7A)            // 음성 출력 불가 등 주의 안내

    // 긴급도: 강조색(글자·액센트 바·점) + 배지 배경
    val NowAccent = Color(0xFFFF8A8A)
    val NowContainer = Color(0xFF3A1D22)
    val TodayAccent = Color(0xFFFFC773)
    val TodayContainer = Color(0xFF382C18)
    val NextAccent = Color(0xFFC7D2E0)
    val NextContainer = Color(0xFF26324A)
}

/** 긴급도 강조색(글자·왼쪽 액센트 바·점)을 돌려줍니다. */
fun Urgency.accent(): Color = when (this) {
    Urgency.NOW -> ChageunColors.NowAccent
    Urgency.TODAY -> ChageunColors.TodayAccent
    Urgency.NEXT -> ChageunColors.NextAccent
}

/** 긴급도 배지의 배경색을 돌려줍니다. */
fun Urgency.container(): Color = when (this) {
    Urgency.NOW -> ChageunColors.NowContainer
    Urgency.TODAY -> ChageunColors.TodayContainer
    Urgency.NEXT -> ChageunColors.NextContainer
}

/**
 * 긴급도를 뜻으로 전달하는 의미 아이콘입니다 (색만으로 구분하지 않기 위한 보조 수단).
 * NOW=즉시 조치가 필요하다는 경고, TODAY=오늘 안에 할 일, NEXT=다음 점검 때 기억할 일정.
 */
fun Urgency.icon(): ImageVector = when (this) {
    Urgency.NOW -> Icons.Filled.Warning
    Urgency.TODAY -> Icons.Filled.Schedule
    Urgency.NEXT -> Icons.Filled.EventAvailable
}
