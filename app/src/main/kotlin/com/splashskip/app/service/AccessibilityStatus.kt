package com.splashskip.app.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val component = ComponentName(context, SplashAccessibilityService::class.java)
        val fullName = component.flattenToString()
        val shortName = component.flattenToShortString()

        val enabledRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (enabledRaw.split(':').any { it.equals(fullName, true) || it.equals(shortName, true) }) {
            return true
        }

        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info.id?.startsWith(context.packageName + "/") == true }
    }
}
