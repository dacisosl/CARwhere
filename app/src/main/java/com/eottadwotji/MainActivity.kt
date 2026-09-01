package com.eottadwotji

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.detection.ParkingDetectionService
import com.eottadwotji.ui.dashboard.DashboardScreen
import com.eottadwotji.ui.onboarding.OnboardingScreen
import com.eottadwotji.ui.theme.EottadwotjiTheme

/** 온보딩 완료 여부에 따라 온보딩/대시보드 분기하는 단일 진입점 */
class MainActivity : ComponentActivity() {

    private lateinit var store: ParkingStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ParkingStore(this)

        setContent {
            EottadwotjiTheme {
                var onboardingDone by remember { mutableStateOf(store.onboardingDone) }
                // 시네마틱 스플래시: 로딩시간과 무관하게 항상 온전히 재생 (프로세스당 1회)
                var showSplash by remember {
                    mutableStateOf(!com.eottadwotji.ui.splash.SplashGate.shown)
                }
                when {
                    showSplash -> com.eottadwotji.ui.splash.CinematicSplash(onDone = {
                        com.eottadwotji.ui.splash.SplashGate.shown = true
                        showSplash = false
                    })
                    onboardingDone -> DashboardScreen()
                    else -> OnboardingScreen(onComplete = {
                        onboardingDone = true
                        startDetectionIfReady()
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 주차 캡슐 복원 — 일반 알림이라 업데이트·재부팅 후에도 여기서 되살린다 (v3.9.5)
        com.eottadwotji.detection.ParkingNotification.syncParkedNotification(this)
        startDetectionIfReady()
    }

    /** 온보딩 완료 + 내 차 지정 + BT 권한이 모두 갖춰졌을 때만 감지 서비스 기동 */
    private fun startDetectionIfReady() {
        if (!store.onboardingDone || store.myCarAddress == null) return

        // API 31+ FGS(connectedDevice)는 BLUETOOTH_CONNECT가 없으면 SecurityException
        val btGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        if (btGranted) {
            ParkingDetectionService.start(this)
        }
    }
}
