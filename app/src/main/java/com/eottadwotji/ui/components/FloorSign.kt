package com.eottadwotji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 층 표지판 (v5.3) — 주차장 벽에 붙은 층 안내판.
 *
 * 조형은 상태바 아이콘(ParkingNotification.renderTextIcon)과 같다: 모서리만 둥근 사각
 * (코너 = 높이의 22%) + 흰 글자.
 *
 * v5.3 색 변경 — 바탕은 층별 색(라임·앰버…)이 아니라 시그니처 딥 파인 그린 하나다.
 * 층별 색은 "어두운 상태바에서 색만 보고 층을 알게" 하려고 고른 형광 계열이라 화면 안
 * 흰 카드 위에서는 너무 튀었다(사용자 피드백). 층별 색은 상태바·알림에만 남는다.
 *
 * 층을 모르거나 주차 중이 아니면 회색 판에 "—".
 */
@Composable
fun FloorSign(
    floor: String?,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lit: Boolean = true,
    /**
     * 꽉 찬 그린 판(false) / 테두리만 있는 흰 판(true).
     * 기록 카드처럼 옆에 그린 [주차] 버튼이 있는 곳은 테두리 판을 쓴다 — 같은 그린 덩어리가
     * 둘이면 어느 쪽이 버튼인지 헷갈린다.
     */
    outlined: Boolean = false,
    cornerFraction: Float = 0.22f
) {
    // 코너는 글자 크기에 비례 — 판이 커져도 같은 비율로 둥글다
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) {
        (fontSize.toPx() * 1.3f * cornerFraction).toDp()
    }
    val shape = RoundedCornerShape(corner)
    val background = when {
        !lit -> Concrete.BgPanel
        outlined -> Concrete.BgDeep
        else -> Concrete.Neon
    }
    Box(
        modifier = modifier
            .background(background, shape)
            .then(
                if (lit && outlined) Modifier.border(2.dp, Concrete.Neon, shape)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (lit) (floor ?: "P") else "—",
            style = AppType.Sign.copy(fontSize = fontSize),
            color = when {
                !lit -> Concrete.TextDim
                outlined -> Concrete.Neon
                else -> Concrete.NeonDeep
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * 작은 사인 배지 — 워드마크의 "P", 위치관리의 지난번 층 등.
 * 색을 직접 넘길 수 있어 층이 아닌 글자(P)도 쓸 수 있다.
 */
@Composable
fun SignBadge(
    text: String,
    background: Color,
    fontSize: TextUnit = 15.sp,
    onBackground: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) {
        (fontSize.toPx() * 1.3f * 0.22f).toDp()
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = fontSize.value.dp * 1.6f)
            .background(background, RoundedCornerShape(corner))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = AppType.Sign.copy(fontSize = fontSize),
            color = onBackground,
            maxLines = 1
        )
    }
}
