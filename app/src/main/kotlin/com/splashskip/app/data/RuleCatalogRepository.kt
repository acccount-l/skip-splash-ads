package com.splashskip.app.data

import android.content.Context
import com.splashskip.engine.model.RuleSet
import com.splashskip.engine.rules.AcceptedImport
import com.splashskip.engine.rules.CatalogModule
import com.splashskip.engine.rules.ExportedModule
import com.splashskip.engine.rules.ImportPlan
import com.splashskip.engine.rules.ModuleOrigin
import com.splashskip.engine.rules.RuleBundleCodec
import com.splashskip.engine.rules.RuleCatalogBuilder
import com.splashskip.engine.rules.RuleImportPlanner
import com.splashskip.engine.rules.RuleJson
import com.splashskip.engine.rules.RuleSources
import com.splashskip.engine.rules.SourceModule
import com.splashskip.engine.rules.TextModule
import java.io.File

data class LoadedCatalog(
    val modules: List<CatalogModule>,
    val ruleSets: List<RuleSet>,
    val errors: List<String>,
    /** 有内置模块的包名（即使当前被导入模块覆盖）。 */
    val builtInPackages: Set<String>,
    /** 当前生效来源是导入文件的包名。 */
    val importedPackages: Set<String>,
)

data class ImportOutcome(
    val written: List<String>,
    val failures: List<String>,
    val catalog: LoadedCatalog,
) {
    val hasWrites: Boolean get() = written.isNotEmpty()
}

/**
 * 规则目录的唯一入口：APK 内置 assets + 应用私有目录中的导入模块。
 * 读取/合并语义全部在 rule-engine 的 RuleCatalogBuilder 中，本类只负责
 * Android 侧的资产读取、落盘和偏好清理。
 */
class RuleCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = RuleModuleStore(File(appContext.filesDir, MODULE_DIR))
    private val settings = SettingsStore(appContext)

    fun readSources(): RuleSources {
        val errors = mutableListOf<String>()
        val builtIns = readBuiltInSources(errors)
        val imported = store.readAll()
        errors += imported.errors
        return RuleSources(
            builtIns = builtIns,
            imported = imported.modules,
            errors = errors,
        )
    }

    fun load(): LoadedCatalog = buildCatalog(readSources())

    fun planImport(candidates: List<TextModule>): ImportPlan {
        val sources = readSources()
        return RuleImportPlanner.plan(
            builtIns = sources.builtIns,
            imported = sources.imported,
            candidates = candidates,
        )
    }

    fun applyImport(accepted: List<AcceptedImport>): ImportOutcome {
        val written = mutableListOf<String>()
        val failures = mutableListOf<String>()
        accepted.forEach { item ->
            store.write(item.packageName, item.sourceText)
                .onSuccess { written += item.packageName }
                .onFailure { error ->
                    failures += "${item.label}: ${error.message ?: "写入失败"}"
                }
        }

        val catalog = load()
        // 资产读取异常导致目录为空时不要清空所有开关；写入本身已成功时仍通知重载。
        if (catalog.modules.isNotEmpty()) {
            settings.pruneRuleToggles(validRuleIds(catalog))
        }
        if (written.isNotEmpty()) settings.bumpRuleRevision()
        return ImportOutcome(written = written, failures = failures, catalog = catalog)
    }

    fun deleteImported(packageName: String): Result<Boolean> =
        store.delete(packageName).onSuccess { deleted ->
            if (deleted) {
                val catalog = load()
                if (catalog.modules.isNotEmpty()) {
                    settings.pruneRuleToggles(validRuleIds(catalog))
                }
                settings.bumpRuleRevision()
            }
        }

    fun exportModuleText(module: CatalogModule): String =
        module.sourceText ?: RuleJson.encode(module.ruleSet)

    fun exportAllZip(modules: List<CatalogModule>): ByteArray =
        RuleBundleCodec.write(
            modules.map { module ->
                ExportedModule(
                    packageName = module.ruleSet.packageName,
                    json = exportModuleText(module),
                )
            },
        )

    private fun buildCatalog(sources: RuleSources): LoadedCatalog {
        val built = RuleCatalogBuilder.build(sources)
        return LoadedCatalog(
            modules = built.modules,
            ruleSets = built.modules.map { it.ruleSet },
            errors = built.errors,
            builtInPackages = built.builtInPackages,
            importedPackages = built.modules
                .filter { it.origin == ModuleOrigin.IMPORTED }
                .map { it.ruleSet.packageName }
                .toSet(),
        )
    }

    private fun validRuleIds(catalog: LoadedCatalog): Set<String> =
        catalog.modules.flatMap { module -> module.ruleSet.rules.map { it.id } }.toSet()

    private fun readBuiltInSources(errors: MutableList<String>): List<SourceModule> {
        val files = try {
            appContext.assets.list(ASSET_DIR)
                ?.filter { it.endsWith(".json") }
                .orEmpty()
                .sorted()
        } catch (t: Throwable) {
            errors += "读取内置规则目录失败：${t.javaClass.simpleName}"
            emptyList()
        }
        if (files.isEmpty()) {
            errors += "assets/$ASSET_DIR 下没有规则文件"
            return emptyList()
        }

        return files.mapNotNull { file ->
            try {
                val text = appContext.assets.open("$ASSET_DIR/$file")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                SourceModule(label = file, text = text)
            } catch (t: Throwable) {
                errors += "$file: 读取失败"
                null
            }
        }
    }

    private companion object {
        const val ASSET_DIR = "rules"
        const val MODULE_DIR = "rule-modules"
    }
}
