package com.splashskip.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.splashskip.app.data.PersistentLogStore
import com.splashskip.app.data.SplashLog

@Composable
fun LogsScreen() {
    val entries by SplashLog.entries.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近 ${entries.size} 条（已持久化）",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                enabled = entries.isNotEmpty(),
                onClick = {
                    val text = entries.joinToString("\n") {
                        "${formatDateTime(it.timeMillis)} ${it.level.name} ${it.message}"
                    }
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("开屏跳过日志", text))
                    Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("复制")
            }
            TextButton(onClick = { SplashLog.clear() }) {
                Text("清空")
            }
        }
        Text(
            text = "仅保存在本机私有目录，不联网；节点文本已脱敏。" +
                "日志最多约 ${PersistentLogStore.MAX_TOTAL_KB} KB，自动轮转删除最旧内容，可随时清空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无日志。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries.asReversed(), key = { it.id }) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: SplashLog.Entry) {
    val color = when (entry.level) {
        SplashLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
        SplashLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
        SplashLog.Level.ERROR -> MaterialTheme.colorScheme.error
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = formatDateTime(entry.timeMillis).takeLast(8),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}
