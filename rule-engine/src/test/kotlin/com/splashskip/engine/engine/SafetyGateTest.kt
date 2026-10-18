package com.splashskip.engine.engine

import com.splashskip.engine.Fixtures
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.UiTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SafetyGateTest {
    private val gate = SafetyGate()

    private fun permissiveRule(
        allowedBounds: String = "[0.0,0.0,1.0,1.0]",
        clickable: Boolean = true,
    ) = RuleParser.parse(
        Fixtures.ruleJson(
            matchJson = """{"textRegex":".*","clickable":$clickable,"allowedBounds":$allowedBounds}""",
        ),
    ).getOrThrow().rules.single()

    private fun gestureRule(
        allowedBounds: String = "[0.0,0.0,1.0,1.0]",
    ) = RuleParser.parse(
        Fixtures.ruleJson(
            matchJson = """{"textRegex":".*","clickable":false,"allowedBounds":$allowedBounds}""",
            tapByGesture = true,
        ),
    ).getOrThrow().rules.single()

    private fun rejectReason(tree: UiTree, ruleIndex: Int, rule: com.splashskip.engine.model.Rule): String {
        val result = gate.evaluate(
            tree = tree,
            candidateIndex = ruleIndex,
            rule = rule,
            foregroundPackage = Fixtures.BILI_PACKAGE,
            screenWidth = Fixtures.SCREEN_W,
            screenHeight = Fixtures.SCREEN_H,
        )
        val rejected = assertIs<GateResult.Rejected>(result)
        return rejected.reason
    }

    @Test
    fun `rejects deny keyword in candidate text`() {
        val node = Fixtures.node(text = "立即下载")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("candidate_denied_text", rejectReason(tree, 1, permissiveRule()))
    }

    @Test
    fun `rejects deny keyword in candidate content description`() {
        val node = Fixtures.node(contentDescription = "打开应用")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("candidate_denied_text", rejectReason(tree, 1, permissiveRule()))
    }

    @Test
    fun `rejects deny id`() {
        val node = Fixtures.node(text = "跳过", viewId = "com.foo:id/download_button")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("candidate_denied_id", rejectReason(tree, 1, permissiveRule()))
    }

    @Test
    fun `rejects invisible candidate`() {
        val node = Fixtures.node(text = "跳过", visible = false)
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("candidate_not_visible", rejectReason(tree, 1, permissiveRule()))
    }

    @Test
    fun `rejects disabled candidate`() {
        val node = Fixtures.node(text = "跳过", enabled = false)
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("candidate_disabled", rejectReason(tree, 1, permissiveRule()))
    }

    @Test
    fun `rejects node package mismatch`() {
        val node = Fixtures.node(text = "跳过", packageName = "com.example.other")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("node_package_mismatch", rejectReason(tree, 1, permissiveRule()))
    }

    @Test
    fun `rejects foreground package mismatch`() {
        val node = Fixtures.node(text = "跳过")
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))
        val rule = permissiveRule()

        val result = gate.evaluate(
            tree = tree,
            candidateIndex = 1,
            rule = rule,
            foregroundPackage = "com.example.other",
            screenWidth = Fixtures.SCREEN_W,
            screenHeight = Fixtures.SCREEN_H,
        )

        assertEquals("foreground_package_mismatch", assertIs<GateResult.Rejected>(result).reason)
    }

    @Test
    fun `rejects candidate outside allowed region`() {
        val node = Fixtures.node(text = "跳过", bounds = RectI(100, 2400, 300, 2600))
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals(
            "candidate_outside_allowed_bounds",
            rejectReason(tree, 1, permissiveRule(allowedBounds = "[0.65,0.0,1.0,0.25]")),
        )
    }

    @Test
    fun `accepts candidate in default top right region`() {
        val node = Fixtures.node(text = "跳过", bounds = RectI(1000, 100, 1240, 250))
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))
        val rule = permissiveRule(allowedBounds = "[0.65,0.0,1.0,0.25]")

        val result = gate.evaluate(
            tree = tree,
            candidateIndex = 1,
            rule = rule,
            foregroundPackage = Fixtures.BILI_PACKAGE,
            screenWidth = Fixtures.SCREEN_W,
            screenHeight = Fixtures.SCREEN_H,
        )

        assertIs<GateResult.Allowed>(result)
    }

    @Test
    fun `rejects when no clickable target exists`() {
        val node = Fixtures.node(text = "跳过", clickable = false)
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))
        val rule = permissiveRule(clickable = false)

        assertEquals("no_clickable_target", rejectReason(tree, 1, rule))
    }

    @Test
    fun `rejects deny keyword on clickable ancestor`() {
        val textNode = Fixtures.node(
            text = "跳过",
            clickable = false,
            bounds = RectI(940, 2440, 1180, 2550),
        )
        val container = Fixtures.node(
            className = "android.widget.FrameLayout",
            text = "立即下载",
            clickable = true,
            bounds = RectI(920, 2420, 1200, 2570),
            children = listOf(textNode),
        )
        val tree = UiTree.fromRoot(Fixtures.screenRoot(container))
        val rule = permissiveRule(clickable = false)

        assertEquals("click_denied_text", rejectReason(tree, 2, rule))
    }

    @Test
    fun `accepts non clickable candidate when tap by gesture`() {
        val node = Fixtures.node(text = "跳过", clickable = false)
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))
        val rule = gestureRule()

        val result = gate.evaluate(
            tree = tree,
            candidateIndex = 1,
            rule = rule,
            foregroundPackage = Fixtures.BILI_PACKAGE,
            screenWidth = Fixtures.SCREEN_W,
            screenHeight = Fixtures.SCREEN_H,
        )

        assertIs<GateResult.Allowed>(result)
    }

    @Test
    fun `rejects oversized gesture target`() {
        val node = Fixtures.node(
            text = "跳过",
            clickable = false,
            bounds = RectI(0, 0, Fixtures.SCREEN_W, Fixtures.SCREEN_H),
        )
        val tree = UiTree.fromRoot(Fixtures.screenRoot(node))

        assertEquals("gesture_target_too_large", rejectReason(tree, 1, gestureRule()))
    }

    @Test
    fun `rejects clickable target larger than screen quarter`() {
        val textNode = Fixtures.node(
            text = "跳过",
            clickable = false,
            bounds = RectI(940, 2440, 1180, 2550),
        )
        val root = Fixtures.screenRoot(textNode).copy(clickable = true)
        val tree = UiTree.fromRoot(root)
        val rule = permissiveRule(clickable = false)

        assertEquals("click_target_too_large", rejectReason(tree, 1, rule))
    }
}
