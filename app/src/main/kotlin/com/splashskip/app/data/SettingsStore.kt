package com.splashskip.app.data

import android.content.Context
import android.content.SharedPreferences
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

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RULE_PREFIX + ruleId, enabled).apply()
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
        private const val PREFS_NAME = "splash_skip_settings"
        private const val KEY_DRY_RUN = "dry_run"
        private const val KEY_HITS = "stats_hits"
        private const val KEY_CLICKS = "stats_clicks"
        private const val KEY_STATS_DATE = "stats_date"
        private const val KEY_LAST_HIT_RULE = "last_hit_rule"
        private const val KEY_LAST_HIT_TIME = "last_hit_time"
    }
}
