package com.splashskip.app.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.UiNode

object NodeMapper {
    private const val MAX_DEPTH = 30
    const val MAX_NODES = 500

    fun map(root: AccessibilityNodeInfo?, fallbackPackage: String?): UiNode? {
        if (root == null) return null
        var count = 0

        fun mapNode(node: AccessibilityNodeInfo, depth: Int): UiNode? {
            if (count >= MAX_NODES) return null
            count++

            val rect = Rect()
            node.getBoundsInScreen(rect)

            val children = ArrayList<UiNode>()
            if (depth < MAX_DEPTH) {
                for (index in 0 until node.childCount) {
                    if (count >= MAX_NODES) break
                    val child = try {
                        node.getChild(index)
                    } catch (t: Throwable) {
                        null
                    } ?: continue
                    mapNode(child, depth + 1)?.let(children::add)
                }
            }

            return UiNode(
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                visibleToUser = node.isVisibleToUser,
                bounds = RectI(rect.left, rect.top, rect.right, rect.bottom),
                packageName = node.packageName?.toString() ?: fallbackPackage,
                depth = depth,
                children = children,
            )
        }

        return mapNode(root, 0)
    }
}
