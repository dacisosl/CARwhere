package com.eottadwotji.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.components.GroundLine
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 온보딩 3단계 (DESIGN.md 5절) — 30초 내 완료 목표.
 * 1. 내 차 블루투스 지정 (이어폰 오작동 방지의 핵심)
 * 2. 권한: 알림 / 근처 기기 / 배터리 최적화 제외 + 삼성 절전 안내
 * 3. 표시 방식 선택 + "지금 주차되어 있나요?" 첫 기록 유도
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .systemBarsPadding() // v2 절대 규칙 8
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // 상단 진행 바 3칸
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (i < step) Concrete.Neon else Concrete.BgPanel,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        when (step) {
            1 -> CarSelectStep(onNext = { step = 2 })
            2 -> PermissionStep(onNext = { step = 3 })
            3 -> DisplayStep(onComplete = onComplete)
        }
    }
}

// ── 1단계: 내 차 선택 ───────────────────────────────────────

private data class BtDevice(val name: String, val address: String)

@Composable
private fun CarSelectStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }

    var btGranted by remember { mutableStateOf(hasBtPermission(context)) }
    var selected by remember { mutableStateOf<BtDevice?>(null) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { btGranted = hasBtPermission(context) }

    val devices = remember(btGranted) {
        if (btGranted) bondedDevices(context) else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("내 차를 골라주세요", style = AppType.Title, color = Concrete.TextMain)
        Spacer(Modifier.height(6.dp))
        Text(
            "선택한 차량 기기가 끊길 때만 반응해요.\n이어폰이 끊길 땐 반응하지 않아요.",
            style = AppType.BodySmall,
            color = Concrete.TextSub
        )
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!btGranted) {
                // 페어링 목록 조회에 근처 기기 권한이 먼저 필요 (API 31+)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Concrete.Neon, RoundedCornerShape(8.dp))
                        .clickable {
                            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "근처 기기 권한 허용하고 목록 보기",
                        style = AppType.FloorButton,
                        color = Concrete.NeonDeep
                    )
                }
                Text(
                    "페어링된 기기 목록을 읽기 위한 권한이에요",
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
            } else if (devices.isEmpty()) {
                Text(
                    "페어링된 블루투스 기기가 없어요.\n차량과 먼저 페어링한 뒤 다시 열어주세요.",
                    style = AppType.Body,
                    color = Concrete.TextSub
                )
            } else {
                devices.forEach { device ->
                    val isSelected = device.address == selected?.address
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp, Concrete.Neon, RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .clickable { selected = device }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            device.name,
                            style = AppType.Body,
                            color = if (isSelected) Concrete.NeonLight else Concrete.TextBody
                        )
                        Spacer(Modifier.weight(1f))
                        if (isSelected) {
                            Text("✓", style = AppType.FloorButton, color = Concrete.Neon)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 탈출구: 차량 블루투스 없는 유저 → 수동 기록 전용 모드
        Text(
            "차량 블루투스가 없어요",
            style = AppType.BodySmall,
            color = Concrete.TextDim,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    store.manualOnlyMode = true
                    store.myCarAddress = null
                    onNext()
                }
                .padding(8.dp)
        )
        Spacer(Modifier.height(8.dp))

        NextButton(enabled = selected != null, label = "다음") {
            selected?.let {
                store.myCarAddress = it.address
                store.myCarName = it.name
                store.manualOnlyMode = false
            }
            onNext()
        }
    }
}

// ── 2단계: 권한 ─────────────────────────────────────────────

