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
 * 팔레트 v5 — 화이트 바탕 + 딥 파인 그린(시그니처) + 머스크 버건디(포인트).
 *
 * 면적 비율 원칙: 여백 70 · 잉크 18 · 그린 9 · 버건디 3.
 *   - neon 슬롯 = 딥 파인 그린 #2F6B4F: 핵심 문장, 밑줄, 로고, 링, 토글 — 화면마다 반드시 한 번
 *   - accent 슬롯 = 머스크 버건디 #9E4A5C: 숫자, 태그, 링크 — 한 화면에 한 군데만
 *   - 잉크 블랙 #17191D: 제목·본문 (순수 검정 대신 — 눈의 피로)
 *   - 보조 문구 #6B7380, 구분선 #E1E1E2, 배지 배경 #F5EBEE
 *
 * 슬롯 이름(neon/neonLight/neonDeep)은 v2 코드 호환을 위해 유지한다 —
 * 의미는 "시그니처 색 / 시그니처 강조 텍스트 / 시그니처 배경 위 텍스트".
 *
 * 모든 화면이 Concrete.X로 색을 읽는다 — palette가 전역 상태(mutableStateOf)라
 * 설정에서 테마를 바꾸면 열려 있는 모든 화면이 즉시 다시 그려진다.
 */
data class Palette(
    val bgScreen: Color,   // 화면 바탕 (여백)
    val bgDeep: Color,     // 카드, 바텀시트
    val bgPanel: Color,    // 버튼, 비선택 층, 행
    val border: Color,     // 구분선, 테두리, 지상선
    val textDim: Color,    // 힌트, 비활성
    val textSub: Color,    // 보조 문구
    val textBody: Color,   // 일반 버튼 텍스트
    val textMain: Color,   // 제목, 본문 (잉크)
    val neon: Color,       // 시그니처 — 딥 파인 그린: 링·토글·선택 테두리·로고
    val neonLight: Color,  // 시그니처 강조 텍스트
    val neonDeep: Color,   // 시그니처 배경 위 텍스트
    val accent: Color,     // 포인트 — 머스크 버건디: 숫자·태그·링크 (화면당 한 곳)
    val accentSoft: Color  // 포인트 배지 배경
)

/** 라이트(기본): 화이트 바탕 — 사용자 팔레트 그대로 */
val LightPalette = Palette(
    bgScreen = Color(0xFFFFFFFF),
    bgDeep = Color(0xFFF6F7F6),
    bgPanel = Color(0xFFECEFEC),
    border = Color(0xFFE1E1E2),
    textDim = Color(0xFF9AA0A8),
    textSub = Color(0xFF6B7380),
    textBody = Color(0xFF2F333A),
    textMain = Color(0xFF17191D),
    neon = Color(0xFF2F6B4F),
    neonLight = Color(0xFF2F6B4F),
    neonDeep = Color(0xFFFFFFFF),
    accent = Color(0xFF9E4A5C),
    accentSoft = Color(0xFFF5EBEE)
)

/** 다크(선택): 잉크 바탕에 같은 색상 계열을 밝혀서 — 톤만 뒤집고 정체성은 유지 */
val DarkPalette = Palette(
    bgScreen = Color(0xFF17191D),
    bgDeep = Color(0xFF1F2227),
    bgPanel = Color(0xFF2A2E34),
    border = Color(0xFF3A3F47),
    textDim = Color(0xFF6B7380),
    textSub = Color(0xFF9AA0A8),
    textBody = Color(0xFFC9CDD3),
    textMain = Color(0xFFF2F3F4),
    neon = Color(0xFF4C9A73),
    neonLight = Color(0xFF7FC29B),
    neonDeep = Color(0xFFFFFFFF),
    accent = Color(0xFFC97A8A),
    accentSoft = Color(0xFF3A2A2E)
)

object Concrete {
    /** 현재 팔레트 — EottadwotjiTheme/설정에서 교체 (전역 리컴포지션 트리거) */
    var palette by mutableStateOf(LightPalette)

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
    val Accent get() = palette.accent
    val AccentSoft get() = palette.accentSoft

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
    /** 카드 섹션 라벨 — v4.3에서 11sp→12sp, 호출부는 TextSub로 (너무 안 보였다) */
    val SectionLabel = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
    )

    /** 한 줄 진단·보조 문구 (헤더 신호 표시 등) */
    val Micro = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)
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
