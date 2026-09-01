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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import com.eottadwotji.BuildConfig
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.data.UpdateChecker
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
    val themeMode = remember(refresh) { store.themeMode }
    val confirmCard = remember(refresh) { store.confirmBeforeDone }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    var activeSheet by remember { mutableStateOf<String?>(null) }
    // 위치 모달: null=닫힘, 빈 id=새 위치
    var editingLot by remember { mutableStateOf<ParkingLotProfile?>(null) }
    var creatingLot by remember { mutableStateOf(false) }
    var showIconModal by remember { mutableStateOf(false) }
    val iconCar = remember(refresh) { store.appIconCar }
    val iconColor = remember(refresh) { store.appIconColor }

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

        // v3.7: 아코디언 구조 — 섹션을 누르면 그 자리에서 펼쳐진다. ★는 대시보드 빠른 설정 등록
        var openSections by remember { mutableStateOf(setOf("위치")) }
        val toggleSection: (String) -> Unit = { name ->
            openSections =
                if (name in openSections) openSections - name else openSections + name
        }
        val starChanged = { refresh++; Unit }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "★를 누르면 대시보드 빠른 설정에 올라가요",
                style = AppType.Hint,
                color = Concrete.TextDim,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            AccordionSection("위치", "위치" in openSections, { toggleSection("위치") }) {
                profiles.forEach { profile ->
                    SettingRow(
                        profile.name,
                        floorsSummary(profile.floors) +
                            if (profile.latitude != null) " · 위치 등록됨" else ""
                    ) { editingLot = profile }
                }
                SettingRow("+ 위치 추가", "") { creatingLot = true }
            }

            AccordionSection("바텀시트", "바텀시트" in openSections, { toggleSection("바텀시트") }) {
                SettingRow(
                    "기본 동작", sheetModeLabel(sheetMode),
                    star = { StarToggle(store, ParkingStore.STAR_SHEET_MODE, starChanged) }
                ) { activeSheet = "sheet_mode" }
                SettingRow(
                    "등록 확인",
                    if (confirmCard) "확인 카드 띄우기" else "바로 등록",
                    star = { StarToggle(store, ParkingStore.STAR_CONFIRM, starChanged) }
                ) { activeSheet = "confirm_card" }
                Text(
                    "위치마다 다르게 하려면 위 위치를 눌러 바꿔주세요",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            AccordionSection("알림", "알림" in openSections, { toggleSection("알림") }) {
                SettingRow(
                    "표시 방식", displayModeLabel(displayMode),
                    star = { StarToggle(store, ParkingStore.STAR_DISPLAY, starChanged) }
                ) { activeSheet = "display" }
            }

            AccordionSection("감지", "감지" in openSections, { toggleSection("감지") }) {
                SwitchRow(
                    "자동감지", pressureOn,
                    star = { StarToggle(store, ParkingStore.STAR_PRESSURE, starChanged) }
                ) {
                    store.pressureAutoDetect = it
                    refresh++
                }
                Text(
                    "기압 추정은 지형 높이에 따라 다를 수 있어요 — 층을 고치면 위치별로 자동 보정돼요",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
            }

            AccordionSection("앱 아이콘", "앱 아이콘" in openSections, { toggleSection("앱 아이콘") }) {
                SettingRow(
                    "차량 아이콘",
                    if (iconCar != null && iconColor != null)
                        "${AppIconSwitcher.carLabel(iconCar)} · ${AppIconSwitcher.colorLabel(iconColor)}"
                    else "기본 (흰색 중형차)"
                ) { showIconModal = true }
            }

            AccordionSection("기타", "기타" in openSections, { toggleSection("기타") }) {
                SettingRow(
                    "테마", themeModeLabel(themeMode),
                    star = { StarToggle(store, ParkingStore.STAR_THEME, starChanged) }
                ) { activeSheet = "theme" }
                UpdateCheckRow()
                SwitchRow(
                    "출차 시 자동 삭제", autoClear,
                    star = { StarToggle(store, ParkingStore.STAR_AUTO_CLEAR, starChanged) }
                ) {
                    store.autoClearOnDeparture = it
                    refresh++
                }
            }

            AccordionSection(
                "배터리 사용 안내", "배터리" in openSections, { toggleSection("배터리") }
            ) {
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
            onSelect = {
                store.displayMode = it
                // 표시 방식 변경 즉시 반영: 상시 알림 상태 + 홈 위젯 (v3.6)
                if (store.hasActiveParking()) {
                    com.eottadwotji.detection.ParkingDetectionService.refresh(context)
                }
                com.eottadwotji.ui.widget.WidgetUpdater.update(context)
            },
            onDismiss = { activeSheet = null; refresh++ }
        )
        "theme" -> OptionSheet(
            title = "테마",
            options = listOf(
                ParkingStore.THEME_SYSTEM to "시스템 따라가기",
                ParkingStore.THEME_DARK to "다크 (지하주차장)",
                ParkingStore.THEME_LIGHT to "라이트"
            ),
            current = themeMode,
            onSelect = {
                store.themeMode = it
                com.eottadwotji.ui.theme.Concrete.apply(it, systemDark) // 전 화면 즉시 반영
            },
            onDismiss = { activeSheet = null; refresh++ }
        )
        "confirm_card" -> OptionSheet(
            title = "등록 확인",
            options = listOf(
                "confirm" to "확인 카드 띄우기 — 맞아요/수정하기",
                "instant" to "바로 등록 — 완료 팝업만"
            ),
            current = if (confirmCard) "confirm" else "instant",
            onSelect = { store.confirmBeforeDone = it == "confirm" },
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

    // ── 앱 아이콘 모달 ──
    if (showIconModal) {
        AppIconModal(
            store = store,
            onDismiss = {
                showIconModal = false
                refresh++
            }
        )
    }
}

// ── 앱 아이콘 선택 모달 (v3.2) ──────────────────────────────

@Composable
private fun AppIconModal(store: ParkingStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var car by remember { mutableStateOf(store.appIconCar) }     // null = 기본
    var color by remember { mutableStateOf(store.appIconColor ?: "white") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("앱 아이콘", style = AppType.Title, color = Concrete.TextMain)

            // 미리보기
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .background(Concrete.BgScreen, RoundedCornerShape(24.dp))
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        iconPreviewRes(car, color)
                    ),
                    contentDescription = "아이콘 미리보기",
                    modifier = Modifier.padding(4.dp).heightIn(max = 120.dp)
                )
            }

            // 미리보기 그리드 — 항목을 직접 보고 고른다 (행=차종, 열=색상)
            Text("항목을 골라주세요", style = AppType.SectionLabel, color = Concrete.TextDim)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIconSwitcher.CARS.forEach { c ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            AppIconSwitcher.carLabel(c),
                            style = AppType.Hint,
                            color = Concrete.TextDim,
                            modifier = Modifier.width(52.dp)
                        )
                        AppIconSwitcher.COLORS.forEach { col ->
                            val selected = car == c && color == col
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    iconPreviewRes(c, col)
                                ),
                                contentDescription =
                                    "${AppIconSwitcher.carLabel(c)} ${AppIconSwitcher.colorLabel(col)}",
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .background(Concrete.BgScreen, RoundedCornerShape(12.dp))
                                    .then(
                                        if (selected) Modifier.border(
                                            2.dp, Concrete.Neon, RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .clickable {
                                        car = c
                                        color = col
                                    }
                            )
                        }
                    }
                }
            }

            Text(
                "기본 아이콘(흰색 중형차)으로 되돌리기",
                style = AppType.BodySmall,
                color = Concrete.TextDim,
                modifier = Modifier
                    .clickable { car = null }
                    .padding(vertical = 4.dp)
            )

            Text(
                "아이콘이 바뀔 때 앱이 잠깐 재시작될 수 있어요",
                style = AppType.Hint,
                color = Concrete.TextDim
            )

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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(Concrete.Neon, RoundedCornerShape(8.dp))
                        .clickable {
                            val finalCar = car
                            val finalColor = if (finalCar == null) null else color
                            store.appIconCar = finalCar
                            store.appIconColor = finalColor
                            AppIconSwitcher.apply(context, finalCar, finalColor)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("적용", style = AppType.FloorButton, color = Concrete.NeonDeep)
                }
            }
        }
    }
}

