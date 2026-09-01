package com.eottadwotji.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 자동차 계기판 무드의 원형 게이지 (DESIGN v2).
 * 형광 테두리 3px 안에 층수 24px + "PARKED" 라벨.
 * 주차 없을 땐 회색 테두리에 "—".
 */
@Composable
fun CircularGauge(
    floor: String?,      // null이면 층 미입력 ("P" 표시)
    parked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp
) {
    val ringColor = if (parked) Concrete.Neon else Concrete.Border
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ringColor,
                style = Stroke(width = 3.dp.toPx())
            )
            // 계기판 디테일: 안쪽 희미한 링
            drawCircle(
                color = Concrete.BgPanel,
                radius = this.size.minDimension / 2 - 8.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (parked) (floor ?: "P") else "—",
                style = AppType.GaugeFloor,
                color = if (parked) Concrete.Neon else Concrete.TextDim
            )
            Text(
                text = if (parked) "PARKED" else "EMPTY",
                style = AppType.LabelCaps,
                color = Concrete.TextDim
            )
        }
    }
}
