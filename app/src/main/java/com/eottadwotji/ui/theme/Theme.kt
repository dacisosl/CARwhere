package com.eottadwotji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eottadwotji.data.ParkingStore

/**
 * 콘크리트 + 네온 사인 팔레트 (DESIGN.md 기반, v3.3에서 라이트 모드 추가).
 *
 * 모든 화면이 Concrete.X로 색을 읽는다 — palette가 전역 상태(mutableStateOf)라
 * 설정에서 테마를 바꾸면 열려 있는 모든 화면이 즉시 다시 그려진다.
 * 규칙: 한 화면에 네온 요소는 1~2개까지 (절대 규칙 3).
 */
data class Palette(
    val bgScreen: Color,   // 화면 최하단 배경
    val bgDeep: Color,     // 카드, 바텀시트
    val bgPanel: Color,    // 버튼, 비선택 층, 행
    val border: Color,     // 테두리, 지상선
    val textDim: Color,    // 힌트, 비활성
    val textSub: Color,    // 보조 텍스트
    val textBody: Color,   // 일반 버튼 텍스트
    val textMain: Color,   // 제목, 본문
    val neon: Color,       // 네온 라임 — 강조 배경/링/큰 숫자
    val neonLight: Color,  // 강조 텍스트 (배경 위)
    val neonDeep: Color    // 네온 배경 위 텍스트
)

/** 다크(기본): 지하주차장 콘크리트 + 밝은 네온 라임 */
val DarkPalette = Palette(
    bgScreen = Color(0xFF1E1E1C),
    bgDeep = Color(0xFF2C2C2A),
    bgPanel = Color(0xFF444441),
    border = Color(0xFF5F5E5A),
    textDim = Color(0xFF888780),
    textSub = Color(0xFFB4B2A9),
    textBody = Color(0xFFD3D1C7),
    textMain = Color(0xFFF1EFE8),
    neon = Color(0xFFAEEA00),      // v3.3: 더 밝은 네온 라임 (구 #97C459)
    neonLight = Color(0xFFD3FF57),
    neonDeep = Color(0xFF1F3D00)
)

/** 라이트: 밝은 콘크리트 + 가독성 위해 살짝 깊은 라임 */
val LightPalette = Palette(
    bgScreen = Color(0xFFF1EFE9),
    bgDeep = Color(0xFFFFFFFF),
    bgPanel = Color(0xFFE8E5DB),
    border = Color(0xFFC6C3B8),
    textDim = Color(0xFF98968B),
    textSub = Color(0xFF75746A),
    textBody = Color(0xFF45443F),
    textMain = Color(0xFF21211E),
    neon = Color(0xFF9CCF00),
    neonLight = Color(0xFF55791A),  // 라이트 배경 위 강조 텍스트는 어둡게
    neonDeep = Color(0xFF1F3D00)
)

object Concrete {
    /** 현재 팔레트 — EottadwotjiTheme/설정에서 교체 (전역 리컴포지션 트리거) */
    var palette by mutableStateOf(DarkPalette)

    val BgScreen get() = palette.bgScreen
    val BgDeep get() = palette.bgDeep
    val BgPanel get() = palette.bgPanel
    val Border get() = palette.border
    val TextDim get() = palette.textDim
    val TextSub get() = palette.textSub
    val TextBody get() = palette.textBody
    val TextMain get() = palette.textMain
    val Neon get() = palette.neon
    val NeonLight get() = palette.neonLight
    val NeonDeep get() = palette.neonDeep

    /** store.themeMode에 따라 팔레트 적용 */
    fun apply(themeMode: String, systemDark: Boolean) {
        palette = when (themeMode) {
            ParkingStore.THEME_LIGHT -> LightPalette
            ParkingStore.THEME_DARK -> DarkPalette
            else -> if (systemDark) DarkPalette else LightPalette
        }
    }
}

/**
 * 계기판 디스플레이 폰트 (v3.9) — 층수 숫자·게이지 캡션 등 "계기" 요소 전용.
 * 라틴/숫자만 포함, 한글은 시스템 폰트로 자동 폴백된다.
 */
val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(
        com.eottadwotji.R.font.chakra_petch_bold,
        FontWeight.Bold
    )
)

/**
 * 타이포 스케일 (DESIGN.md 3절).
 * 층수 숫자가 항상 가장 큰 텍스트. 굵기는 regular/medium 2단계만.
 */
object AppType {
    val FloorBig = TextStyle(
        fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFont
    )
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val Brand = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    val GaugeFloor = TextStyle(
        fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFont
    )
    val LabelCaps = TextStyle(
        fontSize = 9.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp, fontFamily = DisplayFont
    )
    val FloorButton = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val BodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val Hint = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val SectionLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun EottadwotjiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }
    val systemDark = isSystemInDarkTheme()

    // 저장된 테마 모드 반영 (설정 토글 시에는 Concrete.apply가 직접 호출됨)
    remember(systemDark) {
        Concrete.apply(store.themeMode, systemDark)
        true
    }

    val p = Concrete.palette

    // 라이트 팔레트에서는 상태바·내비바 아이콘을 어둡게 (v3.6 — 흰 시계가 안 보이던 문제)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = p == LightPalette
                controller.isAppearanceLightNavigationBars = p == LightPalette
            }
        }
    }
    val scheme = if (p == LightPalette) {
        lightColorScheme(
            primary = p.neon, onPrimary = p.neonDeep,
            secondary = p.textSub, onSecondary = p.bgDeep,
            background = p.bgScreen, onBackground = p.textMain,
            surface = p.bgDeep, onSurface = p.textMain,
            surfaceVariant = p.bgPanel, onSurfaceVariant = p.textBody,
            surfaceContainer = p.bgPanel, surfaceContainerLow = p.bgPanel,
            surfaceContainerHigh = p.bgPanel, surfaceContainerHighest = p.bgPanel,
            outline = p.border
        )
    } else {
        darkColorScheme(
            primary = p.neon, onPrimary = p.neonDeep,
            secondary = p.textSub, onSecondary = p.bgDeep,
            background = p.bgScreen, onBackground = p.textMain,
            surface = p.bgDeep, onSurface = p.textMain,
            surfaceVariant = p.bgPanel, onSurfaceVariant = p.textBody,
            surfaceContainer = p.bgPanel, surfaceContainerLow = p.bgPanel,
            surfaceContainerHigh = p.bgPanel, surfaceContainerHighest = p.bgPanel,
            outline = p.border
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
