package com.eottadwotji.ui.floorpicker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.PhotoStore
import com.eottadwotji.detection.ParkingNotification
import com.eottadwotji.ui.components.FloorSelector
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.EottadwotjiTheme
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 기록 바텀시트 v5 (사용자 스케치 "기록 디자인").
 *
 * 한 장의 카드에서 끝낸다:
 *   [위치정보  (집 ▾) 또는 (⊕ 위치 등록)]                     [주차]
 *          ◁   ┌ B1 ┐   ▷        ┆ 사진 ┆
 *   메모 ___________________________________ 🎤
 *
 * - 층은 ◁ ▷ 스테퍼로 고른다 (◁ 위층, ▷ 아래층). 기압 추정·지난번 층이 초기값.
 * - 사진 슬롯을 누르면 카메라, 찍으면 그 자리에 썸네일.
 * - 메모는 한 줄 입력(음성 입력 지원). [주차]를 누르면 층·메모·사진을 한 번에 저장.
 * - 처음 온 위치면 헤더의 "⊕ 위치 등록"으로 이름·층 구성을 등록하고 돌아온다.
 * - 위치별 바텀시트 모드(층수만/층+메모/층+사진)는 더 이상 흐름을 나누지 않는다 —
 *   세 요소가 항상 한 카드에 있고 비워두면 그냥 건너뛴 것이 된다.
 *
 * 투명 액티비티 + 하단 시트: 다른 앱 사용 중에도 화면 하단을 덮는다 (오버레이 권한).
 * 감지로 열렸을 땐 10초 무응답 시 시트가 내려가고 서비스가 "위치만 저장됨"으로 처리한다.
 */
class FloorPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MANUAL = "manual"
        const val EXTRA_FROM_DETECTION = "from_detection"

        private const val SLIDE_UP_MS = 220         // 시트 등장
        private const val AUTO_DISMISS_MS = 10_000L // 무응답 → 시트 하강
        private const val LOT_RECHECK_MS = 600L     // 수동 기록 좌표 조회 폴링 간격
        private const val LOT_RECHECK_TRIES = 4     // 폴링 횟수 (총 ~2.4초)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 투명(windowIsTranslucent) 창은 adjustResize가 안 먹는다 —
        // 키보드가 저장 버튼을 가리지 않도록 인셋을 직접 받아 imePadding으로 처리
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val store = ParkingStore(this)

        val fromDetection = intent.getBooleanExtra(EXTRA_FROM_DETECTION, false)
        val manual = intent.getBooleanExtra(EXTRA_MANUAL, false)

        // 수동 기록: 감지 없이 열렸으면 세션을 새로 시작하고 좌표 1회 저장
        if (!fromDetection && (manual || !store.hasActiveParking())) {
            if (store.hasActiveParking()) {
                store.expireParking() // 이전 세션은 히스토리로
                WidgetUpdater.update(this)
            }
            store.startParking(System.currentTimeMillis(), manual = true)
            fetchLocationOnce(store)
        }

        setContent {
            EottadwotjiTheme {
                RecordSheet(
                    store = store,
                    autoDismiss = fromDetection,
                    onDone = { finish() }
                )
            }
        }
    }

    /** 수동 기록용 좌표 1회 저장 — 감지 경로에서는 서비스가 이미 저장함 */
    private fun fetchLocationOnce(store: ParkingStore) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        runCatching {
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    location?.let { store.setCoordinates(it.latitude, it.longitude) }
                }
        }
    }

    @Composable
    private fun RecordSheet(store: ParkingStore, autoDismiss: Boolean, onDone: () -> Unit) {
        val context = LocalContext.current

        var lot by remember { mutableStateOf(store.currentLot()) }
        var phase by remember { mutableStateOf(Phase.RECORD) }
        var interacted by remember { mutableStateOf(false) }

        // 층 목록은 위에서 아래로 (3F … 1F, B1 … B3) — ◁는 위층, ▷는 아래층
        val floors = remember(lot) {
            ParkingLotProfile.sortFloors(lot?.floors ?: ParkingLotProfile.DEFAULT_FLOORS)
        }
        val lastFloor = remember(lot) { store.lastFloorForCurrentLocation() }
        val estimatedFloor = remember { store.estimatedFloor }
        // 자동감지 정책: 이 위치에서 아직 한 번도 확인 안 한 추정이면 "첫 확인" 엄격 모드
        val strictEstimate = estimatedFloor != null && lot?.pressureCalibrated != true

        // 스테퍼 초기값: 기압 추정 > 지난번 층 > 이미 저장된 층 > B1 > 가운데
        var floorIndex by remember(floors) {
            val preferred = estimatedFloor ?: lastFloor ?: store.currentFloor() ?: "B1"
            val idx = floors.indexOf(preferred)
            mutableIntStateOf(if (idx >= 0) idx else floors.size / 2)
        }
        var memo by remember { mutableStateOf(store.currentMemo() ?: "") }
        var photoUri by remember { mutableStateOf(store.photoUri) }
        var savedFloor by remember { mutableStateOf<String?>(null) }

        // SETUP의 이전 버튼이 돌아갈 화면
        var setupFrom by remember { mutableStateOf(Phase.RECORD) }
        var editLot by remember { mutableStateOf<ParkingLotProfile?>(null) }
        var lotListVersion by remember { mutableIntStateOf(0) }

        // 수동 기록은 좌표 조회가 비동기 → 매칭될 때까지 잠시 폴링 (최대 ~2.4초).
        // 등록된 위치로 판명되면 그 위치의 층 구성으로 바뀐다.
        LaunchedEffect(Unit) {
            if (lot != null) return@LaunchedEffect
            repeat(LOT_RECHECK_TRIES) {
                delay(LOT_RECHECK_MS)
                if (interacted || savedFloor != null) return@LaunchedEffect
                val matched = store.currentLot()
                if (matched != null) {
                    lot = matched
                    return@LaunchedEffect
                }
            }
        }

        // 확인 카드 설정에 따라: 카드 표시 또는 완료 토스트 (v3.7)
        // 단, 이 위치의 기압 추정 첫 확인이면 설정과 무관하게 카드 1회 강제 (v3.9)
        val confirmOrFinish: () -> Unit = {
            if (store.confirmBeforeDone || strictEstimate) {
                phase = Phase.CONFIRM
            } else {
                android.widget.Toast.makeText(
                    context, "${savedFloor ?: "위치"} 등록됐어요", android.widget.Toast.LENGTH_SHORT
                ).show()
                onDone()
            }
        }

        var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                store.photoUri = pendingPhotoUri?.toString()
                photoUri = store.photoUri
            }
        }
        val launchCamera = {
            interacted = true
            val uri = PhotoStore.newPhotoUri(context, System.currentTimeMillis())
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        }

        // [주차]: 층·메모·(이미 저장된) 사진을 한 번에 확정
        val saveParking = {
            interacted = true
            val floor = floors[floorIndex.coerceIn(0, floors.size - 1)]
            store.recordPressureCalibration(floor) // 기압 추정이 틀렸으면 지형 보정 학습 (v3.7)
            store.setFloor(floor)
            store.rememberFloorForCurrentLocation(floor)
            if (memo.isNotBlank()) store.setMemo(memo.trim())
            ParkingNotification.showParkedNotification(
                context, floor, startedAtMs = store.parkingStartedAt()
            )
            WidgetUpdater.update(context)
            savedFloor = floor
            confirmOrFinish()
        }

        // 무응답 10초 → 시트 하강 (서비스가 "위치만 저장됨" 알림 처리)
        if (autoDismiss) {
            LaunchedEffect(Unit) {
                delay(AUTO_DISMISS_MS)
                if (!interacted && savedFloor == null) onDone()
            }
        }

        val visibleState =
            remember { MutableTransitionState(false).apply { targetState = true } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDone)
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(SLIDE_UP_MS)
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Concrete.BgScreen,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                        .clickable(enabled = false) {} // 시트 내부 탭이 스크림으로 새지 않게
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .imePadding() // 키보드가 올라오면 시트가 밀려 저장 버튼이 가려지지 않는다
                        .animateContentSize(animationSpec = tween(180))
                ) {
                    // 드래그 핸들
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(Concrete.Border, RoundedCornerShape(2.dp))
                    )

                    when (phase) {
                        Phase.RECORD -> RecordCard(
                            lot = lot,
                            floors = floors,
                            floorIndex = floorIndex,
                            onFloorIndex = { interacted = true; floorIndex = it },
                            floorSuffix = { floor ->
                                when {
                                    floor == estimatedFloor ->
                                        if (strictEstimate) "기압 추정 · 첫 확인" else "보정된 추정"
                                    floor == lastFloor -> "지난번"
                                    else -> lot?.memos?.get(floor)
                                }
                            },
                            memo = memo,
                            onMemo = { interacted = true; memo = it },
                            photoUri = photoUri,
                            onPhoto = launchCamera,
                            onLocationTap = {
                                interacted = true
                                phase = when {
                                    lot != null -> Phase.LOT_SELECT
                                    // 좌표를 알 때만 새 위치 등록 — 없으면 유령 위치가 되므로 목록에서 고른다
                                    store.coordinates() != null -> { setupFrom = Phase.RECORD; Phase.SETUP }
                                    else -> Phase.LOT_SELECT
                                }
                            },
                            onSave = saveParking,
                            hint = when {
                                strictEstimate ->
                                    "기압 추정 첫 확인이에요 — 높이에 따라 다를 수 있으니 꼭 확인하세요"
                                estimatedFloor != null -> "이 위치에 맞게 보정된 추정이에요"
                                autoDismiss -> "◁ ▷로 층을 맞추고 주차 · 10초 무응답 시 위치만 저장"
                                else -> "◁ ▷로 층을 맞추고 주차를 누르세요"
                            },
                            hintStrong = strictEstimate
                        )

                        Phase.LOT_SELECT -> {
                            SheetTitle(
                                title = "어느 주차장이에요?",
                                onBack = { interacted = true; phase = Phase.RECORD }
                            )
                            LotSelectList(
                                profiles = remember(lotListVersion) {
                                    store.profiles().sortedBy { it.name }
                                },
                                currentLotId = lot?.id,
                                onPick = { picked ->
                                    interacted = true
                                    store.assignLot(picked.id)
                                    lot = picked
                                    phase = Phase.RECORD
                                },
                                onEdit = { picked ->
                                    interacted = true
                                    editLot = picked
                                },
                                onNew = {
                                    interacted = true
                                    setupFrom = Phase.LOT_SELECT
                                    phase = Phase.SETUP
                                },
                                onNone = {
                                    interacted = true
                                    store.clearLot()
                                    lot = null
                                    phase = Phase.RECORD
                                }
                            )
                        }

                        Phase.SETUP -> {
                            SheetTitle(
                                title = "새로운 곳이네요 — 어디예요?",
                                onBack = { interacted = true; phase = setupFrom }
                            )
                            LotSetup(
                                onInteract = { interacted = true },
                                onSave = { name, selectedFloors ->
                                    val coords = store.coordinates()
                                    val profile = ParkingLotProfile(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        latitude = coords?.first,
                                        longitude = coords?.second,
                                        floors = ParkingLotProfile.sortFloors(selectedFloors.toList()),
                                        memos = emptyMap(),
                                        lastFloor = null
                                    )
                                    store.saveProfile(profile)
                                    store.assignLot(profile.id)
                                    lot = profile
                                    phase = Phase.RECORD
                                },
                                onSkip = {
                                    interacted = true
                                    phase = Phase.RECORD
                                }
                            )
                        }

                        Phase.CONFIRM -> {
                            SheetTitle(title = "이렇게 등록했어요 — 맞아요?", onBack = null)
                            ConfirmCard(
                                lotName = lot?.name,
                                floor = savedFloor,
                                memo = store.currentMemo(),
                                hasPhoto = photoUri != null,
                                startedAtMs = store.parkingStartedAt(),
                                onConfirm = onDone,
                                onEdit = {
                                    savedFloor = null
                                    phase = Phase.RECORD
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // 위치 편집 모달 — 수정·삭제 후 목록/현재 위치 반영
        if (editLot != null) {
            com.eottadwotji.ui.components.LotEditModal(
                store = store,
                profile = editLot,
                onDismiss = {
                    editLot = null
                    lotListVersion++
                    lot = store.currentLot()
                }
            )
        }
    }
}

private enum class Phase { RECORD, LOT_SELECT, SETUP, CONFIRM }

/** 보조 화면(위치 선택·등록·확인)의 제목 줄 — 이전 버튼 선택 */
@Composable
private fun SheetTitle(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", style = AppType.Title, color = Concrete.TextSub)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = AppType.Title, color = Concrete.TextMain)
    }
}

/**
 * 기록 카드 — 스케치 그대로.
 * 헤더: "위치정보" + 위치 칩(등록됨) / "⊕ 위치 등록"(처음) … [주차]
 * 본문: ◁ [층] ▷ + 사진 슬롯 / 메모 한 줄
 */
@Composable
private fun RecordCard(
    lot: ParkingLotProfile?,
    floors: List<String>,
    floorIndex: Int,
    onFloorIndex: (Int) -> Unit,
    floorSuffix: (String) -> String?,
    memo: String,
    onMemo: (String) -> Unit,
    photoUri: String?,
    onPhoto: () -> Unit,
    onLocationTap: () -> Unit,
    onSave: () -> Unit,
    hint: String,
    hintStrong: Boolean
) {
    val index = floorIndex.coerceIn(0, floors.size - 1)
    val floor = floors[index]
    val basement = ParkingLotProfile.isBasement(floor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 헤더: 위치정보 + [주차] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("위치정보", style = AppType.SectionLabel, color = Concrete.TextSub)
            Spacer(Modifier.width(10.dp))
            if (lot != null) {
                Row(
                    modifier = Modifier
                        .background(Concrete.BgPanel, RoundedCornerShape(16.dp))
                        .border(1.5.dp, Concrete.Neon, RoundedCornerShape(16.dp))
                        .clickable(onClick = onLocationTap)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lot.name, style = AppType.BodySmall, color = Concrete.NeonLight)
                    Spacer(Modifier.width(5.dp))
                    Text("▾", style = AppType.Hint, color = Concrete.TextDim)
                }
            } else {
                Row(
                    modifier = Modifier
                        .background(Concrete.BgPanel, RoundedCornerShape(16.dp))
                        .clickable(onClick = onLocationTap)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⊕", style = AppType.BodySmall, color = Concrete.Neon)
                    Spacer(Modifier.width(5.dp))
                    Text("위치 등록", style = AppType.BodySmall, color = Concrete.TextBody)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .background(Concrete.Neon, RoundedCornerShape(10.dp))
                    .clickable(onClick = onSave)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("주차", style = AppType.FloorButton, color = Concrete.NeonDeep)
            }
        }

        // ── 본문: ◁ 층 ▷ + 사진 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepArrow(
                symbol = "◁",
                caption = "위층",
                enabled = index > 0,
                onClick = { onFloorIndex(index - 1) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(12.dp))
                    .border(
                        2.dp,
                        if (basement) Concrete.Neon else Concrete.Accent,
                        RoundedCornerShape(12.dp)
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 지하 = 시그니처 그린, 지상 = 포인트 버건디 (v5 층 색 체계)
                Text(
                    floor,
                    style = AppType.GaugeFloor.copy(fontSize = 40.sp),
                    color = if (basement) Concrete.Neon else Concrete.Accent,
                    maxLines = 1
                )
                floorSuffix(floor)?.let {
                    Text(it, style = AppType.Micro, color = Concrete.TextSub, maxLines = 1)
                }
            }
            StepArrow(
                symbol = "▷",
                caption = "아래층",
                enabled = index < floors.size - 1,
                onClick = { onFloorIndex(index + 1) }
            )
            Spacer(Modifier.width(10.dp))
            PhotoSlot(uriString = photoUri, onClick = onPhoto)
        }

        // ── 메모 한 줄 (음성 입력) ──
        MemoField(value = memo, onChange = onMemo, onDone = onSave)

        Text(
            hint,
            style = AppType.Hint,
            color = if (hintStrong) Concrete.TextSub else Concrete.TextDim,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StepArrow(symbol: String, caption: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            symbol,
            style = AppType.Title.copy(fontSize = 26.sp),
            color = if (enabled) Concrete.TextMain else Concrete.Border
        )
        Text(
            caption,
            style = AppType.Micro,
            color = if (enabled) Concrete.TextDim else Concrete.Border
        )
    }
}

