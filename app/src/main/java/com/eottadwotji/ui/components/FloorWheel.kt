package com.eottadwotji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eottadwotji.R
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 층수 휠 피커 (v3.9) — iOS 시계 스타일 드럼.
 * 등록된 위치(층 구성이 확정된 곳) 전용: 드래그로 돌려 가운데 창에 층을 맞추고
 * 하단 저장 버튼으로 확정한다. 스냅될 때마다 진동 피드백.
 *
 * - 지상층은 앰버(주광), 지하층은 네온 그린(지하 사인) 계열로 색을 나눈다
 * - 우측 세로 바: ▲ 한 층 위 / 메모 / 사진 / ▼ 한 층 아래
 * - 지난번·기압 추정 층은 초기 위치로 미리 맞춰져 있다
 */

/** 지상층 강조색 — 주광 앰버 (지하 네온과 대비) */
private val GroundAmber = Color(0xFFFFC24B)

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── 드럼 ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(itemHeight * visibleRows)
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        vertical = itemHeight * (visibleRows - 1) / 2
                    )
                ) {
                    itemsIndexed(floors) { index, floor ->
                        val isCenter = index == centerIndex
                        val basement = ParkingLotProfile.isBasement(floor)
                        val tone = if (basement) Concrete.Neon else GroundAmber
                        val textColor = when {
                            !enabled && isCenter -> Concrete.NeonDeep
                            isCenter -> tone
                            else -> tone.copy(alpha = 0.35f)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clickable(enabled = enabled) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                floor,
                                fontSize = if (isCenter) 26.sp else 17.sp,
                                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                            val suffix = suffixFor(floor)
                            if (suffix != null && isCenter) {
                                Spacer(Modifier.width(8.dp))
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

            // ── 우측 컨트롤 바: ▲ / 메모 / 사진 / ▼ ──
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WheelSideButton(label = "▲", enabled = enabled) {
                    scope.launch {
                        listState.animateScrollToItem((centerIndex - 1).coerceAtLeast(0))
                    }
                }
                WheelSideButton(label = "✎", enabled = enabled) {
                    onMemo(floors[centerIndex])
                }
                WheelSideButton(iconRes = R.drawable.ic_camera, enabled = enabled) {
                    onPhoto(floors[centerIndex])
                }
                WheelSideButton(label = "▼", enabled = enabled) {
                    scope.launch {
                        listState.animateScrollToItem(
                            (centerIndex + 1).coerceAtMost(floors.lastIndex)
                        )
                    }
                }
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

@Composable
private fun WheelSideButton(
    label: String? = null,
    iconRes: Int? = null,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Concrete.BgPanel, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Concrete.TextSub,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(label ?: "", style = AppType.Body, color = Concrete.TextSub)
        }
    }
}
