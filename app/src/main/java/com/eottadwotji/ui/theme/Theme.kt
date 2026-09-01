package com.eottadwotji.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 콘크리트 + 형광 사인 팔레트 (DESIGN.md 2절 컬러값 그대로).
 * 실제 지하주차장에서 형광 사인만 눈에 띄는 그 느낌.
 *
 * 규칙: 한 화면에 형광(Neon) 요소는 1~2개까지.
 * 형광이 흔해지면 사인이 아니라 벽지가 된다.
 */
object Concrete {
    // 콘크리트 (배경/구조)
    val BgScreen = Color(0xFF1E1E1C)   // v2: 화면 최하단 배경
    val BgDeep = Color(0xFF2C2C2A)     // 카드 배경 (v1의 화면 배경)
    val BgPanel = Color(0xFF444441)    // 버튼, 비선택 층, 패널
    val Border = Color(0xFF5F5E5A)     // 기본 테두리, 지상선
    val TextDim = Color(0xFF888780)    // 힌트, 비활성
    val TextSub = Color(0xFFB4B2A9)    // 보조 텍스트
    val TextBody = Color(0xFFD3D1C7)   // 일반 버튼 텍스트
    val TextMain = Color(0xFFF1EFE8)   // 제목, 본문

    // 형광 사인 (강조 — 선택/활성/내 차 위치에만)
    val Neon = Color(0xFF97C459)       // 테두리, 큰 층수 숫자, 주 버튼 배경
    val NeonLight = Color(0xFFC0DD97)  // 형광 위 텍스트, 선택 층 라벨
    val NeonDeep = Color(0xFF173404)   // 형광 배경 위 텍스트
}

/**
 * 타이포 스케일 (DESIGN.md 3절).
 * 층수 숫자가 항상 가장 큰 텍스트. 굵기는 regular/medium 2단계만.
 */
object AppType {
    val FloorBig = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)  // 대시보드 층수
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)     // 화면 제목
    // v2: 자동차 계기판 어휘 — 레터스페이싱 브랜드/라벨
    val Brand = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
    val GaugeFloor = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium)
    val LabelCaps = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
    val FloorButton = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium) // 팝업 층 버튼
    val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val BodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val Hint = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val SectionLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium) // 설정 섹션 제목
}

private val ConcreteColorScheme = darkColorScheme(
    primary = Concrete.Neon,
    onPrimary = Concrete.NeonDeep,
    secondary = Concrete.TextSub,
    onSecondary = Concrete.BgDeep,
    background = Concrete.BgScreen,
    onBackground = Concrete.TextMain,
    surface = Concrete.BgDeep,
    onSurface = Concrete.TextMain,
    surfaceVariant = Concrete.BgPanel,
    onSurfaceVariant = Concrete.TextBody,
    surfaceContainer = Concrete.BgPanel,
    surfaceContainerLow = Concrete.BgPanel,
    surfaceContainerHigh = Concrete.BgPanel,
    surfaceContainerHighest = Concrete.BgPanel,
    outline = Concrete.Border,
    error = Color(0xFFCF6679)
)

@Composable
fun EottadwotjiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ConcreteColorScheme,
        content = content
    )
}
