package com.example.nodedemo.core.model

data class RectI(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    val area: Long get() = width.toLong() * height.toLong()

    fun isUsable(): Boolean = right > left && bottom > top

    override fun toString(): String = "[$left,$top][$right,$bottom]"
}

/**
 * 与平台无关的界面节点快照，不持有 AccessibilityNodeInfo。
 * toString() 刻意不输出 text/contentDescription，避免日志泄露节点文本。
 */
data class UiNode(
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val visibleToUser: Boolean = true,
    val bounds: RectI? = null,
    val packageName: String? = null,
    val depth: Int = 0,
    val index: Int = 0,
    val parentIndex: Int? = null,
    val path: List<Int> = emptyList(),
    val children: List<UiNode> = emptyList(),
) {
    override fun toString(): String =
        "UiNode(class=$className, clickable=$clickable, enabled=$enabled, " +
            "visible=$visibleToUser, bounds=$bounds, package=$packageName, path=$path)"
}
