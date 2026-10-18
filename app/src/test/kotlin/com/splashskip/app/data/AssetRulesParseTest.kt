package com.splashskip.app.data

import com.splashskip.engine.engine.RuleParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetRulesParseTest {
    @Test
    fun allAssetRulesParseAndHaveUniqueIds() {
        val dir = File("src/main/assets/rules")
        val files = dir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

        assertTrue("assets/rules 下没有规则文件", files.isNotEmpty())

        val ids = mutableSetOf<String>()
        files.forEach { file ->
            val result = RuleParser.parse(file.readText(Charsets.UTF_8))
            assertTrue(
                "${file.name}: ${result.exceptionOrNull()?.message}",
                result.isSuccess,
            )

            result.getOrThrow().rules.forEach { rule ->
                assertTrue("重复规则 id: ${rule.id}", ids.add(rule.id))
                assertTrue("规则 ${rule.id} 缺少包名上下文", rule.packageName.isNotBlank())
            }
        }
    }
}
