package com.splashskip.engine.engine

import com.splashskip.engine.Fixtures
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleParserTest {
    @Test
    fun `parses bilibili rule`() {
        val ruleSet = RuleParser.parse(Fixtures.BILI_JSON).getOrThrow()

        assertEquals(1, ruleSet.schemaVersion)
        assertEquals("tv.danmaku.bili", ruleSet.packageName)
        val rule = ruleSet.rules.single()
        assertEquals("bilibili_splash_skip", rule.id)
        assertEquals(2, rule.activityIds.size)
        assertEquals(listOf(0.70f, 0.80f, 1.0f, 1.0f), rule.match.allowedBounds)
        assertEquals(8_000L, rule.constraints.withinMsAfterLaunch)
        assertTrue(rule.constraints.dryRun)
    }

    @Test
    fun `ignores unknown fields for forward compatibility`() {
        val json = Fixtures.ruleJson()
            .replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"futureField\": true,")

        val ruleSet = RuleParser.parse(json).getOrThrow()
        assertEquals(1, ruleSet.rules.size)
    }

    @Test
    fun `rejects unsupported schema version`() {
        val result = RuleParser.parse(Fixtures.ruleJson(schemaVersion = 2))

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "schemaVersion")
    }

    @Test
    fun `rejects rule without activity ids`() {
        val result = RuleParser.parse(Fixtures.ruleJson(activityIds = emptyList()))

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "activityIds")
    }

    @Test
    fun `rejects invalid regex`() {
        val result = RuleParser.parse(
            Fixtures.ruleJson(
                matchJson = """{"textRegex":"(.*","clickable":true,"allowedBounds":[0.0,0.0,1.0,1.0]}""",
            ),
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "正则")
    }

    @Test
    fun `rejects invalid allowed bounds order`() {
        val result = RuleParser.parse(
            Fixtures.ruleJson(
                matchJson = """{"textRegex":"^跳过$","clickable":true,"allowedBounds":[0.9,0.8,0.7,1.0]}""",
            ),
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "allowedBounds")
    }

    @Test
    fun `rejects out of range bounds`() {
        val result = RuleParser.parse(
            Fixtures.ruleJson(
                matchJson = """{"textRegex":"^跳过$","clickable":true,"allowedBounds":[0.0,1.2,1.0,2.0]}""",
            ),
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "allowedBounds")
    }

    @Test
    fun `rejects unknown action`() {
        val result = RuleParser.parse(Fixtures.ruleJson(action = "TAP"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects rule without any matcher`() {
        val result = RuleParser.parse(
            Fixtures.ruleJson(matchJson = """{"clickable":true,"allowedBounds":[0.0,0.0,1.0,1.0]}"""),
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "匹配条件")
    }

    @Test
    fun `parses gesture rule`() {
        val rule = RuleParser.parse(Fixtures.ruleJson(tapByGesture = true))
            .getOrThrow()
            .rules
            .single()

        assertTrue(rule.tapByGesture)
    }

    @Test
    fun `rejects gesture rule without text matcher`() {
        val result = RuleParser.parse(
            Fixtures.ruleJson(
                matchJson = """{"viewIdRegex":".*skip.*","clickable":false,"allowedBounds":[0.0,0.0,1.0,1.0]}""",
                tapByGesture = true,
            ),
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "手势点击")
    }

    @Test
    fun `rejects window longer than thirty seconds`() {
        val result = RuleParser.parse(
            Fixtures.ruleJson(
                constraintsJson = """{"withinMsAfterLaunch":30001,"cooldownMs":2000,"maxAttempts":1,"dryRun":true}""",
            ),
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "withinMsAfterLaunch")
    }
}
