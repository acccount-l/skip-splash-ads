package com.splashskip.app.data

import com.splashskip.engine.rules.ModuleOrigin
import com.splashskip.engine.rules.RuleCatalogBuilder
import com.splashskip.engine.rules.RuleImportValidator
import com.splashskip.engine.rules.RuleSources
import com.splashskip.engine.rules.SourceModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleCatalogAssetsTest {
    @Test
    fun allBuiltInAssetsBuildWithoutErrors() {
        val files = File("src/main/assets/rules")
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

        assertTrue("assets/rules 下没有规则文件", files.isNotEmpty())

        val sources = files.map { file ->
            SourceModule(label = file.name, text = file.readText(Charsets.UTF_8))
        }
        val result = RuleCatalogBuilder.build(RuleSources(builtIns = sources))

        assertEquals(emptyList<String>(), result.errors)
        assertEquals(20, result.modules.size)
        assertEquals(33, result.modules.sumOf { it.ruleSet.rules.size })
        assertTrue(result.modules.all { it.origin == ModuleOrigin.BUILT_IN })
        assertTrue(result.modules.all { it.sourceText != null })

        val packageNames = result.modules.map { it.ruleSet.packageName }
        assertEquals(packageNames.size, packageNames.toSet().size)
        assertTrue(packageNames.none { RuleImportValidator.isBlockedPackage(it) })
        assertTrue(result.modules.all { module -> module.ruleSet.rules.isNotEmpty() })
        assertNotNull(result.idOwner)
    }
}
