package com.example.nodedemo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 一次性演示授权。授权只在应用内由用户显式创建，默认不存在；
 * 有效期 60 秒，读取或点击一次后立即清除。
 */
class DemoArmStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Mode { READ, CLICK }

    data class Arm(
        val mode: Mode,
        val expiresAtMillis: Long,
    )

    @Synchronized
    fun arm(mode: Mode): Arm {
        val arm = Arm(mode, System.currentTimeMillis() + ARM_TTL_MS)
        preferences.edit()
            .putString(KEY_MODE, mode.name)
            .putLong(KEY_EXPIRES_AT, arm.expiresAtMillis)
            .commit()
        return arm
    }

    @Synchronized
    fun peek(): Arm? {
        val mode = preferences.getString(KEY_MODE, null)
            ?.let { raw -> runCatching { Mode.valueOf(raw) }.getOrNull() }
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (mode == null || expiresAt <= System.currentTimeMillis()) {
            clearArm()
            return null
        }
        return Arm(mode, expiresAt)
    }

    /** 读取并清除授权；只有真正执行动作的路径才会调用。 */
    @Synchronized
    fun consume(): Arm? {
        val current = peek() ?: return null
        clearArm()
        return current
    }

    @Synchronized
    fun cancel() {
        clearArm()
    }

    fun lastResult(): String? = preferences.getString(KEY_LAST_RESULT, null)

    fun lastResultAtMillis(): Long = preferences.getLong(KEY_LAST_RESULT_AT, 0L)

    fun recordResult(message: String) {
        preferences.edit()
            .putString(KEY_LAST_RESULT, message)
            .putLong(KEY_LAST_RESULT_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearArm() {
        preferences.edit()
            .remove(KEY_MODE)
            .remove(KEY_EXPIRES_AT)
            .commit()
    }

    companion object {
        const val ARM_TTL_MS = 60_000L

        private const val PREFS_NAME = "node_demo_settings"
        private const val KEY_MODE = "demo_arm_mode"
        private const val KEY_EXPIRES_AT = "demo_arm_expires_at"
        private const val KEY_LAST_RESULT = "demo_last_result"
        private const val KEY_LAST_RESULT_AT = "demo_last_result_at"
    }
}
