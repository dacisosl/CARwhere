package com.eottadwotji.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.components.FloorSelector
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.EottadwotjiTheme
import com.google.android.gms.location.LocationServices
import java.util.UUID

/**
 * 설정 v3 — 항목 순서: 위치 / 바텀시트 / 알림 / 감지 / 기타.
 * 위치는 탭하면 모달창으로 편집 (이름·층 구성·위치별 바텀시트·좌표).
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EottadwotjiTheme {
                SettingsScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }
    var refresh by remember { mutableIntStateOf(0) }

    val profiles = remember(refresh) { profilesSorted(store) }
    val sheetMode = remember(refresh) { store.defaultSheetMode }
    val displayMode = remember(refresh) { store.displayMode }
    val pressureOn = remember(refresh) { store.pressureAutoDetect }
    val autoClear = remember(refresh) { store.autoClearOnDeparture }
    val myCarName = remember(refresh) { store.myCarName }
    val overlayGranted = remember(refresh) { Settings.canDrawOverlays(context) }

    var activeSheet by remember { mutableStateOf<String?>(null) }
    // 위치 모달: null=닫힘, 빈 id=새 위치
    var editingLot by remember { mutableStateOf<ParkingLotProfile?>(null) }
    var creatingLot by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = Concrete.TextSub
                )
            }
            Text("설정", style = AppType.Title, color = Concrete.TextMain)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── 1. 위치 ──
            SectionLabel("위치")
            profiles.forEach { profile ->
                SettingRow(
                    profile.name,
                    floorsSummary(profile.floors) +
                        if (profile.latitude != null) " · 위치 등록됨" else ""
                ) { editingLot = profile }
            }
            SettingRow("+ 위치 추가", "") { creatingLot = true }

            // ── 2. 바텀시트 ──
            SectionLabel("바텀시트")
            SettingRow("기본 동작", sheetModeLabel(sheetMode)) { activeSheet = "sheet_mode" }
            Text(
                "위치마다 다르게 하려면 위 위치를 눌러 바꿔주세요",
                style = AppType.Hint,
                color = Concrete.TextDim,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            // ── 3. 알림 ──
            SectionLabel("알림")
            SettingRow("표시 방식", displayModeLabel(displayMode)) { activeSheet = "display" }

            // ── 4. 감지 ──
            SectionLabel("감지")
            SwitchRow("자동감지 — 기압으로 층 추천 (베타)", pressureOn) {
                store.pressureAutoDetect = it
                refresh++
            }
            SettingRow("내 차 블루투스", myCarName ?: "미지정") { activeSheet = "car" }
            SettingRow(
                "바텀시트 팝업 (다른 앱 위)",
                if (overlayGranted) "켜짐" else "꺼짐 — 알림으로 대체"
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }

            // ── 5. 기타 ──
            SectionLabel("기타")
            SwitchRow("출차 시 자동 삭제", autoClear) {
                store.autoClearOnDeparture = it
                refresh++
            }
            SectionLabel("배터리 사용 안내")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BatteryInfoLine("대기 중", "블루투스 신호 수신만 — 폴링 없음")
                BatteryInfoLine("위치", "주차 확정 순간 1회만 — 상시 추적 없음")
                BatteryInfoLine("기압 센서", "자동감지 켠 경우, 주행 중에만 초당 1회")
                BatteryInfoLine("위젯", "주차 상태가 바뀔 때만 갱신")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 바텀시트 선택지들 ──
    when (activeSheet) {
        "sheet_mode" -> OptionSheet(
            title = "바텀시트 기본 동작",
            options = listOf(
                ParkingStore.SHEET_FLOOR to "층수만",
                ParkingStore.SHEET_FLOOR_MEMO to "층 + 메모 (기본값)",
                ParkingStore.SHEET_FLOOR_PHOTO to "층 + 사진"
            ),
            current = sheetMode,
            onSelect = { store.defaultSheetMode = it },
            onDismiss = { activeSheet = null; refresh++ }
        )
        "display" -> OptionSheet(
            title = "표시 방식",
            options = listOf(
                ParkingStore.DISPLAY_STATUSBAR to "상태바만",
                ParkingStore.DISPLAY_WIDGET to "홈 위젯만",
                ParkingStore.DISPLAY_BOTH to "홈 위젯 + 상태바"
            ),
            current = displayMode,
            onSelect = { store.displayMode = it },
            onDismiss = { activeSheet = null; refresh++ }
        )
        "car" -> CarPickerSheet(store, onDismiss = { activeSheet = null; refresh++ })
    }

    // ── 위치 모달 ──
    if (creatingLot || editingLot != null) {
        LotModal(
            store = store,
            profile = editingLot,
            onDismiss = {
                creatingLot = false
                editingLot = null
                refresh++
            }
        )
    }
}

// ── 위치 모달창 (v3) ────────────────────────────────────────

@Composable
private fun LotModal(
    store: ParkingStore,
    profile: ParkingLotProfile?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(profile?.name ?: "") }
    var candidates by remember {
        mutableStateOf(
            ParkingLotProfile.sortFloors(
                ParkingLotProfile.DEFAULT_FLOORS + (profile?.floors ?: emptyList())
            )
        )
    }
    var selected by remember {
        mutableStateOf(profile?.floors?.toSet() ?: ParkingLotProfile.DEFAULT_FLOORS.toSet())
    }
    var sheetMode by remember { mutableStateOf(profile?.sheetMode) } // null = 기본값 따름
    var lat by remember { mutableStateOf(profile?.latitude) }
    var lon by remember { mutableStateOf(profile?.longitude) }
    var locationStatus by remember { mutableStateOf<String?>(null) }
    var memos by remember { mutableStateOf(profile?.memos ?: emptyMap()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (profile == null) "위치 추가" else "위치 편집",
                style = AppType.Title,
                color = Concrete.TextMain
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("위치 이름 (예: 우리 아파트)", style = AppType.BodySmall) },
                singleLine = true,
                colors = modalFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // 층 구성: 지상 1~3 + 지하 1~3 기본, "+"로 확장, 탭으로 선택
            Text("층 구성", style = AppType.SectionLabel, color = Concrete.TextDim)
            FloorSelector(
                candidates = candidates,
                selected = selected,
                onToggle = { floor ->
                    selected = if (floor in selected) selected - floor else selected + floor
                },
                onExtendUp = {
                    val next = "${candidates.count { !ParkingLotProfile.isBasement(it) } + 1}F"
                    candidates = ParkingLotProfile.sortFloors(candidates + next)
                },
                onExtendDown = {
                    val next = "B${candidates.count { ParkingLotProfile.isBasement(it) } + 1}"
                    candidates = ParkingLotProfile.sortFloors(candidates + next)
                }
            )

            // 이 위치의 바텀시트 모드 (기본값 따름 / 층수만 / 층+메모 / 층+사진)
            Text("바텀시트", style = AppType.SectionLabel, color = Concrete.TextDim)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "기본값 따름 (${sheetModeLabel(store.defaultSheetMode)})",
                    ParkingStore.SHEET_FLOOR to "층수만",
                    ParkingStore.SHEET_FLOOR_MEMO to "층 + 메모",
                    ParkingStore.SHEET_FLOOR_PHOTO to "층 + 사진"
                ).forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sheetMode = value }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = AppType.BodySmall,
                            color = if (sheetMode == value) Concrete.NeonLight
                            else Concrete.TextBody
                        )
                        Spacer(Modifier.weight(1f))
                        if (sheetMode == value) {
                            Text("✓", style = AppType.BodySmall, color = Concrete.Neon)
                        }
                    }
                }
            }

            // 층별 메모 (선택된 층만)
            Text("층별 메모 (선택)", style = AppType.SectionLabel, color = Concrete.TextDim)
            ParkingLotProfile.sortFloors(selected.toList()).forEach { floor ->
                OutlinedTextField(
                    value = memos[floor] ?: "",
                    onValueChange = { memos = memos + (floor to it) },
                    label = { Text("$floor 메모", style = AppType.BodySmall) },
                    singleLine = true,
                    colors = modalFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 좌표 등록 — 반경 150m 자동 매칭
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (lat != null) "위치 등록됨 ✓" else "위치 미등록",
                    style = AppType.BodySmall,
                    color = if (lat != null) Concrete.NeonLight else Concrete.TextSub
                )
                Text(
                    "등록하면 이 근처(150m)에 주차할 때 자동으로 이 설정을 써요",
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
                Text(
                    "현재 위치로 등록",
                    style = AppType.BodySmall,
                    color = Concrete.TextBody,
                    modifier = Modifier
                        .clickable {
                            fetchLocation(context) { newLat, newLon ->
                                if (newLat != null && newLon != null) {
                                    lat = newLat
                                    lon = newLon
                                    locationStatus = "현재 위치로 등록했어요"
                                } else {
                                    locationStatus = "위치를 가져올 수 없어요 (권한/GPS 확인)"
                                }
                            }
                        }
                        .padding(vertical = 4.dp)
                )
                locationStatus?.let { Text(it, style = AppType.Hint, color = Concrete.TextDim) }
            }

            if (profile != null) {
                Text(
                    "이 위치 삭제",
                    style = AppType.BodySmall,
                    color = Concrete.TextDim,
                    modifier = Modifier
                        .clickable {
                            store.deleteProfile(profile.id)
                            onDismiss()
                        }
                        .padding(vertical = 4.dp)
                )
            }

            // 저장/닫기
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("닫기", style = AppType.BodySmall, color = Concrete.TextSub)
                }
                val canSave = name.isNotBlank() && selected.isNotEmpty()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(
                            if (canSave) Concrete.Neon else Concrete.BgPanel,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = canSave) {
                            store.saveProfile(
                                ParkingLotProfile(
                                    id = profile?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    latitude = lat,
                                    longitude = lon,
                                    floors = ParkingLotProfile.sortFloors(selected.toList()),
                                    memos = memos.filterKeys { it in selected }
                                        .filterValues { it.isNotBlank() },
                                    lastFloor = profile?.lastFloor,
                                    sheetMode = sheetMode
                                )
                            )
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "저장",
                        style = AppType.FloorButton,
                        color = if (canSave) Concrete.NeonDeep else Concrete.TextDim
                    )
                }
            }
        }
    }
}

@Composable
private fun modalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Concrete.Neon,
    unfocusedBorderColor = Concrete.Border,
    focusedTextColor = Concrete.TextMain,
    unfocusedTextColor = Concrete.TextMain,
    cursorColor = Concrete.Neon,
    focusedLabelColor = Concrete.TextSub,
    unfocusedLabelColor = Concrete.TextDim
)

// ── 행 컴포넌트 ─────────────────────────────────────────────

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = AppType.SectionLabel,
        color = Concrete.TextDim,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp, start = 4.dp)
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppType.Body,
            color = if (enabled) Concrete.TextBody else Concrete.TextDim
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = AppType.BodySmall, color = Concrete.TextSub)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppType.Body, color = Concrete.TextBody)
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

@Composable
private fun BatteryInfoLine(label: String, detail: String) {
    Row {
        Text(label, style = AppType.BodySmall, color = Concrete.TextBody)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(detail, style = AppType.Hint, color = Concrete.TextDim)
    }
}

// ── 바텀시트 (드롭다운 대용) ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Concrete.BgPanel
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(title, style = AppType.Title, color = Concrete.TextMain)
            Spacer(Modifier.height(12.dp))
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(value)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = AppType.Body,
                        color = if (value == current) Concrete.NeonLight else Concrete.TextBody
                    )
                    Spacer(Modifier.weight(1f))
                    if (value == current) {
                        Text("✓", style = AppType.Body, color = Concrete.Neon)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarPickerSheet(store: ParkingStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    val devices = remember { if (granted) bondedDevices(context) else emptyList() }
    val currentAddress = store.myCarAddress

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Concrete.BgPanel) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("내 차 블루투스", style = AppType.Title, color = Concrete.TextMain)
            Spacer(Modifier.height(12.dp))
            if (!granted) {
                Text(
                    "근처 기기 권한이 필요해요. 앱 정보 > 권한에서 켜주세요.",
                    style = AppType.BodySmall,
                    color = Concrete.TextSub
                )
            } else if (devices.isEmpty()) {
                Text("페어링된 기기가 없어요", style = AppType.BodySmall, color = Concrete.TextSub)
            } else {
                devices.forEach { (name, address) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                store.myCarAddress = address
                                store.myCarName = name
                                store.manualOnlyMode = false
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            style = AppType.Body,
                            color = if (address == currentAddress)
                                Concrete.NeonLight else Concrete.TextBody
                        )
                        Spacer(Modifier.weight(1f))
                        if (address == currentAddress) {
                            Text("✓", style = AppType.Body, color = Concrete.Neon)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 헬퍼 ────────────────────────────────────────────────────

private fun profilesSorted(store: ParkingStore): List<ParkingLotProfile> =
    store.profiles().sortedBy { it.name }

private fun floorsSummary(floors: List<String>): String {
    val sorted = ParkingLotProfile.sortFloors(floors)
    return if (sorted.isEmpty()) "" else "${sorted.first()}~${sorted.last()}"
}

@SuppressLint("MissingPermission") // 호출부에서 BLUETOOTH_CONNECT 확인 + 여기서도 재확인
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return emptyList()
    return runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList()
        adapter.bondedDevices.map { (it.name ?: it.address) to it.address }
    }.getOrDefault(emptyList())
}

private fun fetchLocation(context: Context, onResult: (Double?, Double?) -> Unit) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        onResult(null, null)
        return
    }
    runCatching {
        LocationServices.getFusedLocationProviderClient(context)
            .lastLocation
            .addOnSuccessListener { location ->
                onResult(location?.latitude, location?.longitude)
            }
            .addOnFailureListener { onResult(null, null) }
    }.onFailure { onResult(null, null) }
}

private fun displayModeLabel(mode: String): String = when (mode) {
    ParkingStore.DISPLAY_WIDGET -> "홈 위젯만"
    ParkingStore.DISPLAY_BOTH -> "홈 위젯 + 상태바"
    else -> "상태바만"
}

private fun sheetModeLabel(mode: String): String = when (mode) {
    ParkingStore.SHEET_FLOOR -> "층수만"
    ParkingStore.SHEET_FLOOR_PHOTO -> "층 + 사진"
    else -> "층 + 메모"
}
