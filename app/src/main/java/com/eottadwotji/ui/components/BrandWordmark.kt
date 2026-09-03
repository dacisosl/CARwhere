package com.eottadwotji.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 브랜드 워드마크 (v5.2) — [P] 표지판 + "어따뒀지".
 *
 * v5.1의 네온 글로우 로고타입은 화이트 팔레트에서 촌스럽다는 피드백을 받았다.
 * 글로우·밑선을 버리고, 앱의 조형 언어인 주차 표지판을 그대로 쓴다:
 * 그린 사각 배지에 흰 P, 옆에 잉크 색 Black 굵기 워드마크.
 * 상태바 표지판·홈 층수 타일과 같은 계열이라 브랜드가 한 덩어리로 읽힌다.
 */
@Composable
fun BrandWordmark(
    fontSizeSp: Float = 24f,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SignBadge(
            text = "P",
            background = Concrete.Neon,
            fontSize = (fontSizeSp * 0.62f).sp
        )
        Text(
            "어따뒀지",
            style = AppType.Sign.copy(fontSize = fontSizeSp.sp),
            color = Concrete.TextMain,
            maxLines = 1
        )
    }
}
