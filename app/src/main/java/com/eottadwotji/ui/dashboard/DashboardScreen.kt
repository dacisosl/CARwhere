package com.eottadwotji.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.eottadwotji.detection.ParkingNotification
import com.eottadwotji.ui.components.BrandWordmark
import com.eottadwotji.ui.floorpicker.FloorPickerActivity
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.appCard
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 대시보드 v5.2 — 홈 탭.
 *
 *   헤더        [P] 어따뒀지 + 감지 LED + 차량 신호 진단 한 줄
 *   경과시간    "경과시간 · 주차 13:54"  ……  3:14 (버건디 숫자)
 *   타일 2개    [층 표지판 → 다시 기록]  [주차된 차 / 사진 → 카메라·크게 보기]
 *   지도 카드   남는 높이를 전부 채운다 (기종마다 다른 화면 높이에 맞춤)
 *   맨 아래     "테스트: 하차 시뮬" 작은 링크
 *
 * v5.2 변경 (사용자 피드백):
 * - 스크롤 제거 → 지도 카드가 weight(1f)로 남는 높이를 먹는다 (지도 밑 여백 없음)
 * - 원형 엔진 버튼 폐기 → 층수 글자만 (v5.3: 필드·표지판 바탕 없이 시그니처 색 숫자)
 * - 모든 카드 Modifier.appCard() — 테두리+그림자로 바탕과 확실히 구분
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

    // v5.4: Android 16에서 "실시간 업데이트"가 꺼져 있으면 잠금화면 칩이 안 뜬다 — 주차 중일 때만 안내
    val liveUpdates = remember(refreshKey) { ParkingNotification.liveUpdatesState(context) }
    val batteryExempt = remember(refreshKey) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .statusBarsPadding() // 하단은 탭바(MainShell)가 맡는다 — 절대 규칙 8
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── 헤더: 워드마크 + 감지 LED ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandWordmark(fontSizeSp = 24f)
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
            Spacer(Modifier.height(10.dp))
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
        if (detecting && !batteryExempt) {
            Spacer(Modifier.height(10.dp))
            BannerRow(text = "감지가 끊기지 않게 배터리 예외를 허용해주세요", action = "허용") {
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

        // ── 잠금화면 Now Bar(Live Updates) 꺼짐 안내 — Android 16, 주차 중일 때만 (v5.4) ──
        if (isParked && liveUpdates == ParkingNotification.LiveUpdatesState.DISABLED) {
            Spacer(Modifier.height(10.dp))
            BannerRow(text = "잠금화면 Now Bar 표시가 꺼져 있어요 — 실시간 업데이트를 켜주세요", action = "켜기") {
                ParkingNotification.openLiveUpdatesSettings(context)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 경과시간 바 ──
        ElapsedBar(parked = isParked, startedAtMs = startedAt, detecting = detecting)

        Spacer(Modifier.height(10.dp))

        // ── 타일 2개: [층 표지판] [주차된 차 / 사진] ──
        // 높이를 고정해 아래 지도 카드가 먹을 공간이 기종마다 예측 가능하게 남는다
        Row(
            modifier = Modifier.fillMaxWidth().height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 폭 배분 0.62 : 1 — 층수는 글자뿐이라 좁아도 되고, 사진은 가로가 넓어야
            // 주차 구획(천장 조명·구획선)이 잘리지 않는다 (사진 비율 약 1.23:1)
            FloorTile(
                floor = floor,
                parked = isParked,
                modifier = Modifier.weight(0.62f).fillMaxHeight(),
                onPress = { openFloorPicker(context, manual = !isParked) }
            )
            PhotoTile(
                photoUri = if (isParked) photoUri else null,
                parked = isParked,
                appIconCar = store.appIconCar,
                appIconColor = store.appIconColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onPress = {
                    when {
                        !isParked -> Unit // 주차 전엔 사진 슬롯이 비어 있다 — 내 차만 보여준다
                        photoUri != null -> showPhotoDialog = true
                        else -> takePhoto()
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── 지도 카드: 남는 높이를 전부 채운다 (v5.2 반응형) ──
        CarMapCard(
            lots = lots,
            selectedLotId = focusLotId,
            carCoords = carCoords,
            myCoords = myCoords,
            parked = isParked,
            onLotSelect = { selectedLotId = it.id },
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 140.dp)
        )

        // ── 테스트: 실기기에서 감지 경로를 그대로 태워 본다 (릴리스에도 노출 — v5.2) ──
        Text(
            "테스트: 하차 시뮬 (기록 시트 바로 띄우기)",
            style = AppType.Micro,
            color = Concrete.TextDim,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    store.recordCarEvent(connected = false)
                    ParkingDetectionService.notifyCarDisconnected(context)
                }
                .padding(top = 8.dp, bottom = 2.dp)
        )
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

/** 감지 인디케이터: 감지 중이면 그린 점 + 옅은 글로우 */
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
                                    Concrete.Neon.copy(alpha = 0.35f),
                                    Concrete.Neon.copy(alpha = 0f)
                                )
                            ),
                            CircleShape
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (detecting) Concrete.Neon else Concrete.TextDim, CircleShape)
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            if (detecting) "감지 중" else "수동 모드",
            style = AppType.Hint,
            color = if (detecting) Concrete.Neon else Concrete.TextDim
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
            .appCard(12.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = AppType.BodySmall, color = Concrete.TextBody, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.size(8.dp))
            Text(action, style = AppType.BodySmall, color = Concrete.Neon)
        }
    }
}

// ── 경과시간 바 ────────────────────────────────────────────────

