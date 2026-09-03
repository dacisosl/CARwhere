package com.eottadwotji.ui.splash

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eottadwotji.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * 시네마틱 스플래시 (v4.3 — 네온 사인 워드마크).
 *
 * 지하주차장으로 차가 들어와 네온 링 "B3 PARKED"가 그려지는 영상(res/raw, ~4.3초)을
 * 로딩시간과 무관하게 항상 온전히 재생하고, 영상 후반부에 브랜드 워드마크가 점등된다.
 *
 * 워드마크 연출 — 영상 속 주차장 형광등·네온 링과 같은 언어로:
 *   1. 자간이 넓게 벌어진 상태에서 글자가 아래에서 떠오르며
 *   2. 형광등이 켜지듯 글자마다 순서대로 스트라이크(툭 켜졌다 꺼졌다 안착)
 *   3. 켜지는 동안 자간이 조여들어 단어가 "자리를 잡는다"
 *   4. 안착하면 유리관 위로 하이라이트가 한 번 스윕
 *   5. 아래로 헤어라인이 가운데서 양쪽으로 그려지고 태그라인이 올라온다
 *
 * 글로우는 Paint.setShadowLayer 2겹(넓은 라임 헤일로 + 좁은 백열 코어)으로,
 * 하드웨어 가속 캔버스에서도 그대로 동작한다 (Modifier.blur는 API 31+ 전용이라 미사용).
 *
 * 프로세스당 1회만 재생 (SplashGate). 영상 오류/지연 시 5.5초 안전장치로 통과.
 */
object SplashGate {
    var shown = false
}

private const val BRAND = "어따뒀지"
private const val TAGLINE = "기록은 3초 · 확인은 0초"

/** 네온관 색 (v5 시그니처 = 딥 파인 그린): 어두운 영상 위라 밝힌 파인 그린 관 + 연녹 백열 코어 */
private const val TUBE_RGB = 0x4C9A73
private const val CORE_RGB = 0xE6F6EC

// 연출 타임라인 (ms, 영상 재생 시작 기준) — 영상 길이 4.3초 안에 끝난다
private const val RULE_START = 2_480L
private const val RULE_MS = 300f
private const val BRAND_START = 2_600L
private const val GLYPH_STEP = 100L
private const val RISE_MS = 240f
private const val SETTLE_MS = 600f
private const val SWEEP_START = 3_320L
private const val SWEEP_MS = 560f
private const val TAG_START = 3_260L
private const val TAG_MS = 420f
private const val SEQUENCE_END = 3_950L

@Composable
fun CinematicSplash(onDone: () -> Unit) {
    var finished by remember { mutableStateOf(false) }

    // 연출 전체를 재생 경과 시간 하나로 계산한다 (프레임 동기, 끝나면 루프 종료)
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (elapsedMs < SEQUENCE_END) {
            elapsedMs = (withFrameNanos { it } - startNanos) / 1_000_000L
        }
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (finished) 0f else 1f,
        animationSpec = tween(300),
        label = "splashFade"
    )

    // 재생 완료(또는 안전장치) → 페이드아웃 → 대시보드
    LaunchedEffect(finished) {
        if (finished) {
            delay(320)
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        delay(5_500) // 영상이 어떤 이유로든 안 끝나면 강제 통과
        finished = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A09))
            .alpha(screenAlpha)
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(
                        Uri.parse("android.resource://${ctx.packageName}/${R.raw.splash_video}")
                    )
                    setOnPreparedListener { mp ->
                        mp.setVolume(0f, 0f)
                        mp.isLooping = false
                        start()
                    }
                    setOnCompletionListener { finished = true }
                    setOnErrorListener { _, _, _ ->
                        finished = true
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        NeonWordmark(
            elapsedMs = elapsedMs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(116.dp)
        )
    }
}

/**
 * 네온 사인 워드마크 — 글자별 점등 + 자간 수축 + 하이라이트 스윕 + 헤어라인 + 태그라인.
 * 한글은 디스플레이 폰트(Chakra Petch)에 글리프가 없어 시스템 볼드로 그린다.
 */
