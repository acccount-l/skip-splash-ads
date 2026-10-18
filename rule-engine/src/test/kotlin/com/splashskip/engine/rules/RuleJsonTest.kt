package com.splashskip.engine.rules

import com.splashskip.engine.Fixtures
import com.splashskip.engine.engine.RuleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleJsonTest {
    @Test
    fun `round trips built in fixture`() {
        val parsed = RuleParser.parse(Fixtures.BILI_JSON).getOrThrow()

        val encoded = RuleJson.encode(parsed)
        val reparsed = RuleParser.parse(encoded).getOrThrow()

        assertEquals(parsed, reparsed)
    }

    @Test
    fun `writes defaults and omits transient package name`() {
        val parsed = RuleParser.parse(
            Fixtures.ruleJson(packageName = RuleTestData.DEMO, ruleId = "demo_skip"),
        ).getOrThrow()

        val encoded = RuleJson.encode(parsed)

        assertTrue(encoded.contains("\"dryRun\": true"))
        assertTrue(encoded.contains("\"enabled\": true"))
        assertTrue(encoded.contains("\"tapByGesture\": false"))
        assertEquals(1, Regex("\"packageName\"").findAll(encoded).count())
    }

    @Test
    fun `round trips back action disabled gesture and bounds`() {
        val text = RuleTestData.json(
            RuleTestData.DEMO,
            RuleTestData.rule(
                id = "click_rule",
                action = "CLICK",
                tapByGesture = true,
                dryRun = false,
                bounds = "[0.0,0.0,1.0,1.0]",
            ),
            RuleTestData.rule(
                id = "back_rule",
                action = "BACK",
                dryRun = false,
                enabled = false,
            ),
        )
        val parsed = RuleParser.parse(text).getOrThrow()

        val encoded = RuleJson.encode(parsed)
        val reparsed = RuleParser.parse(encoded).getOrThrow()

        assertEquals(parsed, reparsed)
        val clickRule = reparsed.rules.single { it.id == "click_rule" }
        assertTrue(clickRule.tapByGesture)
        assertEquals(listOf(0f, 0f, 1f, 1f), clickRule.match.allowedBounds)
        val backRule = reparsed.rules.single { it.id == "back_rule" }
        assertFalse(backRule.enabled)
    }
}