/** 사진 슬롯 — 점선 상자. 사진이 있으면 썸네일로 채우고, 누르면 (다시) 촬영 */
@Composable
private fun PhotoSlot(uriString: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap: Bitmap? = remember(uriString) {
        uriString?.let { PhotoStore.loadPhoto(context, it) }
    }
    val dash = Concrete.Border
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (bitmap == null) Modifier.drawBehind {
                    drawRoundRect(
                        color = dash,
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "주차 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = "사진 촬영",
                    tint = Concrete.TextSub,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("사진", style = AppType.Micro, color = Concrete.TextSub)
            }
        }
    }
}

/** 메모 한 줄 — 키보드 완료 = 주차, 마이크 = 시스템 음성인식 (v3.6) */
@Composable
private fun MemoField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            onChange(if (value.isBlank()) spoken else "$value $spoken")
        }
    }
    val launchSpeech = {
        runCatching {
            speechLauncher.launch(
                android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "세부구역을 말해주세요 (예: C구역 기둥 27 옆)")
                }
            )
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("메모 (예: C구역 기둥 27 옆)", style = AppType.BodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { launchSpeech() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = "음성으로 입력",
                    tint = Concrete.Neon,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        colors = sheetFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** 위치 선택: 등록된 위치 목록 + 편집 + 새 위치 등록 + 위치 없이 */
@Composable
private fun LotSelectList(
    profiles: List<ParkingLotProfile>,
    currentLotId: String?,
    onPick: (ParkingLotProfile) -> Unit,
    onEdit: (ParkingLotProfile) -> Unit,
    onNew: () -> Unit,
    onNone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        profiles.forEach { profile ->
            val current = profile.id == currentLotId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .border(
                        if (current) 1.5.dp else 0.dp,
                        if (current) Concrete.Neon else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onPick(profile) }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        profile.name,
                        style = AppType.Body,
                        color = if (current) Concrete.NeonLight else Concrete.TextBody
                    )
                    val sorted = ParkingLotProfile.sortFloors(profile.floors)
                    Text(
                        if (sorted.isEmpty()) ""
                        else "${sorted.first()}~${sorted.last()}" +
                            if (profile.latitude != null) " · 위치 등록됨" else "",
                        style = AppType.Hint,
                        color = Concrete.TextDim
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onEdit(profile) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎", style = AppType.Body, color = Concrete.TextDim)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .clickable(onClick = onNone),
                contentAlignment = Alignment.Center
            ) {
                Text("위치 없이", style = AppType.BodySmall, color = Concrete.TextSub)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(Concrete.Neon, RoundedCornerShape(8.dp))
                    .clickable(onClick = onNew),
                contentAlignment = Alignment.Center
            ) {
                Text("+ 새 위치 등록", style = AppType.FloorButton, color = Concrete.NeonDeep)
            }
        }
    }
}