@Composable
private fun NeonWordmark(elapsedMs: Long, modifier: Modifier) {
    val glyphs = remember { BRAND.map(Char::toString) }
    val bold = remember { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val wordPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold }
    }
    val sweepPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold }
    }
    val tagPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.22f
        }
    }

    Canvas(modifier = modifier) {
        val fontPx = 40.sp.toPx()
        val baseline = 62.dp.toPx()
        val ruleY = 82.dp.toPx()
        val tagBaseline = 106.dp.toPx()
        val centerX = size.width / 2f

        wordPaint.textSize = fontPx
        val widths = glyphs.map { wordPaint.measureText(it) }

        // 자간: 넓게 벌어진 상태 → 켜지면서 조여든다
        val settle = easeOut((elapsedMs - BRAND_START) / SETTLE_MS)
        val tracking = lerp(16.dp.toPx(), 7.dp.toPx(), settle)
        val wordWidth = widths.sum() + tracking * (glyphs.size - 1)
        val startX = centerX - wordWidth / 2f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // ── 글자별 점등 ──
            var x = startX
            glyphs.forEachIndexed { index, glyph ->
                val local = elapsedMs - (BRAND_START + index * GLYPH_STEP)
                val level = strikeLevel(local)
                if (level > 0f) {
                    val rise = (1f - easeOut(local / RISE_MS)) * 15.dp.toPx()
                    val y = baseline - rise

                    // 1겹: 넓은 라임 헤일로 (유리관이 주변 콘크리트를 물들이는 빛)
                    wordPaint.color = argb(TUBE_RGB, 0.92f * level)
                    wordPaint.setShadowLayer(
                        22.dp.toPx(), 0f, 0f, argb(TUBE_RGB, 0.80f * level)
                    )
                    native.drawText(glyph, x, y, wordPaint)

                    // 2겹: 백열 코어 (관 안쪽이 하얗게 타는 부분)
                    wordPaint.color = argb(CORE_RGB, level)
                    wordPaint.setShadowLayer(
                        7.dp.toPx(), 0f, 0f, argb(CORE_RGB, 0.85f * level)
                    )
                    native.drawText(glyph, x, y, wordPaint)
                }
                x += widths[index] + tracking
            }
            wordPaint.clearShadowLayer()

            // ── 하이라이트 스윕: 유리관 위를 한 번 지나가는 반사 ──
            val sweep = (elapsedMs - SWEEP_START) / SWEEP_MS
            if (sweep > 0f && sweep < 1f) {
                val band = 60.dp.toPx()
                val bandX = lerp(startX - band, startX + wordWidth + band, sweep)
                sweepPaint.textSize = fontPx
                sweepPaint.shader = LinearGradient(
                    bandX - band / 2f, 0f, bandX + band / 2f, 0f,
                    intArrayOf(argb(CORE_RGB, 0f), argb(0xFFFFFF, 1f), argb(CORE_RGB, 0f)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                sweepPaint.alpha = (sin(PI * sweep) * 190).toInt().coerceIn(0, 255)
                var sx = startX
                glyphs.forEachIndexed { index, glyph ->
                    native.drawText(glyph, sx, baseline, sweepPaint)
                    sx += widths[index] + tracking
                }
                sweepPaint.shader = null
            }

            // ── 태그라인: 제품의 약속 한 줄 ──
            val tag = easeOut((elapsedMs - TAG_START) / TAG_MS)
            if (tag > 0f) {
                tagPaint.textSize = 10.sp.toPx()
                tagPaint.color = argb(0xB4B2A9, 0.9f * tag)
                native.drawText(TAGLINE, centerX, tagBaseline - (1f - tag) * 6.dp.toPx(), tagPaint)
            }
        }

        // ── 헤어라인: 가운데서 양쪽으로 그려진다 ──
        val rule = easeOut((elapsedMs - RULE_START) / RULE_MS)
        if (rule > 0f) {
            val half = (wordWidth / 2f) * rule
            drawLine(
                color = Color(0xFF4C9A73).copy(alpha = 0.5f * rule),
                start = Offset(centerX - half, ruleY),
                end = Offset(centerX + half, ruleY),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 형광등 스트라이크 곡선 — 툭 켜졌다 꺼지고, 다시 붙었다 한 번 흔들린 뒤 안착.
 * 부드러운 페이드가 아니라 계단식인 것이 의도다 (주차장 형광등의 그 느낌).
 */
private fun strikeLevel(localMs: Long): Float = when {
    localMs < 0L -> 0f
    localMs < 40L -> 0.80f
    localMs < 80L -> 0.12f
    localMs < 130L -> 1f
    localMs < 165L -> 0.40f
    else -> 1f
}

private fun easeOut(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    val inv = 1f - c
    return 1f - inv * inv * inv
}

private fun lerp(from: Float, to: Float, t: Float): Float =
    from + (to - from) * t.coerceIn(0f, 1f)

/** RGB 상수 + 알파(0~1) → ARGB 정수 */
private fun argb(rgb: Int, alpha: Float): Int =
    ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (rgb and 0x00FFFFFF)
