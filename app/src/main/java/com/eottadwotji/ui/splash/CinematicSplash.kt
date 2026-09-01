package com.eottadwotji.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eottadwotji.R
import com.eottadwotji.ui.theme.DisplayFont
import kotlinx.coroutines.delay

/**
 * 인앱 시네마틱 스플래시 (v3.9.2).
 *
 * 시스템 스플래시는 로딩이 끝나면 잘려버려 연출이 항상 끊겼다 —
 * 그래서 로딩과 무관하게 "항상 온전히" 재생되는 인앱 연출로 옮겼다.
 *
 * 타임라인 (~2.2초 고정):
 *   0.0s  검은 화면에서 주차 아트 슬라이드-인 프레임 재생 (splash_f00~f11)
 *   1.0s  안착 홀드 + 살짝 줌 (시네마틱 settle)
 *   1.2s  "어따뒀지" 네온 타이포 점등 — 형광등 플리커 (꺼짐→번쩍→꺼짐→점등)
 *   1.9s  전체 페이드아웃 → 대시보드
 *
 * 프로세스당 1회만 재생 (SplashGate).
 */
object SplashGate {
    var shown = false
}

private val FRAMES = intArrayOf(
    R.drawable.splash_f00, R.drawable.splash_f01, R.drawable.splash_f02,
    R.drawable.splash_f03, R.drawable.splash_f04, R.drawable.splash_f05,
    R.drawable.splash_f06, R.drawable.splash_f07, R.drawable.splash_f08,
    R.drawable.splash_f09, R.drawable.splash_f10, R.drawable.splash_f11
)

private val NeonGreen = Color(0xFFAEEA00)

@Composable
fun CinematicSplash(onDone: () -> Unit) {
    var frame by remember { mutableIntStateOf(0) }
    var brandAlpha by remember { mutableFloatStateOf(0f) }
    var settled by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // 안착 후 살짝 줌인 (Ken Burns), 종료 시 페이드아웃
    val artScale by animateFloatAsState(
        targetValue = if (settled) 1.06f else 1f,
        animationSpec = tween(900),
        label = "artScale"
    )
    val screenAlpha by animateFloatAsState(
        targetValue = if (finished) 0f else 1f,
        animationSpec = tween(300),
        label = "screenAlpha"
    )

    LaunchedEffect(Unit) {
        // 1) 슬라이드-인 프레임 (원본 애니메이션 타이밍 재현)
        val durations = intArrayOf(55, 55, 55, 55, 55, 55, 55, 55, 70, 70, 90, 120)
        for (i in FRAMES.indices) {
            frame = i
            delay(durations[i].toLong())
        }
        settled = true
        delay(250)

        // 2) 네온 타이포 플리커 점등 (형광등 켜지는 연출)
        val flicker = listOf(0.9f to 40L, 0.1f to 60L, 1f to 50L, 0.25f to 70L, 1f to 400L)
        for ((a, holdMs) in flicker) {
            brandAlpha = a
            delay(holdMs)
        }

        // 3) 페이드아웃 → 대시보드
        finished = true
        delay(320)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0D))
            .alpha(screenAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(FRAMES[frame]),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .scale(artScale)
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "어따뒀지",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = DisplayFont,
                letterSpacing = 6.sp,
                color = NeonGreen,
                modifier = Modifier.alpha(brandAlpha)
            )
        }
    }
}
