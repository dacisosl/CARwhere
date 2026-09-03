package com.eottadwotji.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.LightPalette

/**
 * 브랜드 워드마크 "어따뒀지" — 네온 사인 타이포 (v4.3).
 *
 * 스플래시의 점등 연출과 같은 조형을 정지 상태로 쓴다:
 * 넓은 라임 헤일로 + 백열 코어 2겹 글로우, 좁은 자간, 단어 폭만큼의 헤어라인.
 * 글로우는 Paint.setShadowLayer로 그려 API 26부터 하드웨어 가속에서 그대로 동작한다
 * (Modifier.blur는 API 31+ 전용이라 쓰지 않는다).
 *
 * 한글은 디스플레이 폰트(Chakra Petch)에 글리프가 없어 시스템 볼드로 그린다.
 * 라이트 테마에서는 백열 코어가 배경에 묻히므로 본문색 코어 + 옅은 라임 글로우로 바꾼다.
 */
@Composable
fun BrandWordmark(
    text: String = "어따뒀지",
    fontSizeSp: Float = 26f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val bold = remember { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold } }
    val glyphs = remember(text) { text.map(Char::toString) }

    val light = Concrete.palette == LightPalette
    val neon = Concrete.Neon
    // 다크: 관 안쪽이 하얗게 타는 백열 코어 / 라이트: 본문색 코어 (묻히지 않게)
    val core = if (light) Concrete.TextMain else androidx.compose.ui.graphics.Color(0xFFF4FFD1)

    val fontPx = with(density) { fontSizeSp.sp.toPx() }
    val trackingPx = with(density) { 2.5.dp.toPx() }
    val glowPx = with(density) { 13.dp.toPx() }

    paint.textSize = fontPx
    val widths = glyphs.map { paint.measureText(it) }
    val wordPx = widths.sum() + trackingPx * (glyphs.size - 1)

    val widthDp = with(density) { (wordPx + glowPx * 2).toDp() }
    val heightDp = with(density) { (fontPx * 1.18f + glowPx + 8.dp.toPx()).toDp() }

    Canvas(modifier = modifier.size(widthDp, heightDp)) {
        val baseline = fontPx * 0.92f + glowPx * 0.5f
        val startX = (size.width - wordPx) / 2f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            var x = startX
            glyphs.forEachIndexed { index, glyph ->
                // 1겹: 넓은 라임 헤일로
                paint.color = neon.copy(alpha = if (light) 0.35f else 0.92f).toArgb()
                paint.setShadowLayer(
                    glowPx, 0f, 0f, neon.copy(alpha = if (light) 0.30f else 0.75f).toArgb()
                )
                native.drawText(glyph, x, baseline, paint)

                // 2겹: 코어
                paint.color = core.toArgb()
                paint.setShadowLayer(
                    glowPx * 0.32f, 0f, 0f, core.copy(alpha = 0.8f).toArgb()
                )
                native.drawText(glyph, x, baseline, paint)

                x += widths[index] + trackingPx
            }
            paint.clearShadowLayer()
        }

        // 헤어라인 — 단어 폭에 딱 맞는 네온 밑선
        val ruleY = baseline + fontPx * 0.24f
        drawLine(
            color = neon.copy(alpha = if (light) 0.55f else 0.45f),
            start = Offset(startX, ruleY),
            end = Offset(startX + wordPx, ruleY),
            strokeWidth = with(density) { 1.5.dp.toPx() },
            cap = StrokeCap.Round
        )
    }
}
