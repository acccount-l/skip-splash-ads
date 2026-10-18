package com.splashskip.engine.rules

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuleBundleCodecTest {
    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipOfEntries(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `writes and reads a zip sorted by package`() {
        val moduleA = ExportedModule("com.example.a", RuleTestData.json("com.example.a", RuleTestData.rule("a_rule")))
        val moduleB = ExportedModule("com.example.b", RuleTestData.json("com.example.b", RuleTestData.rule("b_rule")))

        val bytes = RuleBundleCodec.write(listOf(moduleB, moduleA))

        assertTrue(RuleBundleCodec.looksLikeZip(bytes))
        val result = RuleBundleCodec.read(bytes)
        assertEquals(emptyList(), result.errors)
        assertEquals(listOf("com.example.a.json", "com.example.b.json"), result.modules.map { it.label })
        assertEquals(moduleA.json, result.modules[0].text)
        assertEquals(moduleB.json, result.modules[1].text)
    }

    @Test
    fun `skips macos hidden directories and non json files`() {
        val bytes = zipOf(
            "com.example.demo.json" to RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_rule")),
            "__MACOSX/._com.example.demo.json" to "junk",
            ".hidden.json" to "junk",
            "notes.txt" to "junk",
            "dir/" to "",
        )

        val result = RuleBundleCodec.read(bytes)

        assertEquals(listOf("com.example.demo.json"), result.modules.map { it.label })
        assertEquals(emptyList(), result.errors)
    }

    @Test
    fun `rejects path traversal entry names`() {
        val bytes = zipOf(
            "../evil.json" to RuleTestData.json(RuleTestData.OTHER, RuleTestData.rule("evil_rule")),
            "com.example.demo.json" to RuleTestData.json(RuleTestData.DEMO, RuleTestData.rule("demo_rule")),
        )

        val result = RuleBundleCodec.read(bytes)

        assertEquals(listOf("com.example.demo.json"), result.modules.map { it.label })
        assertTrue(result.errors.any { it.contains("非法条目路径") })
    }

    @Test
    fun `rejects overlong entry`() {
        val bytes = zipOfEntries(
            "huge.json" to ByteArray(RuleBundleCodec.MAX_ENTRY_BYTES + 1) { 'x'.code.toByte() },
        )

        val result = RuleBundleCodec.read(bytes)

        assertEquals(emptyList(), result.modules)
        assertTrue(result.errors.any { it.contains("条目超过") })
    }

    @Test
    fun `rejects too many entries`() {
        val entries = (1..(RuleBundleCodec.MAX_ENTRIES + 1))
            .map { "f$it.json" to "{}" }
            .toTypedArray()

        val result = RuleBundleCodec.read(zipOf(*entries))

        assertTrue(result.errors.any { it.contains("条目数超过") })
    }

    @Test
    fun `rejects total uncompressed size over limit`() {
        val chunk = ByteArray(250 * 1024) { 'x'.code.toByte() }
        val entries = (1..9)
            .map { "f$it.json" to chunk }
            .toTypedArray()

        val result = RuleBundleCodec.read(zipOfEntries(*entries))

        assertTrue(result.errors.any { it.contains("解压总量超过") })
        assertTrue(result.modules.size < 9)
    }

    @Test
    fun `reports malformed zip`() {
        val result = RuleBundleCodec.read(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0))

        assertTrue(result.modules.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `reports empty zip`() {
        val result = RuleBundleCodec.read(zipOf())

        assertTrue(result.modules.isEmpty())
        assertContains(result.errors.single(), "没有可导入")
    }

    @Test
    fun `rejects oversized zip before reading`() {
        val result = RuleBundleCodec.read(ByteArray(RuleBundleCodec.MAX_ZIP_BYTES + 1))

        assertTrue(result.errors.single().contains("ZIP 超过"))
    }

    @Test
    fun `write rejects unsafe package name`() {
        assertFailsWith<IllegalArgumentException> {
            RuleBundleCodec.write(listOf(ExportedModule("../evil", "{}")))
        }
    }

    @Test
    fun `write rejects oversized module`() {
        val json = "x".repeat(RuleBundleCodec.MAX_ENTRY_BYTES + 1)
        assertFailsWith<IllegalArgumentException> {
            RuleBundleCodec.write(listOf(ExportedModule("com.example.demo", json)))
        }
    }
}
