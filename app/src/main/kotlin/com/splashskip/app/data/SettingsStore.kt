package com.splashskip.app.data

import android.content.Context
import android.content.SharedPreferences
import com.splashskip.engine.model.Rule
import java.time.LocalDate

class SettingsStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var dryRun: Boolean
        get() = preferences.getBoolean(KEY_DRY_RUN, true)
        set(value) {
            preferences.edit().putBoolean(KEY_DRY_RUN, value).apply()
        }

    /**
     * 全探测模式：订阅所有应用，只记录“跳过/关闭广告”候选，绝不点击。
     * 默认关闭；开启时规则与 dry-run 均不参与。
     */
    var probeAll: Boolean
        get() = preferences.getBoolean(KEY_PROBE_ALL, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PROBE_ALL, value).apply()
        }

    fun isRuleEnabled(ruleId: String): Boolean =
        preferences.getBoolean(KEY_RULE_PREFIX + ruleId, true)

    /** 模块内 enabled 与用户开关同时为 true 时才生效。 */
    fun isRuleActive(rule: Rule): Boolean = rule.enabled && isRuleEnabled(rule.id)

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RULE_PREFIX + ruleId, enabled).apply()
    }

    /**
     * 规则目录变化后清理已不存在规则的开关，避免恢复内置/删除导入后
     * 旧 id 的偏好继续残留。幸存的同 id 规则保留用户原有开关。
     */
    fun pruneRuleToggles(validIds: Set<String>) {
        val stale = preferences.all.keys.filter { key ->
            key.startsWith(KEY_RULE_PREFIX) &&
                key.removePrefix(KEY_RULE_PREFIX) !in validIds
        }
        if (stale.isEmpty()) return
        val editor = preferences.edit()
        stale.forEach(editor::remove)
        editor.apply()
    }

    /** 规则模块写入/删除后递增，服务监听此键重新加载规则。 */
    fun bumpRuleRevision() {
        val next = preferences.getLong(KEY_RULE_REVISION, 0L) + 1L
        preferences.edit()
            .putLong(KEY_RULE_REVISION, next)
            .apply()
    }

    fun registerRuleChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterRuleChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun recordHit() {
        ensureToday()
        preferences.edit().putInt(KEY_HITS, preferences.getInt(KEY_HITS, 0) + 1).apply()
    }

    fun recordClick() {
        ensureToday()
        preferences.edit().putInt(KEY_CLICKS, preferences.getInt(KEY_CLICKS, 0) + 1).apply()
    }

    fun recordLastHit(ruleId: String) {
        preferences.edit()
            .putString(KEY_LAST_HIT_RULE, ruleId)
            .putLong(KEY_LAST_HIT_TIME, System.currentTimeMillis())
            .apply()
    }

    fun stats(): Stats {
        ensureToday()
        return Stats(
            date = preferences.getString(KEY_STATS_DATE, today()).orEmpty(),
            hits = preferences.getInt(KEY_HITS, 0),
            clicks = preferences.getInt(KEY_CLICKS, 0),
        )
    }

    fun lastHit(): LastHit? {
        val ruleId = preferences.getString(KEY_LAST_HIT_RULE, null) ?: return null
        val time = preferences.getLong(KEY_LAST_HIT_TIME, 0L)
        if (time <= 0L) return null
        return LastHit(ruleId, time)
    }

    private fun ensureToday() {
        val today = today()
        if (preferences.getString(KEY_STATS_DATE, null) != today) {
            preferences.edit()
                .putString(KEY_STATS_DATE, today)
                .putInt(KEY_HITS, 0)
                .putInt(KEY_CLICKS, 0)
                .apply()
        }
    }

    private fun today(): String = LocalDate.now().toString()

    data class Stats(
        val date: String,
        val hits: Int,
        val clicks: Int,
    )

    data class LastHit(
        val ruleId: String,
        val atMillis: Long,
    )

    companion object {
        const val KEY_RULE_PREFIX = "rule_enabled_"
        const val KEY_PROBE_ALL = "probe_all"
        const val KEY_RULE_REVISION = "rule_revision"
        private const val PREFS_NAME = "splash_skip_settings"
        private const val KEY_DRY_RUN = "dry_run"
        private const val KEY_HITS = "stats_hits"
        private const val KEY_CLICKS = "stats_clicks"
        private const val KEY_STATS_DATE = "stats_date"
        private const val KEY_LAST_HIT_RULE = "last_hit_rule"
        private const val KEY_LAST_HIT_TIME = "last_hit_time"
    }
}
