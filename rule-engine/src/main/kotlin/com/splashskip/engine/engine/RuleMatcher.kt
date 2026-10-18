package com.splashskip.engine.engine

import com.splashskip.engine.model.MatchSpec
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.UiNode
import com.splashskip.engine.model.UiTree

data class Candidate(
    val nodeIndex: Int,
    val clickNodeIndex: Int,
    val node: UiNode,
    val clickNode: UiNode,
)

class RuleMatcher(
    private val safetyGate: SafetyGate = SafetyGate(),
) {
    private val regexCache = HashMap<String, Regex>()

    /**
     * 返回屏幕内满足规则且通过安全门的候选；多个候选取点击区域最小的一个。
     */
    fun findBest(
        tree: UiTree,
        rule: Rule,
        foregroundPackage: String?,
        screenWidth: Int,
        screenHeight: Int,
    ): Candidate? {
        var best: Candidate? = null
        var bestArea = Long.MAX_VALUE

        for (index in 0 until tree.size) {
            val node = tree.node(index)
            if (!matches(rule.match, node)) continue

            val gate = safetyGate.evaluate(
                tree = tree,
                candidateIndex = index,
                rule = rule,
                foregroundPackage = foregroundPackage,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
            if (gate !is GateResult.Allowed) continue

            val clickNode = tree.node(gate.clickNodeIndex)
            val area = clickNode.bounds?.area ?: Long.MAX_VALUE
            if (area < bestArea) {
                bestArea = area
                best = Candidate(
                    nodeIndex = index,
                    clickNodeIndex = gate.clickNodeIndex,
                    node = node,
                    clickNode = clickNode,
                )
            }
        }
        return best
    }

    /**
     * 用当前节点的字段重新跑一遍规则匹配。
     * 点击前校验快照时使用：倒计时文字变化（“跳过 3”->“跳过 2”）仍可命中，
     * 但文字变成“跳过广告，立即下载”等不再满足规则时拒绝。
     */
    fun matchesValues(
        match: MatchSpec,
        text: String?,
        contentDescription: String?,
        viewId: String?,
        className: String?,
        clickable: Boolean,
    ): Boolean {
        if (match.clickable && !clickable) return false

        val ruleClassName = match.className
        if (ruleClassName != null && className != ruleClassName) return false

        val trimmedText = text?.trim()?.take(MAX_TEXT_LENGTH)
        val hasTextRule = match.textRegex != null || match.texts.isNotEmpty()
        if (hasTextRule) {
            if (trimmedText.isNullOrEmpty()) return false
            val regexHit = match.textRegex?.let { pattern ->
                regex(pattern)?.matches(trimmedText) == true
            } ?: false
            val exactHit = match.texts.any { it.trim() == trimmedText }
            if (!regexHit && !exactHit) return false
        }

        val descRegex = match.contentDescriptionRegex
        if (descRegex != null) {
            val desc = contentDescription?.trim()?.take(MAX_TEXT_LENGTH)
            if (desc.isNullOrEmpty()) return false
            if (regex(descRegex)?.matches(desc) != true) return false
        }

        val idRegex = match.viewIdRegex
        if (idRegex != null) {
            if (viewId.isNullOrEmpty()) return false
            if (regex(idRegex)?.matches(viewId) != true) return false
        }

        return true
    }

    /** 诊断用：不经过安全门，只判断节点字面字段是否满足规则。 */
    fun matchesNode(rule: Rule, node: UiNode): Boolean = matches(rule.match, node)

    private fun matches(match: MatchSpec, node: UiNode): Boolean =
        matchesValues(
            match = match,
            text = node.text,
            contentDescription = node.contentDescription,
            viewId = node.viewId,
            className = node.className,
            clickable = node.clickable,
        )

    private fun regex(pattern: String): Regex? {
        synchronized(regexCache) {
            regexCache[pattern]?.let { return it }
        }
        val compiled = runCatching { Regex(pattern) }.getOrNull() ?: return null
        synchronized(regexCache) {
            regexCache[pattern] = compiled
        }
        return compiled
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 200
    }
}
