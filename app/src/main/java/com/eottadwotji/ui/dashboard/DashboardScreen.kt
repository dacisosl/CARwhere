package com.eottadwotji.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eottadwotji.BuildConfig
import com.eottadwotji.R
import com.eottadwotji.data.HistoryDb
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingRecord
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.PhotoStore
import com.eottadwotji.data.UpdateChecker
import com.eottadwotji.detection.ParkingDetectionService
import com.eottadwotji.ui.components.BrandWordmark
import com.eottadwotji.ui.components.CircularGauge
import com.eottadwotji.ui.components.LotEditModal
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import com.eottadwotji.ui.history.HistoryActivity
import com.eottadwotji.ui.settings.SettingsActivity
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 대시보드 v4.2 — 사용자 스케치 기반 3카드 구조.
 *
 *   헤더        브랜드 + 감지 LED + 마지막 차량 신호(진단)
 *   ① 주차 카드  [엔진 버튼: 층수 → 다시 기록] [내 차 아이콘]  / 위치 한 줄 / [사진 보기] [+경과시간]
 *   ② 지도 카드  저장된 위치 칩(집·학교…) / GPS 지도: 차 위치 + 내 위치에서 화살표
 *   ③ 설정 카드  즐겨찾기 행(항상 표시) + "더 보기" 아코디언 → 전체 빠른 설정 + 모든 설정
 *
 * 형광은 엔진 버튼 링·차 아이콘 링·감지 LED로 제한 (절대 규칙 3).
 * 내 위치는 화면 복귀 시 1회만 조회 (절대 규칙 6 — 상시 추적 금지).
 */
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }

    // 화면 복귀·3초 경과마다 상태 다시 읽기 (SharedPreferences 기반)
    var refreshKey by remember { mutableIntStateOf(0) }
    // 복귀 횟수 — 내 위치 1회 조회의 트리거 (3초 주기 refreshKey와 분리: 폴링 금지)
    var resumeKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                resumeKey++
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
    val currentLot = remember(refreshKey) { store.currentLot() }
    val startedAt = remember(refreshKey) { store.parkingStartedAt() }
    val photoUri = remember(refreshKey) { store.photoUri }
    val carCoords = remember(refreshKey) { if (store.hasActiveParking()) store.coordinates() else null }
    val detecting = remember(refreshKey) { store.onboardingDone && store.myCarAddress != null }
    val lastCarEventAt = remember(refreshKey) { store.lastCarEventAt() }
    val lastCarEventConnected = remember(refreshKey) { store.lastCarEventConnected() }

    // 내 위치 — 주차 중 + 차 좌표가 있을 때, 화면 복귀마다 1회만 (저장하지 않음)
    var myCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(resumeKey, isParked, carCoords) {
        if (isParked && carCoords != null) {
            fetchMyLocationOnce(context) { myCoords = it }
        } else {
            myCoords = null
        }
    }

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

    // 위치(프로필) 편집 모달 상태 — 지도 카드 칩에서 진입
    var editingLot by remember { mutableStateOf<ParkingLotProfile?>(null) }
    var creatingLot by remember { mutableStateOf(false) }
    var lotsVersion by remember { mutableIntStateOf(0) }
    val lots = remember(lotsVersion, refreshKey) { store.profiles().sortedBy { it.name } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .systemBarsPadding() // v2 절대 규칙 8: 시스템 바 침범 금지
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── 헤더: 브랜드(레터스페이싱) + 감지 상태 배지 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandWordmark(fontSizeSp = 27f)
            Spacer(Modifier.weight(1f))
            DetectionBadge(detecting)
        }
        // 마지막 차량 BT 신호 — 리시버가 실제로 깨어나는지 확인하는 진단 줄.
        // v4.3: 두 줄로 넘치던 문구를 한 줄에 들어가는 길이로 줄이고 10sp로 (maxLines=1)
        if (detecting) {
            Text(
                if (lastCarEventAt > 0L)
                    "차량 신호 ${formatSignalTime(lastCarEventAt)} " +
                        if (lastCarEventConnected) "연결" else "끊김"
                else "차량 신호 기록 없음",
                style = AppType.Micro,
                color = Concrete.TextDim,
                maxLines = 1,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // ── 업데이트 배너 (새 버전이 있을 때만) ──
        update?.let { u ->
            Spacer(Modifier.height(12.dp))
            BannerRow(
                text = if (downloading) "다운로드 중 — 완료되면 설치 화면이 떠요"
                else "새 버전 ${u.label} 나왔어요",
                action = if (downloading) null else "업데이트",
                enabled = !downloading
            ) {
                downloading = true
                UpdateChecker.downloadAndInstall(context, u)
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
            BannerRow(
                text = "감지가 끊기지 않게 배터리 예외를 허용해주세요",
                action = "허용"
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── ① 주차 카드 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            // v4.3: 엔진 버튼을 왼쪽 끝으로 붙이고 126dp로 확대, 내 차 배지는 오른쪽 끝
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 엔진 스타트 버튼: 누르면 기록 시트 (주차 중=다시 기록, 아니면=수동 기록)
                CircularGauge(
                    floor = floor,
                    parked = isParked,
                    size = 126.dp,
                    onPress = { openFloorPicker(context, manual = !isParked) }
                )
                // 내 차 — 실제 앱 아이콘을 그대로 박는다 (설정에서 차종·색을 고르면 그 아이콘)
                MyCarBadge(
                    car = store.appIconCar,
                    color = store.appIconColor,
                    parked = isParked,
                    size = 104.dp,
                    onPress = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                )
            }

            // 주차 중일 때 장소 문구는 두지 않는다 (v4.3) —
            // 바로 아래 지도 카드의 위치 칩과 지도가 같은 정보를 더 잘 보여준다.
            if (!isParked) {
                Spacer(Modifier.height(14.dp))
                Text("지금 주차 중이 아니에요", style = AppType.Body, color = Concrete.TextSub)
                Text(
                    if (detecting) "차에서 내리면 자동으로 물어볼게요"
                    else "수동 모드 — 엔진 버튼을 눌러 기록해요",
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isParked) {
                    SmallConcreteButton(
                        label = if (photoUri != null) "사진 보기" else "사진 찍기",
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
                    // 경과 시간만 크게 — 주차 시각 캡션은 뺐다 (v4.3)
                    ElapsedChip(
                        text = formatElapsedClock(startedAt),
                        lit = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    SmallConcreteButton("수동 기록", Modifier.weight(1f)) {
                        openFloorPicker(context, manual = true)
                    }
                    ElapsedChip(text = "--:--", lit = false, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── ② 지도 카드: 저장된 위치 칩 + 차 위치 지도 ──
        CarMapCard(
            lots = lots,
            currentLotId = currentLot?.id,
            carCoords = carCoords,
            myCoords = myCoords,
            parked = isParked,
            onLotTap = { editingLot = it },
            onAddLot = { creatingLot = true }
        )
        if (editingLot != null || creatingLot) {
            LotEditModal(
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

        Spacer(Modifier.height(12.dp))

        // ── 최근 주차 카드 (표시 여부는 설정 — v3.9.4, 기본 숨김) ──
        val showRecentCard = remember(refreshKey) { store.showRecentCard }
        if (showRecentCard && recent.isNotEmpty()) {
            RecentCard(recent)
            Spacer(Modifier.height(12.dp))
        }

        // ── ③ 설정 카드: 즐겨찾기 + 아코디언 ──
        SettingsCard(
            store = store,
            refreshKey = refreshKey,
            detecting = detecting,
            batteryExempt = batteryExempt,
            onChanged = { refreshKey++ }
        )

        // 디버그 빌드 한정: 감지 시뮬레이션 (에뮬레이터에서 BT 이벤트 재현 불가 대응)
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "[디버그] 하차 시뮬",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.clickable {
                        store.recordCarEvent(connected = false)
                        ParkingDetectionService.notifyCarDisconnected(context)
                    }
                )
                Text(
                    "[디버그] 재탑승 시뮬",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.clickable {
                        store.recordCarEvent(connected = true)
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

// ── 헤더 ─────────────────────────────────────────────────────

/** 계기판 인디케이터 LED: 감지 중이면 글로우 점등 (v3.9) */
@Composable
private fun DetectionBadge(detecting: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            if (detecting) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            Brush.radialGradient(
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
                    .background(if (detecting) Concrete.Neon else Concrete.TextDim, CircleShape)
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

@Composable
private fun BannerRow(
    text: String,
    action: String?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = AppType.BodySmall, color = Concrete.TextBody, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.size(8.dp))
            Text(action, style = AppType.BodySmall, color = Concrete.NeonLight)
        }
    }
}

// ── ① 주차 카드 부품 ──────────────────────────────────────────

/**
 * 내 차 배지 — 실제 앱 아이콘을 원판에 박는다 (v4.3).
 *
 * 설정에서 차종·색을 골랐으면 그 전경 에셋(ic_fg_*)을, 고르지 않았으면 런처에 깔린
 * 앱 아이콘 자체(PackageManager)를 그린다. 어댑티브 아이콘은 108dp 캔버스 중 가운데
 * 72dp만 콘텐츠라 1.48배로 키워 원을 채운다. 링은 주차 중일 때만 점등.
 */
@Composable
private fun MyCarBadge(
    car: String?,
    color: String?,
    parked: Boolean,
    size: Dp,
    onPress: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(car, color) {
        val resId = if (car != null && color != null) {
            context.resources.getIdentifier("ic_fg_${car}_$color", "drawable", context.packageName)
        } else {
            0
        }
        val drawable = if (resId != 0) {
            ContextCompat.getDrawable(context, resId)
        } else {
            // 런처에 실제로 깔린 앱 아이콘 (어댑티브 배경+전경 합성)
            runCatching { context.packageManager.getApplicationIcon(context.packageName) }
                .getOrNull()
        }
        drawable?.let {
            runCatching { it.toBitmap(width = 288, height = 288) }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF373733), Color(0xFF1F1F1D)),
                    radius = 160f
                )
            )
            .border(
                2.dp,
                if (parked) Concrete.Neon else Concrete.Border.copy(alpha = 0.5f),
                CircleShape
            )
            .clickable(onClick = onPress),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap.asImageBitmap(),
                contentDescription = "내 차",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.48f
                        scaleY = 1.48f
                        alpha = if (parked) 1f else 0.6f
                    }
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_car),
                contentDescription = "내 차",
                tint = if (parked) Concrete.Neon else Concrete.TextDim,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

/** 경과 시간 칩 — 트립미터 표기 하나만 크게 (v4.3: 주차 시각 캡션 제거) */
@Composable
private fun ElapsedChip(text: String, lit: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = AppType.GaugeFloor.copy(fontSize = 25.sp),
            color = if (lit) Concrete.TextMain else Concrete.TextDim,
            maxLines = 1
        )
    }
}

// ── 최근 주차 카드 (v3.9 유지) ─────────────────────────────────

@Composable
private fun RecentCard(recent: List<ParkingRecord>) {
    val context = LocalContext.current
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
            Text("최근 주차", style = AppType.SectionLabel, color = Concrete.TextSub)
            Spacer(Modifier.weight(1f))
            Text(if (recentExpanded) "▴" else "▾", style = AppType.BodySmall, color = Concrete.TextDim)
        }
        if (recentExpanded) {
            Spacer(Modifier.height(10.dp))
            recent.forEach { record ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatDate(record.endedAt), style = AppType.BodySmall, color = Concrete.TextDim)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        record.lotName ?: "이름 없는 주차장",
                        style = AppType.BodySmall,
                        color = Concrete.TextBody
                    )
                    Spacer(Modifier.weight(1f))
                    Text(record.floor ?: "—", style = AppType.BodySmall, color = Concrete.TextSub)
                }
            }
            Text(
                "전체 보기 →",
                style = AppType.BodySmall,
                color = Concrete.TextSub,
                modifier = Modifier
                    .clickable { context.startActivity(Intent(context, HistoryActivity::class.java)) }
                    .padding(vertical = 6.dp)
            )
        }
    }
}

// ── ③ 설정 카드 ────────────────────────────────────────────────

/**
 * 즐겨찾기(★) 행은 항상 보이고, 왼쪽 아래 "더 보기"를 누르면 아코디언이 아래로 쏟아진다.
 * 아코디언: ★ 안 붙은 빠른 설정 + 감지 상태(내 차·배터리 예외) + 최근 카드 토글 + 모든 설정.
 * 즐겨찾기가 많아지면 카드가 길어지고 화면 전체가 스크롤된다 (별도 내부 스크롤 없음).
 */
@Composable
private fun SettingsCard(
    store: ParkingStore,
    refreshKey: Int,
    detecting: Boolean,
    batteryExempt: Boolean,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var expanded by remember { mutableStateOf(store.quickSettingsExpanded) }
    var version by remember { mutableIntStateOf(0) }
    val starred = remember(version, refreshKey) { store.starredSettings }
    val bump = { version++; onChanged() }

    // 빠른 설정 항목을 한 곳에서 정의 — 즐겨찾기/아코디언 양쪽이 같은 행을 쓴다
    val rowFor: @Composable (String) -> Unit = { key ->
        when (key) {
            ParkingStore.STAR_PRESSURE -> InlineToggle("자동감지", store.pressureAutoDetect) {
                store.pressureAutoDetect = it
                bump()
            }
            ParkingStore.STAR_CONFIRM -> InlineToggle("등록 확인 카드", store.confirmBeforeDone) {
                store.confirmBeforeDone = it
                bump()
            }
            ParkingStore.STAR_THEME -> InlineValueRow("테마", inlineThemeLabel(store.themeMode)) {
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
            ParkingStore.STAR_SHEET_MODE -> InlineValueRow(
                "바텀시트", inlineSheetModeLabel(store.defaultSheetMode)
            ) {
                val next = when (store.defaultSheetMode) {
                    ParkingStore.SHEET_FLOOR -> ParkingStore.SHEET_FLOOR_MEMO
                    ParkingStore.SHEET_FLOOR_MEMO -> ParkingStore.SHEET_FLOOR_PHOTO
                    else -> ParkingStore.SHEET_FLOOR
                }
                store.defaultSheetMode = next
                bump()
            }
            else -> Unit
        }
    }
    val allKeys = listOf(
        ParkingStore.STAR_PRESSURE, ParkingStore.STAR_SHEET_MODE,
        ParkingStore.STAR_CONFIRM, ParkingStore.STAR_THEME
    )
    val favorites = allKeys.filter { it in starred }
    val others = allKeys.filter { it !in starred }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
            .animateContentSize(animationSpec = tween(180))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("설정", style = AppType.SectionLabel, color = Concrete.TextSub)
            Spacer(Modifier.weight(1f))
            Text(
                "모든 설정 →",
                style = AppType.BodySmall,
                color = Concrete.NeonLight,
                modifier = Modifier
                    .clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            )
        }

        // 즐겨찾기 — 항상 표시
        if (favorites.isEmpty()) {
            Text(
                "설정에서 ★를 누르면 여기에 올라와요",
                style = AppType.Hint,
                color = Concrete.TextDim,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Spacer(Modifier.height(4.dp))
            favorites.forEach { rowFor(it) }
        }

        // 아코디언 — 왼쪽 아래 "더 보기"
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .clickable {
                    expanded = !expanded
                    store.quickSettingsExpanded = expanded
                }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (expanded) "▾" else "▸",
                style = AppType.BodySmall,
                color = Concrete.TextSub
            )
            Spacer(Modifier.size(6.dp))
            Text(
                if (expanded) "접기" else "더 보기",
                style = AppType.BodySmall,
                color = Concrete.TextSub
            )
        }

        if (expanded) {
            Spacer(Modifier.height(4.dp))
            others.forEach { rowFor(it) }
            InlineToggle("최근 주차 카드", store.showRecentCard) {
                store.showRecentCard = it
                bump()
            }
            // 감지 상태 요약 — 실기기 확인용
            InlineValueRow(
                "내 차 블루투스",
                if (detecting) (store.myCarName ?: "지정됨") else "미지정"
            ) { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            InlineValueRow(
                "배터리 예외",
                if (batteryExempt) "허용됨" else "필요"
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
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

// ── 공용 ──────────────────────────────────────────────────────

private fun openFloorPicker(context: Context, manual: Boolean = false) {
    context.startActivity(
        Intent(context, FloorPickerActivity::class.java)
            .putExtra(FloorPickerActivity.EXTRA_MANUAL, manual)
    )
}

/**
 * 내 위치 1회 조회 (절대 규칙 6). 현재 위치 → 실패하면 마지막 위치 폴백.
 * 권한이 없으면 조용히 건너뛴다 — 지도 카드는 차 위치만 보여준다.
 */
private fun fetchMyLocationOnce(context: Context, onResult: (Pair<Double, Double>) -> Unit) {
    val granted = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) return

    runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fallbackToLast = {
            runCatching {
                client.lastLocation.addOnSuccessListener { last ->
                    last?.let { onResult(it.latitude to it.longitude) }
                }
            }
        }
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) onResult(location.latitude to location.longitude)
                else fallbackToLast()
            }
            .addOnFailureListener { fallbackToLast() }
    }
}

@Composable
private fun SmallConcreteButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
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

/** 진단 줄용 짧은 표기 "9/3 16:54" — 한 줄에 들어가야 한다 (v4.3) */
private fun formatSignalTime(timestampMs: Long): String =
    SimpleDateFormat("M/d H:mm", Locale.KOREAN).format(Date(timestampMs))

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("M/d", Locale.KOREAN).format(Date(timestampMs))

/** 트립미터 표기: +HH:MM (24시간 넘으면 시간이 그대로 커진다 — +26:19) */
private fun formatElapsedClock(startedAtMs: Long): String {
    val minutes = ((System.currentTimeMillis() - startedAtMs) / 60_000L).coerceAtLeast(0L)
    return String.format(Locale.US, "+%02d:%02d", minutes / 60, minutes % 60)
}
