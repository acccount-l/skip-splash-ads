package com.splashskip.engine.model

/**
 * 把嵌套 UiNode 展平成带父节点索引的树，方便安全门向上寻找可点击祖先。
 * path 是从根节点到当前节点的 child index 列表，点击前用它重新定位真实节点。
 */
class UiTree private constructor(
    private val nodes: List<UiNode>,
    private val parents: IntArray,
) {
    val size: Int get() = nodes.size

    fun node(index: Int): UiNode = nodes[index]

    fun parentOf(index: Int): Int? = parents[index].let { if (it >= 0) it else null }

    /** 从当前节点向上找最近的可见、启用、可点击节点；包含自身。 */
    fun findClickableAncestor(index: Int): Int? {
        var current: Int? = index
        while (current != null) {
            val node = nodes[current]
            if (node.clickable && node.enabled && node.visibleToUser) return current
            current = parentOf(current)
        }
        return null
    }

    companion object {
        fun fromRoot(root: UiNode): UiTree {
            val out = ArrayList<UiNode>()
            val parents = ArrayList<Int>()

            fun walk(node: UiNode, parent: Int, path: List<Int>, depth: Int) {
                val index = out.size
                out.add(
                    node.copy(
                        children = emptyList(),
                        depth = depth,
                        index = index,
                        parentIndex = parent.takeIf { it >= 0 },
                        path = path,
                    ),
                )
                parents.add(parent)
                node.children.forEachIndexed { childIndex, child ->
                    walk(child, index, path + childIndex, depth + 1)
                }
            }

            walk(root, -1, emptyList(), 0)
            return UiTree(out, parents.toIntArray())
        }
    }
}
