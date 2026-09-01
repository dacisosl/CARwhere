package com.eottadwotji

import android.app.Application
import com.eottadwotji.detection.ParkingNotification

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 알림 채널은 앱 시작 시 1회 생성 (idle/popup/parked 3종)
        // 감지 서비스 기동은 MainActivity·BootReceiver에서 — Application에서 FGS를
        // 시작하면 백그라운드 기동 제한에 걸릴 수 있다.
        ParkingNotification.createChannels(this)
    }
}
