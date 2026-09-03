package com.eottadwotji.detection

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.eottadwotji.data.ParkingStore

/**
 * 차량 블루투스 연결/해제 감지 리시버.
 *
 * 핵심 원칙:
 * 1. 온보딩에서 지정한 "내 차" MAC 주소와 일치하는 기기만 반응 (이어폰 오작동 방지)
 * 2. 연결 해제 즉시 팝업을 띄우지 않고 ParkingDetectionService에 넘겨
 *    5초 재연결 필터를 거침 (신호 순간 끊김 대응)
 *
 * 등록은 이중화:
 * - AndroidManifest.xml 정적 등록 — 대기 중 서비스가 없으므로(v3.6) 사실상 주 경로.
 *   반드시 exported="true" (송신자가 블루투스 스택 uid라 비공개 리시버엔 전달되지 않음 — v4.2)
 * - ParkingDetectionService 내부 동적 등록 (RECEIVER_EXPORTED) — 감지 창 동안의 백업
 * 같은 이벤트가 두 번 도달할 수 있지만 서비스 쪽 처리가 멱등이라 안전하다.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        @Suppress("DEPRECATION")
        val device: BluetoothDevice? =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val deviceAddress = device?.address ?: return

        val store = ParkingStore(context)
        val myCarAddress = store.myCarAddress ?: return

        // 내 차가 아니면 무시 — 이어폰/워치 끊김에 반응하지 않는 핵심 필터 (절대 규칙 4).
        // MAC 대소문자 표기가 기기마다 달라도 같은 기기로 인식하도록 무시하고 비교.
        if (!deviceAddress.equals(myCarAddress, ignoreCase = true)) return

        // API 31+ connectedDevice 타입 FGS는 BLUETOOTH_CONNECT 없이 시작하면 SecurityException
        // → 권한이 사라진 상태(사용자가 설정에서 회수)면 크래시 대신 조용히 무시
        val btGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        if (!btGranted) return

        val connected = action == BluetoothDevice.ACTION_ACL_CONNECTED
        // 진단용 — 대시보드에서 "마지막 차량 신호"로 확인 (v4.2)
        store.recordCarEvent(connected)

        if (connected) {
            // 재탑승 → 진행 중인 필터 취소 + 저장된 주차 기록 만료 처리
            ParkingDetectionService.notifyCarConnected(context)
        } else {
            // 하차 후보 이벤트 → 서비스에서 5초 필터 시작
            ParkingDetectionService.notifyCarDisconnected(context)
        }
    }
}
