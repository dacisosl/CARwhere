package com.eottadwotji.detection

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import kotlin.math.roundToInt

/**
 * 주차 감지 상태 머신을 관리하는 포그라운드 서비스.
 *
 * 상태 흐름:
 *   DRIVING ──(차 BT 끊김)──> PENDING(5초 대기) ──(재연결 없음)──> PARKED(바텀시트/알림)
 *   PENDING ──(5초 내 재연결)──> DRIVING (오작동 필터: 신호 순간 끊김 무시)
 *   PARKED  ──(차 BT 재연결)──> DRIVING (주차 기록 만료 → Room 히스토리, 표시 제거)
 *   PARKED  ──(시트를 그냥 둠)──> PARKED(층 없음, 캡슐은 "P" — v5.2에서 타임아웃 제거)
 *
 * v3.6 수명 정책 — "감지 대기 중" 상시 알림 제거:
 * 포그라운드 서비스 알림은 시스템이 최소 LOW로 승격해 상태바에 항상 떠 버린다.
 * 그래서 대기 중에는 서비스를 아예 돌리지 않는다 — 차 블루투스 끊김/연결
 * 브로드캐스트(매니페스트 등록, BT 브로드캐스트는 FGS 시작 예외 대상)가 앱을 깨운다.
 * 서비스가 살아 있는 구간은 딱 두 가지:
 *   1. 주차 확정~출차 (P·B3 캡슐 = 서비스 알림 그 자체 → 알림 1개, 안정적)
 *   2. 기압 자동감지 켠 상태의 주행 중 (센서 샘플링 유지용)
 *
 * 위치는 주차 확정 순간 lastLocation 1회만 조회 (절대 규칙 6).
 */
class ParkingDetectionService : Service(), SensorEventListener {

    companion object {
        /**
         * 하차 오탐 되돌리기 창 (v5.5).
         *
         * v5.4까지는 "5초 기다렸다가 재연결이 없으면 주차 확정"이었다 — 안전하지만 차를 끄고
         * 5초가 지나야 시트가 떠서 "주차했네? 앱이 켜지네?"라는 체감이 죽었다.
         * 순서를 뒤집었다: 끊기면 곧바로 확정하고 시트를 띄우고, 이 창 안에 다시 연결되면
         * (엔진 재시동·순간 끊김) 방금 만든 기록을 히스토리에도 남기지 않고 되돌리고 시트를 닫는다.
         * 필터가 8초로 늘어난 대신 사용자가 기다리는 시간은 0이 된다.
         */
        private const val RECONNECT_RETRACT_MS = 8_000L

        /** 층고 약 3m ≈ 0.36hPa (PRD v2 7절) — 아래로 갈수록 기압 증가 */
        private const val PRESSURE_HPA_PER_FLOOR = 0.36f
        private const val PRESSURE_SAMPLING_US = 1_000_000 // 1초 — 저빈도 (배터리)
        private const val PRESSURE_EMA_ALPHA = 0.3f        // 노이즈 완화용 지수평활

        private const val ACTION_CAR_DISCONNECTED = "com.eottadwotji.CAR_DISCONNECTED"
        private const val ACTION_CAR_CONNECTED = "com.eottadwotji.CAR_CONNECTED"

        /**
         * 앱 실행/부팅 시: 주차 캡슐 알림만 복원한다 (v3.9.5).
         * 캡슐은 서비스와 분리된 일반 알림이라 서비스를 띄울 필요가 없다 —
         * 대기 상태의 감지는 BT 브로드캐스트가 서비스를 깨워서 처리.
         */
        fun start(context: Context) {
            ParkingNotification.syncParkedNotification(context)
        }

        /** 설정 변경(표시 방식 등) 후 캡슐 표시 상태를 다시 계산 */
        fun refresh(context: Context) {
            ParkingNotification.syncParkedNotification(context)
        }

        fun notifyCarDisconnected(context: Context) {
            context.startForegroundService(
                Intent(context, ParkingDetectionService::class.java)
                    .setAction(ACTION_CAR_DISCONNECTED)
            )
        }

        fun notifyCarConnected(context: Context) {
            context.startForegroundService(
                Intent(context, ParkingDetectionService::class.java)
                    .setAction(ACTION_CAR_CONNECTED)
            )
        }
    }

    // 기압 추정 상태 (주행 세션 한정)
    private var pressureAtDriveStart: Float? = null
    private var smoothedPressure: Float? = null
    private var pressureRegistered = false

