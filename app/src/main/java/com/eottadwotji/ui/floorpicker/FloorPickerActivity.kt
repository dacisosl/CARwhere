package com.eottadwotji.ui.floorpicker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import android.speech.RecognizerIntent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.PhotoStore
import com.eottadwotji.detection.ParkingNotification
import com.eottadwotji.ui.components.FloorSelector
import com.eottadwotji.ui.components.FloorWheel
import com.eottadwotji.ui.components.GroundLine
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.EottadwotjiTheme
import com.eottadwotji.ui.widget.WidgetUpdater
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 층 선택 바텀시트 (v3 흐름도).
 *
 * - 등록된 위치에 주차 → 그 위치의 층 구성으로 바로 층 선택. 기압 자동감지 켜져 있으면 추정 층 미리 강조
 * - 새로운 곳에 주차 → 위치 설정(이름 + 층 구성)을 먼저 하고 층 선택
 * - 층 선택 후 동작은 위치별 바텀시트 모드: 층수만 / 층+메모(기본값) / 층+사진
 *
 * 투명 액티비티 + 하단 시트: 다른 앱 사용 중에도 화면 하단을 덮는다 (오버레이 권한).
 * 층 버튼은 세로 스택, 지상선은 지상/지하 경계에 (절대 규칙 2).
 */
class FloorPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MANUAL = "manual"
        const val EXTRA_FROM_DETECTION = "from_detection"

        private const val NEON_FEEDBACK_MS = 120L   // 층 선택 형광 점등
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
                FloorSheet(
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
    private fun FloorSheet(store: ParkingStore, autoDismiss: Boolean, onDone: () -> Unit) {
        val context = LocalContext.current

        var lot by remember { mutableStateOf(store.currentLot()) }
        // 새 위치 등록(SETUP)은 좌표를 알 때만 묻는다 — 좌표가 없으면 등록해도
        // 반경 매칭이 불가능한 유령 위치만 쌓이므로 바로 층 선택으로 간다.
        var phase by remember {
            mutableStateOf(
                if (lot == null && store.coordinates() != null) Phase.SETUP else Phase.FLOOR
            )
        }
        var interacted by remember { mutableStateOf(false) }

        val floors = remember(lot) { lot?.floors ?: ParkingLotProfile.DEFAULT_FLOORS }
        val lastFloor = remember(lot) { store.lastFloorForCurrentLocation() }
        val estimatedFloor = remember { store.estimatedFloor }
        // v3.9 자동감지 정책: 이 위치에서 아직 한 번도 확인 안 한 추정이면 "첫 확인" 엄격 모드
        val strictEstimate = estimatedFloor != null && lot?.pressureCalibrated != true

        var selectedFloor by remember { mutableStateOf<String?>(null) }
        var confirmAfterCamera by remember { mutableStateOf(false) }
        // 휠 우측 메모/사진 버튼: 위치별 모드와 무관하게 다음 단계를 강제 (v3.9)
        var forcedNext by remember { mutableStateOf<String?>(null) }
        // SETUP의 이전 버튼이 돌아갈 화면 (자동 진입=FLOOR, 위치 선택 경유=LOT_SELECT)
        var setupFrom by remember { mutableStateOf(Phase.FLOOR) }
        // 위치 편집 모달 (v3.9.5 — 공용 LotEditModal)
        var editLot by remember { mutableStateOf<ParkingLotProfile?>(null) }
        var lotListVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }

        // 수동 기록은 좌표 조회가 비동기 → 매칭될 때까지 잠시 폴링 (최대 ~2.4초).
        // 등록된 위치로 판명되면 그 위치의 층 구성만 표시된다 (v3.6 — 해당 층수만).
        LaunchedEffect(Unit) {
            if (lot != null) return@LaunchedEffect
            repeat(LOT_RECHECK_TRIES) {
                delay(LOT_RECHECK_MS)
                if (interacted || selectedFloor != null) return@LaunchedEffect
                val matched = store.currentLot()
                when {
                    matched != null -> {
                        lot = matched
                        phase = Phase.FLOOR
                        return@LaunchedEffect
                    }
                    // 좌표가 늦게 도착했고 매칭 실패 → 이제 새 위치 등록을 물을 수 있다
                    store.coordinates() != null && phase == Phase.FLOOR -> {
                        phase = Phase.SETUP
                        return@LaunchedEffect
                    }
                }
            }
        }

        // 확인 카드 설정에 따라: 카드 표시 또는 바로 등록 + 완료 팝업 (v3.7)
        // 단, 이 위치의 기압 추정 첫 확인이면 설정과 무관하게 카드 1회 강제 (v3.9)
        val confirmOrFinish: () -> Unit = {
            if (store.confirmBeforeDone || strictEstimate) {
                phase = Phase.CONFIRM
            } else {
                android.widget.Toast.makeText(
                    context,
                    "${selectedFloor ?: "위치"} 등록됐어요",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onDone()
            }
        }

        var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) store.photoUri = pendingPhotoUri?.toString()
            if (confirmAfterCamera) confirmOrFinish()
        }
        val launchCamera = {
            val uri = PhotoStore.newPhotoUri(context, System.currentTimeMillis())
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        }

        // 층 선택: 저장 → 형광 점등 → 모드별 다음 단계 (메모/사진/닫기)
        val onFloorPicked: (String) -> Unit = { floor ->
            interacted = true
            store.recordPressureCalibration(floor) // 기압 추정이 틀렸으면 지형 보정 학습 (v3.7)
            store.setFloor(floor)
            store.rememberFloorForCurrentLocation(floor)
            selectedFloor = floor
        }
        LaunchedEffect(selectedFloor) {
            val floor = selectedFloor ?: return@LaunchedEffect
            delay(NEON_FEEDBACK_MS)
            ParkingNotification.showParkedNotification(
                context, floor, startedAtMs = store.parkingStartedAt()
            )
            WidgetUpdater.update(context)
            val nextMode = when (forcedNext) {
                "memo" -> ParkingStore.SHEET_FLOOR_MEMO
                "photo" -> ParkingStore.SHEET_FLOOR_PHOTO
                else -> store.sheetModeForCurrentLocation()
            }
            forcedNext = null
            when (nextMode) {
                ParkingStore.SHEET_FLOOR_MEMO -> phase = Phase.MEMO
                ParkingStore.SHEET_FLOOR_PHOTO -> {
                    confirmAfterCamera = true
                    launchCamera()
                }
                else -> confirmOrFinish() // 층수만 모드 — 설정에 따라 확인 카드 또는 즉시 완료
            }
        }

        // 무응답 10초 → 시트 하강 (서비스가 "위치만 저장됨" 알림 처리)
        if (autoDismiss) {
            LaunchedEffect(Unit) {
                delay(AUTO_DISMISS_MS)
                if (!interacted && selectedFloor == null) onDone()
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
                            Concrete.BgDeep,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                        .clickable(enabled = false) {} // 시트 내부 탭이 스크림으로 새지 않게
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .imePadding() // 키보드가 올라오면 시트가 그만큼 밀려 저장 버튼이 가려지지 않는다
                        .animateContentSize(animationSpec = tween(180))
                ) {
                    // 드래그 핸들
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 4.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(Concrete.Border, RoundedCornerShape(2.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 이전 버튼: 위치 선택/등록 화면에서 한 단계 뒤로 (v3.9.3)
                        if (phase == Phase.LOT_SELECT || phase == Phase.SETUP) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Concrete.BgPanel, RoundedCornerShape(10.dp))
                                    .clickable {
                                        interacted = true
                                        phase = if (phase == Phase.SETUP) setupFrom
                                        else Phase.FLOOR
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("‹", style = AppType.Title, color = Concrete.TextSub)
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            when (phase) {
                                Phase.SETUP -> "새로운 곳이네요 — 어디예요?"
                                Phase.LOT_SELECT -> "어느 주차장이에요?"
                                Phase.FLOOR ->
                                    if (lot != null) "${lot!!.name} — 몇 층?" else "몇 층에 댔어요?"
                                Phase.MEMO -> "${selectedFloor ?: ""} 저장됨 — 세부구역은?"
                                Phase.CONFIRM -> "이렇게 등록했어요 — 맞아요?"
                            },
                            style = AppType.Title,
                            color = Concrete.TextMain
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { interacted = true; launchCamera() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera),
                                contentDescription = "사진 촬영",
                                tint = Concrete.TextSub,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 위치 칩: 현재 매칭된 위치 표시 + 탭해서 변경/등록 (v3.9)
                    if (phase == Phase.FLOOR && selectedFloor == null) {
                        Row(
                            modifier = Modifier
                                .background(Concrete.BgPanel, RoundedCornerShape(20.dp))
                                .clickable {
                                    interacted = true
                                    phase = Phase.LOT_SELECT
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                lot?.name ?: "위치 선택하기",
                                style = AppType.BodySmall,
                                color = if (lot != null) Concrete.NeonLight else Concrete.TextSub
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("▾", style = AppType.Hint, color = Concrete.TextDim)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    when (phase) {
                        Phase.LOT_SELECT -> LotSelectList(
                            profiles = remember(lotListVersion) {
                                store.profiles().sortedBy { it.name }
                            },
                            currentLotId = lot?.id,
                            onPick = { picked ->
                                interacted = true
                                store.assignLot(picked.id)
                                lot = picked
                                phase = Phase.FLOOR
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
                                phase = Phase.FLOOR
                            }
                        )
                        Phase.SETUP -> LotSetup(
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
                                phase = Phase.FLOOR
                            },
                            onSkip = {
                                interacted = true
                                phase = Phase.FLOOR
                            }
                        )
                        Phase.FLOOR -> Column {
                            if (lot != null) {
                                // 등록된 위치: 층 구성이 확정돼 있으니 휠 피커 (v3.9)
                                FloorWheel(
                                    floors = ParkingLotProfile.sortFloors(floors),
                                    initialFloor = estimatedFloor ?: lastFloor,
                                    selectedFloor = selectedFloor,
                                    suffixFor = { floor ->
                                        when {
                                            floor == estimatedFloor ->
                                                if (strictEstimate) "기압 추정 · 첫 확인"
                                                else "보정된 추정"
                                            floor == lastFloor -> "지난번"
                                            else -> null
                                        }
                                    },
                                    onSave = onFloorPicked,
                                    onMemo = { floor ->
                                        forcedNext = "memo"
                                        onFloorPicked(floor)
                                    },
                                    onPhoto = { floor ->
                                        forcedNext = "photo"
                                        onFloorPicked(floor)
                                    }
                                )
                            } else {
                                // 미등록 위치: 층 구성이 불확실하니 직접 탭 선택 유지
                                FloorStack(
                                    floors = floors,
                                    lastFloor = lastFloor,
                                    estimatedFloor = estimatedFloor,
                                    selectedFloor = selectedFloor,
                                    memoFor = { store.currentLot()?.memos?.get(it) },
                                    onPick = onFloorPicked
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when {
                                    strictEstimate ->
                                        "기압 추정 첫 확인이에요 — 높이에 따라 다를 수 있으니 꼭 확인하세요"
                                    estimatedFloor != null ->
                                        "이 위치에 맞게 보정된 추정이에요"
                                    lot != null ->
                                        "휠을 돌려 맞추고 저장 · 10초 무응답 시 위치만 저장"
                                    else -> "탭 한 번이면 저장 · 10초 무응답 시 위치만 저장"
                                },
                                style = AppType.Hint,
                                color = if (strictEstimate) Concrete.TextSub else Concrete.TextDim,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        Phase.MEMO -> MemoInput(
                            onSave = { memoText ->
                                if (memoText.isNotBlank()) {
                                    store.setMemo(memoText.trim())
                                    selectedFloor?.let {
                                        ParkingNotification.showParkedNotification(
                                            context, it, startedAtMs = store.parkingStartedAt()
                                        )
                                    }
                                    WidgetUpdater.update(context)
                                }
                                confirmOrFinish()
                            }
                        )
                        Phase.CONFIRM -> ConfirmCard(
                            lotName = lot?.name,
                            floor = selectedFloor,
                            memo = store.currentMemo(),
                            startedAtMs = store.parkingStartedAt(),
                            onConfirm = onDone,
                            onEdit = {
                                // 층부터 다시 — 선택 해제 후 층 선택 화면으로
                                selectedFloor = null
                                phase = Phase.FLOOR
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // 위치 편집 모달 — 수정·삭제 후 목록/현재 위치 반영 (v3.9.5)
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

private enum class Phase { SETUP, LOT_SELECT, FLOOR, MEMO, CONFIRM }

/** v3.9 위치 선택: 등록된 위치 목록 + 편집 + 새 위치 등록 + 위치 없이 */
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (profile.id == currentLotId) Concrete.BgPanel else Concrete.BgPanel,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        if (profile.id == currentLotId) 1.5.dp else 0.dp,
                        if (profile.id == currentLotId) Concrete.Neon else Color.Transparent,
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
                        color = if (profile.id == currentLotId) Concrete.NeonLight
                        else Concrete.TextBody
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
                // 편집 → 설정의 위치 모달 (수정·삭제)
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
 * v3.6 자동 주차기록 운영 방침: 저장 내용을 요약해 보여주고 최종 확인을 받는다.
 * [맞아요] → 시트 닫기, [수정하기] → 층 선택부터 다시.
 */
@Composable
private fun ConfirmCard(
    lotName: String?,
    floor: String?,
    memo: String?,
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
                Text(
                    floor ?: "층 미지정",
                    style = AppType.Title,
                    color = if (floor != null) Concrete.NeonLight else Concrete.TextDim
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
            if (!memo.isNullOrBlank()) {
                Text(memo, style = AppType.BodySmall, color = Concrete.TextSub)
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

/** 새 위치 등록: 이름 + 층 구성 선택 (v3 흐름 — 새로운 곳은 위치 설정 먼저) */
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

/** 층 버튼 세로 스택: 지상 층 → 지상선 → 지하 층 (깊을수록 아래) */
@Composable
private fun FloorStack(
    floors: List<String>,
    lastFloor: String?,
    estimatedFloor: String?,
    selectedFloor: String?,
    memoFor: (String) -> String?,
    onPick: (String) -> Unit
) {
    // 형광 강조는 1개만: 기압 추정 > 지난번 층 (절대 규칙 3)
    val highlight = estimatedFloor ?: lastFloor
    val sorted = ParkingLotProfile.sortFloors(floors)
    val ground = sorted.filter { !ParkingLotProfile.isBasement(it) }
    val basement = sorted.filter { ParkingLotProfile.isBasement(it) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        ground.forEach { floor ->
            FloorRow(floor, highlight, estimatedFloor, lastFloor, selectedFloor, memoFor, onPick)
        }
        GroundLine(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
        basement.forEach { floor ->
            FloorRow(floor, highlight, estimatedFloor, lastFloor, selectedFloor, memoFor, onPick)
        }
    }
}

@Composable
private fun FloorRow(
    floor: String,
    highlight: String?,
    estimatedFloor: String?,
    lastFloor: String?,
    selectedFloor: String?,
    memoFor: (String) -> String?,
    onPick: (String) -> Unit
) {
    val isHighlight = floor == highlight
    val isSelected = floor == selectedFloor
    val memoText = memoFor(floor)
    val suffix = when {
        floor == estimatedFloor -> "기압 추정"
        floor == lastFloor -> "지난번"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isHighlight) 54.dp else 46.dp) // 강조 층은 살짝 큰 패딩
            .background(
                if (isSelected) Concrete.Neon else Concrete.BgPanel,
                RoundedCornerShape(8.dp)
            )
            .border(
                if (isHighlight && !isSelected) 2.dp else 0.dp,
                if (isHighlight && !isSelected) Concrete.Neon else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = selectedFloor == null) { onPick(floor) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isHighlight) {
            Icon(
                painter = painterResource(R.drawable.ic_car),
                contentDescription = null,
                tint = if (isSelected) Concrete.NeonDeep else Concrete.Neon,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            floor,
            style = AppType.FloorButton,
            color = when {
                isSelected -> Concrete.NeonDeep
                isHighlight -> Concrete.NeonLight
                else -> Concrete.TextBody
            }
        )
        if (suffix != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                suffix,
                style = AppType.Hint,
                color = if (isSelected) Concrete.NeonDeep else Concrete.TextDim
            )
        }
        Spacer(Modifier.weight(1f))
        if (memoText != null) {
            Text(
                memoText,
                style = AppType.BodySmall,
                color = if (isSelected) Concrete.NeonDeep else Concrete.TextSub
            )
        }
    }
}

/** 층+메모 모드: 층 선택 직후 자동으로 뜨는 세부구역 입력 (v3 기본값) */
@Composable
private fun MemoInput(onSave: (String) -> Unit) {
    var memo by remember { mutableStateOf("") }

    // v3.6: 음성으로 메모 입력 — 시스템 음성인식 (권한 불필요, 결과 텍스트만 수신)
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            memo = if (memo.isBlank()) spoken else "$memo $spoken"
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("세부구역 (예: C구역 기둥 27 옆)", style = AppType.BodySmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(memo) }), // 키보드 완료 = 저장
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .clickable { onSave("") },
                contentAlignment = Alignment.Center
            ) {
                Text("건너뛰기", style = AppType.BodySmall, color = Concrete.TextSub)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Concrete.Neon, RoundedCornerShape(8.dp))
                    .clickable { onSave(memo) },
                contentAlignment = Alignment.Center
            ) {
                Text("저장", style = AppType.FloorButton, color = Concrete.NeonDeep)
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
    unfocusedLabelColor = Concrete.TextDim
)
