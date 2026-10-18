package com.splashskip.engine.rules

import com.splashskip.engine.engine.RuleParser
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.RuleSet

/** 一个待解析的模块来源。导入文件的 expectedPackage 来自文件名。 */
data class SourceModule(
    val label: String,
    val text: String,
    val expectedPackage: String? = null,
)

enum class ModuleOrigin {
    BUILT_IN,
    IMPORTED,
}

data class CatalogModule(
    val ruleSet: RuleSet,
    val origin: ModuleOrigin,
    val overridesBuiltIn: Boolean,
    /** 能完整代表该模块时保留原始 JSON；跨文件合并或丢过规则时为 null。 */
    val sourceText: String?,
)

data class RuleSources(
    val builtIns: List<SourceModule> = emptyList(),
    val imported: List<SourceModule> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class CatalogBuildResult(
    val modules: List<CatalogModule>,
    val errors: List<String>,
    /** 所有内置 + 已接受导入模块的规则 id 归属，用于跨模块冲突检查。 */
    val idOwner: Map<String, String>,
    /** 内置规则 id 永久保留，即使当前被导入模块遮蔽。 */
    val builtInRuleIds: Set<String>,
    /** 至少有一条有效规则的内置包名。 */
    val builtInPackages: Set<String>,
)

/**
 * 把“内置模块 + 用户导入模块”合并成唯一一份有效规则目录。
 *
 * 合并规则：
 * 1. 内置模块先按包名跨文件合并，规则 id 全局先到先得（沿用旧版行为）；
 * 2. 导入模块整包覆盖同包名内置模块，不做规则级合并；
 * 3. 导入模块的规则 id 不得与其他包名冲突（被遮蔽的内置 id 也算保留）；
 * 4. 无效的导入文件被忽略并记录错误，内置版本继续生效。
 */
object RuleCatalogBuilder {
    fun build(sources: RuleSources): CatalogBuildResult {
        val errors = sources.errors.toMutableList()
        val idOwner = HashMap<String, String>()
        val builtInRuleIds = HashSet<String>()

        data class BuiltInPackage(
            val rules: MutableList<Rule> = mutableListOf(),
            val sourceTexts: MutableList<String> = mutableListOf(),
            var droppedRules: Boolean = false,
        )

        val builtInByPackage = linkedMapOf<String, BuiltInPackage>()

        sources.builtIns.forEach { source ->
            RuleParser.parse(source.text)
                .onSuccess { ruleSet ->
                    val pkg = ruleSet.packageName
                    val target = builtInByPackage.getOrPut(pkg) { BuiltInPackage() }
                    target.sourceTexts += source.text
                    ruleSet.rules.forEach { rule ->
                        if (idOwner.containsKey(rule.id)) {
                            target.droppedRules = true
                            errors += "${source.label}: 重复规则 id ${rule.id}"
                        } else {
                            idOwner[rule.id] = pkg
                            builtInRuleIds += rule.id
                            target.rules += rule
                        }
                    }
                }
                .onFailure { error ->
                    errors += "${source.label}: ${error.message ?: "解析失败"}"
                }
        }

        val importedByPackage = linkedMapOf<String, CatalogModule>()

        sources.imported.forEach { source ->
            RuleImportValidator.validateText(source.text, source.label)
                .onSuccess { ruleSet ->
                    val pkg = ruleSet.packageName

                    if (source.expectedPackage != null && source.expectedPackage != pkg) {
                        errors += "${source.label}: 文件名与包名不一致" +
                            "（${source.expectedPackage} != $pkg）"
                        return@onSuccess
                    }
                    if (importedByPackage.containsKey(pkg)) {
                        errors += "${source.label}: 重复的导入包名 $pkg"
                        return@onSuccess
                    }

                    val conflict = ruleSet.rules.firstOrNull { rule ->
                        val owner = idOwner[rule.id]
                        owner != null && owner != pkg
                    }
                    if (conflict != null) {
                        errors += "${source.label}: 规则 id ${conflict.id} 与其他包名冲突"
                        return@onSuccess
                    }

                    ruleSet.rules.forEach { rule -> idOwner[rule.id] = pkg }
                    importedByPackage[pkg] = CatalogModule(
                        ruleSet = ruleSet,
                        origin = ModuleOrigin.IMPORTED,
                        overridesBuiltIn = builtInByPackage[pkg]?.rules?.isNotEmpty() == true,
                        sourceText = RuleImportValidator.normalize(source.text),
                    )
                }
                .onFailure { error ->
                    errors += error.message ?: "${source.label}: 校验失败"
                }
        }

        val effective = linkedMapOf<String, CatalogModule>()

        builtInByPackage.forEach { (pkg, builtIn) ->
            if (builtIn.rules.isEmpty()) return@forEach
            val builtInModule = CatalogModule(
                ruleSet = RuleSet(
                    schemaVersion = 1,
                    packageName = pkg,
                    rules = builtIn.rules,
                ),
                origin = ModuleOrigin.BUILT_IN,
                overridesBuiltIn = false,
                sourceText = if (builtIn.sourceTexts.size == 1 && !builtIn.droppedRules) {
                    RuleImportValidator.normalize(builtIn.sourceTexts.single())
                } else {
                    null
                },
            )
            effective[pkg] = importedByPackage[pkg] ?: builtInModule
        }

        importedByPackage.values
            .sortedBy { it.ruleSet.packageName }
            .forEach { module ->
                if (module.ruleSet.packageName !in effective) {
                    effective[module.ruleSet.packageName] = module
                }
            }

        // 防御性检查：正常情况下前两步已经保证 id 全局唯一。
        val seenIds = HashSet<String>()
        val modules = mutableListOf<CatalogModule>()
        effective.values.forEach { module ->
            var droppedAny = false
            val rules = module.ruleSet.rules.filter { rule ->
                if (seenIds.add(rule.id)) {
                    true
                } else {
                    droppedAny = true
                    errors += "${module.ruleSet.packageName}: 规则 id 重复 ${rule.id}"
                    false
                }
            }
            if (rules.isEmpty()) return@forEach
            modules += if (droppedAny) {
                module.copy(ruleSet = module.ruleSet.copy(rules = rules), sourceText = null)
            } else {
                module
            }
        }

        val nonEmptyBuiltInPackages = builtInByPackage
            .filterValues { it.rules.isNotEmpty() }
            .keys
            .toSet()

        return CatalogBuildResult(
            modules = modules,
            errors = errors,
            idOwner = idOwner.toMap(),
            builtInRuleIds = builtInRuleIds.toSet(),
            builtInPackages = nonEmptyBuiltInPackages,
        )
    }
}
