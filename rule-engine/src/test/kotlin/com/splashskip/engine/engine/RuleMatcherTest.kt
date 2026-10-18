package com.splashskip.engine.engine

import com.splashskip.engine.Fixtures
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.UiTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RuleMatcherTest {
    private val matcher = RuleMatcher()
    private val biliRule = Fixtures.biliRule()

    @Test
    fun `finds bilibili skip and ignores danmaku close control`() {
        val skip = Fixtures.node(text = "跳过 3")
        val close = Fixtures.node(
            contentDescription = "关闭弹幕",
            className = "android.widget.ImageView",
            bounds = RectI(670, 2523, 866, 2635),
        )
        val tree = UiTree.fromRoot(Fixtures.screenRoot(skip, close))

        val found = assertNotNull(
            matcher.findBest(tree, biliRule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H),
        )

        assertEquals("跳过 3", found.node.text)
        assertEquals(RectI(923, 2425, 1204, 2565), found.clickNode.bounds)
        assertEquals(listOf(0), found.node.path)
    }

    @Test
    fun `does not match close danmaku even when it has text`() {
        val close = Fixtures.node(
            text = "关闭弹幕",
            bounds = RectI(670, 2523, 866, 2635),
        )
        val tree = UiTree.fromRoot(Fixtures.screenRoot(close))

        assertNull(matcher.findBest(tree, biliRule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H))
    }

    @Test
    fun `does not match skip text with extra advertising words`() {
        val node = Fixtures.node(text = "跳过广告，立即下载")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertNull(matcher.findBest(tree, biliRule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H))
    }

    @Test
    fun `matches exact text fallback`() {
        val node = Fixtures.node(text = "跳过")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertNotNull(matcher.findBest(tree, biliRule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H))
    }

    @Test
    fun `rejects candidate outside configured region`() {
        val node = Fixtures.node(text = "跳过 3", bounds = RectI(10, 10, 200, 150))
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertNull(matcher.findBest(tree, biliRule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H))
    }

    @Test
    fun `rejects wrong foreground package`() {
        val node = Fixtures.node(text = "跳过 3")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertNull(matcher.findBest(tree, biliRule, "com.example.other", Fixtures.SCREEN_W, Fixtures.SCREEN_H))
    }

    @Test
    fun `chooses smallest clickable candidate`() {
        val big = Fixtures.node(text = "跳过 3", bounds = RectI(923, 2425, 1204, 2565))
        val small = Fixtures.node(text = "跳过 2", bounds = RectI(1000, 2470, 1120, 2560))
        val tree = UiTree.fromRoot(Fixtures.screenRoot(big, small))

        val found = assertNotNull(
            matcher.findBest(tree, biliRule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H),
        )

        assertEquals(RectI(1000, 2470, 1120, 2560), found.clickNode.bounds)
    }

    @Test
    fun `can click nearest clickable ancestor when rule allows it`() {
        val textNode = Fixtures.node(
            text = "跳过",
            clickable = false,
            bounds = RectI(940, 2440, 1180, 2550),
        )
        val containerBounds = RectI(920, 2420, 1200, 2570)
        val container = Fixtures.node(
            className = "android.widget.FrameLayout",
            clickable = true,
            bounds = containerBounds,
            children = listOf(textNode),
        )
        val rule = biliRule.let { it.copy(match = it.match.copy(clickable = false)) }
        val tree = UiTree.fromRoot(Fixtures.screenRoot(container))

        val found = assertNotNull(
            matcher.findBest(tree, rule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H),
        )

        assertEquals(containerBounds, found.clickNode.bounds)
        assertEquals(2, found.nodeIndex)
        assertEquals(1, found.clickNodeIndex)
    }

    @Test
    fun `probe rule accepts common skip text variants`() {
        val rule = biliRule.let {
            it.copy(
                match = it.match.copy(
                    textRegex = "(?i)^(点击)?\\s*(跳过|skip)(\\s*广告)?\\s*[|·]?\\s*\\d*\\s*[sS秒]?$",
                    texts = emptyList(),
                    clickable = false,
                    allowedBounds = listOf(0.55f, 0.0f, 1.0f, 1.0f),
                ),
            )
        }

        fun probeTree(text: String): UiTree {
            val textNode = Fixtures.node(
                text = text,
                clickable = false,
                bounds = RectI(1000, 2400, 1200, 2550),
            )
            val container = Fixtures.node(
                className = "android.widget.FrameLayout",
                clickable = true,
                bounds = RectI(990, 2390, 1210, 2560),
                children = listOf(textNode),
            )
            return UiTree.fromRoot(Fixtures.screenRoot(container))
        }

        listOf("跳过", "跳过3", "跳过 3s", "跳过广告", "点击跳过", "跳过|5", "跳过 5 秒").forEach { text ->
            assertNotNull(
                matcher.findBest(
                    probeTree(text),
                    rule,
                    Fixtures.BILI_PACKAGE,
                    Fixtures.SCREEN_W,
                    Fixtures.SCREEN_H,
                ),
                "应匹配：$text",
            )
        }

        listOf("跳过广告，立即下载", "关闭广告", "跳过领取红包").forEach { text ->
            assertNull(
                matcher.findBest(
                    probeTree(text),
                    rule,
                    Fixtures.BILI_PACKAGE,
                    Fixtures.SCREEN_W,
                    Fixtures.SCREEN_H,
                ),
                "不应匹配：$text",
            )
        }
    }

    @Test
    fun `rejects full screen clickable ancestor`() {
        val textNode = Fixtures.node(
            text = "跳过",
            clickable = false,
            bounds = RectI(940, 2440, 1180, 2550),
        )
        val root = Fixtures.screenRoot(textNode).copy(clickable = true)
        val rule = biliRule.let { it.copy(match = it.match.copy(clickable = false)) }
        val tree = UiTree.fromRoot(root)

        assertNull(matcher.findBest(tree, rule, Fixtures.BILI_PACKAGE, Fixtures.SCREEN_W, Fixtures.SCREEN_H))
    }
}
