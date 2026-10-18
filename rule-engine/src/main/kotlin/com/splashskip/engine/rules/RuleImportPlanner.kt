package com.splashskip.engine.rules

import com.splashskip.engine.model.Action
import com.splashskip.engine.model.RuleSet

enum class ImportStatus {
    APPLY,
    SKIP,
}

enum class Replacement {
    NEW,
    OVERRIDES_BUILT_IN,
    REPLACES_IMPORTED,
}

data class AcceptedImport(
    val packageName: String,
    val label: String,
    val sourceText: String,
    val ruleSet: RuleSet,
)

data class ImportPreviewModule(
    val packageName: String?,
    val label: String,
    val status: ImportStatus,
    val replacement: Replacement?,
    val hasBuiltInBelow: Boolean,
    val ruleCount: Int,
    val clickCount: Int,
    val backCount: Int,
    val dryRunCount: Int,
    val gestureCount: Int,
    val disabledCount: Int,
    val fullScreenCount: Int,
    val previousRuleCount: Int,
    val messages: List<String>,
)

data class ImportPlan(
    val modules: List<ImportPreviewModule>,
    val accepted: List<AcceptedImport>,
    val sourceErrors: List<String>,
) {
    val applicableCount: Int get() = accepted.size
}

/**
 * 对一次导入批次做预览规划：校验每个候选、检测同批次重复包名和跨包 id 冲突，
 * 生成可直接展示给用户的列表。只有 status=APPLY 的模块会被写入。
 *
 * 批次内的旧导入 id 仍然保留为占用，避免“先替换 A、再让 B 复用 A 旧 id”
 * 在部分写入失败时产生冲突；id 的释放留到下一次导入。
 */
object RuleImportPlanner {
    fun plan(
        builtIns: List<SourceModule>,
        imported: List<SourceModule>,
        candidates: List<TextModule>,
    ): ImportPlan {
        val current = RuleCatalogBuilder.build(
            RuleSources(builtIns = builtIns, imported = imported),
        )
        val owners = current.idOwner.toMutableMap()
        val currentModules = current.modules.associateBy { it.ruleSet.packageName }
        val claimedPackages = mutableSetOf<String>()
        val previews = mutableListOf<ImportPreviewModule>()
        val accepted = mutableListOf<AcceptedImport>()

        candidates.forEachIndexed { index, candidate ->
            val label = candidate.label.ifBlank { "文件 ${index + 1}" }
            val parsed = RuleImportValidator.validateText(candidate.text, label)
            if (parsed.isFailure) {
                previews += skipped(
                    label = label,
                    packageName = null,
                    message = parsed.exceptionOrNull()?.message ?: "校验失败",
                )
                return@forEachIndexed
            }

            val ruleSet = parsed.getOrThrow()
            val pkg = ruleSet.packageName

            if (pkg in claimedPackages) {
                previews += skipped(label, pkg, "同批次重复包名，已跳过")
                return@forEachIndexed
            }

            val conflict = ruleSet.rules.firstOrNull { rule ->
                val owner = owners[rule.id]
                owner != null && owner != pkg
            }
            if (conflict != null) {
                previews += skipped(
                    label = label,
                    packageName = pkg,
                    message = "规则 id ${conflict.id} 与其他包名冲突",
                )
                return@forEachIndexed
            }

            ruleSet.rules.forEach { rule -> owners[rule.id] = pkg }
            claimedPackages += pkg

            val existing = currentModules[pkg]
            val hasBuiltIn = pkg in current.builtInPackages
            val replacement = when {
                existing?.origin == ModuleOrigin.IMPORTED -> Replacement.REPLACES_IMPORTED
                hasBuiltIn -> Replacement.OVERRIDES_BUILT_IN
                else -> Replacement.NEW
            }
            val previousRuleCount = existing?.ruleSet?.rules?.size ?: 0

            previews += ImportPreviewModule(
                packageName = pkg,
                label = label,
                status = ImportStatus.APPLY,
                replacement = replacement,
                hasBuiltInBelow = hasBuiltIn,
                ruleCount = ruleSet.rules.size,
                clickCount = ruleSet.rules.count { it.action == Action.CLICK },
                backCount = ruleSet.rules.count { it.action == Action.BACK },
                dryRunCount = ruleSet.rules.count { it.constraints.dryRun },
                gestureCount = ruleSet.rules.count { it.tapByGesture },
                disabledCount = ruleSet.rules.count { !it.enabled },
                fullScreenCount = ruleSet.rules.count { isFullScreen(it.match.allowedBounds) },
                previousRuleCount = previousRuleCount,
                messages = messagesFor(
                    replacement = replacement,
                    previousRuleCount = previousRuleCount,
                    ruleSet = ruleSet,
                    hasBuiltIn = hasBuiltIn,
                ),
            )
            accepted += AcceptedImport(
                packageName = pkg,
                label = label,
                sourceText = RuleImportValidator.normalize(candidate.text),
                ruleSet = ruleSet,
            )
        }

        return ImportPlan(
            modules = previews,
            accepted = accepted,
            sourceErrors = current.errors,
        )
    }

    private fun messagesFor(
        replacement: Replacement,
        previousRuleCount: Int,
        ruleSet: RuleSet,
        hasBuiltIn: Boolean,
    ): List<String> {
        val messages = mutableListOf<String>()
        messages += when (replacement) {
            Replacement.NEW -> "新增模块"
            Replacement.OVERRIDES_BUILT_IN ->
                "将整体替换内置模块（$previousRuleCount 条 → ${ruleSet.rules.size} 条）"
            Replacement.REPLACES_IMPORTED ->
                if (hasBuiltIn) "将替换现有导入模块（内置版本仍可恢复）"
                else "将替换现有导入模块"
        }

        val disabled = ruleSet.rules.count { !it.enabled }
        if (disabled > 0) messages += "含 $disabled 条模块内禁用规则"
        val fullScreen = ruleSet.rules.count { isFullScreen(it.match.allowedBounds) }
        if (fullScreen > 0) messages += "含 $fullScreen 条全屏允许区域规则"
        if (ruleSet.rules.any { !it.constraints.dryRun }) {
            messages += "含真实点击规则；全局 dry-run 仍会覆盖"
        }
        return messages
    }

    private fun skipped(
        label: String,
        packageName: String?,
        message: String,
    ): ImportPreviewModule = ImportPreviewModule(
        packageName = packageName,
        label = label,
        status = ImportStatus.SKIP,
        replacement = null,
        hasBuiltInBelow = false,
        ruleCount = 0,
        clickCount = 0,
        backCount = 0,
        dryRunCount = 0,
        gestureCount = 0,
        disabledCount = 0,
        fullScreenCount = 0,
        previousRuleCount = 0,
        messages = listOf(message),
    )

    private fun isFullScreen(bounds: List<Float>?): Boolean {
        if (bounds == null || bounds.size != 4) return false
        return bounds[0] <= 0f &&
            bounds[1] <= 0f &&
            bounds[2] >= 1f &&
            bounds[3] >= 1f
    }
}
