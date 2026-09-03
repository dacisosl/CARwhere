package com.eottadwotji.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 앱 공용 카드 (v5.2).
 *
 * 화이트 팔레트에서 카드와 바탕 구분이 애매하다는 피드백을 한 곳에서 해결한다:
 * 옅은 그림자 + 1dp 테두리 + 카드색. 모든 화면이 이 modifier를 쓰므로
 * 카드 경계 규칙을 바꿀 때 여기만 고치면 된다.
 */
@Composable
fun Modifier.appCard(radius: Dp = 16.dp): Modifier = this
    .shadow(1.dp, RoundedCornerShape(radius), clip = false)
    .background(Concrete.BgDeep, RoundedCornerShape(radius))
    .border(1.dp, Concrete.Border, RoundedCornerShape(radius))
