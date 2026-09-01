package com.eottadwotji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 위치의 층 구성 선택기 (v3) — 지상/지하 2열 세로 레이아웃.
 *
 *   지상        지하
 *    +          B1
 *    3F         B2
 *    2F         B3
 *    1F          +
 *
 * 탭해서 이 주차장이 가진 층만 남기고, "+"로 범위를 위(4F…)/아래(B4…)로 늘린다.
 * 층은 위아래 개념 — 세로 스택 유지 (절대 규칙 2).
 */
@Composable
fun FloorSelector(
    candidates: List<String>,       // 정렬된 후보 (지상 위 → 지하 아래)
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onExtendUp: () -> Unit,
    onExtendDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ground = candidates.filter { !ParkingLotProfile.isBasement(it) }   // 높은 층 먼저
    val basement = candidates.filter { ParkingLotProfile.isBasement(it) } // B1부터 아래로

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 지상 열: 위로 갈수록 높은 층 → "+"가 맨 위
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ColumnHeader("지상")
            FloorChip("+", selected = false, onClick = onExtendUp)
            ground.forEach { floor ->
                FloorChip(floor, floor in selected) { onToggle(floor) }
            }
        }
        // 지하 열: 아래로 갈수록 깊은 층 → "+"가 맨 아래
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ColumnHeader("지하")
            basement.forEach { floor ->
                FloorChip(floor, floor in selected) { onToggle(floor) }
            }
            FloorChip("+", selected = false, onClick = onExtendDown)
        }
    }
}

@Composable
private fun ColumnHeader(label: String) {
    Text(
        label,
        style = AppType.SectionLabel,
        color = Concrete.TextDim,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FloorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                if (selected) Concrete.Neon else Concrete.BgPanel,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (label == "+") Modifier.border(
                    1.dp, Concrete.Border, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
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
