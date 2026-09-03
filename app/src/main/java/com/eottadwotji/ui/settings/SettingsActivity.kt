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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
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
import com.eottadwotji.ui.theme.appCard
import com.eottadwotji.ui.theme.EottadwotjiTheme
import com.google.android.gms.location.LocationServices
import java.util.UUID

/**
 * 설정 — 항목 순서: 바텀시트 / 알림 / 감지 / 앱 아이콘 / 기타 / 배터리 안내.
 * v5.0: 하단 탭 "설정"에 임베드된다 (SettingsScreen(embedded = true)).
 * 이 액티비티는 알림·다른 화면에서 직접 열 때의 진입점으로 남겨둔다.
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

/**
 * @param onClose 뒤로 버튼 동작. null이면 뒤로 버튼을 그리지 않는다 (탭 임베드).
 * @param embedded 탭 안에 있으면 하단은 탭바가 맡으므로 상단 패딩만 준다.
 */
@Composable
fun SettingsScreen(onClose: (() -> Unit)? = null, embedded: Boolean = false) {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }
    var refresh by remember { mutableIntStateOf(0) }

    val sheetMode = remember(refresh) { store.defaultSheetMode }
    val pressureOn = remember(refresh) { store.pressureAutoDetect }
    val myCarName = remember(refresh) { store.myCarName }
    val overlayGranted = remember(refresh) { Settings.canDrawOverlays(context) }
    val themeMode = remember(refresh) { store.themeMode }
    val confirmCard = remember(refresh) { store.confirmBeforeDone }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    var activeSheet by remember { mutableStateOf<String?>(null) }
    var showIconModal by remember { mutableStateOf(false) }
    val iconCar = remember(refresh) { store.appIconCar }
    val iconColor = remember(refresh) { store.appIconColor }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .then(if (embedded) Modifier.statusBarsPadding() else Modifier.systemBarsPadding())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                        tint = Concrete.TextSub
                    )
                }
            }
            Text("설정", style = AppType.Title, color = Concrete.TextMain)
        }

        // v3.7: 아코디언 구조 — 섹션을 누르면 그 자리에서 접힌다. 기본은 전부 열림 (v3.9)
        var openSections by remember {
            mutableStateOf(setOf("바텀시트", "알림", "감지", "앱 아이콘", "기타", "배터리"))
        }
        val toggleSection: (String) -> Unit = { name ->
            openSections =
                if (name in openSections) openSections - name else openSections + name
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccordionSection("바텀시트", "바텀시트" in openSections, { toggleSection("바텀시트") }) {
                SettingRow("기본 동작", sheetModeLabel(sheetMode)) { activeSheet = "sheet_mode" }
                SettingRow(
                    "등록 확인",
                    if (confirmCard) "확인 카드" else "바로 등록"
                ) { activeSheet = "confirm_card" }
                Text(
                    "위치마다 다르게 하려면 위치관리 탭에서 그 위치를 열어 바꿔주세요",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            AccordionSection("알림", "알림" in openSections, { toggleSection("알림") }) {
                SettingRow("앱 알림 설정 열기", "카테고리·중요도 확인") {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                }
                Text(
                    "삼성 폰에서 상태바 캡슐이 사라지면: 폰 설정 > 알림 > 고급 설정 > " +
                        "상태 표시줄 > \"모든 알림\"으로 바꿔주세요 (기본값은 최근 3개만 표시라 " +
                        "다른 알림이 오면 캡슐이 밀려나요)",
                    style = AppType.Hint,
                    color = Concrete.TextDim,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            AccordionSection("감지", "감지" in openSections, { toggleSection("감지") }) {
                SwitchRow("자동감지", pressureOn) {
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
                    if (overlayGranted) "켜짐" else "알림으로 대체"
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
                SettingRow("테마", themeModeLabel(themeMode)) { activeSheet = "theme" }
                UpdateCheckRow()
                SettingRow("주차 기록", "전체 보기") {
                    context.startActivity(
                        Intent(context, com.eottadwotji.ui.history.HistoryActivity::class.java)
                    )
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
        "theme" -> OptionSheet(
            title = "테마",
            options = listOf(
                ParkingStore.THEME_LIGHT to "라이트 (기본)",
                ParkingStore.THEME_DARK to "다크",
                ParkingStore.THEME_SYSTEM to "시스템 따라가기"
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
            .appCard(12.dp)
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
            Text(
                title,
                style = AppType.Body,
                color = Concrete.TextMain,
                modifier = Modifier.weight(1f)
            )
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

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = AppType.SectionLabel,
        color = Concrete.TextDim,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp, start = 4.dp)
    )
}

/**
 * 설정 한 줄 — 왼쪽 라벨, 오른쪽 값.
 * v5.2: 라벨이 길면 값과 겹쳐 보이던 문제 — 라벨에 weight(1f), 값은 폭 상한 + 우측 정렬
 * 2줄까지 허용해 서로 밀어내지 않게 했다.
 */
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
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppType.Body,
            color = if (enabled) Concrete.TextBody else Concrete.TextDim,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = AppType.BodySmall,
            color = Concrete.TextSub,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 150.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
            .padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = AppType.Body,
            color = Concrete.TextBody,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
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
