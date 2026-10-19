package com.example.nodedemo.data

/**
 * 持久化日志的一行格式：timeMillis<TAB>LEVEL<TAB>message。
 * message 在写入前去掉换行和制表符，保证一行一条，便于进程重启后恢复。
 */
object LogLineCodec {
    private const val SEP = '\t'

    fun encode(entry: DemoLog.Entry): String {
        val safeMessage = entry.message
            .replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
        return "${entry.timeMillis}$SEP${entry.level.name}$SEP$safeMessage"
    }

    fun decode(line: String): DemoLog.Entry? {
        val first = line.indexOf(SEP)
        if (first <= 0) return null
        val second = line.indexOf(SEP, first + 1)
        if (second <= first) return null

        val timeMillis = line.substring(0, first).toLongOrNull() ?: return null
        val level = runCatching {
            DemoLog.Level.valueOf(line.substring(first + 1, second))
        }.getOrNull() ?: return null
        val message = line.substring(second + 1)
        if (message.isEmpty()) return null

        return DemoLog.Entry(
            id = 0L,
            timeMillis = timeMillis,
            level = level,
            message = message,
        )
    }
}
