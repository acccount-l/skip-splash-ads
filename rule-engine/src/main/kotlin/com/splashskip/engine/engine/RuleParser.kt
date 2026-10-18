package com.splashskip.engine.engine

import com.splashskip.engine.model.Action
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.RuleSet
import kotlinx.serialization.json.Json

object RuleParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    fun parse(text: String): Result<RuleSet> = runCatching {
        val ruleSet = json.decodeFromString<RuleSet>(text)
        validate(ruleSet)
        ruleSet.copy(
            rules = ruleSet.rules.map { it.copy(packageName = ruleSet.packageName) },
        )
    }

    fun validate(ruleSet: RuleSet) {
        require(ruleSet.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "不支持的 schemaVersion: ${ruleSet.schemaVersion}"
        }
        require(PACKAGE_REGEX.matches(ruleSet.packageName)) {
            "非法包名: ${ruleSet.packageName}"
        }
        require(ruleSet.rules.isNotEmpty()) { "规则集为空" }

        val ids = HashSet<String>()
        ruleSet.rules.forEach { rule ->
            validateRule(rule)
            require(ids.add(rule.id)) { "重复规则 id: ${rule.id}" }
        }
    }

    private fun validateRule(rule: Rule) {
        require(RULE_ID_REGEX.matches(rule.id)) { "非法规则 id: ${rule.id}" }

        require(rule.activityIds.isNotEmpty()) { "规则 ${rule.id} 必须配置 activityIds" }
        require(rule.activityIds.size <= MAX_ACTIVITY_IDS) { "规则 ${rule.id} 的 activityIds 过多" }
        rule.activityIds.forEach { activity ->
            require(activity.isNotBlank() && activity.length <= MAX_ACTIVITY_ID_LENGTH) {
                "规则 ${rule.id} 的 activityId 非法"
            }
        }

        val match = rule.match
        val hasMatcher = !match.textRegex.isNullOrBlank() ||
            match.texts.isNotEmpty() ||
            !match.contentDescriptionRegex.isNullOrBlank() ||
            !match.viewIdRegex.isNullOrBlank()
        require(hasMatcher) { "规则 ${rule.id} 缺少匹配条件" }

        if (rule.tapByGesture) {
            require(rule.action == Action.CLICK) {
                "规则 ${rule.id} 的 tapByGesture 只支持 CLICK 动作"
            }
            require(
                !match.textRegex.isNullOrBlank() ||
                    match.texts.isNotEmpty() ||
                    !match.contentDescriptionRegex.isNullOrBlank(),
            ) {
                "规则 ${rule.id} 的手势点击必须基于可见文字或内容描述，不能只靠 viewId"
            }
        }

        match.textRegex?.let { validateRegex(rule.id, "textRegex", it) }
        match.contentDescriptionRegex?.let { validateRegex(rule.id, "contentDescriptionRegex", it) }
        match.viewIdRegex?.let { validateRegex(rule.id, "viewIdRegex", it) }

        require(match.texts.size <= MAX_TEXTS) { "规则 ${rule.id} 的 texts 过多" }
        match.texts.forEach { text ->
            require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) {
                "规则 ${rule.id} 的 texts 含空值或超长文本"
            }
        }
        match.className?.let { className ->
            require(className.isNotBlank() && className.length <= MAX_CLASS_NAME_LENGTH) {
                "规则 ${rule.id} 的 className 非法"
            }
        }
        match.allowedBounds?.let { bounds ->
            require(bounds.size == 4) { "规则 ${rule.id} 的 allowedBounds 必须是 4 个数" }
            require(bounds.all { it.isFinite() && it in 0f..1f }) {
                "规则 ${rule.id} 的 allowedBounds 必须在 0..1 之间"
            }
            require(bounds[0] < bounds[2] && bounds[1] < bounds[3]) {
                "规则 ${rule.id} 的 allowedBounds 顺序非法"
            }
        }

        val constraints = rule.constraints
        require(constraints.withinMsAfterLaunch in MIN_WINDOW_MS..MAX_WINDOW_MS) {
            "规则 ${rule.id} 的 withinMsAfterLaunch 超出范围"
        }
        require(constraints.cooldownMs in 0L..MAX_COOLDOWN_MS) {
            "规则 ${rule.id} 的 cooldownMs 超出范围"
        }
        require(constraints.maxAttempts in 1..MAX_ATTEMPTS) {
            "规则 ${rule.id} 的 maxAttempts 超出范围"
        }
    }

    private fun validateRegex(ruleId: String, field: String, pattern: String) {
        require(pattern.isNotBlank() && pattern.length <= MAX_REGEX_LENGTH) {
            "规则 $ruleId 的 $field 为空或过长"
        }
        runCatching { Regex(pattern) }.getOrElse {
            throw IllegalArgumentException("规则 $ruleId 的 $field 不是合法正则")
        }
    }

    private const val SUPPORTED_SCHEMA_VERSION = 1
    private const val MAX_ACTIVITY_IDS = 10
    private const val MAX_ACTIVITY_ID_LENGTH = 200
    private const val MAX_TEXTS = 10
    private const val MAX_TEXT_LENGTH = 32
    private const val MAX_CLASS_NAME_LENGTH = 200
    private const val MAX_REGEX_LENGTH = 200
    private const val MIN_WINDOW_MS = 500L
    private const val MAX_WINDOW_MS = 30_000L
    private const val MAX_COOLDOWN_MS = 10_000L
    private const val MAX_ATTEMPTS = 3

    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val RULE_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")
}
