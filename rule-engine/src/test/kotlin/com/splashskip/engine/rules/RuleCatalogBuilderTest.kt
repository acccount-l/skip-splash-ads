package com.splashskip.engine.rules

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleCatalogBuilderTest {
    private fun builtIn(label: String, packageName: String, vararg rules: String) =
        SourceModule(label, RuleTestData.json(packageName, *rules))

    @Test
    fun `merges built in files with same package`() {
        val sources = RuleSources(
            builtIns = listOf(
                builtIn("a.json", RuleTestData.DEMO, RuleTestData.rule("a_rule")),
                builtIn("b.json", RuleTestData.DEMO, RuleTestData.rule("b_rule")),
            ),
        )

        val result = RuleCatalogBuilder.build(sources)

        val module = result.modules.single()
        assertEquals(RuleTestData.DEMO, module.ruleSet.packageName)
        assertEquals(listOf("a_rule", "b_rule"), module.ruleSet.rules.map { it.id })
        assertEquals(ModuleOrigin.BUILT_IN, module.origin)
        assertNull(module.sourceText)
        assertEquals(emptyList(), result.errors)
    }

    @Test
    fun `keeps raw source text for a single file built in module`() {
        val text = RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_rule"))
        val result = RuleCatalogBuilder.build(RuleSources(builtIns = listOf(SourceModule("demo.json", text))))

        assertEquals(text, result.modules.single().sourceText)
    }

    @Test
    fun `drops duplicate built in rule id across packages`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = listOf(
                    builtIn("a.json", RuleTestData.DEMO, RuleTestData.rule("shared")),
                    builtIn("b.json", RuleTestData.OTHER, RuleTestData.rule("shared")),
                ),
            ),
        )

        val module = result.modules.single()
        assertEquals(RuleTestData.DEMO, module.ruleSet.packageName)
        assertTrue(result.errors.any { it.contains("重复规则 id shared") })
        assertEquals(setOf("shared"), result.builtInRuleIds)
        assertEquals(setOf(RuleTestData.DEMO), result.builtInPackages)
        assertFalse(RuleTestData.OTHER in result.builtInPackages)
    }

    @Test
    fun `imported module replaces built in package`() {
        val builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("demo_rule")))
        val importedText = RuleTestData.json(
            RuleTestData.DEMO,
            RuleTestData.rule("demo_rule", textRegex = "^关闭$"),
        )

        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = builtIns,
                imported = listOf(SourceModule("demo.json", importedText, RuleTestData.DEMO)),
            ),
        )

        val module = result.modules.single()
        assertEquals(ModuleOrigin.IMPORTED, module.origin)
        assertTrue(module.overridesBuiltIn)
        assertEquals(importedText, module.sourceText)
        assertEquals("^关闭$", module.ruleSet.rules.single().match.textRegex)
        assertEquals(setOf(RuleTestData.DEMO), result.builtInPackages)
        assertEquals(setOf("demo_rule"), result.builtInRuleIds)
    }

    @Test
    fun `imported module without built in is appended sorted`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("demo_rule"))),
                imported = listOf(
                    SourceModule(
                        "b.json",
                        RuleTestData.json("com.example.b", RuleTestData.rule("b_rule")),
                        "com.example.b",
                    ),
                    SourceModule(
                        "a.json",
                        RuleTestData.json("com.example.a", RuleTestData.rule("a_rule")),
                        "com.example.a",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(RuleTestData.DEMO, "com.example.a", "com.example.b"),
            result.modules.map { it.ruleSet.packageName },
        )
        assertEquals(ModuleOrigin.BUILT_IN, result.modules[0].origin)
        assertEquals(ModuleOrigin.IMPORTED, result.modules[1].origin)
        assertFalse(result.modules[1].overridesBuiltIn)
    }

    @Test
    fun `invalid imported module falls back to built in`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("demo_rule"))),
                imported = listOf(SourceModule("broken.json", "{broken}", RuleTestData.DEMO)),
            ),
        )

        assertEquals(ModuleOrigin.BUILT_IN, result.modules.single().origin)
        assertTrue(result.errors.any { it.contains("broken.json") })
    }

    @Test
    fun `imported file name must match package`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                imported = listOf(
                    SourceModule(
                        "wrong.json",
                        RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("other_rule")),
                        RuleTestData.DEMO,
                    ),
                ),
            ),
        )

        assertTrue(result.modules.isEmpty())
        assertTrue(result.errors.any { it.contains("文件名与包名不一致") })
    }

    @Test
    fun `imported id conflicting with another package is rejected`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("shared"))),
                imported = listOf(
                    SourceModule(
                        "other.json",
                        RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("shared")),
                        RuleTestData.OTHER,
                    ),
                ),
            ),
        )

        assertEquals(listOf(RuleTestData.DEMO), result.modules.map { it.ruleSet.packageName })
        assertTrue(result.errors.any { it.contains("规则 id shared 与其他包名冲突") })
    }

    @Test
    fun `shadowed built in ids stay reserved`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("demo_rule"))),
                imported = listOf(
                    SourceModule(
                        "demo.json",
                        RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_new")),
                        RuleTestData.DEMO,
                    ),
                    SourceModule(
                        "other.json",
                        RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("demo_rule")),
                        RuleTestData.OTHER,
                    ),
                ),
            ),
        )

        assertEquals(listOf(RuleTestData.DEMO), result.modules.map { it.ruleSet.packageName })
        assertEquals(setOf("demo_new"), result.modules.single().ruleSet.rules.map { it.id }.toSet())
        assertTrue(result.errors.any { it.contains("规则 id demo_rule 与其他包名冲突") })
    }

    @Test
    fun `duplicate imported package keeps first valid`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                imported = listOf(
                    SourceModule(
                        "first.json",
                        RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("first_rule")),
                        RuleTestData.DEMO,
                    ),
                    SourceModule(
                        "second.json",
                        RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("second_rule")),
                        RuleTestData.DEMO,
                    ),
                ),
            ),
        )

        assertEquals(listOf("first_rule"), result.modules.single().ruleSet.rules.map { it.id })
        assertTrue(result.errors.any { it.contains("重复的导入包名") })
        assertFalse("second_rule" in result.idOwner)
    }

    @Test
    fun `imported module can reuse its own built in id`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("demo_rule"))),
                imported = listOf(
                    SourceModule(
                        "demo.json",
                        RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_rule")),
                        RuleTestData.DEMO,
                    ),
                ),
            ),
        )

        assertEquals(ModuleOrigin.IMPORTED, result.modules.single().origin)
        assertEquals(emptyList(), result.errors)
    }

    @Test
    fun `restoring built in after import keeps ids unique`() {
        val builtIns = listOf(builtIn("demo.json", RuleTestData.DEMO, RuleTestData.rule("demo_rule")))
        val imported = listOf(
            SourceModule(
                "demo.json",
                RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("imported_new")),
                RuleTestData.DEMO,
            ),
        )

        val overridden = RuleCatalogBuilder.build(
            RuleSources(builtIns = builtIns, imported = imported),
        )
        assertEquals(listOf("imported_new"), overridden.modules.single().ruleSet.rules.map { it.id })
        assertEquals(setOf("demo_rule"), overridden.builtInRuleIds)

        val restored = RuleCatalogBuilder.build(RuleSources(builtIns = builtIns))

        assertEquals(listOf("demo_rule"), restored.modules.single().ruleSet.rules.map { it.id })
        assertEquals(emptyList<String>(), restored.errors)
        assertEquals(setOf("demo_rule"), restored.idOwner.keys)
    }

    @Test
    fun `reports parse errors from explicit source errors`() {
        val result = RuleCatalogBuilder.build(
            RuleSources(errors = listOf("store: 读取失败")),
        )

        assertContains(result.errors, "store: 读取失败")
        assertTrue(result.modules.isEmpty())
    }
}
