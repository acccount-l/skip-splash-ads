package com.splashskip.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.splashskip.engine.rules.RuleBundleCodec
import com.splashskip.engine.rules.TextModule
import java.io.ByteArrayOutputStream

data class RuleReadResult(
    val candidates: List<TextModule>,
    val errors: List<String>,
)

/**
 * 通过 SAF 返回的 URI 读取用户选中的 JSON / ZIP。
 *
 * 不申请存储权限、不持久化 URI、不写临时文件；所有数据只读进内存，
 * 并用外层大小、条目数和条目大小上限约束。ZIP 按文件头识别，不依赖扩展名。
 */
class RuleFileReader(private val context: Context) {
    fun read(uris: List<Uri>): RuleReadResult {
        val candidates = mutableListOf<TextModule>()
        val errors = mutableListOf<String>()

        val selected = uris.take(MAX_CANDIDATES)
        if (uris.size > MAX_CANDIDATES) {
            errors += "一次最多导入 $MAX_CANDIDATES 个模块，已忽略多余文件"
        }

        selected.forEachIndexed { index, uri ->
            if (candidates.size >= MAX_CANDIDATES) {
                errors += "一次最多导入 $MAX_CANDIDATES 个模块"
                return@forEachIndexed
            }

            val display = displayName(uri, index)
            val bytes = try {
                readBytes(uri)
            } catch (t: Throwable) {
                errors += "$display: 读取失败（${t.javaClass.simpleName}）"
                return@forEachIndexed
            }
            if (bytes == null) {
                errors += "$display: 文件超过 ${RuleBundleCodec.MAX_ZIP_BYTES / 1024 / 1024} MiB"
                return@forEachIndexed
            }
            if (bytes.isEmpty()) {
                errors += "$display: 文件为空"
                return@forEachIndexed
            }

            val prefix = if (bytes.size >= 4) bytes.copyOfRange(0, 4) else bytes
            if (RuleBundleCodec.looksLikeZip(prefix)) {
                if (bytes.size > RuleBundleCodec.MAX_ZIP_BYTES) {
                    errors += "$display: ZIP 超过 ${RuleBundleCodec.MAX_ZIP_BYTES / 1024 / 1024} MiB"
                    return@forEachIndexed
                }
                val result = RuleBundleCodec.read(bytes)
                errors += result.errors.map { error -> "$display: $error" }
                val single = result.modules.size == 1
                result.modules.forEach { module ->
                    if (candidates.size >= MAX_CANDIDATES) return@forEach
                    candidates += TextModule(
                        label = if (single) display else "$display · ${module.label}".take(MAX_LABEL),
                        text = module.text,
                    )
                }
            } else {
                candidates += TextModule(label = display, text = String(bytes, Charsets.UTF_8))
            }
        }

        return RuleReadResult(candidates, errors)
    }

    private fun displayName(uri: Uri, index: Int): String {
        val name = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        } catch (t: Throwable) {
            null
        }
        return (name?.takeIf { it.isNotBlank() } ?: "文件 ${index + 1}")
            .take(MAX_LABEL)
    }

    private fun readBytes(uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > RuleBundleCodec.MAX_ZIP_BYTES) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private companion object {
        const val MAX_CANDIDATES = 256
        const val MAX_LABEL = 120
    }
}
