package com.eottadwotji.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.Concrete

/**
 * 시그니처 그래픽: 미니 단면 (DESIGN.md 4절).
 *
 * 가로 막대를 세로로 쌓은 건물 단면. 층은 위아래 개념이므로 반드시 세로 스택
 * (CLAUDE.md 절대 규칙 2). 내 차가 있는 층만 형광 테두리 + 차 아이콘.
 * 최상단에 지상선(dashed) — 공사 도면 디테일 (DESIGN.md 원칙 4).
 */
@Composable
fun MiniSection(
    floors: List<String>,
    selectedFloor: String?,
    modifier: Modifier = Modifier,
    barWidth: Dp = 72.dp,
    barHeight: Dp = 20.dp
) {
    val sorted = ParkingLotProfile.sortFloors(floors)
    val ground = sorted.filter { !ParkingLotProfile.isBasement(it) }
    val basement = sorted.filter { ParkingLotProfile.isBasement(it) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 지상 층 (높은 층이 위)
        ground.forEach { floor -> FloorBar(floor, floor == selectedFloor, barWidth, barHeight) }
        // 지상선: 지상/지하 경계의 점선 (지상 층이 없으면 맨 위)
        GroundLine(modifier = Modifier.width(barWidth))
        // 지하 층 (깊을수록 아래)
        basement.forEach { floor -> FloorBar(floor, floor == selectedFloor, barWidth, barHeight) }
    }
}

@Composable
private fun FloorBar(floor: String, selected: Boolean, barWidth: Dp, barHeight: Dp) {
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(barHeight)
            .background(Concrete.BgPanel, RoundedCornerShape(4.dp))
            .then(
                if (selected) Modifier.border(
                    2.dp, Concrete.Neon, RoundedCornerShape(4.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_car),
                contentDescription = "내 차 위치",
                tint = Concrete.Neon,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 지상선 (dashed) — 지상/지하 경계. 폭은 modifier로 지정 */
@Composable
fun GroundLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(2.dp)) {
        drawLine(
            color = Concrete.Border,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    }
}
