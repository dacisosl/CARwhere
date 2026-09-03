package com.eottadwotji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 층수 휠 피커 (v3.9.2 레이아웃) — 등록된 위치 전용.
 *
 *  ┌───────────┬───────────┐
 *  │           │ ┌╌╌╌╌╌╌╌┐ │
 *  │   휠      │ ┆  메모  ┆ │   ← 점선 필드: 탭하면 메모 모달
 *  │  (1/2)    │ └╌╌╌╌╌╌╌┘ │
 *  │           │ ┌╌╌╌╌╌╌╌┐ │
 *  │           │ ┆  사진  ┆ │   ← 점선 필드: 탭하면 카메라
 *  └───────────┴───────────┘
 *  [        N층으로 저장       ]
 *
 * 드래그로 가운데 창에 층을 맞추고 저장 버튼으로 확정. 스냅마다 진동.
 * 지상층은 포인트 버건디, 지하층은 시그니처 그린 (v5 팔레트).
 */

/** 지상층 강조색 — 주광 앰버 (지하 네온과 대비) */
// v5: 지상 = 포인트 버건디(Concrete.Accent), 지하 = 시그니처 그린(Concrete.Neon)

@Composable
fun FloorWheel(
    floors: List<String>,              // 위(높은 지상층) → 아래(깊은 지하층) 정렬
    initialFloor: String?,             // 지난번/기압 추정 층 — 시작 위치
    selectedFloor: String?,            // 저장 확정된 층 (null이면 아직 선택 중)
    suffixFor: (String) -> String?,    // "지난번" / "기압 추정 · 첫 확인" 등 라벨
    onSave: (String) -> Unit,
    onMemo: (String) -> Unit,
    onPhoto: (String) -> Unit
) {
    val itemHeight = 52.dp
    val visibleRows = 3
    val wheelHeight = itemHeight * visibleRows
    val startIndex = floors.indexOf(initialFloor).takeIf { it >= 0 } ?: (floors.size / 2)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val enabled = selectedFloor == null

    // 뷰포트 중앙에 가장 가까운 항목 = 현재 선택 후보
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - center) }
                ?.index ?: startIndex
        }
    }

    // 스냅 이동마다 진동 — 시계 휠 감각
    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(centerIndex) {
        if (lastHapticIndex != -1 && lastHapticIndex != centerIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastHapticIndex = centerIndex
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            // ── 왼쪽 1/2: 드럼 ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(wheelHeight)
            ) {
                // 가운데 선택 창
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(itemHeight)
                        .background(
                            if (!enabled) Concrete.Neon else Concrete.BgPanel,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.5.dp,
                            if (!enabled) Concrete.Neon else Concrete.Border,
                            RoundedCornerShape(10.dp)
                        )
                )
                LazyColumn(
                    state = listState,
                    flingBehavior = fling,
                    userScrollEnabled = enabled,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        vertical = itemHeight * (visibleRows - 1) / 2
                    )
                ) {
                    itemsIndexed(floors) { index, floor ->
                        val isCenter = index == centerIndex
                        val basement = ParkingLotProfile.isBasement(floor)
                        val tone = if (basement) Concrete.Neon else Concrete.Accent
                        val textColor = when {
                            !enabled && isCenter -> Concrete.NeonDeep
                            isCenter -> tone
                            else -> tone.copy(alpha = 0.35f)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable(enabled = enabled) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                floor,
                                fontSize = if (isCenter) 26.sp else 17.sp,
                                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = com.eottadwotji.ui.theme.DisplayFont,
                                color = textColor
                            )
                            val suffix = suffixFor(floor)
                            if (suffix != null && isCenter) {
                                Text(
                                    suffix,
                                    style = AppType.Hint,
                                    color = if (!enabled) Concrete.NeonDeep else Concrete.TextSub
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // ── 오른쪽 1/2: 메모(상) / 사진(하) 점선 필드 ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(wheelHeight),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashedActionField(
                    label = "메모",
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                ) { onMemo(floors[centerIndex]) }
                DashedActionField(
                    label = "사진",
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                ) { onPhoto(floors[centerIndex]) }
            }
        }

        // ── 저장 확정 버튼 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    if (enabled) Concrete.Neon else Concrete.BgPanel,
                    RoundedCornerShape(8.dp)
                )
                .clickable(enabled = enabled) { onSave(floors[centerIndex]) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (enabled) "${floors.getOrNull(centerIndex) ?: ""} 층으로 저장"
                else "${selectedFloor ?: ""} 저장됨",
                style = AppType.FloorButton,
                color = if (enabled) Concrete.NeonDeep else Concrete.TextDim
            )
        }
    }
}

/** 점선 테두리 액션 필드 — 가운데 라벨, 탭하면 동작 (메모 모달 / 카메라) */
@Composable
private fun DashedActionField(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = Concrete.Border
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(9.dp.toPx(), 7.dp.toPx())
                        )
                    )
                )
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.BodySmall, color = Concrete.TextSub)
    }
}