@Composable
private fun PermissionStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }

    var refresh by remember { mutableIntStateOf(0) }
    // 설정 화면에서 돌아올 때 상태 재확인 (배터리 최적화 제외 등)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifGranted = remember(refresh) { hasNotificationPermission(context) }
    val btGranted = remember(refresh) { hasBtPermission(context) }
    val locationGranted = remember(refresh) { hasLocationPermission(context) }
    val batteryExempt = remember(refresh) { isBatteryExempt(context) }
    val overlayGranted = remember(refresh) { Settings.canDrawOverlays(context) }
    val manualOnly = remember { store.manualOnlyMode }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("주차를 놓치지 않으려면", style = AppType.Title, color = Concrete.TextMain)
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PermissionRow(
                label = "알림",
                reason = "하차하면 층수 팝업을 띄워요",
                done = notifGranted
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!manualOnly) {
                PermissionRow(
                    label = "근처 기기",
                    reason = "차량 블루투스 끊김을 감지해요",
                    done = btGranted
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
            }
            PermissionRow(
                label = "배터리 최적화 제외",
                reason = "이걸 꺼야 주차를 놓치지 않아요",
                done = batteryExempt
            ) {
                requestBatteryExemption(context)
            }
            PermissionRow(
                label = "다른 앱 위에 표시 (권장)",
                reason = "하차하면 화면 하단에 층 선택 시트를 바로 띄워요",
                done = overlayGranted
            ) {
                requestOverlayPermission(context)
            }
            PermissionRow(
                label = "위치 (선택)",
                reason = "주차 순간 좌표를 1번만 저장해요 — 추적 안 함",
                done = locationGranted
            ) {
                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            // 삼성 절전 정책 안내 — API로 유도 불가, 화면 안내 필수 (README 기기별 함정)
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("삼성 기기 한 가지 더", style = AppType.Body, color = Concrete.TextMain)
                    Text(
                        "설정 > 배터리 > 백그라운드 사용 제한에서\n\"절전 예외 앱\"에 내차위치를 추가해 주세요.\n안 하면 감지가 중간에 꺼질 수 있어요.",
                        style = AppType.BodySmall,
                        color = Concrete.TextSub
                    )
                    Text(
                        "배터리 설정 열기",
                        style = AppType.BodySmall,
                        color = Concrete.TextBody,
                        modifier = Modifier
                            .clickable { openBatterySettings(context) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 전부 켜야 다음 활성화 (위치는 선택이라 게이트에서 제외)
        val required = notifGranted && batteryExempt && (manualOnly || btGranted)
        NextButton(enabled = required, label = "다음", onClick = onNext)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    reason: String,
    done: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
            .then(
                // 미완료 항목만 형광 테두리 (DESIGN 온보딩 2단계 스펙)
                if (!done) Modifier.border(2.dp, Concrete.Neon, RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppType.Body, color = Concrete.TextMain)
            Text(reason, style = AppType.Hint, color = Concrete.TextDim)
        }
        if (done) {
            Text("✓", style = AppType.FloorButton, color = Concrete.Neon)
        } else {
            Text(
                "켜기",
                style = AppType.BodySmall,
                color = Concrete.NeonLight,
                modifier = Modifier
                    .clickable(onClick = onRequest)
                    .padding(8.dp)
            )
        }
    }
}

// ── 3단계: 표시 안내 + 첫 기록 유도 (표시 방식 설정 제거 — v3.9.5) ──

@Composable
private fun DisplayStep(onComplete: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("이렇게 표시돼요", style = AppType.Title, color = Concrete.TextMain)
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DisplayOption(
                label = "상태바",
                detail = "화면 위에 항상 \"P·B3\" — 확인은 0초",
                selected = true
            ) { }
            DisplayOption(
                label = "홈 화면 위젯",
                detail = "홈 화면에 위젯을 추가하면 층수·시각이 함께 표시돼요",
                selected = false
            ) { }
        }

        Spacer(Modifier.height(28.dp))
        GroundLine(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))

        // 설치 1분 내 핵심 가치 체험: 첫 기록 유도
        Text("지금 차가 주차되어 있나요?", style = AppType.Body, color = Concrete.TextBody)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.Neon, RoundedCornerShape(8.dp))
                    .clickable {
                        store.onboardingDone = true
                        context.startActivity(
                            Intent(context, FloorPickerActivity::class.java)
                                .putExtra(FloorPickerActivity.EXTRA_MANUAL, true)
                        )
                        onComplete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("네, 층 기록하기", style = AppType.FloorButton, color = Concrete.NeonDeep)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .clickable {
                        store.onboardingDone = true
                        onComplete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("아니요", style = AppType.BodySmall, color = Concrete.TextSub)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DisplayOption(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.border(2.dp, Concrete.Neon, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = AppType.Body,
            color = if (selected) Concrete.NeonLight else Concrete.TextMain
        )
        Text(detail, style = AppType.Hint, color = Concrete.TextDim)
    }
}

// ── 공용 ────────────────────────────────────────────────────

@Composable
private fun NextButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                if (enabled) Concrete.Neon else Concrete.BgPanel,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AppType.FloorButton,
            color = if (enabled) Concrete.NeonDeep else Concrete.TextDim
        )
    }
}

// ── 권한/기기 헬퍼 ──────────────────────────────────────────

private fun hasBtPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

private fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

@Suppress("BatteryLife") // 감지 서비스 생존이 제품 핵심 — 온보딩에서 명시적으로 요청
private fun requestBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    }
}

private fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/** 오버레이(다른 앱 위에 표시) — 바텀시트 팝업용. 거부해도 알림 폴백으로 동작 */
private fun requestOverlayPermission(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}

@SuppressLint("MissingPermission") // 진입 전 hasBtPermission 확인 + 여기서도 재확인
private fun bondedDevices(context: Context): List<BtDevice> {
    if (!hasBtPermission(context)) return emptyList()
    return runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList()
        adapter.bondedDevices.map { BtDevice(it.name ?: it.address, it.address) }
    }.getOrDefault(emptyList())
}
