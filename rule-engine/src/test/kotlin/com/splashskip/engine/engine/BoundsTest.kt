package com.splashskip.engine.engine

import com.splashskip.engine.Fixtures
import com.splashskip.engine.model.RectI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundsTest {
    private val biliAllowed = listOf(0.70f, 0.80f, 1.0f, 1.0f)
    private val biliSkipBounds = RectI(923, 2425, 1204, 2565)

    @Test
    fun `normalizes measured bilibili skip button`() {
        val (nx, ny) = Bounds.normalizedCenter(biliSkipBounds, Fixtures.SCREEN_W, Fixtures.SCREEN_H)

        assertEquals(0.8436f, nx, 0.001f)
        assertEquals(0.8911f, ny, 0.001f)
    }

    @Test
    fun `bilibili button is inside configured bottom right region`() {
        assertTrue(
            Bounds.contains(
                biliAllowed,
                biliSkipBounds.centerX,
                biliSkipBounds.centerY,
                Fixtures.SCREEN_W,
                Fixtures.SCREEN_H,
            ),
        )
    }

    @Test
    fun `bilibili button is outside default top right region`() {
        assertFalse(
            Bounds.contains(
                SafetyGate.DEFAULT_ALLOWED_BOUNDS,
                biliSkipBounds.centerX,
                biliSkipBounds.centerY,
                Fixtures.SCREEN_W,
                Fixtures.SCREEN_H,
            ),
        )
    }

    @Test
    fun `includes allowed region edges`() {
        assertTrue(Bounds.contains(listOf(0f, 0f, 1f, 1f), 0, 0, 100, 100))
        assertTrue(Bounds.contains(listOf(0f, 0f, 1f, 1f), 100, 100, 100, 100))
    }

    @Test
    fun `rejects invalid screen size`() {
        assertFalse(Bounds.contains(listOf(0f, 0f, 1f, 1f), 0, 0, 0, 100))
        assertFalse(Bounds.contains(listOf(0f, 0f, 1f, 1f), 0, 0, 100, 0))
    }
}
