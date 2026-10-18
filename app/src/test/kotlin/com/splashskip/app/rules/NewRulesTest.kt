package com.splashskip.app.rules

import com.splashskip.engine.engine.RuleMatcher
import com.splashskip.engine.engine.RuleParser
import com.splashskip.engine.model.RectI
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.UiNode
import com.splashskip.engine.model.UiTree
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NewRulesTest {
    private val matcher = RuleMatcher()
    private val screenW = 1260
    private val screenH = 2800

    private fun loadRule(fileName: String, ruleId: String): Rule {
        val result = RuleParser.parse(
            File("src/main/assets/rules/$fileName").readText(Charsets.UTF_8),
        )
        assertTrue("${result.exceptionOrNull()?.message}", result.isSuccess)
        return result.getOrThrow().rules.single { it.id == ruleId }
    }

    private fun tree(packageName: String, vararg children: UiNode): UiTree = UiTree.fromRoot(
        UiNode(
            className = "android.widget.FrameLayout",
            bounds = RectI(0, 0, screenW, screenH),
            packageName = packageName,
            children = children.toList(),
        ),
    )

    @Test
    fun `china mobile video time skip id matches in top right`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_id_probe")
        assertTrue(rule.tapByGesture)
        val node = UiNode(
            viewId = "com.greenpoint.android.mc10086.activity:id/video_time_skip",
            className = "android.widget.TextView",
            text = "2 跳过",
            clickable = true,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile video time skip id rejects ad call to action`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_id_probe")
        val node = UiNode(
            viewId = "com.greenpoint.android.mc10086.activity:id/video_time_skip",
            className = "android.widget.TextView",
            text = "跳过广告，立即下载",
            clickable = true,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile skip text matches without view id`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_text_probe")
        val node = UiNode(
            className = "android.widget.TextView",
            text = "跳过 5",
            clickable = false,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile text rule matches countdown prefix`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_text_probe")
        val node = UiNode(
            className = "android.widget.TextView",
            text = "2 跳过",
            clickable = false,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile skip id outside top region is rejected`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_id_probe")
        val node = UiNode(
            viewId = "com.greenpoint.android.mc10086.activity:id/video_time_skip",
            className = "android.widget.TextView",
            text = "5",
            clickable = false,
            bounds = RectI(980, 2500, 1220, 2620),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile text rule rejects ad call to action`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_text_probe")
        val node = UiNode(
            className = "android.widget.TextView",
            text = "跳过广告，立即下载",
            clickable = false,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `baidu map kuaishou splash skip id matches in top region`() {
        val rule = loadRule("baidumap.json", "baidumap_splash_sdk_id_probe")
        val node = UiNode(
            viewId = "com.baidu.BaiduMap:id/ksad_splash_skip_right_view",
            className = "com.kwad.components.ad.splashscreen.widget.SkipView",
            text = null,
            clickable = true,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile bare countdown on skip view matches`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_countdown_probe")
        val node = UiNode(
            viewId = "com.greenpoint.android.mc10086.activity:id/video_time_skip",
            className = "android.widget.TextView",
            text = "5",
            clickable = false,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile bare countdown on non skip id is rejected`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_countdown_probe")
        val node = UiNode(
            viewId = "com.greenpoint.android.mc10086.activity:id/some_number",
            className = "android.widget.TextView",
            text = "5",
            clickable = false,
            bounds = RectI(980, 80, 1220, 200),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `china mobile bare countdown outside top right is rejected`() {
        val rule = loadRule("china_mobile.json", "china_mobile_splash_countdown_probe")
        val node = UiNode(
            viewId = "com.greenpoint.android.mc10086.activity:id/video_time_skip",
            className = "android.widget.TextView",
            text = "5",
            clickable = false,
            bounds = RectI(980, 2500, 1220, 2620),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `baidu map observed skip text matches in top right`() {
        val rule = loadRule("baidumap.json", "baidumap_splash_probe")
        val node = UiNode(
            viewId = null,
            className = "android.widget.TextView",
            text = "跳过 02",
            clickable = true,
            bounds = RectI(955, 70, 1207, 175),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `baidu map skip text outside top right is rejected`() {
        val rule = loadRule("baidumap.json", "baidumap_splash_probe")
        val node = UiNode(
            viewId = null,
            className = "android.widget.TextView",
            text = "跳过 02",
            clickable = true,
            bounds = RectI(955, 2500, 1207, 2605),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `baidu map observed sdk skip frame matches in top right`() {
        val rule = loadRule("baidumap.json", "baidumap_splash_sdk_id_probe")
        val node = UiNode(
            viewId = "com.baidu.BaiduMap:id/nad_skip_view",
            className = "android.widget.FrameLayout",
            text = null,
            clickable = true,
            bounds = RectI(922, 0, 1260, 322),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `baidu map sdk skip id outside top region is rejected`() {
        val rule = loadRule("baidumap.json", "baidumap_splash_sdk_id_probe")
        val node = UiNode(
            viewId = "com.baidu.BaiduMap:id/ksad_skip_view_skip",
            className = "android.widget.TextView",
            text = "跳过",
            clickable = true,
            bounds = RectI(980, 2500, 1220, 2620),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `cainiao splash close fullscreen gesture rule matches observed skip`() {
        val rule = loadRule("cainiao.json", "cainiao_splash_id_gesture")
        assertTrue(rule.tapByGesture)
        val node = UiNode(
            viewId = "com.cainiao.wireless:id/homesplash_close_fullscreen",
            className = "android.widget.TextView",
            text = "跳过 5",
            clickable = true,
            bounds = RectI(962, 189, 1197, 288),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `cainiao splash close fullscreen matches countdown prefix`() {
        val rule = loadRule("cainiao.json", "cainiao_splash_id_gesture")
        val node = UiNode(
            viewId = "com.cainiao.wireless:id/homesplash_close_fullscreen",
            className = "android.widget.TextView",
            text = "3s | 跳过",
            clickable = true,
            bounds = RectI(966, 189, 1197, 288),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `cainiao splash close fullscreen rejects ad call to action`() {
        val rule = loadRule("cainiao.json", "cainiao_splash_id_gesture")
        val node = UiNode(
            viewId = "com.cainiao.wireless:id/homesplash_close_fullscreen",
            className = "android.widget.TextView",
            text = "跳过广告，立即下载",
            clickable = true,
            bounds = RectI(962, 189, 1197, 288),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `cainiao splash close id click rule matches without text`() {
        val rule = loadRule("cainiao.json", "cainiao_splash_id_click")
        val node = UiNode(
            viewId = "com.cainiao.wireless:id/homesplash_close_fullscreen",
            className = "android.widget.TextView",
            text = null,
            clickable = true,
            bounds = RectI(962, 189, 1197, 288),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `cainiao splash close fullscreen outside top right is rejected`() {
        val rule = loadRule("cainiao.json", "cainiao_splash_id_gesture")
        val node = UiNode(
            viewId = "com.cainiao.wireless:id/homesplash_close_fullscreen",
            className = "android.widget.TextView",
            text = "跳过 5",
            clickable = true,
            bounds = RectI(962, 2500, 1197, 2600),
            packageName = rule.packageName,
        )

        assertNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }

    @Test
    fun `cainiao splash text fallback matches without view id`() {
        val rule = loadRule("cainiao.json", "cainiao_splash_text")
        val node = UiNode(
            viewId = null,
            className = "android.widget.TextView",
            text = "跳过 3",
            clickable = false,
            bounds = RectI(962, 189, 1197, 288),
            packageName = rule.packageName,
        )

        assertNotNull(
            matcher.findBest(
                tree(rule.packageName, node),
                rule,
                rule.packageName,
                screenW,
                screenH,
            ),
        )
    }
}
