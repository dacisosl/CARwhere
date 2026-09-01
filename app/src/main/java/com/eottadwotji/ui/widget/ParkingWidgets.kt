package com.eottadwotji.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eottadwotji.MainActivity
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 홈 위젯 2종 (DESIGN v2):
 * - 2x1 필: 형광 배경 "🚗 B3 · 스타필드" — 상태바가 단색 처리되는 한계를 위젯의 형광 필로 보완
 * - 1x1 게이지: 층수만 크게
 * 주차 없을 땐 회색 "기록하기" — 수동 기록 진입점.
 *
 * 갱신은 주차 상태가 바뀌는 순간에만 push (updatePeriodMillis=0 — 배터리 규칙 7).
 */

private val Neon = Color(0xFFAEEA00)
private val NeonDeep = Color(0xFF1F3D00)
private val BgCard = Color(0xFF2C2C2A)
private val TextBody = Color(0xFFD3D1C7)
private val TextDim = Color(0xFF888780)

/** 주차 상태 변경 시 모든 위젯 갱신 */
object WidgetUpdater {
    fun update(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { PillWidget().updateAll(appContext) }
            runCatching { GaugeWidget().updateAll(appContext) }
        }
    }
}

class PillWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PillWidget()
}

class GaugeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GaugeWidget()
}

/** 2x1 형광 필 위젯 — 층수 + 주차 시각 (v3) */
class PillWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = ParkingStore(context)
        val parked = store.hasActiveParking()
        val floor = store.currentFloor()
        val time = formatTime(store.parkingStartedAt())

        provideContent {
            PillContent(parked, floor, time)
        }
    }
}

@Composable
private fun PillContent(parked: Boolean, floor: String?, time: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(if (parked) Neon else BgCard)
            .cornerRadius(24.dp)
            .clickable(
                if (parked) actionStartActivity<MainActivity>()
                else actionStartActivity<FloorPickerActivity>()
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (parked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🚗 ",
                    style = TextStyle(fontSize = 22.sp) // v3: 차 아이콘 크게
                )
                Text(
                    "${floor ?: "P"} · $time",
                    style = TextStyle(
                        color = ColorProvider(NeonDeep),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        } else {
            Text(
                "기록하기",
                style = TextStyle(
                    color = ColorProvider(TextDim),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

/** 1x1 게이지 미니 위젯 — 층수 + 주차 시각 (v3) */
class GaugeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = ParkingStore(context)
        val parked = store.hasActiveParking()
        val floor = store.currentFloor()
        val time = formatTime(store.parkingStartedAt())

        provideContent {
            GaugeContent(parked, floor, time)
        }
    }
}

@Composable
private fun GaugeContent(parked: Boolean, floor: String?, time: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgCard)
            .cornerRadius(28.dp)
            .clickable(
                if (parked) actionStartActivity<MainActivity>()
                else actionStartActivity<FloorPickerActivity>()
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (parked) (floor ?: "P") else "—",
                style = TextStyle(
                    color = ColorProvider(if (parked) Neon else TextDim),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                if (parked) time else "기록",
                style = TextStyle(
                    color = ColorProvider(if (parked) TextBody else TextDim),
                    fontSize = 9.sp
                )
            )
        }
    }
}

/** 주차 시각 "12:02" — 경과 시간은 갱신이 필요해 위젯에선 시각을 쓴다 (배터리 규칙 7) */
private fun formatTime(startedAtMs: Long): String =
    java.text.SimpleDateFormat("H:mm", java.util.Locale.KOREAN)
        .format(java.util.Date(startedAtMs))
