package com.splashskip.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splashskip.app.data.SettingsStore
import com.splashskip.app.service.SplashAccessibilityService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(serviceEnabled: Boolean, refreshTick: Int) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var dryRun by remember { mutableStateOf(settings.dryRun) }
    var probeAll by remember { mutableStateOf(settings.probeAll) }
    var stats by remember { mutableStateOf(settings.stats()) }
    var lastHit by remember { mutableStateOf(settings.lastHit()) }

    LaunchedEffect(refreshTick) {
        dryRun = settings.dryRun
        probeAll = settings.probeAll
        stats = settings.stats()
        lastHit = settings.lastHit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceStatusCard(serviceEnabled, probeAll)
        DryRunCard(dryRun, probeAll) { value ->
            dryRun = value
            settings.dryRun = value
        }
        ProbeAllCard(probeAll) { value ->
            probeAll = value
            settings.probeAll = value
        }
        StatsCard(stats)
        LastHitCard(lastHit)
        GuideCard()
        Text(
            text = "无网络权限 · 无广告 · 无内购 · 日志仅保存在本机私有目录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun ServiceStatusCard(enabled: Boolean, probeAll: Boolean) {
    val context = LocalContext.current
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (enabled) "无障碍服务已开启" else "无障碍服务未开启",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = when {
                    !enabled ->
                        "需要手动开启。Android 13+ 若提示“受限设置”，请到应用信息右上角 ⋮ 允许。"

                    probeAll ->
                        "全探测模式：会在任意 App 启动后的 10 秒内读取界面，只记录，不点击。"

                    else ->
                        "只会在目标 App 启动后的 8 秒内读取界面，找到“跳过/关闭”才动作。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openAccessibilitySettings(context) }) {
                    Text("打开无障碍开关")
                }
                TextButton(onClick = { openAppDetails(context) }) {
                    Text("应用信息")
                }
            }
            Text(
                text = "点击后会打开系统无障碍页，并自动定位到“开屏跳过”这一项。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DryRunCard(dryRun: Boolean, probeAll: Boolean, onChange: (Boolean) -> Unit) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "安全模式（dry-run）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "开启时只记录“本应点击”，绝不点击。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = dryRun, onCheckedChange = onChange)
            }
            if (probeAll) {
                Text(
                    text = "全探测模式已开启：dry-run 开关当前不生效，服务始终不会点击。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!dryRun) {
                Text(
                    text = "已关闭 dry-run：满足规则的控件会被真实点击。请先在日志页确认命中正确。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProbeAllCard(probeAll: Boolean, onChange: (Boolean) -> Unit) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "全探测模式（只记录，不点击）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "开启后订阅所有应用；每个应用进入前台后的 10 秒内读取界面。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = probeAll, onCheckedChange = onChange)
            }
            Text(
                text = "只把“跳过/关闭广告”类短文本写入本机日志，绝不点击。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "开关即时生效；关闭后恢复按包名/Activity 规则跳过。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (probeAll) {
                Text(
                    text = "当前为观察模式：不会执行任何点击或返回操作。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "日志会显示：包名、窗口类名、候选文字、viewId、位置、可点击祖先；" +
                    "其他页面文字不会写入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "建议排查完成后关闭，以减少事件订阅和日志量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsCard(stats: SettingsStore.Stats) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今日统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem("命中", stats.hits.toString())
                StatItem("实际跳过", stats.clicks.toString())
            }
            Text(
                text = "统计日期：${stats.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LastHitCard(lastHit: SettingsStore.LastHit?) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "最近命中",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (lastHit == null) {
                Text(
                    text = "暂无记录。冷启动 B 站可触发一次 dry-run。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "规则：${lastHit.ruleId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "时间：${formatDateTime(lastHit.atMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuideCard() {
    val context = LocalContext.current
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "vivo / OriginOS 保活提示",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "1. 设置 → 电池 → 后台高耗电：允许“开屏跳过”。\n" +
                    "2. i管家 → 自启动管理：允许“开屏跳过”。\n" +
                    "3. 多任务卡片下拉，锁定“开屏跳过”。\n" +
                    "4. 电池优化设为“无限制”。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    openSafely(
                        context,
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }) {
                    Text("电池优化设置")
                }
                TextButton(onClick = { openAppDetails(context) }) {
                    Text("应用信息 / 受限设置")
                }
            }
        }
    }
}

/**
 * 打开系统无障碍页，并利用 Settings 的 fragment 参数尽量滚动到本应用的服务。
 * 这个 extra 不是公开 SDK 常量；不支持它的 ROM 会忽略 extra，仍停留在无障碍列表，
 * 所以这里始终保留无 extra 的回退路径。
 */
private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, SplashAccessibilityService::class.java)
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        putExtra(SETTINGS_FRAGMENT_ARGS_KEY, component.flattenToString())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            openSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
}

private const val SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"

private fun openAppDetails(context: Context) {
    openSafely(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

private fun openSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "系统页面不可用", Toast.LENGTH_SHORT).show()
        }
}

private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

internal fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT)
