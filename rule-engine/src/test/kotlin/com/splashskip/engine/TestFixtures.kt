package com.splashskip.engine

import com.splashskip.engine.engine.RuleParser
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.RuleSet
import com.splashskip.engine.model.UiNode

object Fixtures {
    const val BILI_PACKAGE = "tv.danmaku.bili"
    const val BILI_SPLASH_ACTIVITY = "tv.danmaku.bili.splash.ad.page.HotSplashActivity"
    const val BILI_MAIN_ACTIVITY = "tv.danmaku.bili.MainActivityV2"
    const val SCREEN_W = 1260
    const val SCREEN_H = 2800

    /** 与 app/src/main/assets/rules/bilibili.json 保持一致的测试副本。 */
    val BILI_JSON: String = """
        {
          "schemaVersion": 1,
          "packageName": "tv.danmaku.bili",
          "rules": [
            {
              "id": "bilibili_splash_skip",
              "activityIds": [
                "tv.danmaku.bili.splash.ad.page.HotSplashActivity",
                "tv.danmaku.bili.MainActivityV2"
              ],
              "match": {
                "textRegex": "^跳过\\s*\\d*$",
                "texts": ["跳过", "Skip"],
                "className": "android.widget.TextView",
                "clickable": true,
                "allowedBounds": [0.70, 0.80, 1.0, 1.0]
              },
              "action": "CLICK",
              "constraints": {
                "withinMsAfterLaunch": 8000,
                "cooldownMs": 2000,
                "maxAttempts": 1,
                "dryRun": true
              }
            }
          ]
        }
    """.trimIndent()

    fun biliRuleSet(): RuleSet = RuleParser.parse(BILI_JSON).getOrThrow()

    fun biliRule(): Rule = biliRuleSet().rules.single()

    fun ruleJson(
        schemaVersion: Int = 1,
        packageName: String = BILI_PACKAGE,
        ruleId: String = "bilibili_splash_skip",
        activityIds: List<String> = listOf(BILI_MAIN_ACTIVITY),
        matchJson: String = """{"textRegex":"^跳过\\s*\\d*$","clickable":true,"allowedBounds":[0.70,0.80,1.0,1.0]}""",
        action: String = "CLICK",
        tapByGesture: Boolean = false,
        constraintsJson: String = """{"withinMsAfterLaunch":8000,"cooldownMs":2000,"maxAttempts":1,"dryRun":true}""",
    ): String {
        val activities = activityIds.joinToString(", ") { "\"$it\"" }
        return """
            {
              "schemaVersion": $schemaVersion,
              "packageName": "$packageName",
              "rules": [
                {
                  "id": "$ruleId",
                  "activityIds": [$activities],
                  "match": $matchJson,
                  "action": "$action",
                  "tapByGesture": $tapByGesture,
                  "constraints": $constraintsJson
                }
              ]
            }
        """.trimIndent()
    }

    fun node(
        text: String? = null,
        contentDescription: String? = null,
        className: String = "android.widget.TextView",
        bounds: RectI? = RectI(923, 2425, 1204, 2565),
        clickable: Boolean = true,
        enabled: Boolean = true,
        visible: Boolean = true,
        packageName: String? = BILI_PACKAGE,
        viewId: String? = null,
        children: List<UiNode> = emptyList(),
    ): UiNode = UiNode(
        viewId = viewId,
        className = className,
        text = text,
        contentDescription = contentDescription,
        clickable = clickable,
        enabled = enabled,
        visibleToUser = visible,
        bounds = bounds,
        packageName = packageName,
        children = children,
    )

    fun screenRoot(vararg children: UiNode): UiNode = UiNode(
        className = "android.widget.FrameLayout",
        bounds = RectI(0, 0, SCREEN_W, SCREEN_H),
        packageName = BILI_PACKAGE,
        clickable = false,
        children = children.toList(),
    )
}
