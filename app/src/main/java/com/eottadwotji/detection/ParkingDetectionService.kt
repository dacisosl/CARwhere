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
 * v2 추가:
 * - 팝업: 오버레이 권한 있으면 다른 앱 위 바텀시트(FloorPickerActivity), 없으면 헤드업 알림 폴백
 * - 기압 자동감지(베타): 주행 시작(BT 연결)~하차 사이 기압 변화로 지하 층수 "추정"만.
 *   확정은 항상 사람 탭 (절대 규칙 5). 센서는 주행 중에만 등록 (절대 규칙 7 — 배터리).
 *
 * 위치는 주차 확정 순간 lastLocation 1회만 조회 (절대 규칙 6).
 * 지하에서는 GPS가 잡히지 않으므로 "진입 직전 마지막 위치"가 오히려 정확하다.
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

        /** 앱 실행/온보딩 완료/부팅 시 감지 대기 시작 */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, ParkingDetectionService::class.java)
                    .setAction(ACTION_START)
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

    /** 동적 등록 리시버 — API 33+에서 매니페스트 등록이 제한되는 케이스 대응 (README) */
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
        // 서비스 자체의 최소 상시 알림 (감지 대기 중)
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
            // ACTION_START: 감지 대기만 시작 (리시버는 onCreate에서 이미 등록됨)
        }
        return START_STICKY
    }

    /** 하차 후보: 5초 안에 재연결이 없으면 진짜 주차로 판정 */
    private fun onCarDisconnected() {
        cancelPendingFilter()
        pendingParkingRunnable = Runnable { confirmParked() }.also {
            handler.postDelayed(it, RECONNECT_FILTER_MS)
        }
    }

    /** 재연결(주행 시작): 필터 취소 + 기존 주차 기록 만료 + 기압 기준점 기록 */
    private fun onCarConnected() {
        cancelPendingFilter()
        cancelFloorTimeout()

        val store = ParkingStore(this)
        if (store.hasActiveParking()) {
            store.expireParking()
            if (store.autoClearOnDeparture) {
                ParkingNotification.dismissParkedNotification(this)
            }
            WidgetUpdater.update(this)
        }

        // 주행 시작 → 기압 샘플링 시작 (설정 켠 경우에만, 주행 중에만 — 배터리)
        if (store.pressureAutoDetect) startPressureSampling() else stopPressureSampling()
    }

    /** 주차 확정: 기압 추정 → GPS 좌표 1회 저장 → 바텀시트(또는 알림 폴백) */
    private fun confirmParked() {
        val store = ParkingStore(this)
        store.startParking(timestampMs = System.currentTimeMillis())

        // 기압 추정 층 계산 후 센서 즉시 해제 (하차했으니 더 볼 필요 없음)
        store.estimatedFloor = estimateFloorFromPressure(store)
        stopPressureSampling()

        // lastLocation 1회 조회 후 팝업 — 좌표가 있어야 프로필(지난번 층) 매칭이 된다.
        // 실패해도 팝업은 반드시 떠야 하므로 completion 콜백에서 이어간다.
        fetchLastLocationOnce(store) { showFloorPicker(store) }
        WidgetUpdater.update(this)
    }

    /**
     * 주행 시작 대비 기압 상승분으로 지하 층수 추정.
     * ±1층 오차가 있는 "추정"일 뿐 — 바텀시트에서 미리 선택 + "기압 추정" 라벨만 담당.
     */
    private fun estimateFloorFromPressure(store: ParkingStore): String? {
        val start = pressureAtDriveStart ?: return null
        val now = smoothedPressure ?: return null
        // 아래로 갈수록 기압 증가: 양수 = 지하 n층, 0 이하 = 지상 (0→1F, -1→2F …)
        val floorsBelow = ((now - start) / PRESSURE_HPA_PER_FLOOR).roundToInt()
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
                highlightFloor = store.estimatedFloor ?: store.lastFloorForCurrentLocation()
            )
        }

        // 10초 무응답 → 층수 없이 "위치만 저장됨" 상태로 전환 (강요하지 않음 — PRD)
        cancelFloorTimeout()
        floorTimeoutRunnable = Runnable {
            if (store.hasActiveParking() && store.currentFloor() == null) {
                ParkingNotification.showParkedNotification(
                    this, floor = null, startedAtMs = store.parkingStartedAt()
                )
                WidgetUpdater.update(this)
            }
        }.also { handler.postDelayed(it, FLOOR_TIMEOUT_MS) }
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
