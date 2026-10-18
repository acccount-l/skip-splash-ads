package com.splashskip.app.data

import android.content.Context
import com.splashskip.engine.engine.RuleParser
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.RuleSet

class AssetRuleRepository(private val context: Context) {
    data class LoadResult(
        val ruleSets: List<RuleSet>,
        val errors: List<String>,
    )

    fun loadAll(): LoadResult {
        val errors = mutableListOf<String>()
        val byPackage = linkedMapOf<String, MutableList<Rule>>()
        val seenIds = HashSet<String>()

        val files = try {
            context.assets.list(ASSET_DIR)?.filter { it.endsWith(".json") }.orEmpty()
        } catch (t: Throwable) {
            SplashLog.error("读取规则目录失败：${t.javaClass.simpleName}")
            emptyList()
        }
        if (files.isEmpty()) {
            errors += "assets/$ASSET_DIR 下没有规则文件"
        }

        files.sorted().forEach { file ->
            val text = try {
                context.assets.open("$ASSET_DIR/$file")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            } catch (t: Throwable) {
                errors += "$file: 读取失败"
                return@forEach
            }

            RuleParser.parse(text)
                .onSuccess { ruleSet ->
                    val rules = byPackage.getOrPut(ruleSet.packageName) { mutableListOf() }
                    ruleSet.rules.forEach { rule ->
                        if (!seenIds.add(rule.id)) {
                            errors += "$file: 重复规则 id ${rule.id}"
                        } else {
                            rules += rule
                        }
                    }
                }
                .onFailure { error ->
                    errors += "$file: ${error.message ?: "解析失败"}"
                }
        }

        val ruleSets = byPackage.mapNotNull { (packageName, rules) ->
            if (rules.isEmpty()) {
                null
            } else {
                RuleSet(schemaVersion = 1, packageName = packageName, rules = rules)
            }
        }
        return LoadResult(ruleSets, errors)
    }

    private companion object {
        const val ASSET_DIR = "rules"
    }
}
