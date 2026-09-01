package com.eottadwotji.ui.splash

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eottadwotji.R
import com.eottadwotji.ui.theme.DisplayFont
import kotlinx.coroutines.delay

/**
 * 시네마틱 스플래시 (v4.1 — 실사 영상).
 *
 * 지하주차장으로 차가 들어와 네온 링 "B3 PARKED"가 그려지는 영상(res/raw, ~4.3초)을
 * 로딩시간과 무관하게 항상 온전히 재생한다. 영상 후반부에 "어따뒀지" 네온 타이포가
 * 형광등 플리커로 점등 → 재생 완료 시 페이드아웃 → 대시보드.
 *
 * 프로세스당 1회만 재생 (SplashGate). 영상 오류/지연 시 5.5초 안전장치로 통과.
 */
object SplashGate {
    var shown = false
}

private val NeonGreen = Color(0xFFAEEA00)

@Composable
fun CinematicSplash(onDone: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    var brandAlpha by remember { mutableFloatStateOf(0f) }

    val screenAlpha by animateFloatAsState(
        targetValue = if (finished) 0f else 1f,
        animationSpec = tween(300),
        label = "splashFade"
    )

    // 영상 후반부에 브랜드 타이포 점등 (형광등 플리커)
    LaunchedEffect(Unit) {
        delay(2900)
        val flicker = listOf(0.9f to 40L, 0.1f to 60L, 1f to 50L, 0.25f to 70L, 1f to 600L)
        for ((a, holdMs) in flicker) {
            brandAlpha = a
            delay(holdMs)
        }
    }

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

        Text(
            "어따뒀지",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DisplayFont,
            letterSpacing = 6.sp,
            color = NeonGreen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .alpha(brandAlpha)
        )
    }
}