/** 미리보기 드로어블 매핑 (car=null이면 기본 아이콘) */
private fun iconPreviewRes(car: String?, color: String): Int = when (car) {
    "kei" -> when (color) {
        "black" -> com.eottadwotji.R.drawable.ic_fg_kei_black
        "gray" -> com.eottadwotji.R.drawable.ic_fg_kei_gray
        else -> com.eottadwotji.R.drawable.ic_fg_kei_white
    }
    "sedan" -> when (color) {
        "black" -> com.eottadwotji.R.drawable.ic_fg_sedan_black
        "gray" -> com.eottadwotji.R.drawable.ic_fg_sedan_gray
        else -> com.eottadwotji.R.drawable.ic_fg_sedan_white
    }
    "suv" -> when (color) {
        "black" -> com.eottadwotji.R.drawable.ic_fg_suv_black
        "gray" -> com.eottadwotji.R.drawable.ic_fg_suv_gray
        else -> com.eottadwotji.R.drawable.ic_fg_suv_white
    }
    "sports" -> when (color) {
        "black" -> com.eottadwotji.R.drawable.ic_fg_sports_black
        "gray" -> com.eottadwotji.R.drawable.ic_fg_sports_gray
        else -> com.eottadwotji.R.drawable.ic_fg_sports_white
    }
    else -> com.eottadwotji.R.drawable.ic_fg_sedan_white // 기본 = 흰색 중형차 (v3.5)
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
                                    sheetMode = sheetMode,
                                    pressureOffsetFloors = profile?.pressureOffsetFloors ?: 0
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

/** 업데이트 확인 (v3.4): 탭 → 릴리스 조회 → 새 버전 있으면 한 번 더 탭해서 설치 */
@Composable
private fun UpdateCheckRow() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("현재 v${BuildConfig.VERSION_NAME}") }
    var pending by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingRow("업데이트 확인", status, enabled = !busy) {
        val update = pending
        if (update != null) {
            status = "다운로드 중 — 완료되면 설치 화면이 떠요"
            busy = true
            UpdateChecker.downloadAndInstall(context, update)
        } else {
            status = "확인 중…"
            busy = true
            UpdateChecker.check(BuildConfig.VERSION_CODE) { result ->
                busy = false
                if (result != null) {
                    pending = result
                    status = "새 버전 ${result.label} — 탭해서 설치"
                } else {
                    status = "최신 버전이에요 (v${BuildConfig.VERSION_NAME})"
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

/** v3.7 아코디언 섹션: 헤더를 누르면 그 자리에서 내려온다 */
@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(12.dp))
            .animateContentSize(animationSpec = tween(180))
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = AppType.Body, color = Concrete.TextMain)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "▴" else "▾",
                style = AppType.BodySmall,
                color = Concrete.TextDim
            )
        }
        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                content()
            }
        }
    }
}

/** ★ 토글: 이 설정을 대시보드 빠른 설정(미리보기)에 올린다 (v3.7) */
@Composable
private fun StarToggle(store: ParkingStore, key: String, onChanged: () -> Unit) {
    val starred = key in store.starredSettings
    Text(
        if (starred) "★" else "☆",
        style = AppType.Body,
        color = if (starred) Concrete.Neon else Concrete.TextDim,
        modifier = Modifier
            .clickable {
                store.starredSettings =
                    if (starred) store.starredSettings - key
                    else store.starredSettings + key
                onChanged()
            }
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
    )
}

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
    star: (@Composable () -> Unit)? = null,
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
        star?.invoke()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    star: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit
) {
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
        star?.invoke()
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

private fun themeModeLabel(mode: String): String = when (mode) {
    ParkingStore.THEME_DARK -> "다크"
    ParkingStore.THEME_LIGHT -> "라이트"
    else -> "시스템"
}