/**
 * 확인 카드: 저장 내용을 요약해 보여주고 최종 확인을 받는다.
 * [맞아요] → 시트 닫기, [수정하기] → 기록 카드로 돌아가 다시 고른다.
 */
@Composable
private fun ConfirmCard(
    lotName: String?,
    floor: String?,
    memo: String?,
    hasPhoto: Boolean,
    startedAtMs: Long,
    onConfirm: () -> Unit,
    onEdit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Concrete.BgPanel, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val basement = floor?.let { ParkingLotProfile.isBasement(it) } ?: true
                Text(
                    floor ?: "층 미지정",
                    style = AppType.Title,
                    color = when {
                        floor == null -> Concrete.TextDim
                        basement -> Concrete.Neon
                        else -> Concrete.Accent
                    }
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    lotName ?: "이름 없는 주차장",
                    style = AppType.Body,
                    color = Concrete.TextMain
                )
                Spacer(Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREAN)
                        .format(java.util.Date(startedAtMs)),
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
            }
            val details = listOfNotNull(
                memo?.takeIf { it.isNotBlank() },
                if (hasPhoto) "사진 1장" else null
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = AppType.BodySmall, color = Concrete.TextSub)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Text("수정하기", style = AppType.BodySmall, color = Concrete.TextSub)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.Neon, RoundedCornerShape(8.dp))
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text("맞아요", style = AppType.FloorButton, color = Concrete.NeonDeep)
            }
        }
    }
}

