package com.eottadwotji.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 앱 아이콘 전환 (v3.2) — activity-alias 13개 중 하나만 활성화.
 *
 * 런처 진입점은 전부 alias라 MainActivity는 항상 활성 상태 유지 →
 * 알림/위젯 PendingIntent가 아이콘 변경과 무관하게 동작한다.
 * 변경 순간 런처가 항목을 갱신하며, 일부 런처는 홈 화면 아이콘 위치를 리셋할 수 있다.
 */
object AppIconSwitcher {

    val CARS = listOf("kei", "sedan", "suv", "sports")
    val COLORS = listOf("white", "black", "gray")

    fun carLabel(car: String): String = when (car) {
        "kei" -> "경차"
        "sedan" -> "중형차"
        "suv" -> "SUV"
        else -> "스포츠카"
    }

    fun colorLabel(color: String): String = when (color) {
        "white" -> "흰색"
        "black" -> "검은색"
        else -> "회색"
    }

    private fun aliasName(context: Context, car: String?, color: String?): String =
        if (car == null || color == null) "${context.packageName}.IconDefault"
        else "${context.packageName}.Icon_${car}_${color}"

    /** car/color가 null이면 기본(형광) 아이콘으로 되돌린다 */
    fun apply(context: Context, car: String?, color: String?) {
        val pm = context.packageManager
        val selected = aliasName(context, car, color)

        val all = buildList {
            add(aliasName(context, null, null))
            CARS.forEach { c -> COLORS.forEach { col -> add(aliasName(context, c, col)) } }
        }

        // 선택된 alias를 먼저 켜고 나머지를 꺼서 런처 항목이 사라지는 순간이 없게 한다
        pm.setComponentEnabledSetting(
            ComponentName(context, selected),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        all.filter { it != selected }.forEach { alias ->
            pm.setComponentEnabledSetting(
                ComponentName(context, alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
