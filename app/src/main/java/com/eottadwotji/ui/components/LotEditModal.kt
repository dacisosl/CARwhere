package com.eottadwotji.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.google.android.gms.location.LocationServices
import java.util.UUID

/**
 * 위치 추가/편집 모달 (v3.9.5 — 공용 컴포넌트로 분리).
 * 대시보드 위치 카드와 바텀시트 위치 선택(✎)에서 함께 쓴다.
 * 이름·층 구성·위치별 바텀시트 모드·층별 메모·좌표 등록·삭제.
 */
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

    Dialog(onDismissRequest = onDismiss) {
        // 키보드가 저장 버튼을 가리지 않게 — 다이얼로그 창을 키보드만큼 리사이즈 (v3.9.2)
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)
                ?.window
                ?.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
        }
        // 제목과 삭제·저장 버튼은 스크롤 밖에 고정 — 내용(층별 메모 등)이 길어져도
        // 버튼이 화면 밖으로 밀려나지 않는다. 가운데 입력 영역만 스크롤.
        Column(
            modifier = Modifier
                .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (profile == null) "위치 추가" else "위치 편집",
                style = AppType.Title,
                color = Concrete.TextMain
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
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
                Text("층 구성", style = AppType.SectionLabel, color = Concrete.TextDim)
                FloorSelector(
                    candidates = candidates,
                    selected = selected,
                    onToggle = { floor ->
                        selected = if (floor in selected) selected - floor else selected + floor
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
                Text("바텀시트", style = AppType.SectionLabel, color = Concrete.TextDim)
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
                Text("층별 메모 (선택)", style = AppType.SectionLabel, color = Concrete.TextDim)
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
                                    pressureOffsetFloors = profile?.pressureOffsetFloors ?: 0,
                                    pressureCalibrated = profile?.pressureCalibrated ?: false
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
