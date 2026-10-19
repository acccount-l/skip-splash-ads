package com.example.nodedemo.core.engine

import com.example.nodedemo.core.model.RectI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundsTest {
    @Test
    fun `normalizes center against screen size`() {
        val (nx, ny) = Bounds.normalizedCenter(
            RectI(left = 80, top = 160, right = 120, bottom = 240),
            width = 400,
            height = 800,
        )

        assertEquals(0.25f, nx, 0.001f)
        assertEquals(0.25f, ny, 0.001f)
    }

    @Test
    fun `includes allowed region edges`() {
        assertTrue(Bounds.contains(listOf(0f, 0f, 1f, 1f), 0, 0, 100, 100))
        assertTrue(Bounds.contains(listOf(0f, 0f, 1f, 1f), 100, 100, 100, 100))
        assertFalse(Bounds.contains(listOf(0.5f, 0.5f, 1f, 1f), 49, 50, 100, 100))
    }

    @Test
    fun `rejects invalid screen size or region`() {
        assertFalse(Bounds.contains(listOf(0f, 0f, 1f, 1f), 0, 0, 0, 100))
        assertFalse(Bounds.contains(listOf(0f, 0f, 1f, 1f), 0, 0, 100, 0))
        assertFalse(Bounds.contains(listOf(0f, 0f, 1f), 0, 0, 100, 100))
    }

    @Test
    fun `allows small drift from layout change`() {
        val snapshot = RectI(100, 200, 300, 280)
        assertTrue(Bounds.near(RectI(96, 196, 304, 284), snapshot, 24, 0.35f))
        assertTrue(Bounds.near(RectI(100, 200, 300, 280), snapshot, 24, 0.35f))
    }

    @Test
    fun `rejects large position change`() {
        val snapshot = RectI(100, 200, 300, 280)
        assertFalse(Bounds.near(RectI(400, 200, 600, 280), snapshot, 24, 0.35f))
        assertFalse(Bounds.near(RectI(100, 700, 300, 780), snapshot, 24, 0.35f))
    }

    @Test
    fun `rejects invalid snapshot bounds`() {
        assertFalse(Bounds.near(RectI(100, 200, 300, 280), RectI(100, 200, 100, 280), 24, 0.35f))
    }
}
