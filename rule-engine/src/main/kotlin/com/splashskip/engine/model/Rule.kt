package com.splashskip.engine.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 一个 App 的规则集。规则文件只从 APK assets 读取，第一版不联网更新。
 */
@Serializable
data class RuleSet(
    val schemaVersion: Int,
    val packageName: String,
    val rules: List<Rule>,
)

@Serializable
data class Rule(
    val id: String,
    val enabled: Boolean = true,
    val activityIds: List<String> = emptyList(),
    val match: MatchSpec,
    val action: Action = Action.CLICK,
    /**
     * 对“有跳过文字、但整棵节点树没有 clickable 目标”的广告页，
     * 在匹配节点中心发送一次单点手势。仅对显式开启的规则生效。
     */
    val tapByGesture: Boolean = false,
    val constraints: Constraints = Constraints(),
    /**
     * 由 RuleParser 从所属 RuleSet 注入，不写入 JSON。
     * 安全门用它做双重包名校验；直接构造 Rule 而不注入时为空，所有校验都会失败。
     */
    @Transient
    val packageName: String = "",
)

/**
 * 所有已填写的条件按 AND 组合；textRegex 与 texts 之间是 OR 关系。
 */
@Serializable
data class MatchSpec(
    val textRegex: String? = null,
    val texts: List<String> = emptyList(),
    val contentDescriptionRegex: String? = null,
    val viewIdRegex: String? = null,
    val className: String? = null,
    val clickable: Boolean = true,
    /** 归一化坐标 [left, top, right, bottom]，取值 0..1。为空时使用右上角默认区域。 */
    val allowedBounds: List<Float>? = null,
)

@Serializable
enum class Action {
    CLICK,
    BACK,
}

@Serializable
data class Constraints(
    val withinMsAfterLaunch: Long = 8_000L,
    val cooldownMs: Long = 2_000L,
    val maxAttempts: Int = 1,
    /** true 时该规则永远只记录、不点击，可作为硬保险。 */
    val dryRun: Boolean = true,
)
