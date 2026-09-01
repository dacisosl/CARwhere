package com.eottadwotji.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 주차 상태 저장소 (SharedPreferences 기반).
 * 주차 히스토리 화면을 붙일 때 Room DB로 교체 예정 (CLAUDE.md 기술 스택).
 *
 * 저장 항목:
 * - 온보딩/설정: 내 차 BT, 표시 방식, 입력 세부 수준, 사진 옵션
 * - 현재 주차: 시작 시각, 층, 구역, GPS 좌표, 사진 URI, 매칭된 주차장
 * - 주차장 프로필: JSON 배열 (좌표 반경 150m 매칭)
 * - 히스토리: 만료된 주차 기록 최근 50건 (추후 "주차 기록" 화면용)
 */
class ParkingStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 온보딩 ──────────────────────────────────────────────

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** 내 차 MAC — 이어폰 오작동 방지의 핵심 필터 (CLAUDE.md 절대 규칙 4) */
    var myCarAddress: String?
        get() = prefs.getString(KEY_MY_CAR_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_MY_CAR_ADDRESS, value).apply()

    var myCarName: String?
        get() = prefs.getString(KEY_MY_CAR_NAME, null)
        set(value) = prefs.edit().putString(KEY_MY_CAR_NAME, value).apply()

    /** "차량 블루투스가 없어요" 탈출구 → 수동 기록 전용 모드 */
    var manualOnlyMode: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_ONLY, value).apply()

    // ── 설정 ────────────────────────────────────────────────

    /**
     * v3: 전역 기본 바텀시트 모드 — SHEET_FLOOR / SHEET_FLOOR_MEMO / SHEET_FLOOR_PHOTO.
     * 기본값은 층+메모 (층수 선택 후 세부구역 입력 창 자동 표시).
     * 위치(프로필)마다 sheetMode로 덮어쓸 수 있다.
     */
    var defaultSheetMode: String
        get() = prefs.getString(KEY_SHEET_MODE, SHEET_FLOOR_MEMO)!!
        set(value) = prefs.edit().putString(KEY_SHEET_MODE, value).apply()

    /** 현재 주차 위치에 적용할 바텀시트 모드 (위치별 설정 > 전역 기본값) */
    fun sheetModeForCurrentLocation(): String =
        currentLot()?.sheetMode ?: defaultSheetMode

    /** 앱 아이콘 차종/색상 (null = 기본 형광 아이콘) — AppIconSwitcher의 CARS·COLORS 값 */
    var appIconCar: String?
        get() = prefs.getString(KEY_APP_ICON_CAR, null)
        set(value) = prefs.edit().putString(KEY_APP_ICON_CAR, value).apply()

    var appIconColor: String?
        get() = prefs.getString(KEY_APP_ICON_COLOR, null)
        set(value) = prefs.edit().putString(KEY_APP_ICON_COLOR, value).apply()

    /** v3.3: 테마 모드 — THEME_SYSTEM / THEME_DARK / THEME_LIGHT */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM)!!
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /** v3.7: 층/메모 저장 후 "이렇게 등록했어요" 확인 카드를 띄울지 (끄면 바로 등록 + 완료 팝업) */
    var confirmBeforeDone: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_CARD, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_CARD, value).apply()

    /** v3.7: 대시보드 빠른 설정(미리보기)에 별표로 올려둔 설정 키들 */
    var starredSettings: Set<String>
        get() = prefs.getStringSet(KEY_STARRED_SETTINGS, DEFAULT_STARRED)!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_STARRED_SETTINGS, value).apply()

    /** v3.9.4: 대시보드에 최근 주차 카드를 띄울지 (v3.9.5: 기본 숨김 — 대신 위치 카드) */
    var showRecentCard: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RECENT_CARD, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_RECENT_CARD, value).apply()

    /** v3.9.4: 대시보드 빠른 설정 카드 펼침 상태 — 기본 열림, 닫고 나가면 그대로 유지 */
    var quickSettingsExpanded: Boolean
        get() = prefs.getBoolean(KEY_QUICK_SETTINGS_EXPANDED, true)
        set(value) = prefs.edit().putBoolean(KEY_QUICK_SETTINGS_EXPANDED, value).apply()

    /** v2: 기압 자동감지 베타 — 추정 층을 미리 선택만, 확정은 항상 사람 탭 (절대 규칙 5) */
    var pressureAutoDetect: Boolean
        get() = prefs.getBoolean(KEY_PRESSURE_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_PRESSURE_AUTO, value).apply()

    /** v2: 바텀시트가 계산해 둔 기압 추정 층 (표시용, 세션 한정) */
    var estimatedFloor: String?
        get() = prefs.getString(KEY_ESTIMATED_FLOOR, null)
        set(value) = prefs.edit().putString(KEY_ESTIMATED_FLOOR, value).apply()

    // ── 현재 주차 세션 ──────────────────────────────────────

    fun startParking(timestampMs: Long, manual: Boolean = false) {
        prefs.edit()
            .putLong(KEY_PARKING_STARTED_AT, timestampMs)
            .putBoolean(KEY_PARKING_ACTIVE, true)
            .putBoolean(KEY_PARKING_MANUAL, manual)
            .remove(KEY_PARKING_FLOOR)
            .remove(KEY_PARKING_ZONE)
            .remove(KEY_PARKING_MEMO)
            .remove(KEY_PARKING_LAT)
            .remove(KEY_PARKING_LON)
            .remove(KEY_PARKING_LOT_ID)
            .remove(KEY_PARKING_PHOTO)
            .remove(KEY_ESTIMATED_FLOOR)
            .remove(KEY_PARKING_ADDRESS)
            .apply()
    }

    fun hasActiveParking(): Boolean = prefs.getBoolean(KEY_PARKING_ACTIVE, false)

    fun parkingStartedAt(): Long = prefs.getLong(KEY_PARKING_STARTED_AT, 0L)

    fun setFloor(floor: String) {
        prefs.edit().putString(KEY_PARKING_FLOOR, floor).apply()
    }

    fun currentFloor(): String? = prefs.getString(KEY_PARKING_FLOOR, null)

    fun setZone(zone: String) {
        prefs.edit().putString(KEY_PARKING_ZONE, zone).apply()
    }

    fun currentZone(): String? = prefs.getString(KEY_PARKING_ZONE, null)

    fun setMemo(memo: String) {
        prefs.edit().putString(KEY_PARKING_MEMO, memo).apply()
    }

    fun currentMemo(): String? = prefs.getString(KEY_PARKING_MEMO, null)

    var photoUri: String?
        get() = prefs.getString(KEY_PARKING_PHOTO, null)
        set(value) = prefs.edit().putString(KEY_PARKING_PHOTO, value).apply()

    /** 주차 확정 시 1회 저장되는 좌표 — 상시 추적 금지 (CLAUDE.md 절대 규칙 6) */
    fun setCoordinates(lat: Double, lon: Double) {
        prefs.edit()
            .putLong(KEY_PARKING_LAT, lat.toRawBits())
            .putLong(KEY_PARKING_LON, lon.toRawBits())
            .apply()
        // 좌표가 생긴 순간 프로필 매칭도 확정해 둔다 (대시보드 장소 표시용)
        matchProfile(lat, lon)?.let { profile ->
            prefs.edit().putString(KEY_PARKING_LOT_ID, profile.id).apply()
        }
        // v3.9: 대략적 주소 1회 역지오코딩 (백그라운드, 실패 시 조용히 폴백)
        Thread {
            runCatching {
                if (!android.location.Geocoder.isPresent()) return@runCatching
                @Suppress("DEPRECATION")
                val addr = android.location.Geocoder(appContext, java.util.Locale.KOREAN)
                    .getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@runCatching
                val text = listOfNotNull(
                    addr.locality ?: addr.adminArea,
                    addr.subLocality ?: addr.thoroughfare
                ).joinToString(" ").trim()
                if (text.isNotBlank()) {
                    prefs.edit().putString(KEY_PARKING_ADDRESS, text).apply()
                }
            }
        }.start()
    }

    fun coordinates(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_PARKING_LAT)) return null
        return Double.fromBits(prefs.getLong(KEY_PARKING_LAT, 0L)) to
            Double.fromBits(prefs.getLong(KEY_PARKING_LON, 0L))
    }

    /** 현재 주차에 매칭된 주차장 프로필 (없으면 null) */
    fun currentLot(): ParkingLotProfile? {
        val lotId = prefs.getString(KEY_PARKING_LOT_ID, null) ?: return null
        return profiles().firstOrNull { it.id == lotId }
    }

    /** v3: 바텀시트에서 새 위치를 방금 등록했을 때 현재 세션에 직접 연결 */
    fun assignLot(profileId: String) {
        prefs.edit().putString(KEY_PARKING_LOT_ID, profileId).apply()
    }

    /** v3.9: 위치 선택에서 "위치 없이" — 현재 세션의 위치 연결 해제 */
    fun clearLot() {
        prefs.edit().remove(KEY_PARKING_LOT_ID).apply()
    }

    /** v3.9: 좌표 역지오코딩으로 얻은 대략적 주소 (예: "하남시 신장동") */
    fun approximateAddress(): String? = prefs.getString(KEY_PARKING_ADDRESS, null)

    /** 출차: 기록을 지우지 않고 Room 히스토리로 보관 (대시보드 최근 기록 카드) */
    fun expireParking() {
        if (hasActiveParking()) {
            val record = snapshotRecord()
            Thread {
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        HistoryDb.get(appContext).dao().insert(record)
                    }
                }
            }.start()
        }
        prefs.edit().putBoolean(KEY_PARKING_ACTIVE, false).apply()
    }

    /** 현재 세션의 히스토리 스냅샷 */
    private fun snapshotRecord(): ParkingRecord {
        val coords = coordinates()
        return ParkingRecord(
            startedAt = parkingStartedAt(),
            endedAt = System.currentTimeMillis(),
            floor = currentFloor(),
            zone = currentZone(),
            memo = currentMemo(),
            latitude = coords?.first,
            longitude = coords?.second,
            lotName = currentLot()?.name,
            photoUri = photoUri
        )
    }

    // ── 층 구성 / 지난번 층 (프로필 매칭) ───────────────────

    /** 현재 주차 좌표가 등록된 프로필 반경 안이면 그 프로필의 층 구성, 아니면 기본 B1~B4 */
    fun floorsForCurrentLocation(): List<String> =
        currentLot()?.floors ?: ParkingLotProfile.DEFAULT_FLOORS

    /** 같은 주차장에서의 지난번 층 (팝업 강조용). 프로필 없으면 전역 마지막 층 */
    fun lastFloorForCurrentLocation(): String? {
        val lot = currentLot()
        val candidate = lot?.lastFloor ?: prefs.getString(KEY_LAST_FLOOR_GLOBAL, null)
        // 현재 층 구성에 없는 층을 강조하면 혼란 → 목록에 있을 때만 반환
        return candidate?.takeIf { it in floorsForCurrentLocation() }
    }

    /** 층별 메모 (팝업 버튼 우측 표시용) */
    fun memoForFloor(floor: String): String? = currentLot()?.memos?.get(floor)

    fun rememberFloorForCurrentLocation(floor: String) {
        prefs.edit().putString(KEY_LAST_FLOOR_GLOBAL, floor).apply()
        currentLot()?.let { lot ->
            saveProfile(lot.copy(lastFloor = floor))
        }
    }

    /**
     * v3.7 기압 보정 학습: 추정 층과 실제 고른 층이 다르면 그 차이를
     * 이 위치의 보정값에 누적한다 (언덕 위 지형 등 체계적 오차 대응).
     * 등록된 위치가 있을 때만 — 위치마다 지형이 달라 전역 보정은 무의미.
     */
    fun recordPressureCalibration(actualFloor: String) {
        val estimated = estimatedFloor ?: return
        val lot = currentLot() ?: return
        val diff = ParkingLotProfile.pressureIndex(actualFloor) -
            ParkingLotProfile.pressureIndex(estimated)
        // 추정==실제 확인도 캘리브레이션 완료로 기록 — "첫 확인 후 그대로 유지" (v3.9)
        if (diff == 0 && lot.pressureCalibrated) return
        saveProfile(
            lot.copy(
                pressureOffsetFloors = lot.pressureOffsetFloors + diff,
                pressureCalibrated = true
            )
        )
    }

    // ── 주차장 프로필 ───────────────────────────────────────

    fun profiles(): List<ParkingLotProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { ParkingLotProfile.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveProfile(profile: ParkingLotProfile) {
        val updated = profiles().filter { it.id != profile.id } + profile
        writeProfiles(updated)
    }

    fun deleteProfile(id: String) {
        writeProfiles(profiles().filter { it.id != id })
    }

    /** 좌표 반경 150m 내 프로필 매칭 — 여러 개면 가장 가까운 것 */
    fun matchProfile(lat: Double, lon: Double): ParkingLotProfile? =
        profiles()
            .filter { it.contains(lat, lon) }
            .minByOrNull {
                ParkingLotProfile.distanceMeters(it.latitude!!, it.longitude!!, lat, lon)
            }

    private fun writeProfiles(list: List<ParkingLotProfile>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "eottadwotji"

        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_MY_CAR_ADDRESS = "my_car_bt_address"
        private const val KEY_MY_CAR_NAME = "my_car_bt_name"
        private const val KEY_MANUAL_ONLY = "manual_only_mode"

        private const val KEY_SHEET_MODE = "sheet_mode_default"

        private const val KEY_PARKING_ACTIVE = "parking_active"
        private const val KEY_PARKING_MANUAL = "parking_manual"
        private const val KEY_PARKING_STARTED_AT = "parking_started_at"
        private const val KEY_PARKING_FLOOR = "parking_floor"
        private const val KEY_PARKING_ZONE = "parking_zone"
        private const val KEY_PARKING_MEMO = "parking_memo"
        private const val KEY_PARKING_LAT = "parking_lat"
        private const val KEY_PARKING_LON = "parking_lon"
        private const val KEY_PARKING_LOT_ID = "parking_lot_id"
        private const val KEY_PARKING_PHOTO = "parking_photo_uri"
        private const val KEY_PARKING_ADDRESS = "parking_address"

        private const val KEY_LAST_FLOOR_GLOBAL = "last_floor_global"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_PRESSURE_AUTO = "pressure_auto_detect"
        private const val KEY_ESTIMATED_FLOOR = "estimated_floor"
        private const val KEY_APP_ICON_CAR = "app_icon_car"
        private const val KEY_APP_ICON_COLOR = "app_icon_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CONFIRM_CARD = "confirm_card"
        private const val KEY_STARRED_SETTINGS = "starred_settings"
        private const val KEY_SHOW_RECENT_CARD = "show_recent_card"
        private const val KEY_QUICK_SETTINGS_EXPANDED = "quick_settings_expanded"

        /** 대시보드 빠른 설정 기본 구성 */
        val DEFAULT_STARRED = setOf(STAR_PRESSURE, STAR_SHEET_MODE)

        const val SHEET_FLOOR = "floor"
        const val SHEET_FLOOR_MEMO = "floor_memo"
        const val SHEET_FLOOR_PHOTO = "floor_photo"

        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"

        /** v3.7: 별표(빠른 설정) 가능한 설정 키 */
        const val STAR_PRESSURE = "pressure"     // 자동감지 토글
        const val STAR_THEME = "theme"           // 테마 순환
        const val STAR_SHEET_MODE = "sheetmode"  // 바텀시트 기본 동작 순환
        const val STAR_CONFIRM = "confirm"       // 등록 확인 카드 토글
    }
}
