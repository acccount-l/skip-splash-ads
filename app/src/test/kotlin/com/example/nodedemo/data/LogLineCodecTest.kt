package com.example.nodedemo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogLineCodecTest {
    @Test
    fun roundTripKeepsFieldsAndSanitizesSeparators() {
        val entry = DemoLog.Entry(
            id = 0L,
            timeMillis = 123456789L,
            level = DemoLog.Level.WARN,
            message = "示例\t文本\nline",
        )

        val decoded = LogLineCodec.decode(LogLineCodec.encode(entry))

        assertEquals(123456789L, decoded?.timeMillis)
        assertEquals(DemoLog.Level.WARN, decoded?.level)
        assertEquals("示例 文本 line", decoded?.message)
    }

    @Test
    fun invalidLinesAreIgnored() {
        assertNull(LogLineCodec.decode(""))
        assertNull(LogLineCodec.decode("not-a-time\tINFO\tx"))
        assertNull(LogLineCodec.decode("1\tNOPE\tx"))
        assertNull(LogLineCodec.decode("1\tINFO"))
        assertNull(LogLineCodec.decode("1\tINFO\t"))
    }
}
