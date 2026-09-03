package com.eottadwotji.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eottadwotji.ui.dashboard.DashboardScreen
import com.eottadwotji.ui.locations.LocationsScreen
import com.eottadwotji.ui.settings.SettingsScreen
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete

/**
 * 메인 셸 (v5.0) — 하단 탭 3개: 홈 / 위치관리 / 설정.
 *
 * 사용자 스케치: 대시보드는 경과시간·층수·사진·지도만 남기고, 위치 편집과 설정은
 * 각자의 페이지로 나갔다. 탭 상태는 rememberSaveable — 회전·프로세스 복원 후에도 유지.
 * 시스템 바: 각 화면이 상단(statusBarsPadding)을, 탭바가 하단(navigationBarsPadding)을 맡는다.
 */
private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    LOCATIONS("위치관리", Icons.Filled.Place),
    SETTINGS("설정", Icons.Filled.Settings)
}

@Composable
fun MainShell() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex.coerceIn(0, Tab.entries.size - 1)]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> DashboardScreen()
                Tab.LOCATIONS -> LocationsScreen()
                Tab.SETTINGS -> SettingsScreen(embedded = true)
            }
        }
        BottomNav(selected = tab, onSelect = { tabIndex = it.ordinal })
    }
}

/** 콘크리트 톤 탭바 — 선택 탭만 네온 (절대 규칙 3: 화면당 형광 1~2개) */
@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep)
            .navigationBarsPadding()
    ) {
        // 상단 헤어라인
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Concrete.Border.copy(alpha = 0.6f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interaction,
                            indication = null // 계기판 버튼 느낌 — 리플 없이 색만 바뀐다
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 선택 인디케이터: 아이콘 위 짧은 네온 바
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .background(
                                if (active) Concrete.Neon else Concrete.BgDeep,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Concrete.Neon else Concrete.TextDim,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        style = AppType.Micro,
                        color = if (active) Concrete.NeonLight else Concrete.TextDim,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
