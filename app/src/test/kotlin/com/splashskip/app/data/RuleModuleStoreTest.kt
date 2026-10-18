package com.splashskip.app.data

import com.splashskip.engine.rules.RuleImportValidator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RuleModuleStoreTest {
    private lateinit var root: File
    private lateinit var store: RuleModuleStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("rule-modules-test").toFile()
        store = RuleModuleStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun moduleJson(packageName: String, ruleId: String): String =
        """{"schemaVersion":1,"packageName":"$packageName",""" +
            """"rules":[{"id":"$ruleId","activityIds":["com.example.Main"],""" +
            """"match":{"textRegex":"^跳过$","clickable":true},""" +
            """"constraints":{"dryRun":true}}]}"""

    @Test
    fun `write then read returns module`() {
        val text = moduleJson("com.example.demo", "demo_rule")

        assertTrue(store.write("com.example.demo", text).isSuccess)

        val result = store.readAll()
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(listOf("com.example.demo.json"), result.modules.map { it.label })
        assertEquals("com.example.demo", result.modules.single().expectedPackage)
        assertEquals(text, result.modules.single().text)
    }

    @Test
    fun `second write replaces previous module atomically`() {
        val first = moduleJson("com.example.demo", "first_rule")
        val second = moduleJson("com.example.demo", "second_rule")

        assertTrue(store.write("com.example.demo", first).isSuccess)
        assertTrue(store.write("com.example.demo", second).isSuccess)

        val result = store.readAll()
        assertEquals(second, result.modules.single().text)
        val leftover = root.listFiles().orEmpty().filter { it.name.startsWith(".tmp-") }
        assertTrue("不应残留临时文件", leftover.isEmpty())
        assertEquals(1, root.listFiles().orEmpty().count { it.name.endsWith(".json") })
    }

    @Test
    fun `delete removes module and reports missing`() {
        assertTrue(store.write("com.example.demo", moduleJson("com.example.demo", "demo_rule")).isSuccess)

        assertEquals(true, store.delete("com.example.demo").getOrThrow())
        assertTrue(store.readAll().modules.isEmpty())
        assertEquals(false, store.delete("com.example.demo").getOrThrow())
    }

    @Test
    fun `rejects unsafe package names`() {
        listOf("../evil", "com/example", "com\\example", "", "com..example").forEach { name ->
            val result = store.write(name, "{}")
            assertTrue("应拒绝 $name", result.isFailure)
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `rejects oversized module`() {
        val text = "x".repeat(RuleImportValidator.MAX_MODULE_BYTES + 1)

        val result = store.write("com.example.demo", text)

        assertTrue(result.isFailure)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `skips hidden temp and non json files`() {
        assertTrue(root.exists() || root.mkdirs())
        File(root, ".hidden.json").writeText(moduleJson("com.example.hidden", "hidden_rule"))
        File(root, ".tmp-1.json").writeText(moduleJson("com.example.temp", "temp_rule"))
        File(root, "notes.txt").writeText("not json")
        File(root, "com.example.upper.JSON")
            .writeText(moduleJson("com.example.upper", "upper_rule"))

        val result = store.readAll()

        assertEquals(listOf("com.example.upper.JSON"), result.modules.map { it.label })
        assertEquals("com.example.upper", result.modules.single().expectedPackage)
        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun `reports oversized stored file`() {
        assertTrue(root.exists() || root.mkdirs())
        File(root, "big.json").writeBytes(ByteArray(RuleImportValidator.MAX_MODULE_BYTES + 1))

        val result = store.readAll()

        assertTrue(result.modules.isEmpty())
        assertTrue(result.errors.any { it.contains("模块超过 256 KiB") })
    }

    @Test
    fun `stops reading when total size exceeds limit`() {
        assertTrue(root.exists() || root.mkdirs())
        val chunk = ByteArray(250 * 1024) { 'x'.code.toByte() }
        (1..9).forEach { index ->
            File(root, "com.example.p$index.json").writeBytes(chunk)
        }

        val result = store.readAll()

        assertEquals(8, result.modules.size)
        assertTrue(result.errors.any { it.contains("总量超过") })
    }

    @Test
    fun `missing directory is empty and has no error`() {
        val missing = File(root, "not-created")

        val result = RuleModuleStore(missing).readAll()

        assertTrue(result.modules.isEmpty())
        assertEquals(emptyList<String>(), result.errors)
        assertFalse(missing.exists())
    }

    @Test
    fun `safe package name limits`() {
        assertTrue(RuleModuleStore.isSafePackageName("com.example.demo"))
        assertFalse(RuleModuleStore.isSafePackageName("demo"))
        assertFalse(RuleModuleStore.isSafePackageName("com..demo"))
        assertFalse(RuleModuleStore.isSafePackageName("com.example.demo/../x"))
        assertFalse(
            RuleModuleStore.isSafePackageName(
                "com." + "a".repeat(RuleImportValidator.MAX_PACKAGE_NAME_LENGTH),
            ),
        )
    }
}
