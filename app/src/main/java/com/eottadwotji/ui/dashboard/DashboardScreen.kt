package com.eottadwotji.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.PhotoStore
import com.eottadwotji.data.UpdateChecker
import com.eottadwotji.detection.ParkingDetectionService
import com.eottadwotji.ui.components.BrandWordmark
import com.eottadwotji.ui.components.CircularGauge
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 대시보드 v5.0 — 홈 탭 (사용자 스케치 "대규모 수정").
 *
 *   헤더          워드마크 + 감지 LED + 마지막 차량 신호(진단)
 *   경과시간 바   "경과시간  03:14" — 카드 폭 전체, 가장 먼저 읽히는 정보
 *   타일 2개      [층수 엔진 버튼 → 다시 기록/수동 기록] [사진 또는 내 차 아이콘 → 사진 보기/찍기]
 *   지도 카드     [🔍][집][학교]… 칩 + 지도 (칩 탭 → 그 위치로, 검색 → 저장 위치/지오코딩)
 *
 * 위치 편집·설정은 하단 탭(위치관리·설정)으로 나갔다 — 이 화면엔 결정 하나씩만 남긴다.
 * 형광은 엔진 버튼 링·감지 LED·선택 칩 테두리 (절대 규칙 3).
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
    val lots = remember(refreshKey) { store.profiles().sortedBy { it.name } }

    // 지도 칩 선택 — 기본은 주차 중 매칭된 위치, 사용자가 칩을 누르면 그 위치
    var selectedLotId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusLotId = selectedLotId ?: currentLot?.id

    // 내 위치 — 주차 중 + 차 좌표가 있을 때, 화면 복귀마다 1회만 (저장하지 않음)
    var myCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(resumeKey, isParked, carCoords) {
        if (isParked && carCoords != null) {
            fetchMyLocationOnce(context) { myCoords = it }
        } else {
            myCoords = null
        }
    }

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
    val takePhoto = {
        val uri = PhotoStore.newPhotoUri(context, System.currentTimeMillis())
        pendingPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .statusBarsPadding() // 하단은 탭바(MainShell)가 맡는다 — 절대 규칙 8
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── 헤더: 워드마크 + 감지 LED ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandWordmark(fontSizeSp = 27f)
            Spacer(Modifier.weight(1f))
            DetectionBadge(detecting)
        }
        // 마지막 차량 BT 신호 — 리시버가 실제로 깨어나는지 확인하는 진단 줄 (한 줄 고정)
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

        // ── 경과시간 바 ──
        ElapsedBar(parked = isParked, startedAtMs = startedAt, detecting = detecting)

        Spacer(Modifier.height(10.dp))

        // ── 타일 2개: [층수 엔진 버튼] [사진 / 내 차 아이콘] ──
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FloorTile(
                floor = floor,
                parked = isParked,
                modifier = Modifier.weight(1f),
                onPress = { openFloorPicker(context, manual = !isParked) }
            )
            PhotoTile(
                photoUri = if (isParked) photoUri else null,
                parked = isParked,
                appIconCar = store.appIconCar,
                appIconColor = store.appIconColor,
                modifier = Modifier.weight(1f),
                onPress = {
                    when {
                        !isParked -> Unit // 주차 전엔 사진 슬롯이 비어 있다 — 아이콘만
                        photoUri != null -> showPhotoDialog = true
                        else -> takePhoto()
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── 지도 카드: 위치 칩(검색 포함) + 지도 ──
        CarMapCard(
            lots = lots,
            selectedLotId = focusLotId,
            carCoords = carCoords,
            myCoords = myCoords,
            parked = isParked,
            onLotSelect = { selectedLotId = it.id }
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
                takePhoto()
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

// ── 경과시간 바 ────────────────────────────────────────────────

/**
 * 경과시간 — 카드 폭 전체를 쓰는 한 줄. 스케치의 맨 위 요소.
 * 왼쪽 라벨, 오른쪽 트립미터 숫자(H:MM). 주차 전엔 "--:--"와 다음 행동 힌트.
 */
@Composable
private fun ElapsedBar(parked: Boolean, startedAtMs: Long, detecting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (parked) "경과시간" else "지금 주차 중이 아니에요",
                style = if (parked) AppType.SectionLabel else AppType.Body,
                color = if (parked) Concrete.TextSub else Concrete.TextSub
            )
            if (parked) {
                Text(
                    "주차 ${formatTime(startedAtMs)}",
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
            } else {
                Text(
                    if (detecting) "차에서 내리면 자동으로 물어볼게요"
                    else "왼쪽 엔진 버튼을 눌러 수동으로 기록해요",
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
            }
        }
        // 숫자 = 머스크 버건디 — 이 화면에서 포인트색은 여기 한 곳 (면적 비율 3)
        Text(
            if (parked) formatElapsedClock(startedAtMs) else "--:--",
            style = AppType.GaugeFloor.copy(fontSize = 34.sp),
            color = if (parked) Concrete.Accent else Concrete.TextDim,
            maxLines = 1
        )
    }
}

// ── 타일 ──────────────────────────────────────────────────────

/** 층수 타일 — 정사각 카드 안에 엔진 스타트 버튼. 누르면 다시 기록(주차 전엔 수동 기록) */
@Composable
private fun FloorTile(
    floor: String?,
    parked: Boolean,
    modifier: Modifier = Modifier,
    onPress: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(Concrete.BgDeep, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        CircularGauge(floor = floor, parked = parked, size = 124.dp, onPress = onPress)
        Text(
            if (parked) "탭해서 다시 기록" else "탭해서 수동 기록",
            style = AppType.Micro,
            color = Concrete.TextDim,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}

/**
 * 사진 타일 — 주차 사진이 있으면 사진을 꽉 채우고, 없으면 내 차(앱 아이콘)를 보여준다.
 * 탭: 사진 있음 → 크게 보기 / 없음(주차 중) → 카메라. 주차 전엔 아이콘만.
 */
@Composable
private fun PhotoTile(
    photoUri: String?,
    parked: Boolean,
    appIconCar: String?,
    appIconColor: String?,
    modifier: Modifier = Modifier,
    onPress: () -> Unit
) {
    val context = LocalContext.current
    val photo: Bitmap? = remember(photoUri) {
        photoUri?.let { PhotoStore.loadPhoto(context, it) }
    }
    val iconBitmap = remember(appIconCar, appIconColor) { loadAppIconBitmap(context, appIconCar, appIconColor) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Concrete.BgDeep)
            .clickable(onClick = onPress)
    ) {
        if (photo != null) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = "주차 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "사진 보기",
                style = AppType.Micro,
                color = Concrete.TextMain,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else {
            // 내 차 — 원판 + 앱 아이콘 (주차 중이면 링 점등)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 10.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Concrete.BgScreen, Concrete.BgPanel),
                            radius = 150f
                        )
                    )
                    .border(
                        2.dp,
                        if (parked) Concrete.Neon else Concrete.Border.copy(alpha = 0.5f),
                        CircleShape
                    ),
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
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                if (parked) "탭해서 사진 찍기" else "내 차",
                style = AppType.Micro,
                color = Concrete.TextDim,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }
    }
}

/**
 * 내 차 아이콘 비트맵: 설정에서 고른 차종·색 전경(ic_fg_*) → 없으면 런처에 깔린 앱 아이콘.
 * 어댑티브 아이콘은 가운데 72/108만 콘텐츠라 호출부에서 1.48배로 키운다.
 */
private fun loadAppIconBitmap(context: Context, car: String?, color: String?): Bitmap? {
    val resId = if (car != null && color != null) {
        context.resources.getIdentifier("ic_fg_${car}_$color", "drawable", context.packageName)
    } else {
        0
    }
    val drawable = if (resId != 0) {
        ContextCompat.getDrawable(context, resId)
    } else {
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull()
    }
    return drawable?.let { runCatching { it.toBitmap(width = 288, height = 288) }.getOrNull() }
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
            .height(48.dp)
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

/** 진단 줄용 짧은 표기 "9/3 16:54" — 한 줄에 들어가야 한다 */
private fun formatSignalTime(timestampMs: Long): String =
    SimpleDateFormat("M/d H:mm", Locale.KOREAN).format(Date(timestampMs))

/** 트립미터 표기: H:MM (24시간 넘으면 시간이 그대로 커진다 — 26:19) */
private fun formatElapsedClock(startedAtMs: Long): String {
    val minutes = ((System.currentTimeMillis() - startedAtMs) / 60_000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60)
}
