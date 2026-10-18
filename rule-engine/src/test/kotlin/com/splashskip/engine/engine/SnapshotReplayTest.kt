package com.splashskip.engine.engine

import com.splashskip.engine.Fixtures
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.UiNode
import com.splashskip.engine.model.UiTree
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 用合成 XML 模拟 uiautomator 快照，验证“只点跳过、不点关闭弹幕”。
 */
class SnapshotReplayTest {
    @Test
    fun `replays bilibili splash snapshot and selects only skip`() {
        val xml = """
            <hierarchy>
              <node package="tv.danmaku.bili" class="android.widget.FrameLayout" bounds="[0,0][1260,2800]">
                <node package="tv.danmaku.bili" class="android.widget.TextView" text="跳过 3"
                      clickable="true" enabled="true" visible-to-user="true" bounds="[923,2425][1204,2565]" />
                <node package="tv.danmaku.bili" class="android.widget.ImageView" content-desc="关闭弹幕"
                      clickable="true" enabled="true" visible-to-user="true" bounds="[670,2523][866,2635]" />
              </node>
            </hierarchy>
        """.trimIndent()

        val tree = UiTree.fromRoot(parseSnapshot(xml))
        val found = assertNotNull(
            RuleMatcher().findBest(
                tree = tree,
                rule = Fixtures.biliRule(),
                foregroundPackage = Fixtures.BILI_PACKAGE,
                screenWidth = Fixtures.SCREEN_W,
                screenHeight = Fixtures.SCREEN_H,
            ),
        )

        assertEquals("跳过 3", found.node.text)
        assertEquals(RectI(923, 2425, 1204, 2565), found.clickNode.bounds)
        assertEquals(listOf(0), found.node.path)
    }

    private fun parseSnapshot(xml: String): UiNode {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isExpandEntityReferences = false

        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val hierarchy = document.documentElement
        val firstNode = (0 until hierarchy.childNodes.length)
            .map { hierarchy.childNodes.item(it) }
            .firstOrNull { it.nodeType == Node.ELEMENT_NODE }
            as? Element
            ?: error("snapshot 中没有 node")

        return toUiNode(firstNode)
    }

    private fun toUiNode(element: Element): UiNode {
        val children = (0 until element.childNodes.length).mapNotNull { index ->
            val child = element.childNodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && child is Element && child.tagName == "node") {
                toUiNode(child)
            } else {
                null
            }
        }

        return UiNode(
            className = element.getAttribute("class").takeIf { it.isNotEmpty() },
            text = element.getAttribute("text").takeIf { it.isNotEmpty() },
            contentDescription = element.getAttribute("content-desc").takeIf { it.isNotEmpty() },
            clickable = element.getAttribute("clickable") == "true",
            enabled = element.getAttribute("enabled") != "false",
            visibleToUser = element.getAttribute("visible-to-user") != "false",
            bounds = parseBounds(element.getAttribute("bounds")),
            packageName = element.getAttribute("package").takeIf { it.isNotEmpty() },
            children = children,
        )
    }

    private fun parseBounds(value: String): RectI? {
        val match = BOUNDS_REGEX.find(value) ?: return null
        val (left, top, right, bottom) = match.destructured
        return RectI(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    private companion object {
        val BOUNDS_REGEX = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")
    }
}
