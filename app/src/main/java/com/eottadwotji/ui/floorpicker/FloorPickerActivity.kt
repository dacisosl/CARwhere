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
import androidx.core.content.ContextCompat
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.PhotoStore
import com.eottadwotji.detection.ParkingNotification
import com.eottadwotji.ui.components.FloorSelector
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
        private const val LOT_RECHECK_MS = 800L     // 수동 기록 좌표 조회 완료 대기
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ParkingStore(this)

        val fromDetection = intent.getBooleanExtra(EXTRA_FROM_DETECTION, false)
        val manual = intent.getBooleanExtra(EXTRA_MANUAL, false)

        // 수동 기록: 감지 없이 열렸으면 세션을 새로 시작하고 좌표 1회 저장
        if (!fromDetection && (manual || !store.hasActiveParking())) {
            if (store.hasActiveParking()) store.expireParking() // 이전 세션은 히스토리로
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
        var phase by remember {
            mutableStateOf(if (lot != null) Phase.FLOOR else Phase.SETUP)
        }
        var interacted by remember { mutableStateOf(false) }

        // 수동 기록은 좌표 조회가 비동기 → 잠시 후 매칭 재확인해서 SETUP을 건너뛴다
        LaunchedEffect(Unit) {
            if (lot == null) {
                delay(LOT_RECHECK_MS)
                val matched = store.currentLot()
                if (matched != null && !interacted) {
                    lot = matched
                    phase = Phase.FLOOR
                }
            }
        }

        val floors = remember(lot) { lot?.floors ?: ParkingLotProfile.DEFAULT_FLOORS }
        val lastFloor = remember(lot) { store.lastFloorForCurrentLocation() }
        val estimatedFloor = remember { store.estimatedFloor }

        var selectedFloor by remember { mutableStateOf<String?>(null) }
        var finishAfterCamera by remember { mutableStateOf(false) }

        var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) store.photoUri = pendingPhotoUri?.toString()
            if (finishAfterCamera) onDone()
        }
        val launchCamera = {
            val uri = PhotoStore.newPhotoUri(context, System.currentTimeMillis())
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        }

        // 층 선택: 저장 → 형광 점등 → 모드별 다음 단계 (메모/사진/닫기)
        val onFloorPicked: (String) -> Unit = { floor ->
            interacted = true
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
            when (store.sheetModeForCurrentLocation()) {
                ParkingStore.SHEET_FLOOR_MEMO -> phase = Phase.MEMO
                ParkingStore.SHEET_FLOOR_PHOTO -> {
                    finishAfterCamera = true
                    launchCamera()
                }
                else -> onDone()
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
                        Text(
                            when (phase) {
                                Phase.SETUP -> "새로운 곳이네요 — 어디예요?"
                                Phase.FLOOR ->
                                    if (lot != null) "${lot!!.name} — 몇 층?" else "몇 층에 댔어요?"
                                Phase.MEMO -> "${selectedFloor ?: ""} 저장됨 — 세부구역은?"
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

                    Spacer(Modifier.height(10.dp))

                    when (phase) {
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
                            FloorStack(
                                floors = floors,
                                lastFloor = lastFloor,
                                estimatedFloor = estimatedFloor,
                                selectedFloor = selectedFloor,
                                memoFor = { store.currentLot()?.memos?.get(it) },
                                onPick = onFloorPicked
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "탭 한 번이면 저장 · 10초 무응답 시 위치만 저장",
                                style = AppType.Hint,
                                color = Concrete.TextDim,
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
                                }
                                onDone()
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

private enum class Phase { SETUP, FLOOR, MEMO }

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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("세부구역 (예: C구역 기둥 27 옆)", style = AppType.BodySmall) },
            singleLine = true,
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
