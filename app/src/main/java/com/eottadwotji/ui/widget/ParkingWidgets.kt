package com.eottadwotji.ui.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
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
 * 홈 위젯 2종 — 층수 + 경과 시간 (v4.3에서 잘림 수정).
 *
 * - 2x1 필: 경과 시간이 주인공 (26sp, 카드 폭 전체를 쓰는 한 줄) + 아래 "B3 · 주차 16:54 · 집"
 * - 1x1 게이지: 층수(네온 26sp)가 주인공 + 아래 경과 시간 12sp
 *
 * 왜 세로로 쌓았나 (v4.3): 층수와 시간을 한 줄에 나란히 두면 2x1 셀의 좁은 폭
 * (작은 화면 4열 런처에서 카드 안쪽 ~128dp)에 "40:52:39" 8글자가 못 들어가
 * 말줄임(40:52:…)이 생겼다. 시간에 폭을 전부 주면 26sp로 키워도 96dp면 되므로
 * 어떤 런처에서도 잘리지 않는다.
 *
 * 갱신은 주차 상태가 바뀌는 순간에만 push (updatePeriodMillis=0 — 배터리 규칙 7).
 * 초 단위 흐름은 RemoteViews Chronometer가 런처 프로세스에서 그리므로 앱은 깨어나지 않는다.
 */

// v5 팔레트 — 화이트 카드 위 잉크 글자, 층수는 딥 파인 그린, 경과 시간 숫자는 머스크 버건디
private val Neon = Color(0xFF2F6B4F)      // 시그니처 (층수)
private val Accent = Color(0xFF9E4A5C)    // 포인트 (숫자)
private val BgCard = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF17191D)
private val TextBody = Color(0xFF2F333A)
private val TextDim = Color(0xFF6B7380)

/**
 * Chronometer로 표시할 수 있는 상한 100시간.
 * 넘어가면 "HHH:MM:SS" 9~10글자가 되어 다시 폭을 넘기므로 일·시간 요약으로 바꾼다.
 */
private const val ELAPSED_CHRONO_LIMIT_MS = 100L * 60L * 60L * 1000L

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

/** 2x1 필 위젯 — 경과 시간 크게 + 층·주차시각·장소 한 줄 */
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
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(66.dp)
                .background(BgCard)
                .cornerRadius(22.dp)
                .clickable(
                    if (parked) actionStartActivity<MainActivity>()
                    else actionStartActivity<FloorPickerActivity>()
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (parked) {
                // 경과 시간 — 카드 폭 전체를 쓰므로 잘리지 않는다
                ElapsedTime(
                    startedAtMs = startedAtMs,
                    sizeSp = 26f,
                    color = Accent,
                    layoutRes = R.layout.widget_elapsed_pill,
                    modifier = GlanceModifier.fillMaxWidth().height(34.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        floor ?: "P",
                        style = TextStyle(
                            color = ColorProvider(Neon),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(7.dp))
                    Text(
                        listOfNotNull("주차 ${formatTime(startedAtMs)}", lotName?.take(8))
                            .joinToString(" · "),
                        style = TextStyle(color = ColorProvider(TextDim), fontSize = 11.sp),
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    "탭해서 기록",
                    style = TextStyle(
                        color = ColorProvider(TextMain),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    "주차하면 층수와 시간이 떠요",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/** 1x1 게이지 미니 위젯 — 층수 크게 + 경과 시간 작게 */
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
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(78.dp)
                .background(BgCard)
                .cornerRadius(22.dp)
                .clickable(
                    if (parked) actionStartActivity<MainActivity>()
                    else actionStartActivity<FloorPickerActivity>()
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (parked) (floor ?: "P") else "—",
                style = TextStyle(
                    color = ColorProvider(if (parked) Neon else TextDim),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (parked) {
                ElapsedTime(
                    startedAtMs = startedAtMs,
                    sizeSp = 12f,
                    color = Accent,
                    layoutRes = R.layout.widget_elapsed_mini,
                    modifier = GlanceModifier.fillMaxWidth().height(17.dp)
                )
            } else {
                Text(
                    "기록",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 경과 시간 — RemoteViews Chronometer를 Glance 안에 삽입 (v4.2, 잘림 수정 v4.3).
 *
 * base를 "주차 시각을 elapsedRealtime 축으로 옮긴 값"으로 두면 재부팅 뒤 갱신에도 정확하다.
 * 런처 프로세스가 1초마다 그리며 앱은 깨어나지 않는다 (배터리 규칙 7).
 * 레이아웃은 match_parent + ellipsize=none + tnum이라 말줄임 없이 폭에 딱 맞는다.
 */
@Composable
private fun ElapsedTime(
    startedAtMs: Long,
    sizeSp: Float,
    color: Color,
    @LayoutRes layoutRes: Int,
    modifier: GlanceModifier = GlanceModifier
) {
    val elapsedMs = System.currentTimeMillis() - startedAtMs

    // 100시간 이상(또는 시각이 이상한 경우)은 Chronometer 표기가 너무 길어진다 → 일·시간 요약
    if (startedAtMs <= 0L || elapsedMs < 0L || elapsedMs >= ELAPSED_CHRONO_LIMIT_MS) {
        Text(
            formatDayElapsed(elapsedMs),
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = sizeSp.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        return
    }

    val context = LocalContext.current
    val views = RemoteViews(context.packageName, layoutRes).apply {
        setChronometer(
            R.id.widget_elapsed,
            SystemClock.elapsedRealtime() - elapsedMs,
            "%s",
            true
        )
        setTextViewTextSize(R.id.widget_elapsed, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(R.id.widget_elapsed, color.toArgb())
    }
    AndroidRemoteViews(remoteViews = views, modifier = modifier)
}

/** 100시간 넘는 장기 주차: "4일 21시간" (초 단위 표기는 폭도 의미도 맞지 않는다) */
private fun formatDayElapsed(elapsedMs: Long): String {
    if (elapsedMs <= 0L) return "0:00"
    val hours = elapsedMs / 3_600_000L
    val days = hours / 24L
    return if (days >= 1L) "${days}일 ${hours % 24L}시간" else "${hours}시간"
}

/** 주차 시각 "16:54" — 경과 시간 옆 보조 표기 (1시간 미만 MM:SS 표기의 오독 방지) */
private fun formatTime(startedAtMs: Long): String =
    java.text.SimpleDateFormat("H:mm", java.util.Locale.KOREAN)
        .format(java.util.Date(startedAtMs))
