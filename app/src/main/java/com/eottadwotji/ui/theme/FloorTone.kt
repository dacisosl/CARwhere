package com.eottadwotji.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 층별 색 (v5.3) — 상태바·알림 전용.
 *
 * v5.2에서 홈 타일·기록 카드까지 이 색을 썼지만 "흰 화면에서 색이 너무 튄다"는
 * 피드백으로 되돌렸다. 앱 화면의 층수는 시그니처 딥 파인 그린 하나(FloorSign)이고,
 * 이 형광 계열은 어두운 상태바에서 색만 보고 층을 알기 위한 것이라 여기 남는다.
 * 지상은 따뜻한 앰버 계열, 지하는 차가운 라임 계열이고
 * 깊어질수록 색상(hue)이 옮겨간다 — 어두운 상태바에서 어두운 색은 안 보이므로
 * 구분은 명도가 아니라 색상으로 준다.
 *
 *   지하  B1 라임 → B2 연두 → B3 초록 → B4 시안
 *   지상  1F 앰버 → 2F 오렌지 → 3F 진한 주황 → 4F 코랄
 *
 * 표지판 글자는 항상 잉크(#17191D) — 이 색들은 모두 명도가 높아 검은 글자가 읽힌다.
 */
object FloorTone {

    /** 층을 모를 때 (P) — 시그니처 딥 파인 그린 */
    const val UNKNOWN_ARGB: Int = 0xFF2F6B4F.toInt()

    /** 표지판 위 글자색 — 밝은 바탕이라 항상 잉크 */
    val OnSign: Color = Color(0xFF17191D)

    private val BASEMENT = intArrayOf(
        0xFFC6FF00.toInt(), // B1 라임
        0xFF76FF03.toInt(), // B2 연두
        0xFF00E676.toInt(), // B3 초록
        0xFF00E5FF.toInt()  // B4 시안
    )
    private val GROUND = intArrayOf(
        0xFFFFD54F.toInt(), // 1F 앰버
        0xFFFFAB40.toInt(), // 2F 오렌지
        0xFFFF7043.toInt(), // 3F 진한 주황
        0xFFFF5252.toInt()  // 4F 코랄
    )

    /** 층 이름 → ARGB. 범위를 넘는 층은 가장 깊은 색 유지 */
    fun argb(floor: String?): Int {
        if (floor.isNullOrBlank()) return UNKNOWN_ARGB
        val number = floor.filter { it.isDigit() }.toIntOrNull() ?: return UNKNOWN_ARGB
        val tones = if (floor.startsWith("B")) BASEMENT else GROUND
        return tones[(number - 1).coerceIn(0, tones.size - 1)]
    }

    fun color(floor: String?): Color = Color(argb(floor))
}
