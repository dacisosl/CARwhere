package com.eottadwotji.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import kotlin.math.cos
import kotlin.math.sin

/**
 * 엔진 스타트 버튼 스타일 게이지 (v3.9 — 사용자 디자인 방향).
 * 실차 START/STOP 버튼처럼:
 * - 바깥 널링(그립 톱니) 링 + 금속 베젤(그라데이션)
 * - 주차 중이면 백라이트 네온 링이 점등 (시동 ON), 아니면 소등
 * - 가운데 어두운 버튼 면에 층수 + PARKED/EMPTY
 * 버튼답게 탭 가능 — 누르면 기록 시트가 열린다 (onPress).
 */
@Composable
fun CircularGauge(
    floor: String?,      // null이면 층 미입력 ("P" 표시)
    parked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    onPress: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onPress != null) Modifier.clickable(onClick = onPress) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerR = this.size.minDimension / 2f

            // ── 1. 얇은 금속 베젤 — 미니멀한 한 겹 링 (위에서 빛 받는 톤) ──
            val bezelR = outerR - 1.5.dp.toPx()
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF575651), Color(0xFF2A2A28)),
                    start = Offset(center.x, center.y - bezelR),
                    end = Offset(center.x, center.y + bezelR)
                ),
                radius = bezelR,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // ── 2. 백라이트 링 — 주차 중이면 네온 점등 (시동 ON) ──
            val haloR = outerR - 9.dp.toPx()
            if (parked) {
                listOf(8.dp to 0.10f, 4.5.dp to 0.28f).forEach { (w, a) ->
                    drawCircle(
                        color = Concrete.Neon.copy(alpha = a),
                        radius = haloR,
                        center = center,
                        style = Stroke(width = w.toPx())
                    )
                }
            }
            drawCircle(
                color = if (parked) Concrete.Neon else Concrete.Border.copy(alpha = 0.5f),
                radius = haloR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // ── 3. 버튼 면 — 살짝 볼록해 보이는 어두운 원 ──
            val faceR = outerR - 14.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF373733), Color(0xFF1F1F1D)),
                    center = Offset(center.x, center.y - faceR * 0.35f),
                    radius = faceR * 1.6f
                ),
                radius = faceR,
                center = center
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
                color = if (parked) Concrete.NeonLight.copy(alpha = 0.8f) else Concrete.TextDim
            )
        }
    }
}
