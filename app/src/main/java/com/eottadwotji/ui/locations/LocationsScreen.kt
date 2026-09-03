package com.eottadwotji.ui.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eottadwotji.data.ParkingLotProfile
import com.eottadwotji.data.ParkingStore
import com.eottadwotji.ui.components.LotEditModal
import com.eottadwotji.ui.components.lotFloorsSummary
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 위치 관리 페이지 (v5.0) — 하단 탭 두 번째.
 *
 * 등록된 주차장을 목록으로 보고, 탭하면 편집 모달(이름·층 구성·메모·좌표·삭제),
 * 우상단 "+ 추가"로 새 위치. 대시보드에서 하던 편집을 전부 여기로 옮겼다.
 * 지금 주차 중인 위치는 네온 테두리로 표시.
 */
@Composable
fun LocationsScreen() {
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }
    var version by remember { mutableIntStateOf(0) }
    val lots = remember(version) { store.profiles().sortedBy { it.name } }
    val currentLotId = remember(version) {
        if (store.hasActiveParking()) store.currentLot()?.id else null
    }

    var editing by remember { mutableStateOf<ParkingLotProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("위치 관리", style = AppType.Title, color = Concrete.TextMain)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .background(Concrete.Neon, RoundedCornerShape(8.dp))
                    .clickable { creating = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ 추가", style = AppType.BodySmall, color = Concrete.NeonDeep)
            }
        }
        Text(
            "자주 가는 주차장을 등록하면 근처(150m)에 주차할 때 그 층 구성이 바로 떠요",
            style = AppType.Hint,
            color = Concrete.TextDim,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
        )

        if (lots.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Concrete.BgDeep, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("등록된 위치가 없어요", style = AppType.Body, color = Concrete.TextSub)
                Spacer(Modifier.height(6.dp))
                Text(
                    "집·회사처럼 자주 가는 곳부터 하나 추가해 보세요",
                    style = AppType.Hint,
                    color = Concrete.TextDim
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(lots, key = { it.id }) { lot ->
                    LotRow(
                        lot = lot,
                        active = lot.id == currentLotId,
                        onClick = { editing = lot }
                    )
                }
            }
        }
    }

    if (editing != null || creating) {
        LotEditModal(
            store = store,
            profile = editing,
            onDismiss = {
                editing = null
                creating = false
                version++
            }
        )
    }
}

@Composable
private fun LotRow(lot: ParkingLotProfile, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(14.dp))
            .then(
                if (active) Modifier.border(1.5.dp, Concrete.Neon, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 좌표 등록 여부 점: 등록됨(네온) / 미등록(어둡게)
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (lot.latitude != null) Concrete.Neon else Concrete.Border,
                    CircleShape
                )
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lot.name, style = AppType.Body, color = Concrete.TextMain)
                if (active) {
                    Spacer(Modifier.size(8.dp))
                    // 태그 = 머스크 버건디 배지 — 이 화면의 포인트색은 여기 한 곳
                    Text(
                        "지금 여기",
                        style = AppType.Micro,
                        color = Concrete.Accent,
                        modifier = Modifier
                            .background(Concrete.AccentSoft, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                listOfNotNull(
                    lotFloorsSummary(lot.floors),
                    if (lot.latitude != null) "위치 등록됨" else "위치 미등록",
                    lot.lastFloor?.let { "지난번 $it" }
                ).joinToString(" · "),
                style = AppType.Hint,
                color = Concrete.TextDim
            )
        }
        Text("✎", style = AppType.BodySmall, color = Concrete.TextDim)
    }
}
