package com.splashskip.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splashskip.engine.rules.ImportPlan
import com.splashskip.engine.rules.ImportPreviewModule
import com.splashskip.engine.rules.ImportStatus
import com.splashskip.engine.rules.Replacement
import java.util.Locale

data class CreateDocumentInput(
    val mimeType: String,
    val fileName: String,
)

/** ACTION_CREATE_DOCUMENT 的标准封装，允许指定建议文件名，不需要 FileProvider。 */
class CreateDocumentWithName : ActivityResultContract<CreateDocumentInput, Uri?>() {
    override fun createIntent(context: Context, input: CreateDocumentInput): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

data class ExportDocument(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

fun writeDocument(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> = runCatching {
    val stream = context.contentResolver.openOutputStream(uri, "wt")
        ?: context.contentResolver.openOutputStream(uri)
        ?: error("无法打开目标文件")
    stream.use { it.write(bytes) }
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Reading : ImportUiState
    data class Preview(
        val plan: ImportPlan,
        val readErrors: List<String>,
    ) : ImportUiState

    data object Applying : ImportUiState
    data class Done(
        val written: List<String>,
        val failures: List<String>,
    ) : ImportUiState
}

@Composable
fun ModuleBadge(text: String, emphasized: Boolean) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
fun WorkingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("请稍候") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(text)
            }
        },
        confirmButton = {},
    )
}

@Composable
fun ImportPreviewDialog(
    preview: ImportUiState.Preview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val plan = preview.plan
    val problems = (preview.readErrors + plan.sourceErrors).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入预览") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "可导入 ${plan.applicableCount} / ${plan.modules.size} 个文件。" +
                        "确认后仅写入标记为“可导入”的模块。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (problems.isNotEmpty()) {
                    Text(
                        text = "读取/现有模块问题",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    problems.take(10).forEach { problem ->
                        Text(
                            text = "• $problem",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                plan.modules.forEach { module ->
                    PreviewModuleCard(module)
                }
                if (plan.modules.isEmpty() && problems.isEmpty()) {
                    Text(
                        text = "没有读取到可导入的 JSON 模块。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "全局 dry-run 仍会覆盖所有导入规则；导入不会修改全局开关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = plan.applicableCount > 0,
            ) {
                Text("导入 ${plan.applicableCount} 个")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun PreviewModuleCard(module: ImportPreviewModule) {
    val applied = module.status == ImportStatus.APPLY
    val color = if (applied) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = (if (applied) "可导入" else "跳过") + " · " + module.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        module.packageName?.let { pkg ->
            Text(
                text = "$pkg · ${replacementLabel(module.replacement)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (applied) {
            Text(
                text = "规则 ${module.ruleCount} · CLICK ${module.clickCount} · " +
                    "BACK ${module.backCount} · dry-run ${module.dryRunCount} · " +
                    "手势 ${module.gestureCount} · 禁用 ${module.disabledCount} · " +
                    "全屏区域 ${module.fullScreenCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        module.messages.forEach { message ->
            Text(
                text = "• $message",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

private fun replacementLabel(replacement: Replacement?): String = when (replacement) {
    Replacement.NEW -> "新增模块"
    Replacement.OVERRIDES_BUILT_IN -> "覆盖内置模块"
    Replacement.REPLACES_IMPORTED -> "替换已导入模块"
    null -> "未应用"
}

@Composable
fun ImportResultDialog(
    result: ImportUiState.Done,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入结果") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (result.written.isEmpty()) {
                    Text("没有模块写入成功。", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("已写入 ${result.written.size} 个模块：")
                    result.written.forEach { pkg ->
                        Text(
                            text = "• ${ruleAppLabel(pkg)}（$pkg）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = "规则服务会在后台重新加载；无需重启无障碍服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.failures.isNotEmpty()) {
                    Text(
                        text = "写入失败",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    result.failures.forEach { failure ->
                        Text(
                            text = "• $failure",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
fun DeleteModuleDialog(
    packageName: String,
    restoreBuiltIn: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (restoreBuiltIn) "恢复内置规则？" else "删除导入模块？") },
        text = {
            Text(
                if (restoreBuiltIn) {
                    "将删除 $packageName 的导入模块，并恢复 APK 内置版本。" +
                        "内置版本会立即重新生效；已导入的修改不会被保留。"
                } else {
                    "将删除 $packageName 的导入模块。该 App 没有内置规则，" +
                        "删除后会从规则页消失。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (restoreBuiltIn) "恢复内置" else "删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

fun ruleAppLabel(packageName: String): String = when (packageName) {
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
    "com.cainiao.wireless" -> "菜鸟"
    "com.greenpoint.android.mc10086.activity" -> "中国移动"
    else -> packageName
}

fun exportFileNameSuffix(): String = String.format(
    Locale.US,
    "%tY-%<tm-%<td",
    java.util.Date(),
)
