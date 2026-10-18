package com.splashskip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splashskip.app.data.AssetRuleRepository
import com.splashskip.app.data.SettingsStore
import com.splashskip.engine.model.Rule

@Composable
fun RulesScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var loadResult by remember {
        mutableStateOf(AssetRuleRepository(context).loadAll())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "内置规则",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    loadResult = AssetRuleRepository(context).loadAll()
                }) {
                    Text("重新加载")
                }
            }
        }
        item {
            Text(
                text = "规则来自 APK 内置 assets，不联网更新。开关即时生效；修改规则文件需重新安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (settings.probeAll) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "全探测模式已开启：下列规则和 dry-run 均不参与，服务只记录候选，不点击。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (loadResult.errors.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("规则加载问题", fontWeight = FontWeight.SemiBold)
                        loadResult.errors.forEach { error ->
                            Text(error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        loadResult.ruleSets.forEach { ruleSet ->
            item(key = ruleSet.packageName) {
                Text(
                    text = appLabel(ruleSet.packageName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = ruleSet.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ruleSet.rules, key = { it.id }) { rule ->
                RuleCard(rule = rule, settings = settings)
            }
        }

        if (loadResult.ruleSets.isEmpty()) {
            item {
                Text(
                    text = "没有可用规则。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Text(
                text = "安全边界：包名 + Activity + 文字/正则 + 允许区域 + deny 关键词 + 单次点击 + 冷却 + dry-run。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, settings: SettingsStore) {
    var enabled by remember(rule.id) { mutableStateOf(settings.isRuleEnabled(rule.id)) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.id,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Activity 白名单 ${rule.activityIds.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        settings.setRuleEnabled(rule.id, it)
                    },
                )
            }
            Text(
                text = "匹配：${describeMatch(rule)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "动作：${rule.action}" +
                    (if (rule.tapByGesture) "（手势点击）" else "") +
                    " · dry-run：" +
                    if (rule.constraints.dryRun) "强制开启" else "跟随全局",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "窗口：${rule.constraints.withinMsAfterLaunch} ms · " +
                    "冷却：${rule.constraints.cooldownMs} ms · " +
                    "最多 ${rule.constraints.maxAttempts} 次",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun describeMatch(rule: Rule): String {
    val parts = mutableListOf<String>()
    rule.match.textRegex?.let { parts += "text~/$it/" }
    if (rule.match.texts.isNotEmpty()) parts += "text in ${rule.match.texts}"
    rule.match.contentDescriptionRegex?.let { parts += "desc~/$it/" }
    rule.match.viewIdRegex?.let { parts += "id~/$it/" }
    rule.match.className?.let { parts += "class=$it" }
    parts += "区域=${rule.match.allowedBounds ?: "默认右上角"}"
    return parts.joinToString("；")
}

private fun appLabel(packageName: String): String = when (packageName) {
    "tv.danmaku.bili" -> "哔哩哔哩"
    "com.zhihu.android" -> "知乎"
    "com.taobao.taobao" -> "淘宝"
    "com.jingdong.app.mall" -> "京东"
    "com.xunmeng.pinduoduo" -> "拼多多"
    "com.ss.android.ugc.aweme" -> "抖音"
    "com.xingin.xhs" -> "小红书"
    "com.sina.weibo" -> "微博"
    "com.baidu.searchbox" -> "百度"
    "com.autonavi.minimap" -> "高德地图"
    "com.sankuai.meituan" -> "美团"
    "ctrip.android.view" -> "携程旅行"
    "com.max.xiaoheihe" -> "小黑盒"
    "com.baidu.BaiduMap" -> "百度地图"
    "com.netease.cloudmusic" -> "网易云音乐"
    "com.tencent.qqmusic" -> "QQ音乐"
    "com.youdao.dict" -> "网易有道词典"
    "com.taobao.idlefish" -> "闲鱼"
    else -> packageName
}
