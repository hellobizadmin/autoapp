/*******************************
 목   적 : 고정 데모 목적지 목록 파싱
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
package chageun.core

import com.google.gson.JsonParser

/** FR-07: 실제 검색 없이 사전에 제공한 카테고리별 목적지를 읽습니다. */
object DemoPlaces {
    /** 유효한 좌표와 이름을 가진 장소 목록을 원래 순서대로 반환합니다. */
    fun parse(json: String): Map<String, List<Place>> = JsonParser.parseString(json).asJsonObject.entrySet().associate { (category, value) ->
        category to value.asJsonArray.map {
            val row = it.asJsonObject
            Place(row["name"].asString, row["lat"].asDouble, row["lng"].asDouble).also { place ->
                require(place.name.isNotBlank() && place.lat in -90.0..90.0 && place.lng in -180.0..180.0)
            }
        }
    }
}
