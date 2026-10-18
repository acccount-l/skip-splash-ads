package com.splashskip.app.data

import com.splashskip.engine.rules.RuleBundleCodec
import com.splashskip.engine.rules.RuleImportValidator
import com.splashskip.engine.rules.SourceModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ModuleStoreReadResult(
    val modules: List<SourceModule>,
    val errors: List<String>,
)

/**
 * 用户导入模块的私有存储：`filesDir/rule-modules/<packageName>.json`。
 *
 * 只读/写应用私有目录，不申请存储权限。写入使用同目录临时文件 + fsync +
 * 原子替换，避免进程被杀时留下半个 JSON。文件名永远是包名，ZIP 条目名
 * 不参与落地路径。
 */
class RuleModuleStore(private val root: File) {
    fun readAll(): ModuleStoreReadResult {
        val errors = mutableListOf<String>()
        val modules = mutableListOf<SourceModule>()

        if (!root.exists()) return ModuleStoreReadResult(emptyList(), emptyList())
        if (!root.isDirectory) {
            return ModuleStoreReadResult(emptyList(), listOf("规则模块目录不可用"))
        }

        val files = try {
            root.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
                .filterNot { it.name.startsWith(".") }
                .sortedBy { it.name }
        } catch (t: Throwable) {
            return ModuleStoreReadResult(emptyList(), listOf("读取规则模块目录失败：${t.javaClass.simpleName}"))
        }

        var totalBytes = 0
        for (file in files) {
            val expectedPackage = file.name.dropLast(".json".length)
            val bytes = try {
                readBounded(file, RuleImportValidator.MAX_MODULE_BYTES)
            } catch (t: Throwable) {
                errors += "${file.name}: 读取失败"
                continue
            }
            if (bytes == null) {
                errors += "${file.name}: 模块超过 ${RuleImportValidator.MAX_MODULE_BYTES / 1024} KiB"
                continue
            }
            totalBytes += bytes.size
            if (totalBytes > RuleBundleCodec.MAX_UNCOMPRESSED_TOTAL) {
                errors += "导入模块总量超过 " +
                    "${RuleBundleCodec.MAX_UNCOMPRESSED_TOTAL / 1024 / 1024} MiB"
                break
            }
            modules += SourceModule(
                label = file.name,
                text = String(bytes, Charsets.UTF_8),
                expectedPackage = expectedPackage,
            )
        }

        return ModuleStoreReadResult(modules, errors)
    }

    fun write(packageName: String, text: String): Result<Unit> = runCatching {
        require(isSafePackageName(packageName)) { "非法包名：$packageName" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= RuleImportValidator.MAX_MODULE_BYTES) {
            "模块超过 ${RuleImportValidator.MAX_MODULE_BYTES / 1024} KiB"
        }
        require(root.exists() || root.mkdirs()) { "无法创建规则模块目录" }

        val target = File(root, "$packageName.json")
        val temp = File.createTempFile(".tmp-", ".json", root)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            moveIntoPlace(temp, target)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun delete(packageName: String): Result<Boolean> = runCatching {
        require(isSafePackageName(packageName)) { "非法包名：$packageName" }
        val target = File(root, "$packageName.json")
        if (!target.exists()) return@runCatching false
        check(target.delete() || !target.exists()) { "删除失败：$packageName" }
        true
    }

    private fun moveIntoPlace(temp: File, target: File) {
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (t: Throwable) {
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (second: Throwable) {
                check(temp.renameTo(target)) { "写入失败：${second.javaClass.simpleName}" }
            }
        }
    }

    private fun readBounded(file: File, limit: Int): ByteArray? {
        FileInputStream(file).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    companion object {
        private val PACKAGE_REGEX =
            Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun isSafePackageName(packageName: String): Boolean =
            packageName.length <= RuleImportValidator.MAX_PACKAGE_NAME_LENGTH &&
                !packageName.contains('/') &&
                !packageName.contains('\\') &&
                !packageName.contains("..") &&
                PACKAGE_REGEX.matches(packageName)
    }
}
