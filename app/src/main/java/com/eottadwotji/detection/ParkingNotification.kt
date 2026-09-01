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
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.eottadwotji.MainActivity
import com.eottadwotji.ui.floorpicker.FloorPickerActivity

/**
 * 알림 3종:
 * 1. Idle: 포그라운드 서비스 유지용 최소 알림 (감지 대기 중, 최저 중요도)
 * 2. FloorPicker: 하차 감지 시 층수 버튼이 달린 확장 알림 — 앱 안 열고 알림에서 바로 선택
 * 3. Parked: 층수 저장 후 상태바에 "P·B3" 텍스트 아이콘으로 상시 표시
 *
 * 상태바 텍스트 아이콘 트릭:
 * setSmallIcon은 리소스만 받지만 IconCompat.createWithBitmap으로
 * "P·B3" 같은 텍스트를 그린 비트맵을 동적 생성해서 넣을 수 있음.
 *
 * 알림 액션 3개 제한 대응 (PRD 리스크 3):
 * 지난번 층 + 다음 층 1개 + "전체 보기" → 나머지 층은 풀 팝업에서 선택.
 */
object ParkingNotification {

    const val SERVICE_NOTIFICATION_ID = 1
    const val PARKED_NOTIFICATION_ID = 2

    private const val CHANNEL_IDLE = "idle"       // IMPORTANCE_MIN: 상태바에 안 보임
    private const val CHANNEL_POPUP = "popup"     // IMPORTANCE_HIGH: 헤드업 팝업
    private const val CHANNEL_PARKED = "parked"   // IMPORTANCE_LOW: 조용히 상시 표시

    private const val FLOOR_PICKER_TIMEOUT_MS = 10_000L

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_IDLE, "감지 대기", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POPUP, "주차 팝업", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PARKED, "주차 위치 표시", NotificationManager.IMPORTANCE_LOW)
        )
    }

    /** 1. 포그라운드 서비스 유지용 (사용자 눈에 거의 안 띔) */
    fun buildIdleNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_IDLE)
            .setSmallIcon(IconCompat.createWithBitmap(renderTextIcon("P")))
            .setContentTitle("내차위치 감지 대기 중")
            .setContentIntent(mainActivityIntent(context))
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    /** 2. 하차 감지 팝업: 층수 액션 버튼 (지난번 층 + 1개 + 전체 보기) */
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
            .setTimeoutAfter(FLOOR_PICKER_TIMEOUT_MS) // 10초 후 자동으로 사라짐

        // 알림 액션 버튼은 3개 제한 → 층 2개 + "전체 보기"
        ordered.take(2).forEach { floor ->
            val label = if (floor == highlightFloor) "$floor (지난번)" else floor
            builder.addAction(0, label, floorSelectedIntent(context, floor))
        }
        builder.addAction(0, "전체 보기", floorPickerActivityIntent(context))

        context.getSystemService(NotificationManager::class.java)
            .notify(PARKED_NOTIFICATION_ID, builder.build())
    }

    /**
     * 3. 주차 확정 후 상시 표시: 상태바에 "P·B3".
     * 상태바 아이콘은 시스템이 단색 처리하므로 형광 필은 위젯/확장 뷰 액센트로 보완 (DESIGN v2).
     * 확장 뷰에 "B3 · C구역 · 기둥 27 옆"까지 표시.
     */
    fun showParkedNotification(context: Context, floor: String?, startedAtMs: Long = 0L) {
        val statusText = if (floor != null) "P·$floor" else "P"
        val icon = IconCompat.createWithBitmap(renderTextIcon(floor ?: "P"))

        // v3 알림 설정: "홈 위젯만"이면 상시 알림은 띄우지 않는다 (위젯이 담당)
        val store = com.eottadwotji.data.ParkingStore(context)
        if (store.displayMode == com.eottadwotji.data.ParkingStore.DISPLAY_WIDGET) {
            dismissParkedNotification(context)
            return
        }
        val detailLine = listOfNotNull(
            floor,
            store.currentZone(),
            store.currentMemo(),
            store.currentLot()?.name
        ).joinToString(" · ").ifEmpty { "위치만 저장됨" }

        val notification = NotificationCompat.Builder(context, CHANNEL_PARKED)
            .setSmallIcon(icon)
            .setContentTitle(if (floor != null) "$floor 에 주차됨" else "주차 위치 저장됨")
            .setContentText(detailLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$detailLine\n탭하면 상세 보기 · 출발하면 자동으로 사라져요")
            )
            .setColor(0xFF97C459.toInt()) // 형광 액센트 (아이콘/앱명 틴트)
            .setContentIntent(mainActivityIntent(context))
            .setOngoing(true)          // 스와이프로 지워지지 않음
            .setShowWhen(startedAtMs > 0L)
            .setWhen(if (startedAtMs > 0L) startedAtMs else System.currentTimeMillis())
            .setUsesChronometer(startedAtMs > 0L) // 주차 경과 시간 표시
            .setSubText(statusText)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(PARKED_NOTIFICATION_ID, notification)
    }

    fun dismissParkedNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(PARKED_NOTIFICATION_ID)
    }

    /**
     * "B3" 텍스트를 그린 정사각 비트맵 생성.
     * 상태바 아이콘은 알파 채널만 사용되므로 흰색으로 그림.
     */
    private fun renderTextIcon(text: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            // 글자 수에 따라 크기 조정 (B3=2자 크게, B12=3자 작게)
            textSize = if (text.length <= 2) 64f else 46f
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, paint)
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
