package com.eottadwotji.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eottadwotji.data.ParkingStore

/**
 * 재부팅 후 감지 서비스 재기동.
 * 온보딩을 마치고 내 차를 지정한 사용자만 대상 — 그 전에는 감지할 것이 없다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val store = ParkingStore(context)
        if (store.onboardingDone && store.myCarAddress != null) {
            ParkingDetectionService.start(context)
        }
    }
}