/** 새 위치 등록: 이름 + 층 구성 선택 */
@Composable
private fun LotSetup(
    onInteract: () -> Unit,
    onSave: (name: String, floors: Set<String>) -> Unit,
    onSkip: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(ParkingLotProfile.DEFAULT_FLOORS) }
    var selected by remember { mutableStateOf(ParkingLotProfile.DEFAULT_FLOORS.toSet()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onInteract() },
            label = { Text("위치 이름 (예: 스타필드 하남)", style = AppType.BodySmall) },
            singleLine = true,
            colors = sheetFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Text("이 주차장에 있는 층만 남겨주세요", style = AppType.Hint, color = Concrete.TextDim)
        FloorSelector(
            candidates = candidates,
            selected = selected,
            onToggle = { floor ->
                onInteract()
                selected = if (floor in selected) selected - floor else selected + floor
            },
            onExtendUp = {
                onInteract()
                val highest = candidates.count { !ParkingLotProfile.isBasement(it) }
                val next = "${highest + 1}F"
                candidates = ParkingLotProfile.sortFloors(candidates + next)
                selected = selected + next
            },
            onExtendDown = {
                onInteract()
                val deepest = candidates.count { ParkingLotProfile.isBasement(it) }
                val next = "B${deepest + 1}"
                candidates = ParkingLotProfile.sortFloors(candidates + next)
                selected = selected + next
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip),
                contentAlignment = Alignment.Center
            ) {
                Text("나중에", style = AppType.BodySmall, color = Concrete.TextSub)
            }
            val canSave = name.isNotBlank() && selected.isNotEmpty()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(
                        if (canSave) Concrete.Neon else Concrete.BgPanel,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = canSave) { onSave(name.trim(), selected) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "저장하고 층 고르기",
                    style = AppType.FloorButton,
                    color = if (canSave) Concrete.NeonDeep else Concrete.TextDim
                )
            }
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Concrete.Neon,
    unfocusedBorderColor = Concrete.Border,
    focusedTextColor = Concrete.TextMain,
    unfocusedTextColor = Concrete.TextMain,
    cursorColor = Concrete.Neon,
    focusedLabelColor = Concrete.TextSub,
    unfocusedLabelColor = Concrete.TextDim,
    focusedPlaceholderColor = Concrete.TextDim,
    unfocusedPlaceholderColor = Concrete.TextDim
)
