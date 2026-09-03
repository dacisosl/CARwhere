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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.eottadwotji.ui.components.FloorSign
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.appCard
import com.eottadwotji.ui.theme.EottadwotjiTheme
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 기록 바텀시트 v5.2 (사용자 스케치 + 피드백 반영).
 *
 * 한 장의 카드에서 끝낸다:
 *   [위치정보  (집 ▾) 또는 (⊕ 위치 등록)]                 [📷 사진]
 *          ◁   ┌ B1 ┐   ▷              ┌ 주차 ┐
 *   메모 ___________________________________ 🎤
 *
 * - 층은 ◁ ▷ 스테퍼로 고른다 (◁ 위층, ▷ 아래층). 기압 추정·지난번 층이 초기값.
 *   층 박스는 상태바 아이콘과 같은 조형의 표지판 (v5.3부터 바탕은 시그니처 그린).
 * - v5.2 배치: 가장 중요한 [주차]가 큰 자리(오른쪽 사각)로 오고, 사진은 헤더의 작은
 *   카메라 버튼으로 갔다. 찍으면 헤더 버튼에 썸네일이 들어간다.
 * - 메모는 한 줄 입력(음성 입력 지원). [주차]를 누르면 층·메모·사진을 한 번에 저장하고
 *   토스트로 끝난다 — v5.4에서 "맞아요?" 확인 카드 단계를 없앴다.
 * - v5.2: 10초 무응답 자동 하강을 없앴다 — 시트는 사용자가 닫을 때까지 남고,
 *   서비스가 주차 확정 즉시 층 없는 캡슐(P)을 이미 올려둔다.
 *
 * 투명 액티비티 + 하단 시트: 다른 앱 사용 중에도 화면 하단을 덮는다 (오버레이 권한).
 */
class FloorPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MANUAL = "manual"
        const val EXTRA_FROM_DETECTION = "from_detection"

        private const val SLIDE_UP_MS = 220         // 시트 등장
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
                RecordSheet(store = store, onDone = { finish() })
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
    private fun RecordSheet(store: ParkingStore, onDone: () -> Unit) {
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

        // v5.4: "이렇게 등록했어요 — 맞아요?" 확인 카드 삭제 — [주차] 한 번이면 끝, 토스트로만 알린다.
        // 기압 추정 첫 확인 경고는 기록 카드 하단 warning 문구가 저장 전에 이미 보여준다.
        val confirmOrFinish: () -> Unit = {
            android.widget.Toast.makeText(
                context, "${savedFloor ?: "위치"} 등록됐어요", android.widget.Toast.LENGTH_SHORT
            ).show()
            onDone()
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
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
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
                            // v5.2: 조작 안내 문구는 삭제. 기압 추정 경고만 남긴다 (안전 문구)
                            warning = if (strictEstimate)
                                "기압 추정 첫 확인이에요 — 높이에 따라 다를 수 있으니 꼭 확인하세요"
                            else null
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

private enum class Phase { RECORD, LOT_SELECT, SETUP }

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
 * 기록 카드 — 스케치 + v5.3 격자 배치.
 * 헤더: "위치정보" + 위치 칩 / "⊕ 위치 등록" …… [📷 사진]   (모두 34dp 높이)
 * 본문: ◁ [층] ▷ 56dp 한 줄 + 캡션 한 줄, 오른쪽에 두 줄 높이의 [주차] / 메모 한 줄
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
    warning: String?
) {
    val index = floorIndex.coerceIn(0, floors.size - 1)
    val floor = floors[index]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 헤더: 위치정보 + 사진 — 칩과 사진 버튼은 같은 높이(34dp)로 한 줄에 맞춘다 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("위치정보", style = AppType.SectionLabel, color = Concrete.TextSub)
            Spacer(Modifier.width(10.dp))
            if (lot != null) {
                Row(
                    modifier = Modifier
                        .height(HEADER_CONTROL_HEIGHT)
                        .background(Concrete.BgPanel, RoundedCornerShape(17.dp))
                        .border(1.5.dp, Concrete.Neon, RoundedCornerShape(17.dp))
                        .clickable(onClick = onLocationTap)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lot.name, style = AppType.BodySmall, color = Concrete.Neon, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    Text("▾", style = AppType.Hint, color = Concrete.TextDim)
                }
            } else {
                Row(
                    modifier = Modifier
                        .height(HEADER_CONTROL_HEIGHT)
                        .background(Concrete.BgPanel, RoundedCornerShape(17.dp))
                        .clickable(onClick = onLocationTap)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⊕", style = AppType.BodySmall, color = Concrete.Neon)
                    Spacer(Modifier.width(5.dp))
                    Text("위치 등록", style = AppType.BodySmall, color = Concrete.TextBody, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            PhotoHeaderButton(uriString = photoUri, onClick = onPhoto)
        }

        // ── 본문: ◁ [층] ▷ + [주차] ──
        // 격자 (v5.3 리디자인): 위 줄은 세 조작(◁·층·▷)이 모두 56dp 높이로 한 선에,
        // 아래 줄은 각 조작의 캡션(위층·지난번·아래층)이 같은 선에 놓인다.
        // [주차]는 두 줄을 합친 높이를 그대로 받아(IntrinsicSize.Min) 오른쪽 열을 채운다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepArrow(
                        symbol = "◁",
                        tone = Concrete.StepUp,
                        enabled = index > 0,
                        onClick = { onFloorIndex(index - 1) }
                    )
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 오른쪽 [주차]가 그린 버튼이므로 층 박스는 테두리 판
                        FloorSign(
                            floor = floor,
                            fontSize = 36.sp,
                            outlined = true,
                            modifier = Modifier
                                .height(STEP_CONTROL_HEIGHT)
                                .widthIn(min = 84.dp)
                        )
                    }
                    StepArrow(
                        symbol = "▷",
                        tone = Concrete.StepDown,
                        enabled = index < floors.size - 1,
                        onClick = { onFloorIndex(index + 1) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepCaption(
                        "위층",
                        tone = Concrete.StepUp,
                        enabled = index > 0,
                        modifier = Modifier.width(STEP_ARROW_WIDTH)
                    )
                    // 가운데 캡션(지난번·기압 추정)은 보조 문구라 강조하지 않는다
                    Text(
                        floorSuffix(floor) ?: " ",
                        style = AppType.Micro,
                        color = Concrete.TextDim,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )
                    StepCaption(
                        "아래층",
                        tone = Concrete.StepDown,
                        enabled = index < floors.size - 1,
                        modifier = Modifier.width(STEP_ARROW_WIDTH)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            // 가장 중요한 버튼 — 오른쪽 열 전체 높이, 시그니처 그린
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight()
                    .background(Concrete.Neon, RoundedCornerShape(14.dp))
                    .clickable(onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "주차",
                    style = AppType.Sign.copy(fontSize = 22.sp),
                    color = Concrete.NeonDeep
                )
            }
        }

        // ── 메모 한 줄 (음성 입력) ──
        MemoField(value = memo, onChange = onMemo, onDone = onSave)

        warning?.let {
            Text(
                it,
                style = AppType.Hint,
                color = Concrete.Accent,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * 헤더 사진 버튼 — 카메라 아이콘 + "사진".
 * 이미 찍었으면 썸네일을 아이콘 자리에 넣어 "있다"는 걸 바로 보여준다 (탭 = 다시 찍기).
 */
@Composable
private fun PhotoHeaderButton(uriString: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap: Bitmap? = remember(uriString) {
        uriString?.let { PhotoStore.loadPhoto(context, it) }
    }
    Row(
        modifier = Modifier
            .height(HEADER_CONTROL_HEIGHT)
            .background(Concrete.BgPanel, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "주차 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
                tint = Concrete.TextSub,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (bitmap != null) "사진 1" else "사진",
            style = AppType.BodySmall,
            color = Concrete.TextBody
        )
    }
}

/**
 * 층 스테퍼 화살표 — 44×56 패널 버튼 (v5.3.2: 방향을 색으로).
 * 위층은 파랑, 아래층은 빨강. 옅은 색 바탕 + 같은 색 테두리·글리프로,
 * 층수를 올리는지 내리는지 글자를 읽지 않고도 알게 한다. 캡션은 아래 줄(StepCaption).
 */
@Composable
private fun StepArrow(symbol: String, tone: Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(STEP_ARROW_WIDTH)
            .height(STEP_CONTROL_HEIGHT)
            .background(if (enabled) tone.copy(alpha = 0.12f) else Concrete.BgPanel, shape)
            .then(
                if (enabled) Modifier.border(1.5.dp, tone.copy(alpha = 0.45f), shape)
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            style = AppType.Sign.copy(fontSize = 22.sp),
            color = if (enabled) tone else Concrete.Border
        )
    }
}

/**
 * 스테퍼 아래 줄 캡션 — 위층/아래층. 화살표와 같은 색·굵기로 한 쌍으로 읽히게 한다
 * (v5.3.2: 10sp 회색 → 12sp SemiBold 방향색). 한 줄 고정이라 높이가 어긋나지 않는다.
 */
@Composable
private fun StepCaption(
    text: String,
    tone: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text,
        style = AppType.SectionLabel.copy(letterSpacing = 0.sp),
        color = if (enabled) tone else Concrete.Border,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/** 기록 카드 격자 치수 */
private val HEADER_CONTROL_HEIGHT = 34.dp
private val STEP_CONTROL_HEIGHT = 56.dp
private val STEP_ARROW_WIDTH = 44.dp

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
