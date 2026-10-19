package com.example.nodedemo.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nodedemo.data.DemoArmStore

@Composable
fun DemoScreen(serviceEnabled: Boolean, refreshTick: Int) {
    val context = LocalContext.current
    val armStore = remember { DemoArmStore(context) }
    val lastResult = remember(refreshTick) { armStore.lastResult() }
    val lastResultAt = remember(refreshTick) { armStore.lastResultAtMillis() }

    fun openDemo() {
        context.startActivity(Intent(context, DemoActivity::class.java))
    }

    fun armAndOpen(mode: DemoArmStore.Mode) {
        armStore.arm(mode)
        Toast.makeText(context, "已授权一次，60 秒内有效", Toast.LENGTH_SHORT).show()
        openDemo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "无障碍节点演示",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "演示 AccessibilityService 的事件订阅、节点树读取、边界校验，以及点击/手势派发原语。所有动作只作用于本应用自带的演示页。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DemoCard(title = "服务状态") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (serviceEnabled) "已开启" else "未开启",
                    modifier = Modifier.weight(1f),
                    color = if (serviceEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium,
                )
                Button(onClick = { openAccessibilitySettings(context) }) {
                    Text("打开设置")
                }
            }
            Text(
                text = "请在系统设置中找到“节点演示”并手动开启。本应用不会自动修改系统设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DemoCard(title = "一次性演示") {
            Button(
                onClick = { armAndOpen(DemoArmStore.Mode.CLICK) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开启一次点击演示（60 秒）")
            }
            OutlinedButton(
                onClick = { armAndOpen(DemoArmStore.Mode.READ) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("读取一次节点树（只读）")
            }
            TextButton(
                onClick = {
                    armStore.cancel()
                    openDemo()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开演示页（不触发）")
            }
            Text(
                text = "授权只生效一次：完成读取或点击后立即失效；60 秒内未执行也会自动失效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DemoCard(title = "最近一次结果") {
            if (lastResult.isNullOrBlank()) {
                Text("暂无。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(lastResult, style = MaterialTheme.typography.bodyLarge)
                if (lastResultAt > 0L) {
                    Text(
                        text = "时间：${formatDateTime(lastResultAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DemoCard(title = "安全边界") {
            listOf(
                "只订阅本应用包名的事件。",
                "运行时校验事件包名、活动窗口包名和目标节点包名。",
                "只处理演示页的 demo_target_button，不读取或操作其他应用。",
                "不使用 QUERY_ALL_PACKAGES，不申请网络或存储权限。",
                "日志仅保存在本机私有目录，不记录节点文本。",
            ).forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DemoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "无法打开系统无障碍设置", Toast.LENGTH_SHORT).show()
        }
}
