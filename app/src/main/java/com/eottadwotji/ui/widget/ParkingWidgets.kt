package com.eottadwotji.ui.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eottadwotji.MainActivity
import com.eottadwotji.R
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 홈 위젯 2종 (v4.2 — 층수 + 경과 시간):
 * - 2x1 필: 층수(네온, 왼쪽) + 경과 시간을 크게(오른쪽) + 아래 작게 "주차 13:43 · 장소"
 * - 1x1 게이지: 층수(네온) + 경과 시간 작게
 * 주차 없을 땐 "탭해서 기록" — 수동 기록 진입점.
 *
 * 갱신은 주차 상태가 바뀌는 순간에만 push (updatePeriodMillis=0 — 배터리 규칙 7).
 * 경과 시간은 RemoteViews Chronometer — 런처가 직접 틱을 그리므로 앱이 깨어나지 않는다.
 * 표기는 시스템 규칙(1시간 미만 "MM:SS", 이상 "H:MM:SS")이라 옆에 주차 시각을 같이 둔다.
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
        val startedAt = store.parkingStartedAt()
        val lotName = store.currentLot()?.name

        provideContent {
            PillContent(parked, floor, startedAt, lotName)
        }
    }
}

@Composable
private fun PillContent(parked: Boolean, floor: String?, startedAtMs: Long, lotName: String?) {
    // 카드 높이를 내용에 맞춰 고정 — 셀이 커도 위아래 여백 없이 슬림하게 (v3.8)
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(62.dp)
                .background(BgCard)
                .cornerRadius(22.dp)
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
            // 오른쪽: 경과 시간 크게(위, 살아서 흐름) + 주차 시각·장소 작게(아래)
            Column {
                ElapsedChronometer(
                    startedAtMs = startedAtMs,
                    sizeSp = 26f,
                    color = TextMain,
                    modifier = GlanceModifier.fillMaxWidth().height(30.dp)
                )
                Text(
                    listOfNotNull("주차 ${formatTime(startedAtMs)}", lotName?.take(8))
                        .joinToString(" · "),
                    style = TextStyle(
                        color = ColorProvider(TextDim),
                        fontSize = 11.sp
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
}

/** 1x1 게이지 미니 위젯 — 실사 세단 미니 + 층수 + 시각 */
class GaugeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = ParkingStore(context)
        val parked = store.hasActiveParking()
        val floor = store.currentFloor()
        val startedAt = store.parkingStartedAt()

        provideContent {
            GaugeContent(parked, floor, startedAt)
        }
    }
}

@Composable
private fun GaugeContent(parked: Boolean, floor: String?, startedAtMs: Long) {
    // 카드 높이를 내용에 맞춰 고정 — 위아래 여백 최소화 (v3.8)
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(78.dp)
                .background(BgCard)
                .cornerRadius(22.dp)
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
            if (parked) {
                ElapsedChronometer(
                    startedAtMs = startedAtMs,
                    sizeSp = 12f,
                    color = TextBody,
                    modifier = GlanceModifier.height(16.dp)
                )
            } else {
                Text(
                    "기록",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 11.sp)
                )
            }
        }
        }
    }
}

/**
 * 경과 시간 — RemoteViews Chronometer (v4.2).
 * base를 "주차 시각을 elapsedRealtime 축으로 옮긴 값"으로 두면 재부팅 뒤 갱신에도 정확하다.
 * 런처 프로세스가 1초마다 그리며 앱은 깨어나지 않는다 (배터리 규칙 7).
 */
@Composable
private fun ElapsedChronometer(
    startedAtMs: Long,
    sizeSp: Float,
    color: Color,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_elapsed).apply {
        val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAtMs)
        setChronometer(R.id.widget_elapsed, base, "%s", true)
        setTextViewTextSize(R.id.widget_elapsed, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(R.id.widget_elapsed, color.toArgb())
    }
    AndroidRemoteViews(remoteViews = views, modifier = modifier)
}

/** 주차 시각 "12:02" — 경과 시간 옆 보조 표기 (1시간 미만 MM:SS 표기의 오독 방지) */
private fun formatTime(startedAtMs: Long): String =
    java.text.SimpleDateFormat("H:mm", java.util.Locale.KOREAN)
        .format(java.util.Date(startedAtMs))
