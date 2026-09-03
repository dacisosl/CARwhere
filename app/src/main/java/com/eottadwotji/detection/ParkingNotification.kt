package com.eottadwotji.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.eottadwotji.MainActivity
import com.eottadwotji.ui.floorpicker.FloorPickerActivity

/**
 * 알림 정책 (v3.6 안정화):
 *
 * 상시 알림은 딱 1개 — SERVICE_NOTIFICATION_ID 하나를 상태에 따라 교체한다.
 *   - 대기: "감지 대기" 채널 (IMPORTANCE_MIN → 상태바 아이콘 없음, 알림창 최소화 영역)
 *   - 주차: "주차 위치 표시" 채널 (P·B3 캡슐 아이콘 + colorized 형광그린 배경)
 * 이전처럼 알림 2개가 공존하면 시스템이 자동 그룹핑해서 상태바 캡슐이
 * 그룹 아이콘 뒤로 숨는다 — "어떨 때는 뜨고 어떨 때는 안 뜨는" 원인.
 *
 * 하차 감지 헤드업(POPUP_NOTIFICATION_ID)만 별도 ID — 10초 뒤 자동 소멸.
 *
 * 채널은 v2로 재생성: 예전 설치에서 굳어진 채널 중요도/배지 설정을 리셋하고
 * setShowBadge(false)로 런처 앱 아이콘 배지(점)를 막는다.
 *
 * 상태바 아이콘 색 (v4.3): 순정 안드로이드는 알파 실루엣만 취해 강제 단색으로 그리고,
 * 삼성 One UI 등 일부 기기는 컬러 비트맵을 원본 색으로 보존한다. 그래서 아이콘은
 * "색 표지판 + 글자를 뚫어낸(cutout)" 구조로 만든다 — 색이 보존되면 층별 컬러 표지판,
 * 강제 단색이면 흰 표지판 위 글자로 어느 쪽에서도 읽힌다.
 * 층마다 색을 달리해(floorColor) B1과 B2가 한눈에 구분된다.
 */
object ParkingNotification {

    const val SERVICE_NOTIFICATION_ID = 1
    const val PARKED_NOTIFICATION_ID = 2
    const val POPUP_NOTIFICATION_ID = 3

    private const val CHANNEL_IDLE = "idle_v2"     // IMPORTANCE_MIN: 상태바에 안 보임
    private const val CHANNEL_POPUP = "popup_v2"   // IMPORTANCE_HIGH: 헤드업 팝업
    private const val CHANNEL_PARKED = "parked_v2" // IMPORTANCE_LOW: 조용히 상시 표시

    private const val FLOOR_PICKER_TIMEOUT_MS = 10_000L

    /** 층을 모를 때(P)의 표지판 색 — 시그니처 딥 파인 그린 (v5.2) */
    private val NEON = com.eottadwotji.ui.theme.FloorTone.UNKNOWN_ARGB

