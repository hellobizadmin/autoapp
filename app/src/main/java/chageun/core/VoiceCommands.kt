/*******************************
 목   적 : 음성 명령 사전 로드 및 모호한 입력 차단
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.core

import com.google.gson.JsonParser

/** FR-13: 지원하는 짧은 음성 명령입니다. */
enum class VoiceCommand { REPEAT, ACCEPT_NAVI, DISMISS }

/** FR-13: 외부 전송 없이 사전 키워드로만 명령을 판정합니다. */
class VoiceCommands private constructor(private val commands: Map<VoiceCommand, List<String>>, val dismissResponse: String) {
    /** 공백 제거 후 하나의 동작만 매칭될 때 반환하며 모호한 입력은 무시합니다. */
    fun match(text: String?): VoiceCommand? {
        val normalized = text?.filterNot(Char::isWhitespace).orEmpty()
        if (normalized.isEmpty()) return null
        return commands.filterValues { words -> words.any(normalized::contains) }.keys.singleOrNull()
    }
    /** 음성 명령 JSON의 읽기 진입점입니다. */
    companion object {
        /** 온디바이스 모드와 키워드 구성을 검증하며 파일에서만 키워드를 읽습니다. */
        fun parse(json: String): VoiceCommands {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.getAsJsonObject("_meta")["stt_mode"].asString == "ON_DEVICE")
            val commands = VoiceCommand.entries.associateWith { command ->
                root.getAsJsonObject("commands").getAsJsonArray(command.name).map {
                    it.asString.filterNot(Char::isWhitespace).also { word -> require(word.isNotBlank()) }
                }.also { require(it.isNotEmpty()) }
            }
            return VoiceCommands(commands, root.getAsJsonObject("responses")["DISMISS"].asString)
        }
    }
}
