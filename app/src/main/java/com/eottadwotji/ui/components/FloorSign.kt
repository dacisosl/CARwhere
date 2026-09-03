package com.eottadwotji.ui.components

import androidx.compose.foundation.background
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
import com.eottadwotji.ui.theme.FloorTone

/**
 * 층 표지판 (v5.2) — 주차장 벽에 붙은 층 안내판.
 *
 * 상태바 아이콘(ParkingNotification.renderTextIcon)과 같은 조형을 앱 화면에서도 쓴다:
 * 모서리만 둥근 사각(코너 = 높이의 22%), 층별 색 바탕, 잉크 글자.
 * 엔진 스타트 버튼(원형 게이지)을 버리고 이걸로 통일했다 — 가독성이 목적.
 *
 * 층을 모르거나 주차 중이 아니면 회색 판에 "—".
 */
@Composable
fun FloorSign(
    floor: String?,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lit: Boolean = true,
    cornerFraction: Float = 0.22f
) {
    // 코너는 글자 크기에 비례 — 판이 커져도 같은 비율로 둥글다
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) {
        (fontSize.toPx() * 1.3f * cornerFraction).toDp()
    }
    val background = if (lit) FloorTone.color(floor) else Concrete.BgPanel
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(corner))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (lit) (floor ?: "P") else "—",
            style = AppType.Sign.copy(fontSize = fontSize),
            color = if (lit) FloorTone.OnSign else Concrete.TextDim,
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
