package com.splashskip.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleImportPlannerTest {
    private val builtIns = listOf(
        RuleTestData.source(RuleTestData.DEMO, "demo.json", RuleTestData.rule("demo_rule")),
    )

    private fun plan(
        candidates: List<TextModule>,
        imported: List<SourceModule> = emptyList(),
    ): ImportPlan = RuleImportPlanner.plan(builtIns, imported, candidates)

    @Test
    fun `preview reports counts and warnings`() {
        val text = RuleTestData.json(
            RuleTestData.OTHER,
            RuleTestData.rule(
                id = "click_dry",
                action = "CLICK",
                dryRun = true,
                tapByGesture = true,
                bounds = "[0.0,0.0,1.0,1.0]",
            ),
            RuleTestData.rule(
                id = "back_real",
                action = "BACK",
                dryRun = false,
                enabled = false,
            ),
        )

        val result = plan(listOf(TextModule("other.json", text)))

        val preview = result.modules.single()
        assertEquals(ImportStatus.APPLY, preview.status)
        assertEquals(Replacement.NEW, preview.replacement)
        assertEquals(false, preview.hasBuiltInBelow)
        assertEquals(2, preview.ruleCount)
        assertEquals(1, preview.clickCount)
        assertEquals(1, preview.backCount)
        assertEquals(1, preview.dryRunCount)
        assertEquals(1, preview.gestureCount)
        assertEquals(1, preview.disabledCount)
        assertEquals(1, preview.fullScreenCount)
        assertEquals(0, preview.previousRuleCount)
        assertTrue(preview.messages.contains("新增模块"))
        assertTrue(preview.messages.any { it.contains("模块内禁用规则") })
        assertTrue(preview.messages.any { it.contains("全屏允许区域") })
        assertTrue(preview.messages.any { it.contains("含真实点击规则") })
        assertEquals(1, result.applicableCount)
    }

    @Test
    fun `detects new override built in and replace imported`() {
        val imported = listOf(
            RuleTestData.source(
                RuleTestData.OTHER,
                "other-imported.json",
                RuleTestData.rule("other_old"),
            ),
        )
        val candidates = listOf(
            TextModule(
                "demo.json",
                RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_new")),
            ),
            TextModule(
                "other.json",
                RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("other_new")),
            ),
            TextModule(
                "third.json",
                RuleTestData.json("com.example.third", RuleTestData.rule("third_rule")),
            ),
        )

        val result = plan(candidates, imported)

        assertEquals(
            listOf(
                Replacement.OVERRIDES_BUILT_IN,
                Replacement.REPLACES_IMPORTED,
                Replacement.NEW,
            ),
            result.modules.map { it.replacement },
        )
        assertEquals(
            listOf(true, false, false),
            result.modules.map { it.hasBuiltInBelow },
        )
        assertEquals(1, result.modules[0].previousRuleCount)
        assertEquals(1, result.modules[1].previousRuleCount)
        assertTrue(result.modules[0].messages.any { it.contains("整体替换内置模块") })
        assertTrue(result.modules[1].messages.any { it.contains("替换现有导入模块") })
    }

    @Test
    fun `same batch duplicate package skips later file`() {
        val candidates = listOf(
            TextModule("first.json", RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("first_rule"))),
            TextModule("second.json", RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("second_rule"))),
        )

        val result = plan(candidates)

        assertEquals(
            listOf(ImportStatus.APPLY, ImportStatus.SKIP),
            result.modules.map { it.status },
        )
        assertTrue(result.modules[1].messages.single().contains("同批次重复包名"))
        assertEquals(1, result.accepted.size)
    }

    @Test
    fun `cross package id conflict skips candidate`() {
        val candidates = listOf(
            TextModule(
                "other.json",
                RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("demo_rule")),
            ),
        )

        val result = plan(candidates)

        assertEquals(ImportStatus.SKIP, result.modules.single().status)
        assertTrue(result.modules.single().messages.single().contains("与其他包名冲突"))
        assertTrue(result.accepted.isEmpty())
    }

    @Test
    fun `allows reusing own built in id`() {
        val candidates = listOf(
            TextModule(
                "demo.json",
                RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_rule")),
            ),
        )

        val result = plan(candidates)

        val preview = result.modules.single()
        assertEquals(ImportStatus.APPLY, preview.status)
        assertEquals(Replacement.OVERRIDES_BUILT_IN, preview.replacement)
        assertEquals(1, result.accepted.size)
    }

    @Test
    fun `invalid candidate is skipped while valid one applies`() {
        val candidates = listOf(
            TextModule("broken.json", "{broken}"),
            TextModule(
                "other.json",
                RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("other_rule")),
            ),
        )

        val result = plan(candidates)

        assertEquals(
            listOf(ImportStatus.SKIP, ImportStatus.APPLY),
            result.modules.map { it.status },
        )
        assertTrue(result.modules[0].messages.single().contains("broken.json"))
        assertEquals(1, result.accepted.size)
    }

    @Test
    fun `shadowed built in ids stay reserved within batch`() {
        val candidates = listOf(
            TextModule(
                "demo.json",
                RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_new")),
            ),
            TextModule(
                "other.json",
                RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("demo_rule")),
            ),
        )

        val result = plan(candidates)

        assertEquals(
            listOf(ImportStatus.APPLY, ImportStatus.SKIP),
            result.modules.map { it.status },
        )
        assertTrue(result.modules[1].messages.single().contains("demo_rule"))
    }

    @Test
    fun `applying accepted imports matches rebuilt catalog`() {
        val candidates = listOf(
            TextModule(
                "demo.json",
                RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_rule", textRegex = "^关闭$")),
            ),
        )

        val importPlan = plan(candidates)
        val accepted = importPlan.accepted.single()
        val rebuilt = RuleCatalogBuilder.build(
            RuleSources(
                builtIns = builtIns,
                imported = listOf(
                    SourceModule(
                        label = accepted.label,
                        text = accepted.sourceText,
                        expectedPackage = accepted.packageName,
                    ),
                ),
            ),
        )

        val module = rebuilt.modules.single()
        assertEquals(ModuleOrigin.IMPORTED, module.origin)
        assertEquals(accepted.ruleSet, module.ruleSet)
        assertEquals(emptyList(), rebuilt.errors)
    }

    @Test
    fun `reports current import source errors`() {
        val imported = listOf(SourceModule("broken.json", "{broken}", RuleTestData.DEMO))
        val candidates = listOf(
            TextModule(
                "other.json",
                RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("other_rule")),
            ),
        )

        val result = plan(candidates, imported)

        assertTrue(result.sourceErrors.any { it.contains("broken.json") })
        assertEquals(1, result.accepted.size)
    }

    @Test
    fun `rejects protected package candidate`() {
        val candidates = listOf(
            TextModule(
                "settings.json",
                RuleTestData.json("com.android.settings", RuleTestData.rule("settings_rule")),
            ),
        )

        val result = plan(candidates)

        assertEquals(ImportStatus.SKIP, result.modules.single().status)
        assertTrue(result.modules.single().messages.single().contains("受保护包名"))
        assertTrue(result.accepted.isEmpty())
    }
}
