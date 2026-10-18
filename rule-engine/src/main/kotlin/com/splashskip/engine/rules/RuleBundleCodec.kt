package com.splashskip.engine.rules

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 从 ZIP 或单个 JSON 文件读出的候选模块。label 只用于预览/报错。 */
data class TextModule(
    val label: String,
    val text: String,
)

/** 导出 ZIP 时的一个条目。 */
data class ExportedModule(
    val packageName: String,
    val json: String,
)

data class ZipReadResult(
    val modules: List<TextModule>,
    val errors: List<String>,
)

/**
 * “全部模块导出为 ZIP / 从 ZIP 导入”的纯 JVM 编解码器。
 *
 * 安全边界：只读内存、不落地解压；限制条目数、单条大小和解压总量，
 * 避免误选超大文件或 ZIP 炸弹。条目名只用于显示，最终存储名由包名决定。
 */
object RuleBundleCodec {
    const val MAX_ZIP_BYTES = 4 * 1024 * 1024
    const val MAX_ENTRIES = 256
    const val MAX_ENTRY_BYTES = 256 * 1024
    const val MAX_UNCOMPRESSED_TOTAL = 2 * 1024 * 1024

    fun looksLikeZip(prefix: ByteArray): Boolean {
        if (prefix.size < 4) return false
        if (prefix[0] != 'P'.code.toByte() || prefix[1] != 'K'.code.toByte()) return false
        val third = prefix[2].toInt() and 0xFF
        val fourth = prefix[3].toInt() and 0xFF
        return (third == 3 && fourth == 4) ||
            (third == 5 && fourth == 6) ||
            (third == 7 && fourth == 8)
    }

    fun read(bytes: ByteArray): ZipReadResult {
        if (bytes.size > MAX_ZIP_BYTES) {
            return ZipReadResult(
                emptyList(),
                listOf("ZIP 超过 ${MAX_ZIP_BYTES / 1024 / 1024} MiB"),
            )
        }
        if (!looksLikeZip(bytes)) {
            return ZipReadResult(emptyList(), listOf("不是有效的 ZIP 文件"))
        }

        val modules = mutableListOf<TextModule>()
        val errors = mutableListOf<String>()
        var entryCount = 0
        var totalBytes = 0

        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        errors += "ZIP 条目数超过 $MAX_ENTRIES"
                        break
                    }

                    val rawName = entry.name ?: ""
                    val name = rawName.replace('\\', '/')
                    if (entry.isDirectory || shouldSkip(name)) {
                        zip.closeEntry()
                        continue
                    }
                    if (!isSafeEntryName(name)) {
                        errors += "$name: 非法条目路径，已跳过"
                        zip.closeEntry()
                        continue
                    }

                    val data = readEntryBounded(zip)
                    if (data == null) {
                        errors += "$name: 条目超过 ${MAX_ENTRY_BYTES / 1024} KiB，已跳过"
                        zip.closeEntry()
                        continue
                    }
                    totalBytes += data.size
                    if (totalBytes > MAX_UNCOMPRESSED_TOTAL) {
                        errors += "ZIP 解压总量超过 ${MAX_UNCOMPRESSED_TOTAL / 1024 / 1024} MiB"
                        break
                    }

                    modules += TextModule(name, String(data, Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        } catch (t: Throwable) {
            errors += "ZIP 读取失败：${t.javaClass.simpleName}"
        }

        if (modules.isEmpty() && errors.isEmpty()) {
            errors += "ZIP 中没有可导入的 JSON 模块"
        }
        return ZipReadResult(modules, errors)
    }

    fun write(modules: List<ExportedModule>): ByteArray {
        val out = ByteArrayOutputStream()
        var totalBytes = 0
        ZipOutputStream(out).use { zip ->
            modules.sortedBy { it.packageName }.forEach { module ->
                require(isSafePackageName(module.packageName)) {
                    "非法包名：${module.packageName}"
                }
                val bytes = module.json.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_ENTRY_BYTES) {
                    "${module.packageName}: 模块超过 ${MAX_ENTRY_BYTES / 1024} KiB"
                }
                totalBytes += bytes.size
                require(totalBytes <= MAX_UNCOMPRESSED_TOTAL) {
                    "导出内容超过 ${MAX_UNCOMPRESSED_TOTAL / 1024 / 1024} MiB"
                }

                zip.putNextEntry(ZipEntry("${module.packageName}.json"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun readEntryBounded(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun shouldSkip(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.startsWith("__MACOSX/") || trimmed.contains("/__MACOSX/")) return true
        val fileName = trimmed.substringAfterLast('/')
        if (fileName.startsWith(".")) return true
        return !fileName.endsWith(".json", ignoreCase = true)
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.startsWith("/")) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return false
        return name.split('/').none { it == ".." || it == "." }
    }

    private fun isSafePackageName(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName.contains('/') || packageName.contains('\\')) return false
        if (packageName.contains("..")) return false
        return Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
            .matches(packageName)
    }
}
