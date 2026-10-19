package com.example.nodedemo.data

import android.content.Context
import java.io.File

/**
 * 把已经脱敏的日志追加到应用私有目录，进程被杀后仍可查看。
 * 不申请存储权限，不写入外部存储，不上传。
 *
 * 存储上限：单个文件 128 KB，保留当前文件和一份备份，总计约 256 KB。
 * 达到上限时自动轮转并删除最旧的一份，不会无限增长。
 */
class PersistentLogStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val backup = File(context.applicationContext.filesDir, BACKUP_NAME)
    private val lock = Any()

    init {
        synchronized(lock) {
            runCatching { trimOversized() }
        }
    }

    fun append(entry: DemoLog.Entry) {
        synchronized(lock) {
            runCatching {
                rotateIfNeeded()
                // 极端情况下轮转/删除都失败时，直接清空当前文件，保证硬上限。
                if (file.length() >= MAX_BYTES) {
                    file.writeText("", Charsets.UTF_8)
                }
                file.appendText(LogLineCodec.encode(entry) + "\n", Charsets.UTF_8)
            }
        }
    }

    fun readRecent(maxEntries: Int): List<DemoLog.Entry> {
        synchronized(lock) {
            val lines = runCatching {
                buildList {
                    if (backup.exists() && backup.length() > 0L) {
                        addAll(backup.readLines(Charsets.UTF_8))
                    }
                    if (file.exists() && file.length() > 0L) {
                        addAll(file.readLines(Charsets.UTF_8))
                    }
                }
            }.getOrDefault(emptyList())

            return lines.asSequence()
                .mapNotNull(LogLineCodec::decode)
                .toList()
                .takeLast(maxEntries)
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file.delete() }
            runCatching { backup.delete() }
        }
    }

    /**
     * 单文件达到上限时轮转：删除旧备份，把当前文件改名为备份。
     * 改名失败（极少见）时丢弃当前文件，避免继续增长。
     */
    private fun rotateIfNeeded() {
        if (file.length() < MAX_BYTES) return
        runCatching {
            if (backup.exists()) backup.delete()
            if (!file.renameTo(backup)) {
                file.delete()
            }
        }.onFailure {
            runCatching { file.delete() }
        }
    }

    /** 兼容异常退出留下的超大文件：启动时压回上限。 */
    private fun trimOversized() {
        if (backup.exists() && backup.length() > MAX_BYTES) {
            backup.delete()
        }
        if (file.exists() && file.length() > MAX_BYTES) {
            file.writeText(tailText(file, MAX_BYTES.toInt()), Charsets.UTF_8)
        }
    }

    /** 只保留文件末尾的 maxBytes，并从下一整行开始，避免截断 UTF-8 字符/半行。 */
    private fun tailText(file: File, maxBytes: Int): String {
        val length = file.length()
        if (length <= maxBytes) return file.readText(Charsets.UTF_8)
        return runCatching {
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(length - maxBytes)
                val bytes = ByteArray(maxBytes)
                raf.readFully(bytes)
                val text = String(bytes, Charsets.UTF_8)
                text.substringAfter('\n', text)
            }
        }.getOrDefault("")
    }

    companion object {
        /** 两个文件轮转后的总上限，供 UI 展示。 */
        const val MAX_TOTAL_KB = 256

        private const val FILE_NAME = "node-demo-log.txt"
        private const val BACKUP_NAME = "node-demo-log.1.txt"
        private const val MAX_BYTES = 128L * 1024L
    }
}
