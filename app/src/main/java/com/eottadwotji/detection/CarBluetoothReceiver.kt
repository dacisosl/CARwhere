package com.eottadwotji.detection

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eottadwotji.data.ParkingStore

/**
 * 차량 블루투스 연결/해제 감지 리시버.
 *
 * 핵심 원칙:
 * 1. 온보딩에서 지정한 "내 차" MAC 주소와 일치하는 기기만 반응 (이어폰 오작동 방지)
 * 2. 연결 해제 즉시 팝업을 띄우지 않고 ParkingDetectionService에 넘겨
 *    5초 재연결 필터를 거침 (신호 순간 끊김 대응)
 *
 * 등록은 이중화 (README 권장):
 * - AndroidManifest.xml 정적 등록 (백업 경로)
 * - ParkingDetectionService 내부 동적 등록 (API 33+ 제한 대응)
 * 같은 이벤트가 두 번 도달할 수 있지만 서비스 쪽 처리가 멱등이라 안전하다.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val deviceAddress = device?.address ?: return

        val store = ParkingStore(context)
        val myCarAddress = store.myCarAddress ?: return

        // 내 차가 아니면 무시 — 이어폰/워치 끊김에 반응하지 않는 핵심 필터
        if (deviceAddress != myCarAddress) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // 하차 후보 이벤트 → 서비스에서 5초 필터 시작
                ParkingDetectionService.notifyCarDisconnected(context)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // 재탑승 → 진행 중인 필터 취소 + 저장된 주차 기록 만료 처리
                ParkingDetectionService.notifyCarConnected(context)
            }
        }
    }
}
