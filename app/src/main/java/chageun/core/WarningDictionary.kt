/*******************************
 목   적 : 경고 사전 파싱과 안내 문구 변수 치환
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
 2026-11-13 : FR-08·FR-13 및 앱 모듈 연결 지원
*******************************/
package chageun.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.roundToInt

/** 사전 데이터에서 경고 문구와 후속 행동을 해석합니다. */
class WarningDictionary private constructor(
    private val root: JsonObject,
    private val log: (String) -> Unit,
) {
    /** JSON에서 인스턴스를 생성하는 기능을 제공합니다. */
    companion object {
        // 호출자가 assets/warning_dictionary_ko.json 내용을 전달합니다.
        /** 사전 JSON을 읽고 파싱 실패를 결과 객체로 반환합니다. */
        fun parse(json: String, log: (String) -> Unit = {}): Result<WarningDictionary> = runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.getAsJsonArray("warnings").size() > 0)
            require(root.getAsJsonObject("fuel_words").has(root.getAsJsonObject("_meta")["vehicle_type"].asString))
            root.getAsJsonArray("warnings").forEach {
                val row = it.asJsonObject
                WarningId.valueOf(row["id"].asString)
                require(row["drive"].asString.isNotBlank() && row["park"].asString.isNotBlank())
                require(row["urgency"].asString in Urgency.entries.map { urgency -> urgency.name } + "BY_CODE")
                require(row.getAsJsonObject("action")["type"].asString in listOf("NAVI_SUGGEST", "NONE", "BY_CODE"))
                // FR-15: 같은 긴급도 안에서는 작은 우선순위 값부터 안내합니다.
                row["priority"]?.let { require(it.asString.toIntOrNull()?.let { value -> value >= 0 } == true) }
            }
            WarningDictionary(root, log)
        }
    }

    /** 화면과 응답 문구를 사전에서 읽으며 미치환 변수는 허용하지 않습니다. */
    fun text(key: String, params: Map<String, String> = emptyMap()): String {
        var value = root.getAsJsonObject("ui")[key].asString
        params.forEach { (name, replacement) -> value = value.replace("{$name}", replacement) }
        require('{' !in value && '}' !in value)
        return value
    }

    /** 타이어 위치 선택과 화면에 사용할 쉬운 이름을 제공합니다. */
    fun positions(): Map<String, String> = root.getAsJsonObject("positions").entrySet().associate { it.key to it.value.asString }

    // FR-03: 사전의 긴급도를 사용하며 미확인 Safety 코드는 추측하지 않습니다.
    fun resolve(id: WarningId, params: Map<String, String>): ActiveWarning? {
        val entry = root.getAsJsonArray("warnings").map { it.asJsonObject }
            .firstOrNull { it["id"].asString == id.name } ?: return null
        val urgency = Urgency.entries.firstOrNull { it.name == entry["urgency"].asString }
        if (urgency == null) {
            // TODO(verify-sdk): BY_CODE 활성화 전에 공식 Safety 코드 매핑을 확인해야 합니다.
            log("CHAGEUN unresolved urgency: $id")
            return null
        }
        val values = params.toMutableMap()
        // 화면의 타이어 위치 다이어그램용 원본 키입니다. 사람이 읽는 문구(values["position"])와는 별개로 보관합니다.
        val positionKey = params["position"]?.takeIf { root.getAsJsonObject("positions").has(it) }
        params["position"]?.let { position ->
            values.remove("position")
            root.getAsJsonObject("positions")[position]?.let { values["position"] = it.asString }
        }
        // 화면의 주행 가능 거리 게이지용 반올림 정수값입니다.
        val rangeKm = params["range"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.roundToInt()
        params["range"]?.let { raw ->
            values.remove("range")
            raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let {
                values["range"] = it.roundToInt().toString()
            }
        }
        val fuel = root.getAsJsonObject("fuel_words")
            .getAsJsonObject(root.getAsJsonObject("_meta")["vehicle_type"].asString)
        values["fuel"] = fuel["fuel"].asString
        values["station"] = fuel["station"].asString
        /** 템플릿을 치환하고 미해결 변수가 남으면 로그를 남기고 제외합니다. */
        fun render(template: String): String? {
            val result = Regex("\\{([^{}]+)\\}").replace(template) { values[it.groupValues[1]] ?: it.value }
            if ('{' in result || '}' in result) {
                log("CHAGEUN unresolved template: $id")
                return null
            }
            return result
        }
        val action = entry.getAsJsonObject("action")
        val category = action["category"]?.asString?.let(::render)
        val type = if (action["type"].asString == "NAVI_SUGGEST" && category != null)
            ActionType.NAVI_SUGGEST else ActionType.NONE
        return ActiveWarning(id, urgency, render(entry["drive"].asString), render(entry["park"].asString),
            WarningAction(type, category), positionKey, rangeKm, entry["priority"]?.asInt ?: 100)
    }
}
