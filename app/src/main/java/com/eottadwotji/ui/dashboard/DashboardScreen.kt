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
import com.eottadwotji.detection.ParkingDetectionService
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
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            refreshKey++ // 경과 시간 갱신
        }
    }

    val isParked = remember(refreshKey) { store.hasActiveParking() }
    val floor = remember(refreshKey) { store.currentFloor() }
    val zone = remember(refreshKey) { store.currentZone() }
    val memo = remember(refreshKey) { store.currentMemo() }
    val lotName = remember(refreshKey) { store.currentLot()?.name }
    val startedAt = remember(refreshKey) { store.parkingStartedAt() }
    val photoUri = remember(refreshKey) { store.photoUri }
    val detecting = remember(refreshKey) { store.onboardingDone && store.myCarAddress != null }

    // 최근 주차 기록 (Room)
    val recentFlow: Flow<List<ParkingRecord>> = remember {
        runCatching { HistoryDb.get(context).dao().recent(3) }
            .getOrDefault(flowOf(emptyList()))
    }
    val recent by recentFlow.collectAsState(initial = emptyList())

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
            Text("내차위치", style = AppType.Brand, color = Concrete.TextMain)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (detecting) Concrete.Neon else Concrete.TextDim,
                            CircleShape
                        )
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    if (detecting) "감지 중" else "수동 모드",
                    style = AppType.Hint,
                    color = if (detecting) Concrete.NeonLight else Concrete.TextDim
                )
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
                CircularGauge(floor = floor, parked = isParked)
                Spacer(Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (isParked) {
                        Text(
                            lotName ?: "주차 위치 저장됨",
                            style = AppType.Body,
                            color = Concrete.TextMain
                        )
                        val detail = listOfNotNull(floor, zone, memo).joinToString(" · ")
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

        // ── 최근 주차 카드 ──
        if (recent.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                    .clickable {
                        context.startActivity(Intent(context, HistoryActivity::class.java))
                    }
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row {
                    Text("최근 주차", style = AppType.SectionLabel, color = Concrete.TextDim)
                    Spacer(Modifier.weight(1f))
                    Text("전체 보기 ›", style = AppType.Hint, color = Concrete.TextDim)
                }
                recent.forEach { record ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── 인라인 설정 카드 (접힘 기본, 아래로 펼침) ──
        InlineSettingsCard(store = store, onChanged = { refreshKey++ })

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

/** 자주 쓰는 토글 3개만 인라인, 나머지는 "모든 설정"으로 (DESIGN v2) */
@Composable
private fun InlineSettingsCard(store: ParkingStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pressureOn by remember { mutableStateOf(store.pressureAutoDetect) }
    var widgetAndBar by remember { mutableStateOf(store.displayMode == ParkingStore.DISPLAY_BOTH) }
    var autoClear by remember { mutableStateOf(store.autoClearOnDeparture) }

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
                .clickable { expanded = !expanded },
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
            InlineToggle("자동감지 — 기압 층 추천 (베타)", pressureOn) {
                pressureOn = it
                store.pressureAutoDetect = it
                onChanged()
            }
            InlineToggle("홈 위젯 + 상태바", widgetAndBar) {
                widgetAndBar = it
                store.displayMode =
                    if (it) ParkingStore.DISPLAY_BOTH else ParkingStore.DISPLAY_STATUSBAR
                onChanged()
            }
            InlineToggle("출차 시 자동 삭제", autoClear) {
                autoClear = it
                store.autoClearOnDeparture = it
                onChanged()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "모든 설정 →",
                style = AppType.BodySmall,
                color = Concrete.TextSub,
                modifier = Modifier
                    .clickable {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                    .padding(vertical = 6.dp)
            )
        }
    }
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
