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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
 * 홈 위젯 2종 (v3.6 리디자인 — 앱 아이덴티티와 통일):
 * - 2x1 필: 다크 카드 + 실사 세단 에셋 + 층수(네온) + 시각·장소
 * - 1x1 게이지: 실사 세단 미니 + 층수(네온) + 시각
 * 주차 없을 땐 "탭해서 기록" — 수동 기록 진입점.
 *
 * 갱신은 주차 상태가 바뀌는 순간에만 push (updatePeriodMillis=0 — 배터리 규칙 7).
 */

private val Neon = Color(0xFFAEEA00)
private val BgCard = Color(0xFF1E1E1C)   // 앱 대시보드와 동일한 카드 톤
private val TextMain = Color(0xFFF1EFE9)
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

/** 2x1 필 위젯 — 실사 세단 + 층수 + 시각·장소 */
class PillWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = ParkingStore(context)
        val parked = store.hasActiveParking()
        val floor = store.currentFloor()
        val time = formatTime(store.parkingStartedAt())
        val lotName = store.currentLot()?.name

        provideContent {
            PillContent(parked, floor, time, lotName)
        }
    }
}

@Composable
private fun PillContent(parked: Boolean, floor: String?, time: String, lotName: String?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgCard)
            .cornerRadius(24.dp)
            .clickable(
                if (parked) actionStartActivity<MainActivity>()
                else actionStartActivity<FloorPickerActivity>()
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (parked) {
            // 층수 크게 (네온) — 왼쪽
            Text(
                floor ?: "P",
                style = TextStyle(
                    color = ColorProvider(Neon),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(14.dp))
            // 오른쪽: 위치(위) + 시간(아래)
            Column {
                Text(
                    (lotName ?: "주차 중").take(8),
                    style = TextStyle(
                        color = ColorProvider(TextMain),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    time,
                    style = TextStyle(
                        color = ColorProvider(TextBody),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        } else {
            Column {
                Text(
                    "탭해서 기록",
                    style = TextStyle(
                        color = ColorProvider(TextMain),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    "주차하면 층수가 떠요",
                    style = TextStyle(
                        color = ColorProvider(TextDim),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

/** 1x1 게이지 미니 위젯 — 실사 세단 미니 + 층수 + 시각 */
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
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                if (parked) time else "기록",
                style = TextStyle(
                    color = ColorProvider(if (parked) TextBody else TextDim),
                    fontSize = 11.sp
                )
            )
        }
    }
}

/** 주차 시각 "12:02" — 경과 시간은 갱신이 필요해 위젯에선 시각을 쓴다 (배터리 규칙 7) */
private fun formatTime(startedAtMs: Long): String =
    java.text.SimpleDateFormat("H:mm", java.util.Locale.KOREAN)
        .format(java.util.Date(startedAtMs))