/**
 * 경과시간 — 카드 폭 전체를 쓰는 한 줄.
 * 왼쪽 라벨·주차 시각, 오른쪽 H:MM 숫자(버건디 — 이 화면의 포인트색 한 곳).
 */
@Composable
private fun ElapsedBar(parked: Boolean, startedAtMs: Long, detecting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (parked) "경과시간" else "지금 주차 중이 아니에요",
                style = if (parked) AppType.SectionLabel else AppType.Body,
                color = Concrete.TextSub
            )
            Text(
                when {
                    parked -> "주차 ${formatTime(startedAtMs)}"
                    detecting -> "차에서 내리면 자동으로 물어볼게요"
                    else -> "왼쪽 층 표지판을 눌러 수동으로 기록해요"
                },
                style = AppType.Hint,
                color = Concrete.TextDim
            )
        }
        Text(
            if (parked) formatElapsedClock(startedAtMs) else "--:--",
            style = AppType.Sign.copy(fontSize = 34.sp),
            color = if (parked) Concrete.Accent else Concrete.TextDim,
            maxLines = 1
        )
    }
}

// ── 타일 ──────────────────────────────────────────────────────

/**
 * 층 표시 (v5.3) — 카드도 표지판 바탕도 없이 "층수 글자만".
 *
 * v5.2는 흰 카드 안에 형광 층별 색 표지판을 넣었는데, 필드가 겹쳐 보이고 색이 튄다는
 * 피드백을 받았다. 이제 바탕 위에 시그니처 딥 파인 그린 숫자만 크게 놓는다 —
 * 화면에서 가장 큰 글자이므로 배경 없이도 가장 먼저 읽힌다.
 * 누르면 다시 기록(주차 전엔 수동 기록).
 */
@Composable
private fun FloorTile(
    floor: String?,
    parked: Boolean,
    modifier: Modifier = Modifier,
    onPress: () -> Unit
) {
    Box(
        modifier = modifier.clickable(onClick = onPress),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val label = if (parked) (floor ?: "P") else "—"
            Text(
                label,
                // "B12"처럼 세 글자면 줄여서 좁은 칸을 넘지 않게
                style = AppType.Sign.copy(fontSize = if (label.length >= 3) 46.sp else 62.sp),
                color = if (parked) Concrete.Neon else Concrete.TextDim,
                maxLines = 1,
                softWrap = false
            )
            Text(
                if (parked) "탭해서 다시 기록" else "탭해서 수동 기록",
                style = AppType.Micro,
                color = Concrete.TextDim,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 사진 타일 — 주차 사진이 있으면 사진을 꽉 채우고, 없으면 "주차된 내 차" 사진을 보여준다.
 *
 * v5.3: 합성 주차 구획(그라디언트 + 언더글로우)을 버리고 실제 사진(car_*)을 그대로 쓴다.
 * 원본 아트에서 "주차" 글자 띠를 빼고 라임 액센트를 딥 파인 그린으로 바꾼 이미지라
 * 천장 조명·주차 구획선이 그대로 살아 있다 — 합성 배경이 필요없다.
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
    val carBitmap = remember(appIconCar, appIconColor) {
        loadCarBitmap(context, appIconCar, appIconColor)
    }

    Box(
        modifier = modifier
            .appCard()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPress)
    ) {
        if (photo != null) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = "주차 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text("사진 보기", style = AppType.Micro, color = Color.White)
            }
        } else {
            // 주차된 내 차 사진 — Fit으로 사진을 자르지 않는다 (v5.3.1).
            // Crop은 좌우를 잘라 차 앞부분만 확대돼 보였다. 여백은 주차장 바닥 톤으로 채워
            // 사진 전체(천장 조명 · 구획선 · 차)가 한눈에 들어오게 한다.
            if (carBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF17191D)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = carBitmap.asImageBitmap(),
                        contentDescription = "내 차",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (parked) 1f else 0.72f }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF17191D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_car),
                        contentDescription = "내 차",
                        tint = if (parked) Concrete.Neon else Color(0xFF6B7380),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (parked) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    if (parked) "탭해서 사진 찍기" else "내 차",
                    style = AppType.Micro,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 내 차 사진: 설정에서 고른 차종·색의 주차 사진(car_*) → 없으면 기존 아이콘 아트 → 런처 아이콘.
 * car_* 는 "주차 구획에 세워진 차" 사진에서 "주차" 글자를 빼고 팔레트에 맞춰 재채색한 것이다.
 */
private fun loadCarBitmap(context: Context, car: String?, color: String?): Bitmap? {
    val type = car ?: "sedan"
    val tone = color ?: "white"
    val photoId = context.resources.getIdentifier("car_${type}_$tone", "drawable", context.packageName)
    if (photoId != 0) {
        BitmapFactory.decodeResource(context.resources, photoId)?.let { return it }
    }
    val fallbackId = context.resources.getIdentifier("ic_fg_${type}_$tone", "drawable", context.packageName)
    val drawable = if (fallbackId != 0) {
        ContextCompat.getDrawable(context, fallbackId)
    } else {
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull()
    }
    return drawable?.let { runCatching { it.toBitmap(width = 432, height = 432) }.getOrNull() }
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
private fun SheetButton(
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
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "주차 사진",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                )
            } else {
                Text("사진을 불러올 수 없어요", style = AppType.Body, color = Concrete.TextSub)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetButton("다시 찍기", Modifier.weight(1f), onRetake)
                SheetButton("닫기", Modifier.weight(1f), onDismiss)
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
