package com.eottadwotji.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
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
import java.util.Locale

/**
 * 홈 위젯 2종 — 층수 + 경과 시간(시간:분) (v5.2).
 *
 * - 2x1: [B3] | [3:14 / 집 · B구역] — 층수·경과시간·주차 위치 세 정보를 항상 보여준다 (v5.6).
 *   층수는 왼쪽 열, 오른쪽 열에 시간(위)과 위치(아래). 넓으면 위치 줄에 주차 시각까지 붙는다.
 * - 1x1: 층수 위, 경과 시간 아래.
 *
 * v5.2 — 글씨를 더 두껍게: 본문을 Glance Text에서 RemoteViews(TextView)로 바꿨다.
 * Glance는 Bold가 최대 굵기지만 TextView는 sans-serif-black을 쓸 수 있어 가독성이 확실히
 * 올라간다. 배경·모서리·클릭은 Glance가 맡고, 글자만 네이티브 레이아웃이 그린다.
 *
 * 경과 시간 표기: H:MM. 분이 바뀔 때 다시 그려야 하므로 주차 중에만 1분 간격의 부정확
 * 반복 알람(setInexactRepeating)으로 갱신하고, 출차하면 알람을 끈다.
 */

// v5 팔레트 — 화이트 카드 위 잉크 글자, 층수는 딥 파인 그린, 경과 시간은 머스크 버건디
private val BgCard = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF17191D)
private val TextDim = Color(0xFF6B7380)

/** 주차 상태 변경·분 경과 시 모든 위젯 갱신 + 분 틱 알람 관리 */
object WidgetUpdater {
    fun update(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { PillWidget().updateAll(appContext) }
            runCatching { GaugeWidget().updateAll(appContext) }
        }
        // 주차 중이면 분 단위 갱신 알람 유지, 아니면 해제
        if (ParkingStore(appContext).hasActiveParking()) WidgetTicker.schedule(appContext)
        else WidgetTicker.cancel(appContext)
    }
}

/**
 * 분 틱 — 주차 중에만 살아 있는 부정확 반복 알람.
 * 정확 알람(setExact*)이 아니므로 SCHEDULE_EXACT_ALARM 권한이 필요 없고,
 * 시스템이 다른 알람과 묶어 배터리 영향을 최소화한다.
 */
object WidgetTicker {
    private const val INTERVAL_MS = 60_000L
    private const val REQUEST_CODE = 7001

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent(context)
            )
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** 분 틱 수신 → 위젯만 다시 그린다 (주차가 끝났으면 update가 알람을 해제한다) */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.update(context)
    }
}

class PillWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PillWidget()
}

class GaugeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GaugeWidget()
}

/** 2x1 필 위젯 — [층수] | [경과시간 / 주차 위치] (v5.6) */
class PillWidget : GlanceAppWidget() {

    /** 폭에 따라 위치 줄의 정보량만 바꾼다: 좁으면 위치·구역, 넓으면 주차 시각까지 (v5.6) */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 40.dp), DpSize(200.dp, 40.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = ParkingStore(context)
        val parked = store.hasActiveParking()
        val floor = store.currentFloor()
        val startedAt = store.parkingStartedAt()
        // 위치 한 줄: 등록된 주차장 이름 > 대략 주소, 있으면 구역까지 (v5.6)
        val place = store.currentLot()?.name ?: store.approximateAddress()
        val zone = store.currentZone()

        provideContent {
            PillContent(parked, floor, startedAt, place, zone)
        }
    }
}

@Composable
private fun PillContent(
    parked: Boolean,
    floor: String?,
    startedAtMs: Long,
    place: String?,
    zone: String?
) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= 200.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgCard)
            .cornerRadius(22.dp)
            .clickable(
                if (parked) actionStartActivity<MainActivity>()
                else actionStartActivity<FloorPickerActivity>()
            ),
        contentAlignment = Alignment.Center
    ) {
        if (parked) {
            val views = RemoteViews(context.packageName, R.layout.widget_pill).apply {
                setTextViewText(R.id.w_floor, floor ?: "P")
                setTextViewText(R.id.w_elapsed, formatElapsedHm(startedAtMs))
                // 위치는 좁은 셀에서도 항상 — 길면 말줄임(층수·시간은 절대 안 잘림)
                setTextViewText(R.id.w_sub, placeLine(place, zone, startedAtMs, wide))
            }
            AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.fillMaxSize())
        } else {
            Column(modifier = GlanceModifier.padding(horizontal = 16.dp)) {
                Text(
                    "탭해서 기록",
                    style = TextStyle(
                        color = ColorProvider(TextMain),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
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

/** 1x1 게이지 미니 위젯 — 층수 크게 + 경과 시간(H:MM) 작게 */
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
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgCard)
            .cornerRadius(22.dp)
            .clickable(
                if (parked) actionStartActivity<MainActivity>()
                else actionStartActivity<FloorPickerActivity>()
            ),
        contentAlignment = Alignment.Center
    ) {
        if (parked) {
            val views = RemoteViews(context.packageName, R.layout.widget_gauge).apply {
                setTextViewText(R.id.w_floor, floor ?: "P")
                setTextViewText(R.id.w_elapsed, formatElapsedHm(startedAtMs))
            }
            AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "—",
                    style = TextStyle(
                        color = ColorProvider(TextDim),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    "기록",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 12.sp)
                )
            }
        }
    }
}

/**
 * 2x1 위치 줄 — "집 · B구역". 위치를 모르면 시각이라도 남긴다.
 * 넓은 셀(200dp+)에서는 주차 시각을 뒤에 덧붙인다.
 */
private fun placeLine(place: String?, zone: String?, startedAtMs: Long, wide: Boolean): String {
    val parts = listOfNotNull(
        place?.takeIf { it.isNotBlank() },
        zone?.takeIf { it.isNotBlank() }
    ).toMutableList()
    if (parts.isEmpty()) parts += "위치 저장됨"
    if (wide && startedAtMs > 0L) parts += "주차 ${formatTime(startedAtMs)}"
    return parts.joinToString(" · ")
}

/** 경과 시간 "시간:분" — 3:14 / 40:52. 24시간을 넘어도 시간이 그대로 커진다 */
private fun formatElapsedHm(startedAtMs: Long): String {
    if (startedAtMs <= 0L) return "0:00"
    val minutes = ((System.currentTimeMillis() - startedAtMs) / 60_000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", minutes / 60L, minutes % 60L)
}

/** 주차 시각 "16:54" */
private fun formatTime(startedAtMs: Long): String =
    java.text.SimpleDateFormat("H:mm", Locale.KOREAN).format(java.util.Date(startedAtMs))
