package com.eottadwotji.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 재부팅·앱 업데이트 직후 주차 캡슐 복원 (v3.9.5).
 *
 * 안드로이드는 재부팅/패키지 교체 시 앱의 모든 알림을 지운다 —
 * 그래서 업데이트 직후 "주차 중인데 상태바에 아이콘이 없는" 상태가 됐었다.
 * 두 이벤트 모두 시스템 브로드캐스트로 받아, 앱을 열지 않아도 즉시 캡슐을 다시 게시한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 주차 중이면 캡슐 재게시, 아니면 아무 것도 안 함
                ParkingNotification.syncParkedNotification(context)
            }
        }
    }
}
