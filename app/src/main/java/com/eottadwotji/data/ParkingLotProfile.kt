package com.eottadwotji.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 자주 가는 주차장 프로필.
 *
 * GPS 좌표 반경 150m 매칭으로 같은 주차장 재방문을 판정하고,
 * 그 주차장만의 층 구성(예: 우리 아파트 B1~B4)·층별 메모·지난번 층을 제공한다.
 * 위치 미등록 프로필도 허용 — 이 경우 자동 매칭은 안 되고 수동 선택용.
 */
data class ParkingLotProfile(
    val id: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val floors: List<String>,
    val memos: Map<String, String>,
    val lastFloor: String?,
    /** v3: 이 위치 전용 바텀시트 모드 (null이면 전역 기본값) — SHEET_* 상수 */
    val sheetMode: String? = null,
    /**
     * v3.7: 기압 추정 보정 (층 단위). 지형이 높은 곳(예: 언덕 위 집)은 진입 기준
     * 지하1층이 실제 표기 1층인 식으로 체계적으로 어긋난다 — 사용자가 추정과 다른
     * 층을 고르면 그 차이를 학습해 다음 추정에 더한다.
     */
    val pressureOffsetFloors: Int = 0,
    /**
     * v3.9: 이 위치에서 기압 추정을 사용자가 한 번이라도 확인/보정했는가.
     * false면 "첫 확인" 엄격 모드 — 확인 카드 1회 강제. true면 조용히 유지.
     */
    val pressureCalibrated: Boolean = false
) {

    /** 좌표가 이 주차장 반경(150m) 안인지 판정 */
    fun contains(lat: Double, lon: Double): Boolean {
        if (latitude == null || longitude == null) return false
        return distanceMeters(latitude, longitude, lat, lon) <= MATCH_RADIUS_M
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        if (latitude != null) put("lat", latitude)
        if (longitude != null) put("lon", longitude)
        put("floors", JSONArray(floors))
        put("memos", JSONObject(memos as Map<*, *>))
        if (lastFloor != null) put("lastFloor", lastFloor)
        if (sheetMode != null) put("sheetMode", sheetMode)
        if (pressureOffsetFloors != 0) put("pressureOffset", pressureOffsetFloors)
        if (pressureCalibrated) put("pCal", true)
    }

    companion object {
        /** PRD 4절: 반경 150m 안이면 같은 주차장으로 판정 */
        const val MATCH_RADIUS_M = 150.0

        /** v3 기본 층 구성: 지상 1~3층 + 지하 1~3층 */
        val DEFAULT_FLOORS = listOf("3F", "2F", "1F", "B1", "B2", "B3")

        fun isBasement(floor: String): Boolean = floor.startsWith("B")

        /**
         * 층 정렬값: 위(지상 높은 층)가 음수 → 아래(지하 깊은 층)가 양수.
         * "3F" → -3, "1F" → -1, "B1" → 1, "B4" → 4. 세로 스택은 이 순서대로 위→아래.
         */
        fun floorOrder(floor: String): Int {
            val number = floor.filter { it.isDigit() }.toIntOrNull() ?: 0
            return if (isBasement(floor)) number else -number
        }

        fun sortFloors(floors: List<String>): List<String> =
            floors.distinct().sortedBy { floorOrder(it) }

        /**
         * 기압 추정 좌표계 인덱스: "지상 진입 기준 아래로 몇 층인가".
         * B1→1, B2→2 … / 1F→0, 2F→-1 … (0층이 없어 floorOrder와는 지상에서 1 어긋남)
         */
        fun pressureIndex(floor: String): Int {
            val number = floor.filter { it.isDigit() }.toIntOrNull() ?: 0
            return if (isBasement(floor)) number else 1 - number
        }

        fun fromJson(json: JSONObject): ParkingLotProfile {
            val floors = mutableListOf<String>()
            val floorsJson = json.optJSONArray("floors") ?: JSONArray()
            for (i in 0 until floorsJson.length()) floors.add(floorsJson.getString(i))

            val memos = mutableMapOf<String, String>()
            val memosJson = json.optJSONObject("memos") ?: JSONObject()
            memosJson.keys().forEach { key -> memos[key] = memosJson.getString(key) }

            return ParkingLotProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                latitude = if (json.has("lat")) json.getDouble("lat") else null,
                longitude = if (json.has("lon")) json.getDouble("lon") else null,
                floors = if (floors.isEmpty()) DEFAULT_FLOORS else sortFloors(floors),
                memos = memos,
                lastFloor = json.optString("lastFloor").ifEmpty { null },
                sheetMode = json.optString("sheetMode").ifEmpty { null },
                pressureOffsetFloors = json.optInt("pressureOffset", 0),
                pressureCalibrated = json.optBoolean("pCal", false)
            )
        }

        /** 하버사인 거리(m) — 150m 판정에는 이 정밀도로 충분 */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadiusM = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return earthRadiusM * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
