package com.splashskip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogLineCodecTest {
    @Test
    fun roundTripKeepsFieldsAndSanitizesSeparators() {
        val entry = SplashLog.Entry(
            id = 0L,
            timeMillis = 123456789L,
            level = SplashLog.Level.WARN,
            message = "命中\ttext\nline",
        )

        val decoded = LogLineCodec.decode(LogLineCodec.encode(entry))

        assertEquals(123456789L, decoded?.timeMillis)
        assertEquals(SplashLog.Level.WARN, decoded?.level)
        assertEquals("命中 text line", decoded?.message)
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
