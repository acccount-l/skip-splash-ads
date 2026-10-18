package com.splashskip.engine.rules

import com.splashskip.engine.Fixtures
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleImportValidatorTest {
    @Test
    fun `accepts a valid module`() {
        val text = Fixtures.ruleJson(packageName = RuleTestData.DEMO, ruleId = "demo_skip")

        val ruleSet = RuleImportValidator.validateText(text, "demo.json").getOrThrow()

        assertEquals(RuleTestData.DEMO, ruleSet.packageName)
        assertEquals(1, ruleSet.rules.size)
    }

    @Test
    fun `accepts a module with utf8 bom`() {
        val text = "﻿" + Fixtures.ruleJson(
            packageName = RuleTestData.DEMO,
            ruleId = "demo_skip",
        )

        val ruleSet = RuleImportValidator.validateText(text, "demo.json").getOrThrow()

        assertEquals(RuleTestData.DEMO, ruleSet.packageName)
    }

    @Test
    fun `rejects module larger than 256 kib`() {
        val padding = "x".repeat(300_000)
        val text = Fixtures.ruleJson(packageName = RuleTestData.DEMO, ruleId = "demo_skip")
            .replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"padding\": \"$padding\",")

        val result = RuleImportValidator.validateText(text, "big.json")

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "256 KiB")
    }

    @Test
    fun `rejects module with more than 64 rules`() {
        val rules = (1..65).map { RuleTestData.rule("rule_$it") }.toTypedArray()

        val result = RuleImportValidator.validateText(
            RuleTestData.json(RuleTestData.DEMO, *rules),
            "many.json",
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "规则数超过 64")
    }

    @Test
    fun `rejects blocked packages`() {
        val blocked = listOf(
            "com.splashskip.app",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.gms",
            "com.android.vending",
            "com.vivo.permissionmanager",
            "com.vivo.systemuiplugin",
        )

        blocked.forEach { packageName ->
            val text = Fixtures.ruleJson(packageName = packageName, ruleId = "blocked_rule")
            val result = RuleImportValidator.validateText(text, "blocked.json")
            assertTrue(result.isFailure, "应拒绝受保护包名 $packageName")
            assertContains(result.exceptionOrNull()?.message.orEmpty(), "受保护包名")
        }
    }

    @Test
    fun `rejects overlong package name`() {
        val packageName = "com." + "a".repeat(160)

        val result = RuleImportValidator.validateText(
            RuleTestData.json(packageName, RuleTestData.rule("rule_1")),
            "long.json",
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "包名超过")
    }

    @Test
    fun `rejects malformed json`() {
        val result = RuleImportValidator.validateText("{not-json}", "broken.json")

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "broken.json")
    }

    @Test
    fun `rejects empty rule list`() {
        val result = RuleImportValidator.validateText(
            """{"schemaVersion":1,"packageName":"com.example.demo","rules":[]}""",
            "empty.json",
        )

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "规则集为空")
    }

    @Test
    fun `validate bytes rejects oversize`() {
        val bytes = ByteArray(RuleImportValidator.MAX_MODULE_BYTES + 1) { 'x'.code.toByte() }

        val result = RuleImportValidator.validateBytes(bytes, "bytes.json")

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "256 KiB")
    }
}
