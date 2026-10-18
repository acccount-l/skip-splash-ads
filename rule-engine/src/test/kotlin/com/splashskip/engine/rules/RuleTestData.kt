package com.splashskip.engine.rules

object RuleTestData {
    const val DEMO = "com.example.demo"
    const val OTHER = "com.example.other"

    fun rule(
        id: String,
        action: String = "CLICK",
        tapByGesture: Boolean = false,
        dryRun: Boolean = true,
        enabled: Boolean = true,
        bounds: String? = null,
        textRegex: String = "^跳过$",
    ): String {
        val boundsField = if (bounds != null) ""","allowedBounds": $bounds""" else ""
        return """
            {
              "id": "$id",
              "enabled": $enabled,
              "activityIds": ["com.example.Main"],
              "match": {"textRegex": "$textRegex", "clickable": true$boundsField},
              "action": "$action",
              "tapByGesture": $tapByGesture,
              "constraints": {"withinMsAfterLaunch": 8000, "cooldownMs": 100, "maxAttempts": 2, "dryRun": $dryRun}
            }
        """.trimIndent()
    }

    fun json(packageName: String, vararg rules: String): String =
        """{"schemaVersion":1,"packageName":"$packageName","rules":[${rules.joinToString(",")}]}"""

    fun source(
        packageName: String,
        label: String = "$packageName.json",
        vararg rules: String,
    ): SourceModule = SourceModule(
        label = label,
        text = json(packageName, *rules),
        expectedPackage = packageName,
    )
}
