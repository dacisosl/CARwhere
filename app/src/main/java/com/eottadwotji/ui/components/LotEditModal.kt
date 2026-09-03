package com.eottadwotji.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.google.android.gms.location.LocationServices
import java.util.UUID

/**
 * 위치 추가/편집 모달 (v4.3 — 헤더 액션 + 삭제 확인).
 *
 * 대시보드 위치 칩과 바텀시트 위치 선택(✎)에서 함께 쓴다.
 * v4.3 변경: 저장·삭제를 제목 오른쪽 헤더로 올려 스크롤과 무관하게 항상 손에 닿게 하고,
 * 삭제는 휴지통 아이콘 + 확인 모달을 거치게 했다 (되돌릴 수 없는 동작이라).
 * 본문만 스크롤되며 스크롤바는 형광으로 직접 그린다 (기본 스크롤바는 거의 안 보인다).
 */

/** 파괴적 동작 전용 색 — 형광(강조)과 구분되는 경고 톤. 삭제 버튼에만 쓴다. */
private val DangerRed = Color(0xFFCF4A3A)

@Composable
fun LotEditModal(
    store: ParkingStore,
    profile: ParkingLotProfile?,   // null = 새 위치 추가
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
    var confirmDelete by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && selected.isNotEmpty()
    val save = {
        store.saveProfile(
            ParkingLotProfile(
                id = profile?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                latitude = lat,
                longitude = lon,
                floors = ParkingLotProfile.sortFloors(selected.toList()),
                memos = memos.filterKeys { it in selected }.filterValues { it.isNotBlank() },
                lastFloor = profile?.lastFloor,
                sheetMode = sheetMode,
                pressureOffsetFloors = profile?.pressureOffsetFloors ?: 0,
                pressureCalibrated = profile?.pressureCalibrated ?: false
            )
        )
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        // 키보드가 입력 필드를 가리지 않게 — 다이얼로그 창을 키보드만큼 리사이즈 (v3.9.2)
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)
                ?.window
                ?.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
        }

        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .heightIn(max = 600.dp)
                .padding(20.dp)
        ) {
            // ── 헤더: 제목 + 삭제(휴지통) + 저장 — 스크롤과 무관하게 고정 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (profile == null) "위치 추가" else "위치 편집",
                    style = AppType.Title,
                    color = Concrete.TextMain
                )
                Spacer(Modifier.weight(1f))
                if (profile != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                            .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { confirmDelete = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_trash),
                            contentDescription = "이 위치 삭제",
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .background(
                            if (canSave) Concrete.Neon else Concrete.BgPanel,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = canSave) { save() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "저장",
                        style = AppType.FloorButton,
                        color = if (canSave) Concrete.NeonDeep else Concrete.TextDim
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 본문 (스크롤) + 형광 스크롤바 ──
            Box(modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scroll)
                        .padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("위치 이름 (예: 우리 아파트)", style = AppType.BodySmall) },
                        singleLine = true,
                        colors = lotFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 층 구성: 지상 1~3 + 지하 1~3 기본, "+"로 확장, 탭으로 선택
                    Text("층 구성", style = AppType.SectionLabel, color = Concrete.TextSub)
                    FloorSelector(
                        candidates = candidates,
                        selected = selected,
                        onToggle = { floor ->
                            selected =
                                if (floor in selected) selected - floor else selected + floor
                        },
                        onExtendUp = {
                            val next =
                                "${candidates.count { !ParkingLotProfile.isBasement(it) } + 1}F"
                            candidates = ParkingLotProfile.sortFloors(candidates + next)
                        },
                        onExtendDown = {
                            val next =
                                "B${candidates.count { ParkingLotProfile.isBasement(it) } + 1}"
                            candidates = ParkingLotProfile.sortFloors(candidates + next)
                        }
                    )

                    // 이 위치의 바텀시트 모드 (기본값 따름 / 층수만 / 층+메모 / 층+사진)
                    Text("바텀시트", style = AppType.SectionLabel, color = Concrete.TextSub)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            null to "기본값 따름 (${lotSheetModeLabel(store.defaultSheetMode)})",
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
                    Text("층별 메모 (선택)", style = AppType.SectionLabel, color = Concrete.TextSub)
                    ParkingLotProfile.sortFloors(selected.toList()).forEach { floor ->
                        OutlinedTextField(
                            value = memos[floor] ?: "",
                            onValueChange = { memos = memos + (floor to it) },
                            label = { Text("$floor 메모", style = AppType.BodySmall) },
                            singleLine = true,
                            colors = lotFieldColors(),
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
                                    fetchLotLocation(context) { newLat, newLon ->
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
                        locationStatus?.let {
                            Text(it, style = AppType.Hint, color = Concrete.TextDim)
                        }
                    }
                }

                NeonScrollbar(
                    state = scroll,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(5.dp)
                )
            }
        }
    }

    // ── 삭제 확인 ──
    if (confirmDelete && profile != null) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(
                modifier = Modifier
                    .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("'${profile.name}' 삭제할까요?", style = AppType.Title, color = Concrete.TextMain)
                Text(
                    "이 위치의 층 구성·층별 메모·등록 좌표가 함께 지워져요. " +
                        "이미 저장된 주차 기록은 그대로 남아요.",
                    style = AppType.BodySmall,
                    color = Concrete.TextSub
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(Concrete.BgPanel, RoundedCornerShape(8.dp))
                            .clickable { confirmDelete = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("취소", style = AppType.BodySmall, color = Concrete.TextBody)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(DangerRed, RoundedCornerShape(8.dp))
                            .clickable {
                                store.deleteProfile(profile.id)
                                confirmDelete = false
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("삭제", style = AppType.FloorButton, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 형광 스크롤바 — 기본 안드로이드 스크롤바는 이 카드 톤에서 거의 안 보인다.
 * 트랙은 옅은 테두리색, 썸은 형광. 내용이 넘칠 때만 그린다.
 */
@Composable
private fun NeonScrollbar(state: ScrollState, modifier: Modifier) {
    val track = Concrete.Border.copy(alpha = 0.35f)
    val thumb = Concrete.Neon
    Canvas(modifier = modifier) {
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@Canvas

        val viewport = size.height
        val contentHeight = viewport + max
        val thumbHeight = (viewport * viewport / contentHeight)
            .coerceIn(24.dp.toPx(), viewport)
        val progress = state.value.toFloat() / max.toFloat()
        val thumbTop = (viewport - thumbHeight) * progress.coerceIn(0f, 1f)
        val radius = CornerRadius(size.width / 2f, size.width / 2f)

        drawRoundRect(color = track, cornerRadius = radius)
        drawRoundRect(
            color = thumb,
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = radius
        )
    }
}

/** 위치 카드/목록용 층 구성 요약: "1F~B3" */
fun lotFloorsSummary(floors: List<String>): String {
    val sorted = ParkingLotProfile.sortFloors(floors)
    return if (sorted.isEmpty()) "" else "${sorted.first()}~${sorted.last()}"
}

private fun lotSheetModeLabel(mode: String): String = when (mode) {
    ParkingStore.SHEET_FLOOR -> "층수만"
    ParkingStore.SHEET_FLOOR_PHOTO -> "층 + 사진"
    else -> "층 + 메모"
}

@Composable
private fun lotFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Concrete.Neon,
    unfocusedBorderColor = Concrete.Border,
    focusedTextColor = Concrete.TextMain,
    unfocusedTextColor = Concrete.TextMain,
    cursorColor = Concrete.Neon,
    focusedLabelColor = Concrete.TextSub,
    unfocusedLabelColor = Concrete.TextDim
)

private fun fetchLotLocation(context: Context, onResult: (Double?, Double?) -> Unit) {
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