    /** 서비스 생존 중엔 동적 등록도 병행 — 매니페스트 등록의 백업 (README) */
    private val dynamicReceiver = CarBluetoothReceiver()

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this, dynamicReceiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 계약: 무조건 startForeground 먼저.
        // 서비스 알림은 항상 "감지 중"(MIN — 상태바 미노출) 하나뿐 (v3.9.5).
        // 주차 캡슐은 서비스와 분리된 일반 알림(ID 2)이라 서비스가 죽어도 남는다.
        ServiceCompat.startForeground(
            this,
            ParkingNotification.SERVICE_NOTIFICATION_ID,
            ParkingNotification.buildIdleNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )

        when (intent?.action) {
            ACTION_CAR_DISCONNECTED -> onCarDisconnected()
            ACTION_CAR_CONNECTED -> onCarConnected()
            // 그 외: 감지할 것 없으면 즉시 종료
            else -> stopIfNothingToShow()
        }
        return START_STICKY
    }

    /** 하차: 기다리지 않고 바로 확정 — 되돌리기는 재연결 시 onCarConnected가 맡는다 (v5.5) */
    private fun onCarDisconnected() {
        val store = ParkingStore(this)
        // 이미 이 세션이 잡혀 있으면(중복 브로드캐스트) 시트를 두 번 띄우지 않는다
        if (store.hasActiveParking() && !store.isParkingManual()) {
            stopIfNothingToShow()
            return
        }
        confirmParked()
    }

    /** 재연결(주행 시작): 오탐이면 되돌리고, 진짜 출차면 기록 만료 + 기압 기준점 기록 */
    private fun onCarConnected() {
        val store = ParkingStore(this)
        // 오탐이면 기록을 지우고 시트를 닫는다 — 그게 아니면 진짜 출차다
        val retracted = retractIfFalseAlarm(store)
        if (!retracted && store.hasActiveParking()) {
            store.expireParking()
            // 출차하면 캡슐 제거 (기록은 히스토리에 보관 — v3.9.5부터 항상 자동)
            ParkingNotification.dismissParkedNotification(this)
            WidgetUpdater.update(this)
        }

        // 주행 시작 → 기압 샘플링 시작 (설정 켠 경우에만, 주행 중에만 — 배터리)
        if (store.pressureAutoDetect) startPressureSampling() else stopPressureSampling()

        stopIfNothingToShow()
    }

    /**
     * 하차 오탐 되돌리기 — 감지가 방금(RECONNECT_RETRACT_MS 안에) 만들었고 사용자가 층을
     * 고르지 않은 기록이면, 히스토리에도 남기지 않고 지우고 떠 있는 시트를 닫는다.
     * 사람이 직접 연 기록이나 이미 층을 고른 기록은 건드리지 않는다.
     */
    private fun retractIfFalseAlarm(store: ParkingStore): Boolean {
        if (!store.hasActiveParking() || store.isParkingManual()) return false
        if (store.currentFloor() != null) return false
        val age = System.currentTimeMillis() - store.parkingStartedAt()
        if (age !in 0..RECONNECT_RETRACT_MS) return false

        store.discardParking()
        ParkingNotification.dismissParkedNotification(this)
        // 떠 있는 기록 시트 닫기 (감지가 띄운 것만 — 매니페스트 미등록, 앱 내부 전용 브로드캐스트)
        sendBroadcast(
            Intent(FloorPickerActivity.ACTION_RETRACT).setPackage(packageName)
        )
        WidgetUpdater.update(this)
        return true
    }

    /**
     * 주차 확정 (v5.5 — 체감 속도 우선).
     *
     * 시트를 GPS보다 먼저 띄운다: lastLocation 콜백을 기다리면 수백 ms~수 초가 더 걸리는데,
     * 좌표는 시트가 뜬 뒤에 채워도 된다 — 시트가 위치 매칭을 잠시 폴링해서 등록된 주차장이면
     * 그 층 구성으로 바뀐다(FloorPickerActivity.LOT_RECHECK_*). 기압 추정도 두 번 계산한다:
     * 먼저 지형 보정 없이(즉시), 좌표가 오면 그 위치의 보정을 반영해 다듬는다.
     */
    private fun confirmParked() {
        val store = ParkingStore(this)
        store.startParking(timestampMs = System.currentTimeMillis())

        // ① 보정 없는 기압 추정 → 시트 즉시 표시
        store.estimatedFloor = estimateFloorFromPressure(store)
        showFloorPicker(store)
        WidgetUpdater.update(this)

        // ② 좌표는 뒤따라 저장 — 등록된 위치면 지형 보정을 반영해 추정을 다듬는다 (v3.7)
        fetchLastLocationOnce(store) {
            if (store.hasActiveParking() && store.currentFloor() == null) {
                store.estimatedFloor = estimateFloorFromPressure(store)
            }
            stopPressureSampling()
            stopIfNothingToShow()
        }
    }

    /**
     * 주행 시작 대비 기압 상승분으로 지하 층수 추정.
     * ±1층 오차가 있는 "추정"일 뿐 — 바텀시트에서 미리 선택 + "기압 추정" 라벨만 담당.
     * 등록된 위치의 pressureOffsetFloors(지형 보정 학습값)를 더한다 (v3.7).
     */
    private fun estimateFloorFromPressure(store: ParkingStore): String? {
        val start = pressureAtDriveStart ?: return null
        val now = smoothedPressure ?: return null
        // 아래로 갈수록 기압 증가: 양수 = 지하 n층, 0 이하 = 지상 (0→1F, -1→2F …)
        var floorsBelow = ((now - start) / PRESSURE_HPA_PER_FLOOR).roundToInt()
        floorsBelow += store.currentLot()?.pressureOffsetFloors ?: 0
        val candidate = if (floorsBelow >= 1) "B$floorsBelow" else "${1 - floorsBelow}F"
        // 현재 층 구성에 없는 층을 제안하면 혼란 → 목록 안에 있을 때만
        return candidate.takeIf { it in store.floorsForCurrentLocation() }
    }

    private fun fetchLastLocationOnce(store: ParkingStore, onDone: () -> Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            onDone()
            return
        }

        runCatching {
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    location?.let { store.setCoordinates(it.latitude, it.longitude) }
                    onDone()
                }
                .addOnFailureListener { onDone() }
        }.onFailure { onDone() }
    }

    private fun showFloorPicker(store: ParkingStore) {
        if (Settings.canDrawOverlays(this)) {
            // 다른 앱 위 바텀시트 — 오버레이 권한 보유 시 백그라운드 액티비티 시작 허용됨
            startActivity(
                Intent(this, FloorPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(FloorPickerActivity.EXTRA_FROM_DETECTION, true)
            )
        } else {
            // 폴백: 헤드업 알림 (오버레이 권한 없는 유저)
            ParkingNotification.showFloorPickerNotification(
                context = this,
                floors = store.floorsForCurrentLocation(),
                highlightFloor = store.estimatedFloor ?: store.lastFloorForCurrentLocation(),
                isEstimate = store.estimatedFloor != null
            )
        }

        // v5.2: 10초 무응답 타임아웃을 없앴다 (사용자 요청).
        // 대신 시트를 띄우는 즉시 층 없는 캡슐(P)을 게시한다 — 사용자가 시트를 그냥 두거나
        // 나중에 층을 골라도 상태바에는 항상 "주차 중"이 남는다. 층을 고르면 캡슐이 갱신된다.
        if (store.hasActiveParking() && store.currentFloor() == null) {
            ParkingNotification.showParkedNotification(
                this, floor = null, startedAtMs = store.parkingStartedAt()
            )
            WidgetUpdater.update(this)
        }
        // 서비스 종료는 좌표 콜백까지 끝난 뒤 confirmParked가 판단한다 (v5.5)
    }

    /**
     * 서비스가 계속 떠 있어야 할 이유가 없으면 종료 (v3.9.5).
     * 남는 이유: 주행 중 기압 샘플링 (v5.5부터 하차 판정 대기 시간은 없다).
     * 주차 캡슐은 서비스와 분리된 일반 알림이라 종료와 무관하게 유지된다.
     */
    private fun stopIfNothingToShow() {
        if (pressureRegistered) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── 기압 센서 (주행 중에만 등록 — 절대 규칙 7) ─────────

    private fun startPressureSampling() {
        if (pressureRegistered) {
            // 이미 샘플링 중 — 새 주행 세션이므로 기준점만 갱신
            pressureAtDriveStart = smoothedPressure
            return
        }
        val sensorManager = getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        pressureAtDriveStart = null
        smoothedPressure = null
        pressureRegistered = sensorManager.registerListener(
            this, sensor, PRESSURE_SAMPLING_US
        )
    }

    private fun stopPressureSampling() {
        if (pressureRegistered) {
            getSystemService(SensorManager::class.java)?.unregisterListener(this)
            pressureRegistered = false
        }
        pressureAtDriveStart = null
        smoothedPressure = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val raw = event.values[0]
        smoothedPressure = smoothedPressure
            ?.let { it + PRESSURE_EMA_ALPHA * (raw - it) }
            ?: raw
        // 첫 안정값을 주행 시작 기준점으로
        if (pressureAtDriveStart == null) pressureAtDriveStart = smoothedPressure
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── 정리 ────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPressureSampling()
        runCatching { unregisterReceiver(dynamicReceiver) }
        super.onDestroy()
    }
}