    /**
     * 층 이름 → 표지판 색. v5.2부터 공용 FloorTone에 위임한다 —
     * 상태바 아이콘·알림 배경·홈 층수 타일·기록 카드 층 박스가 모두 같은 색을 쓴다.
     */
    internal fun floorColor(floor: String?): Int =
        com.eottadwotji.ui.theme.FloorTone.argb(floor)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // 예전 채널 삭제 — 사용자가 바꿨거나 구버전에서 잘못 만들어진 중요도를 리셋
        listOf("idle", "popup", "parked").forEach { nm.deleteNotificationChannel(it) }

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_IDLE, "감지 대기", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POPUP, "주차 팝업", NotificationManager.IMPORTANCE_HIGH)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PARKED, "주차 위치 표시", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
    }

    /**
     * 현재 상태에 맞게 주차 캡슐을 게시/제거 — 앱 실행·부팅·설정 변경 시 호출.
     * 서비스가 필요 없다 (일반 알림이라 어디서든 게시 가능).
     */
    fun syncParkedNotification(context: Context) {
        val store = com.eottadwotji.data.ParkingStore(context)
        if (store.hasActiveParking()) {
            showParkedNotification(context, store.currentFloor(), store.parkingStartedAt())
        } else {
            dismissParkedNotification(context)
        }
    }

    /**
     * 감지 진행용 임시 알림 — 하차 판정·층 선택 대기, 주행 중 기압 샘플링 동안만.
     * 대기 상태에서는 서비스 자체가 없으므로 이 알림도 없다 (v3.6).
     */
    fun buildIdleNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_IDLE)
            .setSmallIcon(IconCompat.createWithBitmap(renderTextIcon("P")))
            .setContentTitle("어따뒀지 감지 중")
            .setContentIntent(mainActivityIntent(context))
            .setOngoing(true)
            .setShowWhen(false)
            .setNumber(0)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    /** 하차 감지 팝업: 층수 액션 버튼 (지난번 층 + 1개 + 전체 보기) */
    fun showFloorPickerNotification(
        context: Context,
        floors: List<String>,          // 예: ["B1", "B2", "B3", "B4"]
        highlightFloor: String?,       // 지난번 층 → 첫 번째 버튼으로
        isEstimate: Boolean = false    // v3.7: 기압 추정 층이면 확인 문구 표시
    ) {
        // 지난번 층을 맨 앞으로 정렬 (엄지가 가장 먼저 닿는 버튼)
        val ordered = if (highlightFloor != null)
            listOf(highlightFloor) + floors.filter { it != highlightFloor }
        else floors

        val bodyText = if (isEstimate && highlightFloor != null)
            "기압 추정 $highlightFloor — 높이에 따라 다를 수 있으니 꼭 확인하세요"
        else "몇 층에 주차했는지 탭하세요"

        val builder = NotificationCompat.Builder(context, CHANNEL_POPUP)
            .setSmallIcon(IconCompat.createWithBitmap(renderTextIcon("P?")))
            .setContentTitle("주차하셨나요?")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(floorPickerActivityIntent(context)) // 알림 본문 탭 → 풀 팝업
            .setAutoCancel(true)
            .setNumber(0)
            .setTimeoutAfter(FLOOR_PICKER_TIMEOUT_MS) // 10초 후 자동으로 사라짐

        // 알림 액션 버튼은 3개 제한 → 층 2개 + "전체 보기"
        ordered.take(2).forEach { floor ->
            val label = if (floor == highlightFloor) "$floor (지난번)" else floor
            builder.addAction(0, label, floorSelectedIntent(context, floor))
        }
        builder.addAction(0, "전체 보기", floorPickerActivityIntent(context))

        context.getSystemService(NotificationManager::class.java)
            .notify(POPUP_NOTIFICATION_ID, builder.build())
    }

    /**
     * 주차 캡슐 게시 (v3.9.5 — 서비스와 분리된 일반 상시 알림).
     *
     * 왜 서비스(FGS) 알림이 아닌가: FGS 알림은 서비스가 죽으면 시스템이 함께
     * 지운다 — 삼성 절전이 서비스를 죽이는 순간 캡슐이 사라지던 원인.
     * 일반 알림은 시스템 소유라 앱 프로세스가 죽어도 상태바에 남는다.
     * ID(2)는 항상 parked_v2 채널 고정이라 채널 갇힘 문제도 없다.
     */
    fun showParkedNotification(context: Context, floor: String?, startedAtMs: Long = 0L) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // 하차 헤드업이 남아 있으면 정리
        nm.cancel(POPUP_NOTIFICATION_ID)
        nm.notify(PARKED_NOTIFICATION_ID, buildParkedNotification(context, floor, startedAtMs))
    }

    internal fun buildParkedNotification(
        context: Context,
        floor: String?,
        startedAtMs: Long
    ): Notification {
        val statusText = if (floor != null) "P·$floor" else "P"
        val tone = floorColor(floor)
        val icon = IconCompat.createWithBitmap(renderTextIcon(floor ?: "P", tone))

        val store = com.eottadwotji.data.ParkingStore(context)
        val detailLine = listOfNotNull(
            floor,
            store.currentZone(),
            store.currentMemo(),
            store.currentLot()?.name ?: store.approximateAddress()?.let { "$it 근처" }
        ).joinToString(" · ").ifEmpty { "위치만 저장됨" }

        // 알림창은 풀컬러가 허용되는 영역 — 실사 차 에셋을 큰 아이콘으로 (v3.9.2)
        val largeIcon = runCatching {
            android.graphics.BitmapFactory.decodeResource(
                context.resources, com.eottadwotji.R.drawable.ic_fg_sedan_white
            )
        }.getOrNull()

        return NotificationCompat.Builder(context, CHANNEL_PARKED)
            .setSmallIcon(icon)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentTitle(if (floor != null) "$floor 에 주차됨" else "주차 위치 저장됨")
            .setContentText(detailLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$detailLine\n탭하면 상세 보기 · 출발하면 자동으로 사라져요")
            )
            .setColor(tone) // 알림창 colorized 배경도 층 색으로 — 상태바와 같은 신호
            .setColorized(true) // 포그라운드 서비스 알림 → 알림창 배경 자체가 형광그린
            .setContentIntent(mainActivityIntent(context))
            .setOngoing(true)          // 스와이프로 지워지지 않음
            .setShowWhen(startedAtMs > 0L)
            .setWhen(if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis())
            .setUsesChronometer(startedAtMs > 0L) // 주차 경과 시간 표시
            .setSubText(statusText)
            .setNumber(0)
            .setSilent(true)
            .build()
    }

    fun dismissParkedNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(PARKED_NOTIFICATION_ID)
        nm.cancel(POPUP_NOTIFICATION_ID)
    }

    /**
     * 상태바 아이콘 — 주차장 층 표지판 (v4.3, 사용자 첨부 디자인 반영).
     *
     * 완전한 캡슐이 아니라 모서리만 살짝 둥근 사각 표지판으로 바꾸고, 캔버스를 거의 꽉
     * 채운 뒤 글자를 실측해서 표지판 안쪽을 최대한 채운다 (이전 고정 58f 대비 크게).
     *
     * 글자는 색을 칠하는 대신 투명하게 뚫는다(cutout). 첨부 디자인은 회색 판 위 초록
     * 글자지만, 글자에 색을 칠하면 알파 실루엣만 취하는 기기에서 판이 통째로 하얀 덩어리가
     * 되어 층을 읽을 수 없다. 뚫어내면 색 보존 기기에선 컬러 표지판 + 어두운 글자,
     * 강제 단색 기기에선 흰 표지판 + 글자로 양쪽 모두 읽힌다.
     */
    private fun renderTextIcon(text: String, tint: Int = NEON): Bitmap {
        val size = 144 // 상태바에서 축소되므로 해상도를 올려 글자 획을 선명하게
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val inset = 3f
        val top = inset
        val bottom = size - inset
        val corner = (bottom - top) * 0.22f // 캡슐(0.5)보다 각진 표지판
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
        canvas.drawRoundRect(0f, top, size.toFloat(), bottom, corner, corner, plate)

        val punch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        // 글자 크기를 실측으로 맞춘다 — "B2"든 "B12"든 표지판을 같은 비율로 채운다
        val bounds = android.graphics.Rect()
        punch.textSize = 100f
        punch.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            punch.textSize = 100f * minOf(
                (bottom - top) * 0.74f / bounds.height(),
                size * 0.84f / bounds.width()
            )
            punch.getTextBounds(text, 0, text.length, bounds)
        }
        // 글자의 시각적 중심을 표지판 중심에 맞춘다 (ascent/descent 대신 실제 획 경계)
        val baseline = (top + bottom) / 2f - (bounds.top + bounds.bottom) / 2f
        canvas.drawText(text, size / 2f, baseline, punch)
        return bitmap
    }

    private fun floorSelectedIntent(context: Context, floor: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            floor.hashCode(),
            Intent(context, FloorSelectedReceiver::class.java)
                .setAction("com.eottadwotji.FLOOR_SELECTED")
                .putExtra("floor", floor),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun floorPickerActivityIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1001,
            Intent(context, FloorPickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun mainActivityIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1002,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
