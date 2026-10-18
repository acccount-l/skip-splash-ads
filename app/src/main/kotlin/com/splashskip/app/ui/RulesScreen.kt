package com.splashskip.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.splashskip.app.data.LoadedCatalog
import com.splashskip.app.data.RuleCatalogRepository
import com.splashskip.app.data.RuleFileReader
import com.splashskip.app.data.SettingsStore
import com.splashskip.engine.model.Rule
import com.splashskip.engine.rules.CatalogModule
import com.splashskip.engine.rules.ModuleOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RulesScreen(refreshTick: Int) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val repository = remember { RuleCatalogRepository(context) }
    val fileReader = remember { RuleFileReader(context) }
    val scope = rememberCoroutineScope()

    var catalog by remember { mutableStateOf<LoadedCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailure by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var importState by remember { mutableStateOf<ImportUiState>(ImportUiState.Idle) }
    var pendingDelete by remember { mutableStateOf<CatalogModule?>(null) }
    var pendingExport by remember { mutableStateOf<ExportDocument?>(null) }

    LaunchedEffect(refreshTick, reloadToken) {
        loading = true
        val loaded = withContext(Dispatchers.IO) { runCatching { repository.load() } }
        loaded.onSuccess {
            catalog = it
            loadFailure = null
        }.onFailure {
            loadFailure = "加载规则失败：${it.message ?: it.javaClass.simpleName}"
        }
        loading = false
    }

    val exportLauncher = rememberLauncherForActivityResult(CreateDocumentWithName()) { uri ->
        val request = pendingExport
        pendingExport = null
        if (uri == null || request == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                writeDocument(context, uri, request.bytes)
            }
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { "已导出 ${request.fileName}" },
                    onFailure = { "导出失败：${it.message ?: "未知错误"}" },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importState = ImportUiState.Reading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val read = fileReader.read(uris)
                    read to repository.planImport(read.candidates)
                }
            }
            importState = result.fold(
                onSuccess = { (read, plan) ->
                    ImportUiState.Preview(plan = plan, readErrors = read.errors)
                },
                onFailure = { error ->
                    ImportUiState.Done(
                        written = emptyList(),
                        failures = listOf("读取失败：${error.message ?: error.javaClass.simpleName}"),
                    )
                },
            )
        }
    }

    val startExportAll: () -> Unit = {
        val modules = catalog?.modules.orEmpty()
        if (modules.isEmpty()) {
            Toast.makeText(context, "没有可导出的模块", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        ExportDocument(
                            fileName = "splashskip-rules-${exportFileNameSuffix()}.zip",
                            mimeType = "application/zip",
                            bytes = repository.exportAllZip(modules),
                        )
                    }
                }
                result.onSuccess { document ->
                    pendingExport = document
                    exportLauncher.launch(
                        CreateDocumentInput(document.mimeType, document.fileName),
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "导出失败：${error.message ?: "未知错误"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val startExportModule: (CatalogModule) -> Unit = { module ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val packageName = module.ruleSet.packageName
                    ExportDocument(
                        fileName = "$packageName.json",
                        mimeType = "application/json",
                        bytes = repository.exportModuleText(module).toByteArray(Charsets.UTF_8),
                    )
                }
            }
            result.onSuccess { document ->
                pendingExport = document
                exportLauncher.launch(CreateDocumentInput(document.mimeType, document.fileName))
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "导出失败：${error.message ?: "未知错误"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val confirmDelete: (CatalogModule) -> Unit = { module ->
        val packageName = module.ruleSet.packageName
        val restore = module.origin == ModuleOrigin.IMPORTED &&
            packageName in catalog?.builtInPackages.orEmpty()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.deleteImported(packageName)
            }
            result.onSuccess { deleted ->
                val message = when {
                    !deleted -> "没有找到导入模块"
                    restore -> "已恢复内置：${ruleAppLabel(packageName)}"
                    else -> "已删除导入模块：${ruleAppLabel(packageName)}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "操作失败：${error.message ?: "未知错误"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
            reloadToken++
        }
    }

    val applyPreview: (ImportUiState.Preview) -> Unit = { preview ->
        scope.launch {
            importState = ImportUiState.Applying
            val result = withContext(Dispatchers.IO + NonCancellable) {
                runCatching { repository.applyImport(preview.plan.accepted) }
            }
            importState = result.fold(
                onSuccess = { ImportUiState.Done(it.written, it.failures) },
                onFailure = { error ->
                    ImportUiState.Done(
                        written = emptyList(),
                        failures = listOf("写入失败：${error.message ?: error.javaClass.simpleName}"),
                    )
                },
            )
            reloadToken++
        }
    }

    val errors = buildList {
        loadFailure?.let(::add)
        catalog?.errors?.let { addAll(it) }
    }.distinct()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ModuleOverviewCard(
                catalog = catalog,
                loading = loading,
                onReload = {
                    settings.bumpRuleRevision()
                    reloadToken++
                },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onExportAll = startExportAll,
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
        if (errors.isNotEmpty()) {
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
                        errors.take(20).forEach { error ->
                            Text(error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (errors.size > 20) {
                            Text(
                                "另有 ${errors.size - 20} 条问题未显示",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        val modules = catalog?.modules.orEmpty()
        modules.forEach { module ->
            val packageName = module.ruleSet.packageName
            val restoreBuiltIn = packageName in catalog?.builtInPackages.orEmpty()

            item(key = "module-$packageName") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ruleAppLabel(packageName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        ModuleBadge(
                            text = when {
                                module.origin == ModuleOrigin.BUILT_IN -> "内置"
                                module.overridesBuiltIn -> "导入 · 覆盖内置"
                                else -> "导入"
                            },
                            emphasized = module.origin == ModuleOrigin.IMPORTED,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { startExportModule(module) }) {
                            Text("导出")
                        }
                        if (module.origin == ModuleOrigin.IMPORTED) {
                            TextButton(onClick = { pendingDelete = module }) {
                                Text(if (restoreBuiltIn) "恢复内置" else "删除")
                            }
                        }
                    }
                }
            }
            items(module.ruleSet.rules, key = { it.id }) { rule ->
                RuleCard(rule = rule, settings = settings)
            }
        }

        if (modules.isEmpty() && !loading) {
            item {
                Text(
                    text = "没有可用规则。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Text(
                text = "安全边界：包名 + Activity + 文字/正则 + 允许区域 + deny 关键词 + " +
                    "单次点击 + 冷却 + dry-run。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (val state = importState) {
        ImportUiState.Idle -> Unit
        ImportUiState.Reading -> WorkingDialog("正在读取所选文件…")
        ImportUiState.Applying -> WorkingDialog("正在写入导入模块…")
        is ImportUiState.Preview -> ImportPreviewDialog(
            preview = state,
            onDismiss = { importState = ImportUiState.Idle },
            onConfirm = { applyPreview(state) },
        )
        is ImportUiState.Done -> ImportResultDialog(
            result = state,
            onDismiss = { importState = ImportUiState.Idle },
        )
    }

    pendingDelete?.let { module ->
        val packageName = module.ruleSet.packageName
        DeleteModuleDialog(
            packageName = packageName,
            restoreBuiltIn = packageName in catalog?.builtInPackages.orEmpty(),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                confirmDelete(module)
            },
        )
    }
}

@Composable
private fun ModuleOverviewCard(
    catalog: LoadedCatalog?,
    loading: Boolean,
    onReload: () -> Unit,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "规则模块",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReload) {
                    Text("重新加载")
                }
            }
            Text(
                text = "内置 ${catalog?.builtInPackages?.size ?: 0} 个 · " +
                    "当前导入 ${catalog?.importedPackages?.size ?: 0} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport) {
                    Text("导入模块")
                }
                OutlinedButton(onClick = onExportAll) {
                    Text("导出全部")
                }
            }
            Text(
                text = "支持单个 JSON 或 ZIP；导入文件保存在应用私有目录，卸载后删除。" +
                    "同包名导入会整体覆盖内置模块，可随时恢复内置。导入只读取你选择的文件，不联网。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, settings: SettingsStore) {
    var userEnabled by remember(rule.id, rule.enabled) {
        mutableStateOf(settings.isRuleEnabled(rule.id))
    }

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
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = rule.enabled && userEnabled,
                        enabled = rule.enabled,
                        onCheckedChange = {
                            userEnabled = it
                            settings.setRuleEnabled(rule.id, it)
                        },
                    )
                    if (!rule.enabled) {
                        Text(
                            text = "模块内已禁用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
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
