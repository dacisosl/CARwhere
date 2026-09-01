package com.eottadwotji.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eottadwotji.BuildConfig
import com.eottadwotji.data.HistoryDb
import com.eottadwotji.data.ParkingRecord
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.PhotoStore
import com.eottadwotji.data.UpdateChecker
import com.eottadwotji.detection.ParkingDetectionService
import com.eottadwotji.ui.widget.WidgetUpdater
import com.eottadwotji.ui.components.CircularGauge
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import com.eottadwotji.ui.history.HistoryActivity
import com.eottadwotji.ui.settings.SettingsActivity
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 대시보드 v2 (DESIGN v2) — 자동차 계기판 무드.
 * 위에서 아래: 헤더(브랜드+감지 배지) / 계기판 카드 / 최근 주차 카드 / 인라인 설정 카드.
 * 형광은 게이지 링·감지 배지 정도로 제한 (절대 규칙 3).
 */
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }

    // 화면 복귀·1분 경과마다 상태 다시 읽기 (SharedPreferences 기반)
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                WidgetUpdater.update(context) // 앱 복귀 시 위젯도 최신 상태로 동기화 (v3.6)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000L)
            refreshKey++ // 화면 떠 있는 동안 상태·경과 시간 갱신 (SharedPreferences 읽기 — 가벼움)
        }
    }

    val isParked = remember(refreshKey) { store.hasActiveParking() }
    val floor = remember(refreshKey) { store.currentFloor() }
    val zone = remember(refreshKey) { store.currentZone() }
    val memo = remember(refreshKey) { store.currentMemo() }
    val lotName = remember(refreshKey) { store.currentLot()?.name }
    val address = remember(refreshKey) { store.approximateAddress() } // v3.9 대략적 GPS 주소
    val startedAt = remember(refreshKey) { store.parkingStartedAt() }
    val photoUri = remember(refreshKey) { store.photoUri }
    val detecting = remember(refreshKey) { store.onboardingDone && store.myCarAddress != null }

    // 최근 주차 기록 (Room)
    val recentFlow: Flow<List<ParkingRecord>> = remember {
        runCatching { HistoryDb.get(context).dao().recent(3) }
            .getOrDefault(flowOf(emptyList()))
    }
    val recent by recentFlow.collectAsState(initial = emptyList())

    // 새 버전 확인 (GitHub Releases) — 실패하면 조용히 무시
    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    var downloading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        UpdateChecker.check(BuildConfig.VERSION_CODE) { update = it }
    }

    var showPhotoDialog by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            store.photoUri = pendingPhotoUri?.toString()
            refreshKey++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .systemBarsPadding() // v2 절대 규칙 8: 시스템 바 침범 금지
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // 헤더: 브랜드(레터스페이싱) + 감지 상태 배지
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("어따뒀지", style = AppType.Brand, color = Concrete.TextMain)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 계기판 인디케이터 LED: 감지 중이면 글로우 점등 (v3.9)
                Box(
                    modifier = Modifier.size(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (detecting) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        listOf(
                                            Concrete.Neon.copy(alpha = 0.55f),
                                            Concrete.Neon.copy(alpha = 0f)
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (detecting) Concrete.Neon else Concrete.TextDim,
                                CircleShape
                            )
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    if (detecting) "감지 중" else "수동 모드",
                    style = AppType.Hint,
                    color = if (detecting) Concrete.NeonLight else Concrete.TextDim
                )
            }
        }

        // ── 업데이트 배너 (새 버전이 있을 때만) ──
        update?.let { u ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgDeep, RoundedCornerShape(12.dp))
                    .clickable(enabled = !downloading) {
                        downloading = true
                        UpdateChecker.downloadAndInstall(context, u)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (downloading) "다운로드 중 — 완료되면 설치 화면이 떠요"
                    else "새 버전 ${u.label} 나왔어요",
                    style = AppType.BodySmall,
                    color = Concrete.TextBody
                )
                Spacer(Modifier.weight(1f))
                if (!downloading) {
                    Text("업데이트", style = AppType.BodySmall, color = Concrete.NeonLight)
                }
            }
        }

        // ── 배터리 최적화 예외 안내 (삼성 등에서 감지가 끊기는 최대 원인 — v3.9) ──
        val batteryExempt = remember(refreshKey) {
            runCatching {
                (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(true)
        }
        if (detecting && !batteryExempt) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgDeep, RoundedCornerShape(12.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings
                                        .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "감지가 끊기지 않게 배터리 예외를 허용해주세요",
                    style = AppType.BodySmall,
                    color = Concrete.TextBody
                )
                Spacer(Modifier.weight(1f))
                Text("허용", style = AppType.BodySmall, color = Concrete.NeonLight)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 계기판 카드 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 엔진 스타트 버튼: 누르면 기록 시트 (주차 중=다시 기록, 아니면=수동 기록)
                CircularGauge(
                    floor = floor,
                    parked = isParked,
                    onPress = { openFloorPicker(context, manual = !isParked) }
                )
                Spacer(Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (isParked) {
                        // 위치명 > 역지오코딩 주소 > 폴백 (v3.9)
                        Text(
                            lotName ?: address?.let { "$it 근처" } ?: "주차 위치 저장됨",
                            style = AppType.Body,
                            color = Concrete.TextMain
                        )
                        // 층수는 엔진 버튼에 이미 크게 있음 — 중복 제거 (v3.9)
                        val detail = listOfNotNull(zone, memo).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(detail, style = AppType.BodySmall, color = Concrete.TextSub)
                        }
                        Text(
                            "${formatTime(startedAt)} · ${formatElapsed(startedAt)}",
                            style = AppType.Hint,
                            color = Concrete.TextDim
                        )
                    } else {
                        Text(
                            "지금 주차 중이 아니에요",
                            style = AppType.Body,
                            color = Concrete.TextSub
                        )
                        Text(
                            "차에서 내리면 자동으로 물어볼게요",
                            style = AppType.Hint,
                            color = Concrete.TextDim
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isParked) {
                    SmallConcreteButton(
                        label = if (photoUri != null) "사진 보기" else "사진",
                        modifier = Modifier.weight(1f)
                    ) {
                        if (photoUri != null) {
                            showPhotoDialog = true
                        } else {
                            val uri = PhotoStore.newPhotoUri(context, System.currentTimeMillis())
                            pendingPhotoUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }
                    SmallConcreteButton("다시 기록", Modifier.weight(1f)) {
                        openFloorPicker(context)
                    }
                } else {
                    SmallConcreteButton("수동 기록", Modifier.weight(1f)) {
                        openFloorPicker(context, manual = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 위치 카드 (v3.9.5): 등록 위치를 바로 보고, 탭해서 수정·삭제 ──
        var editingLot by remember {
            mutableStateOf<com.eottadwotji.data.ParkingLotProfile?>(null)
        }
        var creatingLot by remember { mutableStateOf(false) }
        var lotsVersion by remember { mutableIntStateOf(0) }
        val lots = remember(lotsVersion, refreshKey) { store.profiles().sortedBy { it.name } }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("위치", style = AppType.SectionLabel, color = Concrete.TextDim)
                Spacer(Modifier.weight(1f))
                Text(
                    "+ 추가",
                    style = AppType.BodySmall,
                    color = Concrete.NeonLight,
                    modifier = Modifier
                        .clickable { creatingLot = true }
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
            if (lots.isEmpty()) {
                Text(
                    "등록된 위치가 없어요 — 자주 가는 주차장을 등록해 두세요",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            lots.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingLot = profile }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = AppType.BodySmall, color = Concrete.TextBody)
                        Text(
                            com.eottadwotji.ui.components.lotFloorsSummary(profile.floors) +
                                if (profile.latitude != null) " · 위치 등록됨" else "",
                            style = AppType.Hint,
                            color = Concrete.TextDim
                        )
                    }
                    Text("✎", style = AppType.BodySmall, color = Concrete.TextDim)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (editingLot != null || creatingLot) {
            com.eottadwotji.ui.components.LotEditModal(
                store = store,
                profile = editingLot,
                onDismiss = {
                    editingLot = null
                    creatingLot = false
                    lotsVersion++
                    refreshKey++
                }
            )
        }

        // ── 최근 주차 카드 (토글로 접기/펼치기 — v3.9, 표시 여부는 설정 — v3.9.4) ──
        val showRecentCard = remember(refreshKey) { store.showRecentCard }
        if (showRecentCard && recent.isNotEmpty()) {
            var recentExpanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                    .animateContentSize(animationSpec = tween(180))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { recentExpanded = !recentExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("최근 주차", style = AppType.SectionLabel, color = Concrete.TextDim)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (recentExpanded) "▴" else "▾",
                        style = AppType.BodySmall,
                        color = Concrete.TextDim
                    )
                }
                if (recentExpanded) {
                    Spacer(Modifier.height(10.dp))
                    recent.forEach { record ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatDate(record.endedAt),
                                style = AppType.BodySmall,
                                color = Concrete.TextDim
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                record.lotName ?: "이름 없는 주차장",
                                style = AppType.BodySmall,
                                color = Concrete.TextBody
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                record.floor ?: "—",
                                style = AppType.BodySmall,
                                color = Concrete.TextSub
                            )
                        }
                    }
                    Text(
                        "전체 보기 →",
                        style = AppType.BodySmall,
                        color = Concrete.TextSub,
                        modifier = Modifier
                            .clickable {
                                context.startActivity(
                                    Intent(context, HistoryActivity::class.java)
                                )
                            }
                            .padding(vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── 인라인 설정 카드 (접힘 기본, 아래로 펼침) ──
        InlineSettingsCard(store = store, refreshKey = refreshKey, onChanged = { refreshKey++ })

        // 디버그 빌드 한정: 감지 시뮬레이션 (에뮬레이터에서 BT 이벤트 재현 불가 대응)
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "[디버그] 하차 시뮬",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.clickable {
                        ParkingDetectionService.notifyCarDisconnected(context)
                    }
                )
                Text(
                    "[디버그] 재탑승 시뮬",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.clickable {
                        ParkingDetectionService.notifyCarConnected(context)
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showPhotoDialog && photoUri != null) {
        PhotoDialog(
            uriString = photoUri,
            onRetake = {
                showPhotoDialog = false
                val uri = PhotoStore.newPhotoUri(context, System.currentTimeMillis())
                pendingPhotoUri = uri
                cameraLauncher.launch(uri)
            },
            onDismiss = { showPhotoDialog = false }
        )
    }
}

/** 빠른 설정(미리보기): 설정에서 ★로 올린 항목만 표시 (v3.7) */
@Composable
private fun InlineSettingsCard(store: ParkingStore, refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    // v3.9.4: 펼침 상태 유지 — 기본 열림, 닫은 채로 나가면 닫힌 상태 그대로
    var expanded by remember { mutableStateOf(store.quickSettingsExpanded) }
    var version by remember { mutableIntStateOf(0) }
    val starred = remember(version, refreshKey) { store.starredSettings }
    val bump = { version++; onChanged() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
            .animateContentSize(animationSpec = tween(180))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                    store.quickSettingsExpanded = expanded
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("설정", style = AppType.SectionLabel, color = Concrete.TextDim)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "▴" else "▾",
                style = AppType.BodySmall,
                color = Concrete.TextDim
            )
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))

            if (ParkingStore.STAR_PRESSURE in starred) {
                InlineToggle("자동감지", store.pressureAutoDetect) {
                    store.pressureAutoDetect = it
                    bump()
                }
            }
            if (ParkingStore.STAR_CONFIRM in starred) {
                InlineToggle("등록 확인 카드", store.confirmBeforeDone) {
                    store.confirmBeforeDone = it
                    bump()
                }
            }
            if (ParkingStore.STAR_THEME in starred) {
                InlineValueRow("테마", inlineThemeLabel(store.themeMode)) {
                    // 탭할 때마다 시스템 → 다크 → 라이트 순환
                    val next = when (store.themeMode) {
                        ParkingStore.THEME_SYSTEM -> ParkingStore.THEME_DARK
                        ParkingStore.THEME_DARK -> ParkingStore.THEME_LIGHT
                        else -> ParkingStore.THEME_SYSTEM
                    }
                    store.themeMode = next
                    Concrete.apply(next, systemDark)
                    bump()
                }
            }
            if (ParkingStore.STAR_SHEET_MODE in starred) {
                InlineValueRow("바텀시트", inlineSheetModeLabel(store.defaultSheetMode)) {
                    val next = when (store.defaultSheetMode) {
                        ParkingStore.SHEET_FLOOR -> ParkingStore.SHEET_FLOOR_MEMO
                        ParkingStore.SHEET_FLOOR_MEMO -> ParkingStore.SHEET_FLOOR_PHOTO
                        else -> ParkingStore.SHEET_FLOOR
                    }
                    store.defaultSheetMode = next
                    bump()
                }
            }
            if (starred.isEmpty()) {
                Text(
                    "설정에서 ★를 누르면 여기에 올라와요",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            // 모든 설정 — 버튼 스타일 (v3.9.5)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .clickable {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("모든 설정  →", style = AppType.BodySmall, color = Concrete.TextBody)
            }
        }
    }
}

/** 값을 탭해서 순환시키는 빠른 설정 행 (테마·바텀시트 모드용) */
@Composable
private fun InlineValueRow(label: String, value: String, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppType.BodySmall, color = Concrete.TextBody)
        Spacer(Modifier.weight(1f))
        Text(value, style = AppType.BodySmall, color = Concrete.NeonLight)
    }
}

private fun inlineThemeLabel(mode: String): String = when (mode) {
    ParkingStore.THEME_DARK -> "다크"
    ParkingStore.THEME_LIGHT -> "라이트"
    else -> "시스템"
}

private fun inlineSheetModeLabel(mode: String): String = when (mode) {
    ParkingStore.SHEET_FLOOR -> "층수만"
    ParkingStore.SHEET_FLOOR_PHOTO -> "층 + 사진"
    else -> "층 + 메모"
}

@Composable
private fun InlineToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppType.BodySmall, color = Concrete.TextBody)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Concrete.Neon,
                checkedThumbColor = Concrete.NeonDeep,
                uncheckedTrackColor = Concrete.BgScreen,
                uncheckedThumbColor = Concrete.TextDim,
                uncheckedBorderColor = Concrete.Border
            )
        )
    }
}

private fun openFloorPicker(context: Context, manual: Boolean = false) {
    context.startActivity(
        Intent(context, FloorPickerActivity::class.java)
            .putExtra(FloorPickerActivity.EXTRA_MANUAL, manual)
    )
}

@Composable
private fun SmallConcreteButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.BodySmall, color = Concrete.TextBody)
    }
}

@Composable
private fun PhotoDialog(uriString: String, onRetake: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(uriString) { PhotoStore.loadPhoto(context, uriString) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Concrete.BgDeep, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "주차 사진",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("사진을 불러올 수 없어요", style = AppType.Body, color = Concrete.TextSub)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallConcreteButton("다시 찍기", Modifier.weight(1f), onRetake)
                SmallConcreteButton("닫기", Modifier.weight(1f), onDismiss)
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String =
    SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date(timestampMs))

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("M/d", Locale.KOREAN).format(Date(timestampMs))

private fun formatElapsed(startedAtMs: Long): String {
    val minutes = (System.currentTimeMillis() - startedAtMs) / 60_000L
    return when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 경과"
        else -> "${minutes / 60}시간 ${minutes % 60}분 경과"
    }
}
