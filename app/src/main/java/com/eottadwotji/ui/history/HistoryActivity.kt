package com.eottadwotji.ui.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eottadwotji.data.HistoryDb
import com.eottadwotji.data.ParkingRecord
import com.eottadwotji.ui.theme.AppType
import com.eottadwotji.ui.theme.Concrete
import com.eottadwotji.ui.theme.EottadwotjiTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주차 히스토리 전체 목록 (Room) — 대시보드 최근 주차 카드에서 진입 */
class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EottadwotjiTheme {
                HistoryScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val records by remember { HistoryDb.get(context).dao().all() }
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Concrete.BgScreen)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = Concrete.TextSub
                )
            }
            Text("주차 기록", style = AppType.Title, color = Concrete.TextMain)
        }

        if (records.isEmpty()) {
            Spacer(Modifier.size(32.dp))
            Text(
                "아직 기록이 없어요.\n출차하면 여기에 쌓여요.",
                style = AppType.Body,
                color = Concrete.TextSub
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(records) { record -> HistoryRow(record) }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: ParkingRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Concrete.BgDeep, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.lotName ?: "이름 없는 주차장",
                style = AppType.Body,
                color = Concrete.TextBody
            )
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(record.floor, record.zone).joinToString(" · ")
                    .ifEmpty { "층 미입력" },
                style = AppType.BodySmall,
                color = Concrete.TextSub
            )
        }
        val duration = formatDuration(record.startedAt, record.endedAt)
        Text(
            "${formatDateTime(record.startedAt)} · $duration" +
                (record.memo?.let { " · $it" } ?: ""),
            style = AppType.Hint,
            color = Concrete.TextDim
        )
    }
}

private fun formatDateTime(ms: Long): String =
    SimpleDateFormat("M/d a h:mm", Locale.KOREAN).format(Date(ms))

private fun formatDuration(startMs: Long, endMs: Long): String {
    val minutes = ((endMs - startMs) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 60 -> "${minutes}분 주차"
        else -> "${minutes / 60}시간 ${minutes % 60}분 주차"
    }
}
