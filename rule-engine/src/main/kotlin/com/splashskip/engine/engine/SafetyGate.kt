package com.splashskip.engine.engine

import com.splashskip.engine.model.Action
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.UiTree

sealed interface GateResult {
    data class Allowed(val clickNodeIndex: Int) : GateResult

    data class Rejected(val reason: String) : GateResult
}

/**
 * 不可被规则绕过的硬安全门。
 *
 * 规则只能缩小匹配范围，不能关闭这些检查：
 * - 前台包名必须与规则包名一致
 * - 节点必须在规则允许区域内
 * - text/desc 命中 deny 关键词直接拒绝
 * - viewId 命中 deny 正则直接拒绝
 * - 点击目标必须可见、启用、可点击，且不能是超过屏幕 25% 的大容器
 */
class SafetyGate(
    private val denyKeywords: List<String> = DEFAULT_DENY_KEYWORDS,
    private val denyIdRegex: Regex = DEFAULT_DENY_ID_REGEX,
) {
    fun evaluate(
        tree: UiTree,
        candidateIndex: Int,
        rule: Rule,
        foregroundPackage: String?,
        screenWidth: Int,
        screenHeight: Int,
    ): GateResult {
        if (screenWidth <= 0 || screenHeight <= 0) return GateResult.Rejected("invalid_screen")
        if (foregroundPackage != rule.packageName) {
            return GateResult.Rejected("foreground_package_mismatch")
        }

        val candidate = tree.node(candidateIndex)
        if (candidate.packageName != null && candidate.packageName != rule.packageName) {
            return GateResult.Rejected("node_package_mismatch")
        }
        if (!candidate.visibleToUser) return GateResult.Rejected("candidate_not_visible")
        if (!candidate.enabled) return GateResult.Rejected("candidate_disabled")

        val candidateBounds = candidate.bounds
        if (candidateBounds == null || !candidateBounds.isUsable()) {
            return GateResult.Rejected("candidate_bounds_invalid")
        }

        val allowed = rule.match.allowedBounds ?: DEFAULT_ALLOWED_BOUNDS
        if (!Bounds.contains(allowed, candidateBounds.centerX, candidateBounds.centerY, screenWidth, screenHeight)) {
            return GateResult.Rejected("candidate_outside_allowed_bounds")
        }
        if (isDeniedText(candidate.text) || isDeniedText(candidate.contentDescription)) {
            return GateResult.Rejected("candidate_denied_text")
        }
        if (isDeniedId(candidate.viewId)) return GateResult.Rejected("candidate_denied_id")

        if (rule.action == Action.BACK) return GateResult.Allowed(candidateIndex)

        val screenArea = screenWidth.toLong() * screenHeight.toLong()
        if (rule.tapByGesture) {
            // 手势点击直接落在“跳过/关闭”文字节点中心。
            // 仍然要求节点可见、启用、位于允许区域、无 deny 语义，且面积不过大。
            if (candidateBounds.area > (screenArea * MAX_CLICK_AREA_RATIO).toLong()) {
                return GateResult.Rejected("gesture_target_too_large")
            }
            return GateResult.Allowed(candidateIndex)
        }

        val clickIndex = resolveClickable(tree, candidateIndex)
            ?: return GateResult.Rejected("no_clickable_target")
        val clickNode = tree.node(clickIndex)

        if (!clickNode.visibleToUser) return GateResult.Rejected("click_not_visible")
        if (!clickNode.enabled) return GateResult.Rejected("click_disabled")
        if (!clickNode.clickable) return GateResult.Rejected("click_not_clickable")

        val clickBounds = clickNode.bounds
        if (clickBounds == null || !clickBounds.isUsable()) {
            return GateResult.Rejected("click_bounds_invalid")
        }
        if (!Bounds.contains(allowed, clickBounds.centerX, clickBounds.centerY, screenWidth, screenHeight)) {
            return GateResult.Rejected("click_outside_allowed_bounds")
        }
        if (isDeniedText(clickNode.text) || isDeniedText(clickNode.contentDescription)) {
            return GateResult.Rejected("click_denied_text")
        }
        if (isDeniedId(clickNode.viewId)) return GateResult.Rejected("click_denied_id")

        if (clickBounds.area > (screenArea * MAX_CLICK_AREA_RATIO).toLong()) {
            return GateResult.Rejected("click_target_too_large")
        }

        return GateResult.Allowed(clickIndex)
    }

    private fun resolveClickable(tree: UiTree, start: Int): Int? =
        tree.findClickableAncestor(start)

    private fun isDeniedText(text: String?): Boolean {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return false
        return denyKeywords.any { value.contains(it, ignoreCase = true) }
    }

    private fun isDeniedId(viewId: String?): Boolean {
        val value = viewId ?: return false
        return denyIdRegex.containsMatchIn(value)
    }

    companion object {
        /** 点击目标最大占屏比，防止误点广告整块容器。 */
        const val MAX_CLICK_AREA_RATIO = 0.25

        /** 规则未配置 allowedBounds 时的默认安全区：右上角。 */
        val DEFAULT_ALLOWED_BOUNDS: List<Float> = listOf(0.65f, 0.0f, 1.0f, 0.25f)

        val DEFAULT_DENY_KEYWORDS: List<String> = listOf(
            "下载", "安装", "更新", "升级", "购买", "支付", "付款", "充值", "开通",
            "订阅", "续费", "授权", "登录", "注册", "领取", "提现", "绑定", "实名",
            "同意", "允许", "打开", "去看看", "立即购买", "免费领取", "红包", "优惠券",
            "install", "download", "buy", "purchase", "pay", "subscribe", "login",
            "sign in", "register", "authorize", "allow", "open",
        )

        val DEFAULT_DENY_ID_REGEX: Regex = Regex(
            "(install|download|pay|buy|purchase|login|auth|update)",
            RegexOption.IGNORE_CASE,
        )
    }
}
