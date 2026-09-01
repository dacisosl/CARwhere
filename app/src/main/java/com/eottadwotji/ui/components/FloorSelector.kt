package com.eottadwotji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 위치의 층 구성 선택기 (v3 설정).
 * 후보 층(기본 지상 1~3 + 지하 1~3)을 탭해서 이 주차장이 가진 층만 고르고,
 * "+"로 후보 범위를 위(4F…)/아래(B4…)로 늘린다.
 * 지상/지하 사이에 지상선(dashed) — 건물 단면 메타포 유지.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloorSelector(
    candidates: List<String>,       // 정렬된 후보 (지상 위 → 지하 아래)
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onExtendUp: () -> Unit,
    onExtendDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ground = candidates.filter { !ParkingLotProfile.isBasement(it) }
    val basement = candidates.filter { ParkingLotProfile.isBasement(it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloorChip("+", false, onExtendUp) // 지상 층 추가 (4F…)
            ground.forEach { floor ->
                FloorChip(floor, floor in selected) { onToggle(floor) }
            }
        }
        GroundLine(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            basement.forEach { floor ->
                FloorChip(floor, floor in selected) { onToggle(floor) }
            }
            FloorChip("+", false, onExtendDown) // 지하 층 추가 (B4…)
        }
    }
}

@Composable
private fun FloorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Concrete.Neon else Concrete.BgPanel,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (label == "+") Modifier.border(
                    1.dp, Concrete.Border, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            style = AppType.BodySmall,
            color = when {
                selected -> Concrete.NeonDeep
                label == "+" -> Concrete.TextDim
                else -> Concrete.TextBody
            }
        )
    }
}
