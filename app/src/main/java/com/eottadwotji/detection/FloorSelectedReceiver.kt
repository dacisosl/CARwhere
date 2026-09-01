package com.eottadwotji.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eottadwotji.data.ParkingStore

/** 알림의 층수 버튼 탭 → 층 저장 + 상시 표시로 전환 */
class FloorSelectedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val floor = intent.getStringExtra("floor") ?: return

        val store = ParkingStore(context)
        store.recordPressureCalibration(floor) // 기압 추정이 틀렸으면 지형 보정 학습 (v3.7)
        store.setFloor(floor)
        store.rememberFloorForCurrentLocation(floor) // 다음 방문 때 "지난번" 강조용

        // 팝업 알림을 상시 표시 알림으로 교체 + 홈 위젯 갱신
        ParkingNotification.showParkedNotification(
            context, floor, startedAtMs = store.parkingStartedAt()
        )
        com.eottadwotji.ui.widget.WidgetUpdater.update(context)
    }
}
