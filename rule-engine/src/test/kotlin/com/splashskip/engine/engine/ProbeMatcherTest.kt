package com.splashskip.engine.engine

import com.splashskip.engine.Fixtures
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.UiNode
import com.splashskip.engine.model.UiTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProbeMatcherTest {
    private fun candidates(
        vararg nodes: UiNode,
        max: Int = 8,
    ): List<ProbeMatcher.ProbeCandidate> = ProbeMatcher.findCandidates(
        tree = UiTree.fromRoot(Fixtures.screenRoot(*nodes)),
        screenWidth = Fixtures.SCREEN_W,
        screenHeight = Fixtures.SCREEN_H,
        max = max,
    )

    @Test
    fun `recognizes skip variants`() {
        listOf(
            "跳过",
            "跳过 5",
            "跳过5秒",
            "跳过广告",
            "跳过广告 5",
            "跳过此广告",
            "点击跳过",
            "Skip",
            "Skip Ad 5",
        ).forEach { text ->
            assertEquals(ProbeMatcher.Kind.SKIP, ProbeMatcher.kindOf(text), "should match: $text")
        }
    }

    @Test
    fun `recognizes close variants`() {
        listOf(
            "关闭",
            "关闭 5",
            "关闭广告",
            "关闭广告 5s",
            "close ad",
        ).forEach { text ->
            assertEquals(ProbeMatcher.Kind.CLOSE, ProbeMatcher.kindOf(text), "should match: $text")
        }
    }

    @Test
    fun `rejects unrelated short texts`() {
        listOf(
            "关闭弹幕",
            "关闭广告，立即下载",
            "跳过广告，立即下载",
            "跳过领取红包",
            "开始跳过",
            "skip to content",
            "5 跳过",
            "",
            "   ",
        ).forEach { text ->
            assertNull(ProbeMatcher.kindOf(text), "should not match: $text")
        }
    }

    @Test
    fun `prefers text over content description`() {
        val found = candidates(
            Fixtures.node(text = "跳过", contentDescription = "关闭广告"),
        ).single()

        assertEquals(ProbeMatcher.Source.TEXT, found.source)
        assertEquals(ProbeMatcher.Kind.SKIP, found.kind)
        assertEquals("跳过", found.text)
    }

    @Test
    fun `falls back to content description`() {
        val found = candidates(
            Fixtures.node(text = null, contentDescription = "关闭广告"),
        ).single()

        assertEquals(ProbeMatcher.Source.DESCRIPTION, found.source)
        assertEquals(ProbeMatcher.Kind.CLOSE, found.kind)
    }

    @Test
    fun `skips invisible disabled and invalid nodes`() {
        val hidden = Fixtures.node(text = "跳过", visible = false)
        val disabled = Fixtures.node(text = "跳过", enabled = false)
        val noBounds = Fixtures.node(text = "跳过", bounds = null)
        val emptyBounds = Fixtures.node(text = "跳过", bounds = RectI(0, 0, 0, 0))
        val offScreen = Fixtures.node(text = "跳过", bounds = RectI(1300, 100, 1400, 200))

        assertTrue(
            candidates(hidden, disabled, noBounds, emptyBounds, offScreen).isEmpty(),
        )
    }

    @Test
    fun `reports clickable ancestor container`() {
        val label = Fixtures.node(
            text = "跳过",
            clickable = false,
            bounds = RectI(1008, 208, 1211, 320),
        )
        val container = Fixtures.node(
            className = "android.widget.FrameLayout",
            clickable = true,
            bounds = RectI(980, 180, 1240, 350),
            children = listOf(label),
        )

        val found = candidates(container).single()

        assertEquals(false, found.clickable)
        assertEquals(1, found.clickableAncestorIndex ?: -1)
    }

    @Test
    fun `reports self when candidate is clickable`() {
        val found = candidates(Fixtures.node(text = "跳过", clickable = true)).single()

        assertTrue(found.clickable)
        assertEquals(1, found.clickableAncestorIndex ?: -1)
    }

    @Test
    fun `reports no clickable ancestor when none exists`() {
        val found = candidates(Fixtures.node(text = "跳过", clickable = false)).single()

        assertNull(found.clickableAncestorIndex)
    }

    @Test
    fun `normalizes center to integer percentages`() {
        val found = candidates(
            Fixtures.node(text = "跳过", bounds = RectI(1008, 208, 1211, 320)),
        ).single()

        assertEquals(88, found.centerXPercent)
        assertEquals(9, found.centerYPercent)
    }

    @Test
    fun `keeps skip candidates before close and honors max`() {
        val close = Fixtures.node(text = "关闭", bounds = RectI(100, 100, 200, 200))
        val skip1 = Fixtures.node(text = "跳过", bounds = RectI(300, 100, 400, 200))
        val skip2 = Fixtures.node(text = "跳过", bounds = RectI(500, 100, 600, 200))

        val found = candidates(close, skip1, skip2, max = 2)

        assertEquals(2, found.size)
        assertTrue(found.all { it.kind == ProbeMatcher.Kind.SKIP })
    }

    @Test
    fun `dedupes identical candidates with digit normalization`() {
        val first = Fixtures.node(text = "跳过 5")
        val second = Fixtures.node(text = "跳过 6")

        assertEquals(1, candidates(first, second).size)
    }

    @Test
    fun `normalizes non breaking spaces`() {
        val found = candidates(Fixtures.node(text = "跳过  5")).single()

        assertEquals("跳过 5", found.text)
        assertEquals(ProbeMatcher.Kind.SKIP, found.kind)
    }

    @Test
    fun `findClickableAncestor returns self`() {
        val tree = UiTree.fromRoot(
            Fixtures.screenRoot(Fixtures.node(text = "跳过", clickable = true)),
        )

        assertEquals(1, tree.findClickableAncestor(1))
    }

    @Test
    fun `findClickableAncestor walks up to clickable parent`() {
        val child = Fixtures.node(text = "跳过", clickable = false)
        val parent = Fixtures.node(
            className = "android.widget.FrameLayout",
            clickable = true,
            children = listOf(child),
        )
        val tree = UiTree.fromRoot(Fixtures.screenRoot(parent))

        assertEquals(1, tree.findClickableAncestor(2))
    }

    @Test
    fun `findClickableAncestor returns null when none exists`() {
        val child = Fixtures.node(text = "跳过", clickable = false)
        val parent = Fixtures.node(
            className = "android.widget.FrameLayout",
            clickable = false,
            children = listOf(child),
        )
        val tree = UiTree.fromRoot(Fixtures.screenRoot(parent))

        assertNull(tree.findClickableAncestor(2))
    }
}
