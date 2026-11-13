/*******************************
 목   적 : 주행 가능 거리의 경고 임계값과 해소 조건 판정
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
*******************************/
package chageun.core

import com.google.gson.JsonParser

/** 거리 경고 판정에 사용할 임계값과 해소 여유값입니다. */
data class UrgencyRules(val lowKm: Double, val criticalKm: Double, val hysteresisKm: Double) {
    // 임계값이 유효한 유한수이며 크기 관계가 올바른지 확인합니다.
    init {
        require(lowKm.isFinite() && criticalKm.isFinite() && hysteresisKm.isFinite())
        require(criticalKm > 0 && lowKm > criticalKm && hysteresisKm >= 0)
    }
    /** JSON에서 인스턴스를 생성하는 기능을 제공합니다. */
    companion object {
        /** 설정 JSON에서 거리 임계값과 해소 여유값을 읽습니다. */
        fun parse(json: String): UrgencyRules {
            val root = JsonParser.parseString(json).asJsonObject
            return UrgencyRules(root["range_low_km"].asDouble, root["range_critical_km"].asDouble,
                root["range_hysteresis_km"].asDouble)
        }
    }

    // FR-03: CRITICAL이 LOW를 대체하며 해소 여유값으로 경계 부근의 반복 전환을 막습니다.
    fun rangeWarning(km: Double, current: WarningId?): WarningId? {
        require(km.isFinite() && km >= 0)
        return when {
            km < criticalKm -> WarningId.RANGE_CRITICAL
            current == WarningId.RANGE_CRITICAL && km < criticalKm + hysteresisKm -> WarningId.RANGE_CRITICAL
            km < lowKm -> WarningId.RANGE_LOW
            current != null && km < lowKm + hysteresisKm -> WarningId.RANGE_LOW
            else -> null
        }
    }
}
