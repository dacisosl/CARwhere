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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
 *   PARKED  ──(10초 무응답)──> PARKED(층 없음, "위치만 저장됨" 표시)
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
        private const val RECONNECT_FILTER_MS = 5_000L
        private const val FLOOR_TIMEOUT_MS = 10_000L

        /** 층고 약 3m ≈ 0.36hPa (PRD v2 7절) — 아래로 갈수록 기압 증가 */
        private const val PRESSURE_HPA_PER_FLOOR = 0.36f
        private const val PRESSURE_SAMPLING_US = 1_000_000 // 1초 — 저빈도 (배터리)
        private const val PRESSURE_EMA_ALPHA = 0.3f        // 노이즈 완화용 지수평활

        private const val ACTION_START = "com.eottadwotji.START_DETECTION"
        private const val ACTION_CAR_DISCONNECTED = "com.eottadwotji.CAR_DISCONNECTED"
        private const val ACTION_CAR_CONNECTED = "com.eottadwotji.CAR_CONNECTED"
        private const val ACTION_REFRESH = "com.eottadwotji.REFRESH_NOTIFICATION"

        /**
         * 앱 실행/부팅 시: 주차 중일 때만 서비스를 붙여 P·B3 캡슐을 복원한다.
         * 대기 상태에서는 서비스가 필요 없다 — BT 브로드캐스트가 깨운다 (v3.6).
         */
        fun start(context: Context) {
            if (ParkingStore(context).hasActiveParking()) {
                context.startForegroundService(
                    Intent(context, ParkingDetectionService::class.java)
                        .setAction(ACTION_START)
                )
            }
        }

        /** 설정 변경(표시 방식 등) 후 상시 알림 상태를 다시 계산 */
        fun refresh(context: Context) {
            context.startForegroundService(
                Intent(context, ParkingDetectionService::class.java)
                    .setAction(ACTION_REFRESH)
            )
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

    private val handler = Handler(Looper.getMainLooper())
    private var pendingParkingRunnable: Runnable? = null
    private var floorTimeoutRunnable: Runnable? = null

    // 기압 추정 상태 (주행 세션 한정)
    private var pressureAtDriveStart: Float? = null
    private var smoothedPressure: Float? = null
    private var pressureRegistered = false

    /** 서비스 생존 중엔 동적 등록도 병행 — 매니페스트 등록의 백업 (README) */
    private val dynamicReceiver = CarBluetoothReceiver()

    /** 현재 게시된 상시 알림이 주차 캡슐인지 (채널 전환 감지용 — v3.9) */
    private var shownParked: Boolean? = null

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
        postForegroundNotification()

        when (intent?.action) {
            ACTION_CAR_DISCONNECTED -> onCarDisconnected()
            ACTION_CAR_CONNECTED -> onCarConnected()
            // ACTION_START / ACTION_REFRESH: 상태 재계산만 — 필요 없으면 즉시 종료
            else -> stopIfNothingToShow()
        }
        return START_STICKY
    }

    /**
     * 상시 알림 게시 — 감지 중(MIN 채널) ↔ 주차 캡슐(LOW 채널) 전환 처리 (v3.9).
     * Android는 이미 게시된 알림의 채널 변경을 무시하므로, 상태 클래스가 바뀌면
     * stopForeground(REMOVE)로 제거한 뒤 신규 게시해야 채널(=상태바 노출)이 올바르게 적용된다.
     * 이게 빠지면 감지 → 주차 전환 시 MIN에 갇혀 상태바 캡슐이 안 뜬다 (간헐 버그의 원인).
     */
    private fun postForegroundNotification() {
        val wantParked = ParkingNotification.wantsParkedNotification(this)
        if (shownParked != null && shownParked != wantParked) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        ServiceCompat.startForeground(
            this,
            ParkingNotification.SERVICE_NOTIFICATION_ID,
            ParkingNotification.buildServiceNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )
        shownParked = wantParked
    }

    /** 하차 후보: 5초 안에 재연결이 없으면 진짜 주차로 판정 */
    private fun onCarDisconnected() {
        cancelPendingFilter()
        pendingParkingRunnable = Runnable {
            pendingParkingRunnable = null
            confirmParked()
        }.also {
            handler.postDelayed(it, RECONNECT_FILTER_MS)
        }
    }

    /** 재연결(주행 시작): 필터 취소 + 기존 주차 기록 만료 + 기압 기준점 기록 */
    private fun onCarConnected() {
        cancelPendingFilter()
        cancelFloorTimeout()

        val store = ParkingStore(this)
        var keepDepartedNotification = false
        if (store.hasActiveParking()) {
            store.expireParking()
            // 자동 삭제 꺼짐: 출차 후에도 마지막 주차 표시를 남긴다 (스와이프로 지울 수 있음)
            keepDepartedNotification = !store.autoClearOnDeparture
            WidgetUpdater.update(this)
        }

        // 주행 시작 → 기압 샘플링 시작 (설정 켠 경우에만, 주행 중에만 — 배터리)
        if (store.pressureAutoDetect) startPressureSampling() else stopPressureSampling()

        stopIfNothingToShow(keepNotification = keepDepartedNotification)
    }

    /** 주차 확정: GPS 좌표 1회 저장 → 기압 추정(위치 보정 반영) → 바텀시트(또는 알림 폴백) */
    private fun confirmParked() {
        val store = ParkingStore(this)
        store.startParking(timestampMs = System.currentTimeMillis())

        // lastLocation 1회 조회 후 팝업 — 좌표가 있어야 프로필(지난번 층·기압 보정) 매칭이 된다.
        // 실패해도 팝업은 반드시 떠야 하므로 completion 콜백에서 이어간다.
        fetchLastLocationOnce(store) {
            // 기압 추정은 위치 매칭 후에 — 등록된 위치면 학습된 지형 보정을 더한다 (v3.7)
            store.estimatedFloor = estimateFloorFromPressure(store)
            stopPressureSampling()
            showFloorPicker(store)
        }
        WidgetUpdater.update(this)
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

        // 10초 무응답 → 층수 없이 "위치만 저장됨" 상태로 전환 (강요하지 않음 — PRD)
        cancelFloorTimeout()
        floorTimeoutRunnable = Runnable {
            floorTimeoutRunnable = null
            if (store.hasActiveParking() && store.currentFloor() == null) {
                ParkingNotification.showParkedNotification(
                    this, floor = null, startedAtMs = store.parkingStartedAt()
                )
                WidgetUpdater.update(this)
            }
            stopIfNothingToShow() // "홈 위젯만" 모드면 여기서 서비스 정리
        }.also { handler.postDelayed(it, FLOOR_TIMEOUT_MS) }
    }

    /**
     * 서비스가 계속 떠 있어야 할 이유가 없으면 종료 (v3.6).
     * 남는 이유: ① 상태바에 P·B3 표시 중 ② 하차 판정/층 선택 대기 중 ③ 주행 중 기압 샘플링.
     * keepNotification=true면 알림을 떼어놓고(detach) 종료 — 출차 후 표시 유지 옵션.
     */
    private fun stopIfNothingToShow(keepNotification: Boolean = false) {
        val store = ParkingStore(this)
        val showingParked = store.hasActiveParking() &&
            store.displayMode != ParkingStore.DISPLAY_WIDGET
        val detectionWindow = pendingParkingRunnable != null || floorTimeoutRunnable != null
        if (showingParked || detectionWindow || pressureRegistered) {
            // 계속 표시해야 함 — 알림 내용만 최신으로
            return
        }
        if (keepNotification) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        } else {
            ParkingNotification.dismissParkedNotification(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
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

    private fun cancelPendingFilter() {
        pendingParkingRunnable?.let { handler.removeCallbacks(it) }
        pendingParkingRunnable = null
    }

    private fun cancelFloorTimeout() {
        floorTimeoutRunnable?.let { handler.removeCallbacks(it) }
        floorTimeoutRunnable = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelPendingFilter()
        cancelFloorTimeout()
        stopPressureSampling()
        runCatching { unregisterReceiver(dynamicReceiver) }
        super.onDestroy()
    }
}
