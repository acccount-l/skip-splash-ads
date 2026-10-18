package com.splashskip.engine.engine

import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.UiTree

/**
 * 全探测模式使用的只读候选匹配器。
 *
 * 与 RuleMatcher/SafetyGate 完全独立：不关心包名、Activity、允许区域，
 * 也不产生任何点击动作；只把“像跳过/关闭广告”的短文本节点整理出来，
 * 供无障碍服务写入脱敏日志。
 */
object ProbeMatcher {
    enum class Kind { SKIP, CLOSE }

    enum class Source { TEXT, DESCRIPTION }

    data class ProbeCandidate(
        val nodeIndex: Int,
        val kind: Kind,
        val source: Source,
        val text: String,
        val viewId: String?,
        val className: String?,
        val clickable: Boolean,
        val bounds: RectI,
        val centerXPercent: Int,
        val centerYPercent: Int,
        val clickableAncestorIndex: Int?,
    )

    private val SKIP_REGEX = Regex(
        "^(点击)?\\s*(跳过|skip)(\\s*(此|本)?\\s*(广告|ad))?\\s*[|·]?\\s*\\d*\\s*[sS秒]?$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * “关闭”允许单独出现（很多开屏广告只有一个关闭按钮），
     * 但必须整段匹配，所以“关闭弹幕”“关闭广告，立即下载”不会命中。
     */
    private val CLOSE_REGEX = Regex(
        "^(关闭|close)\\s*(广告|ad)?\\s*[|·]?\\s*\\d*\\s*[sS秒]?$",
        RegexOption.IGNORE_CASE,
    )

    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val DIGITS = Regex("\\d+")

    fun findCandidates(
        tree: UiTree,
        screenWidth: Int,
        screenHeight: Int,
        max: Int = 8,
    ): List<ProbeCandidate> {
        if (max <= 0 || screenWidth <= 0 || screenHeight <= 0) return emptyList()

        // 同一轮去重：父/子节点或 text/desc 重复时只留一个。
        val found = LinkedHashMap<String, ProbeCandidate>()

        for (index in 0 until tree.size) {
            val node = tree.node(index)
            if (!node.visibleToUser || !node.enabled) continue

            val bounds = node.bounds ?: continue
            if (!bounds.isUsable()) continue
            if (bounds.centerX !in 0 until screenWidth ||
                bounds.centerY !in 0 until screenHeight
            ) {
                continue
            }

            val text = normalize(node.text)
            val textKind = kindOf(text)
            val kind: Kind
            val source: Source
            val raw: String
            if (textKind != null) {
                kind = textKind
                source = Source.TEXT
                raw = text
            } else {
                val desc = normalize(node.contentDescription)
                val descKind = kindOf(desc) ?: continue
                kind = descKind
                source = Source.DESCRIPTION
                raw = desc
            }

            val candidate = ProbeCandidate(
                nodeIndex = index,
                kind = kind,
                source = source,
                text = raw,
                viewId = node.viewId,
                className = node.className,
                clickable = node.clickable,
                bounds = bounds,
                centerXPercent = bounds.centerX * 100 / screenWidth,
                centerYPercent = bounds.centerY * 100 / screenHeight,
                clickableAncestorIndex = tree.findClickableAncestor(index),
            )

            val key = buildString {
                append(kind.name).append('|')
                append(source.name).append('|')
                append(normalizeDigits(raw)).append('|')
                append(node.className).append('|')
                append(bounds)
            }
            if (!found.containsKey(key)) found[key] = candidate
        }

        // “跳过”比“关闭”更强，优先保留；同 kind 按节点顺序。
        return found.values
            .sortedWith(compareBy<ProbeCandidate> { it.kind.ordinal }.thenBy { it.nodeIndex })
            .take(max)
    }

    fun kindOf(raw: String): Kind? {
        val value = normalize(raw)
        if (value.isEmpty()) return null
        return when {
            SKIP_REGEX.matches(value) -> Kind.SKIP
            CLOSE_REGEX.matches(value) -> Kind.CLOSE
            else -> null
        }
    }

    private fun normalize(raw: String?): String =
        raw?.replace(WHITESPACE, " ")?.trim().orEmpty()

    private fun normalizeDigits(value: String): String =
        value.lowercase().replace(DIGITS, "#")
}
