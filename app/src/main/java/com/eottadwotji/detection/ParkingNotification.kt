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
 * 상태바 아이콘 색: Android는 모든 앱의 상태바 아이콘을 강제로 단색(흰/검) 처리한다.
 * 형광그린은 OS 제약상 상태바에선 불가능 — 대신 알림창에서 colorized로 형광 배경.
 */
object ParkingNotification {

    const val SERVICE_NOTIFICATION_ID = 1
    const val POPUP_NOTIFICATION_ID = 2

    private const val CHANNEL_IDLE = "idle_v2"     // IMPORTANCE_MIN: 상태바에 안 보임
    private const val CHANNEL_POPUP = "popup_v2"   // IMPORTANCE_HIGH: 헤드업 팝업
    private const val CHANNEL_PARKED = "parked_v2" // IMPORTANCE_LOW: 조용히 상시 표시

    private const val FLOOR_PICKER_TIMEOUT_MS = 10_000L

    private const val NEON = 0xFFAEEA00.toInt()

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

    /** 서비스 시작 시 현재 상태에 맞는 상시 알림 — 재시작/재부팅 후에도 표시 일관성 유지 */
    fun buildServiceNotification(context: Context): Notification {
        val store = com.eottadwotji.data.ParkingStore(context)
        return if (store.hasActiveParking() && store.displayMode != com.eottadwotji.data.ParkingStore.DISPLAY_WIDGET) {
            buildParkedNotification(context, store.currentFloor(), store.parkingStartedAt())
        } else {
            buildIdleNotification(context)
        }
    }

    /**
     * 감지 진행용 임시 알림 — 하차 판정·층 선택 대기, 주행 중 기압 샘플링 동안만.
     * 대기 상태에서는 서비스 자체가 없으므로 이 알림도 없다 (v3.6).
     */
    fun buildIdleNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_IDLE)
            .setSmallIcon(IconCompat.createWithBitmap(renderTextIcon("P")))
            .setContentTitle("내차위치 감지 중")
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
        highlightFloor: String?        // 지난번 층 → 첫 번째 버튼으로
    ) {
        // 지난번 층을 맨 앞으로 정렬 (엄지가 가장 먼저 닿는 버튼)
        val ordered = if (highlightFloor != null)
            listOf(highlightFloor) + floors.filter { it != highlightFloor }
        else floors

        val builder = NotificationCompat.Builder(context, CHANNEL_POPUP)
            .setSmallIcon(IconCompat.createWithBitmap(renderTextIcon("P?")))
            .setContentTitle("주차하셨나요?")
            .setContentText("몇 층에 주차했는지 탭하세요")
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
     * 주차 확정: 상시 알림(서비스 알림)을 "P·B3" 캡슐로 교체.
     * 알림이 항상 1개뿐이라 상태바 캡슐이 그룹 아이콘에 가려질 일이 없다.
     * 알림창에서는 colorized로 형광그린 배경 (상태바 아이콘 자체는 OS가 단색 강제).
     */
    fun showParkedNotification(context: Context, floor: String?, startedAtMs: Long = 0L) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // 하차 헤드업이 남아 있으면 정리 — 상시 알림 1개 원칙
        nm.cancel(POPUP_NOTIFICATION_ID)

        // v3 알림 설정: "홈 위젯만"이면 상시 캡슐을 띄우지 않는다 (위젯이 담당).
        // 서비스가 떠 있으면 층 선택 타임아웃 시점에 스스로 정리한다.
        val store = com.eottadwotji.data.ParkingStore(context)
        if (store.displayMode == com.eottadwotji.data.ParkingStore.DISPLAY_WIDGET) {
            nm.cancel(SERVICE_NOTIFICATION_ID)
            return
        }
        nm.notify(SERVICE_NOTIFICATION_ID, buildParkedNotification(context, floor, startedAtMs))
    }

    private fun buildParkedNotification(
        context: Context,
        floor: String?,
        startedAtMs: Long
    ): Notification {
        val statusText = if (floor != null) "P·$floor" else "P"
        val icon = IconCompat.createWithBitmap(renderTextIcon(floor ?: "P"))

        val store = com.eottadwotji.data.ParkingStore(context)
        val detailLine = listOfNotNull(
            floor,
            store.currentZone(),
            store.currentMemo(),
            store.currentLot()?.name
        ).joinToString(" · ").ifEmpty { "위치만 저장됨" }

        return NotificationCompat.Builder(context, CHANNEL_PARKED)
            .setSmallIcon(icon)
            .setContentTitle(if (floor != null) "$floor 에 주차됨" else "주차 위치 저장됨")
            .setContentText(detailLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$detailLine\n탭하면 상세 보기 · 출발하면 자동으로 사라져요")
            )
            .setColor(NEON)
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
        nm.cancel(SERVICE_NOTIFICATION_ID) // 서비스가 떠 있으면 stopForeground가 마저 제거
        nm.cancel(POPUP_NOTIFICATION_ID)
    }

    /**
     * 형광 필 스타일 상태바 아이콘: 꽉 찬 캡슐에서 글자를 뚫어낸(cutout) 비트맵.
     * 상태바는 시스템이 단색 처리하지만 필 실루엣이라 훨씬 눈에 띄고,
     * 알림창/잠금화면에서는 colorized 형광그린 배경 위에 얹힌다.
     */
    private fun renderTextIcon(text: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pillTop = 22f
        val pillBottom = size - 22f
        val radius = (pillBottom - pillTop) / 2f // 반지름 = 높이 절반 → 완전한 캡슐
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(0f, pillTop, size.toFloat(), pillBottom, radius, radius, pill)

        // 글자를 투명하게 뚫기 — 필 위에 글씨가 도드라져 보이는 효과
        val punch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            // 글자 수에 따라 크기 조정 (B3=2자 크게, B12=3자 작게)
            textSize = if (text.length <= 2) 46f else 36f
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val y = size / 2f - (punch.descent() + punch.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, punch)
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
