package com.example.nodedemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiTreeTest {
    private val button = UiNode(
        className = "android.widget.Button",
        clickable = true,
        bounds = RectI(20, 40, 180, 100),
        children = emptyList(),
    )
    private val label = UiNode(
        className = "android.widget.TextView",
        clickable = false,
        bounds = RectI(20, 120, 180, 160),
        children = emptyList(),
    )
    private val root = UiNode(
        className = "android.widget.FrameLayout",
        clickable = false,
        bounds = RectI(0, 0, 200, 200),
        children = listOf(button, label),
    )

    @Test
    fun `flattens children and keeps parent indexes`() {
        val tree = UiTree.fromRoot(root)

        assertEquals(3, tree.size)
        assertNull(tree.parentOf(0))
        assertEquals(0, tree.parentOf(1))
        assertEquals(0, tree.parentOf(2))
        assertEquals(listOf(0), tree.node(1).path)
        assertEquals(listOf(1), tree.node(2).path)
    }

    @Test
    fun `finds clickable node itself`() {
        val tree = UiTree.fromRoot(root)

        assertEquals(1, tree.findClickableAncestor(1))
    }

    @Test
    fun `walks up to a clickable ancestor`() {
        val child = UiNode(className = "android.widget.TextView", clickable = false)
        val parent = UiNode(className = "android.widget.Button", clickable = true, children = listOf(child))
        val tree = UiTree.fromRoot(parent)

        assertEquals(0, tree.findClickableAncestor(1))
    }

    @Test
    fun `returns null when no clickable ancestor is usable`() {
        val child = UiNode(className = "android.widget.TextView", clickable = false)
        val parent = UiNode(
            className = "android.widget.Button",
            clickable = true,
            enabled = false,
            children = listOf(child),
        )
        val tree = UiTree.fromRoot(parent)

        assertNull(tree.findClickableAncestor(1))
    }

    @Test
    fun `snapshot string does not expose node text`() {
        val node = UiNode(text = "secret", contentDescription = "secret-description")
        val rendered = node.toString()

        assertFalse(rendered.contains("secret"))
        assertTrue(rendered.contains("UiNode"))
    }
}
