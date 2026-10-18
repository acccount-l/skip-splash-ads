package com.splashskip.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.splashskip.app.data.AssetRuleRepository
import com.splashskip.app.data.SettingsStore
import com.splashskip.app.data.SplashLog
import com.splashskip.engine.engine.Bounds
import com.splashskip.engine.engine.Candidate
import com.splashskip.engine.engine.ProbeMatcher
import com.splashskip.engine.engine.RuleMatcher
import com.splashskip.engine.engine.SafetyGate
import com.splashskip.engine.model.Action
import com.splashskip.engine.model.Rule
import com.splashskip.engine.model.RuleSet
import com.splashskip.engine.model.UiNode
import com.splashskip.engine.model.UiTree
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 只处理白名单包、只在进入目标 App 后的有限时间窗口内扫描。
 * 任何一步失败都选择“不点击”。
 */
class SplashAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "splash-scan")
    }
    private val matcher = RuleMatcher()
    private val scanInFlight = AtomicBoolean(false)

    private lateinit var settings: SettingsStore
    private var loadedRuleSets: List<RuleSet> = emptyList()
    private var session: Session? = null
    private var foregroundPackage: String? = null
    private var foregroundActivity: String? = null
    private var debounceRunnable: Runnable? = null
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastEndedSessionPackage: String? = null
    private var lastEndedSessionAtMs: Long = 0L

    private var probeSession: ProbeSession? = null
    private var probeDebounceRunnable: Runnable? = null
    private var homePackage: String? = null
    private val inputMethodPackages = mutableSetOf<String>()
    private val probeReportedKeys = LinkedHashSet<String>()

    @Volatile
    private var destroyed = false

    private data class Session(
        val ruleSet: RuleSet,
        val startedAtMs: Long,
        val maxWindowMs: Long,
        /** 会话开始时的 Activity；后台 Fragment/弹窗事件不应让它失效。 */
        val startActivity: String?,
        var attempts: Int = 0,
        var lastAttemptAtMs: Long = 0L,
        var clicked: Boolean = false,
        val reportedKeys: MutableSet<String> = mutableSetOf(),
        val reportedActivityMismatches: MutableSet<String> = mutableSetOf(),
        var scanDiagnosticLogged: Boolean = false,
        var nonTrivialScanLogged: Boolean = false,
    )

    private data class ScanHit(
        val rule: Rule,
        val candidate: Candidate,
    )

    /** 全探测模式的一次只读会话；字段都在主线程读写。 */
    private data class ProbeSession(
        val packageName: String,
        val startedAtMs: Long,
        var activity: String?,
        var scans: Int = 0,
        var candidatesLogged: Int = 0,
        var firstScanLogged: Boolean = false,
        var readFailureLogged: Boolean = false,
        var capLogged: Boolean = false,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsStore(this)

        // 重连时清掉旧会话与旧回调，避免重复探测/重复扫描。
        cancelProbeCallbacks()
        probeSession = null
        session = null
        foregroundPackage = null
        foregroundActivity = null
        probeReportedKeys.clear()

        homePackage = homePackageName()
        inputMethodPackages.clear()
        inputMethodPackages += enabledInputMethodPackages()

        prefListener?.let { settings.unregisterRuleChangeListener(it) }
        val loaded = AssetRuleRepository(this).loadAll()
        loadedRuleSets = loaded.ruleSets
        applyEventFilter()

        loaded.errors.forEach { SplashLog.error("规则错误：$it") }
        if (loaded.ruleSets.isEmpty()) {
            SplashLog.error("没有可用规则；规则模式不会处理 App（全探测模式不受影响）")
        } else {
            val count = loaded.ruleSets.sumOf { it.rules.size }
            SplashLog.info("服务已连接：${loaded.ruleSets.size} 个 App，$count 条规则")
        }
        if (settings.probeAll) {
            SplashLog.info("全探测模式已开启：只记录，不点击")
        }

        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                key == SettingsStore.KEY_PROBE_ALL -> {
                    val enabled = settings.probeAll
                    endSession("probe_mode_changed")
                    endProbeSession("probe_mode_changed")
                    foregroundPackage = null
                    foregroundActivity = null
                    lastEndedSessionPackage = null
                    lastEndedSessionAtMs = 0L
                    applyEventFilter()
                    SplashLog.info(
                        if (enabled) "全探测模式已开启：只记录，不点击"
                        else "全探测模式已关闭：恢复规则模式",
                    )
                }

                key != null && key.startsWith(SettingsStore.KEY_RULE_PREFIX) -> {
                    if (!settings.probeAll) applyEventFilter()
                }
            }
        }.also { settings.registerRuleChangeListener(it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentEvent = event ?: return
        if (!this::settings.isInitialized || destroyed) return

        val pkg = currentEvent.packageName?.toString() ?: return
        if (settings.probeAll) {
            when (currentEvent.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleProbeWindowState(pkg, currentEvent.className?.toString())
                }

                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleProbeContentChanged(pkg)
                }
            }
            return
        }

        when (currentEvent.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowState(pkg, currentEvent.className?.toString())
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChanged(pkg)
            }
        }
    }

    override fun onInterrupt() {
        endProbeSession("interrupted")
        endSession("interrupted")
    }

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        if (this::settings.isInitialized) {
            prefListener?.let { settings.unregisterRuleChangeListener(it) }
        }
        prefListener = null
        scanExecutor.shutdownNow()
        session = null
        probeSession = null
        probeDebounceRunnable = null
        super.onDestroy()
    }

    private fun applyEventFilter() {
        if (settings.probeAll) {
            serviceInfo = serviceInfo.apply {
                // AccessibilityServiceInfo：null 表示接收所有包的事件。
                packageNames = null
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100L
                flags = flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            SplashLog.info("事件过滤：全探测模式（所有应用，只记录不点击）")
            return
        }

        val enabledPackages = loadedRuleSets
            .filter { ruleSet -> ruleSet.rules.any { settings.isRuleEnabled(it.id) } }
            .map { it.packageName }
            .distinct()

        val packages = if (enabledPackages.isEmpty()) {
            arrayOf(DISABLED_PACKAGE_FILTER)
        } else {
            // 额外接收桌面的窗口事件：用户从桌面再次启动同一个 App 时，
            // 先看到桌面事件，foregroundPackage 才会真正切换，下一次启动
            // 就能按“新会话”处理。只读取包名/类名，不读取桌面内容。
            (enabledPackages + listOfNotNull(homePackageName())).distinct().toTypedArray()
        }

        serviceInfo = serviceInfo.apply {
            packageNames = packages
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50L
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        SplashLog.info("事件过滤包：${packages.joinToString()}")
    }

    private fun homePackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = try {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (t: Throwable) {
            null
        } ?: return null

        val name = resolved.activityInfo?.packageName ?: return null
        if (name == "android" || name == packageName) return null
        return name
    }

    /** 已启用输入法的包名；探测时当作系统悬浮层忽略，避免打断对目标 App 的观察。 */
    private fun enabledInputMethodPackages(): Set<String> = try {
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            ?.toSet()
            .orEmpty()
    } catch (t: Throwable) {
        emptySet()
    }

    private fun handleWindowState(pkg: String, activity: String?) {
        val previousPackage = foregroundPackage
        foregroundPackage = pkg
        if (previousPackage != pkg) {
            foregroundActivity = null
        }
        // 忽略 Toast、输入法等系统窗口类名，避免把非 Activity 当白名单。
        if (!activity.isNullOrBlank() && !activity.startsWith("android.")) {
            // 弹窗会以独立窗口上报类名（BMAlertDialog、SplashAdDialog 等）。
            // 若它不是规则显式允许的窗口，且已有同包 Activity，则保留原 Activity：
            // 弹窗关闭后通常不会再发 Activity 事件，否则会话会永久卡在弹窗类名上。
            val allowedByRule = isActivityAllowed(pkg, activity)
            val currentAllowed = foregroundActivity?.let { isActivityAllowed(pkg, it) } == true
            // 会话进行中，如果当前 Activity 已在规则白名单里，不要被后台窗口
            // （如主界面的 AppTabFragment）覆盖，否则后续扫描会找不到规则。
            val keepAllowedActivity = session != null && currentAllowed && !allowedByRule
            if (allowedByRule || foregroundActivity == null ||
                (!isOverlayWindowClass(activity) && !keepAllowedActivity)
            ) {
                foregroundActivity = activity
            }
        }

        val ruleSet = loadedRuleSets.firstOrNull { it.packageName == pkg }
        if (ruleSet == null) {
            if (session != null) endSession("left_target_package")
            return
        }

        if (previousPackage != pkg) {
            startSession(ruleSet, foregroundActivity)
            return
        }

        if (session == null) {
            maybeRestartSamePackageSession(ruleSet)
        } else {
            scheduleScan(SCAN_AFTER_WINDOW_STATE_MS)
        }
    }

    /** 该窗口类名是否被同包任意一条启用规则显式列入 activityIds。 */
    private fun isActivityAllowed(pkg: String, activity: String): Boolean {
        val ruleSet = loadedRuleSets.firstOrNull { it.packageName == pkg } ?: return false
        return ruleSet.rules.any { rule ->
            rule.enabled &&
                settings.isRuleEnabled(rule.id) &&
                activity in rule.activityIds
        }
    }

    /** 常见弹窗窗口类名。仅用于避免覆盖已知 Activity，不参与点击决策。 */
    private fun isOverlayWindowClass(activity: String): Boolean {
        val name = activity.lowercase()
        return "dialog" in name || "popupwindow" in name || name.endsWith("popup")
    }

    /**
     * 事件过滤只包含目标包和桌面；用户切到其它 App 时我们收不到事件，
     * foregroundPackage 会停留在上一个目标包。再次启动同一个目标 App 时，
     * 必须把“同包的新窗口”当成一次新的开屏，否则第一次成功后永远不再检测。
     *
     * 只在该 Activity 确实有启用规则时重启，并加冷却，避免 App 内部
     * Activity 切换被误判成新的开屏。
     */
    private fun maybeRestartSamePackageSession(ruleSet: RuleSet) {
        val activity = foregroundActivity ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastEndedSessionPackage == ruleSet.packageName &&
            now - lastEndedSessionAtMs < SAME_PACKAGE_RESTART_COOLDOWN_MS
        ) {
            return
        }

        val activityAllowed = ruleSet.rules.any { rule ->
            rule.enabled &&
                settings.isRuleEnabled(rule.id) &&
                activity in rule.activityIds
        }
        if (!activityAllowed) return

        startSession(ruleSet, activity, samePackageRestart = true)
    }

    private fun handleContentChanged(pkg: String) {
        val current = session ?: return
        if (current.ruleSet.packageName != pkg) return

        val now = SystemClock.elapsedRealtime()
        if (now - current.startedAtMs > current.maxWindowMs) {
            endSession("timeout")
            return
        }

        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            debounceRunnable = null
            performScan()
        }
        debounceRunnable = runnable
        mainHandler.postDelayed(runnable, CONTENT_DEBOUNCE_MS)
    }

    private fun scheduleScan(delayMs: Long) {
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            debounceRunnable = null
            performScan()
        }
        debounceRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun startSession(
        ruleSet: RuleSet,
        activity: String?,
        samePackageRestart: Boolean = false,
    ) {
        if (settings.probeAll) return
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = null

        val enabledRules = ruleSet.rules.filter { settings.isRuleEnabled(it.id) }
        val matchingRules = enabledRules.count { rule ->
            activity != null && activity in rule.activityIds
        }
        val windowMs = enabledRules.maxOfOrNull { it.constraints.withinMsAfterLaunch } ?: 0L
        if (windowMs <= 0L) {
            session = null
            return
        }

        val now = SystemClock.elapsedRealtime()
        val newSession = Session(
            ruleSet = ruleSet,
            startedAtMs = now,
            maxWindowMs = windowMs,
            startActivity = activity,
        )
        session = newSession
        val prefix = if (samePackageRestart) "开始检测（同包重启）" else "开始检测"
        SplashLog.info(
            "$prefix：${ruleSet.packageName} / ${activity ?: "unknown"}，窗口 ${windowMs}ms，" +
                "当前 Activity 匹配 ${matchingRules}/${enabledRules.size} 条规则",
        )

        RECHECK_DELAYS_MS.forEach { postScanAt(newSession, it) }
        mainHandler.postDelayed({
            if (session === newSession) endSession("timeout")
        }, windowMs + TIMEOUT_GRACE_MS)
    }

    private fun postScanAt(target: Session, delayMs: Long) {
        mainHandler.postDelayed({
            if (session === target) performScan()
        }, delayMs)
    }

    private fun endSession(reason: String) {
        val current = session ?: return
        session = null
        lastEndedSessionPackage = current.ruleSet.packageName
        lastEndedSessionAtMs = SystemClock.elapsedRealtime()
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = null
        SplashLog.info("检测结束：$reason（${current.ruleSet.packageName}）")
    }

    // ---------------------------------------------------------------------
    // 全探测模式：订阅所有应用，只记录“跳过/关闭广告”类候选，绝不点击。
    // 该路径不使用 RuleMatcher/SafetyGate，也不读写命中/点击统计。
    // ---------------------------------------------------------------------

    private fun handleProbeWindowState(pkg: String, activity: String?) {
        if (!settings.probeAll) return

        if (pkg == homePackage) {
            endProbeSession("home")
            return
        }
        // 自身、SystemUI、权限控制器、GMS、安装器、输入法等悬浮层：
        // 不结束会话，也不开始新会话，避免打断对目标 App 的观察。
        if (isProbeIgnoredPackage(pkg)) return

        val current = probeSession
        if (current == null) {
            startProbeSession(pkg, activity)
            return
        }
        if (current.packageName != pkg) {
            endProbeSession("package_switch")
            startProbeSession(pkg, activity)
            return
        }

        if (!activity.isNullOrBlank() && !activity.startsWith("android.")) {
            current.activity = activity
        }
        scheduleProbeScan(PROBE_SCAN_AFTER_WINDOW_STATE_MS)
    }

    private fun isProbeIgnoredPackage(pkg: String): Boolean =
        pkg == packageName ||
            pkg in PROBE_IGNORED_PACKAGES ||
            pkg in inputMethodPackages

    private fun handleProbeContentChanged(pkg: String) {
        if (!settings.probeAll) return
        val current = probeSession ?: return
        if (current.packageName != pkg) return

        val now = SystemClock.elapsedRealtime()
        if (now - current.startedAtMs > PROBE_WINDOW_MS) {
            endProbeSession("timeout")
            return
        }
        scheduleProbeScan(PROBE_CONTENT_DEBOUNCE_MS)
    }

    private fun startProbeSession(pkg: String, activity: String?) {
        if (!settings.probeAll) return
        cancelProbeCallbacks()

        val newSession = ProbeSession(
            packageName = pkg,
            startedAtMs = SystemClock.elapsedRealtime(),
            activity = activity,
        )
        probeSession = newSession
        SplashLog.info(
            "[探测] 开始：$pkg / ${activity ?: "unknown"}，" +
                "窗口 ${PROBE_WINDOW_MS}ms（只记录，不点击）",
        )

        PROBE_RECHECK_MS.forEach { delay -> scheduleProbeScanAt(newSession, delay) }
        mainHandler.postDelayed({
            if (probeSession === newSession) endProbeSession("timeout")
        }, PROBE_WINDOW_MS + PROBE_TIMEOUT_GRACE_MS)
    }

    private fun scheduleProbeScanAt(target: ProbeSession, delayMs: Long) {
        mainHandler.postDelayed({
            if (probeSession === target && settings.probeAll) performProbeScan()
        }, delayMs)
    }

    private fun scheduleProbeScan(delayMs: Long) {
        probeDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            probeDebounceRunnable = null
            performProbeScan()
        }
        probeDebounceRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun endProbeSession(reason: String) {
        val current = probeSession ?: return
        probeSession = null
        cancelProbeCallbacks()
        SplashLog.info(
            "[探测] 结束：$reason（${current.packageName}，扫描 ${current.scans} 次，" +
                "新候选 ${current.candidatesLogged} 个）",
        )
    }

    private fun cancelProbeCallbacks() {
        probeDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
        probeDebounceRunnable = null
    }

    private fun performProbeScan() {
        val current = probeSession ?: return
        if (!settings.probeAll) {
            endProbeSession("probe_off")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - current.startedAtMs > PROBE_WINDOW_MS) {
            endProbeSession("timeout")
            return
        }
        if (current.scans >= PROBE_MAX_SCANS) {
            endProbeSession("scan_cap")
            return
        }
        current.scans++

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        }
        if (root == null) {
            if (!current.readFailureLogged) {
                current.readFailureLogged = true
                SplashLog.warn("[探测] 无法读取界面：${current.packageName}（可能为安全窗口）")
            }
            return
        }
        if (root.packageName?.toString() != current.packageName) return

        val tree = try {
            NodeMapper.map(root, current.packageName)?.let(UiTree::fromRoot)
        } catch (t: Throwable) {
            SplashLog.warn("[探测] 读取界面失败：${t.javaClass.simpleName}")
            null
        } ?: return

        val (width, height) = screenSize()
        if (width <= 0 || height <= 0) return

        val candidates = ProbeMatcher.findCandidates(
            tree = tree,
            screenWidth = width,
            screenHeight = height,
            max = PROBE_MAX_CANDIDATES_PER_SCAN,
        )

        if (!current.firstScanLogged) {
            current.firstScanLogged = true
            SplashLog.info(
                "[探测] 扫描：${current.packageName} / ${current.activity ?: "unknown"}，" +
                    "节点 ${tree.size}，候选 ${candidates.size}",
            )
        }

        for (candidate in candidates) {
            if (current.candidatesLogged >= PROBE_MAX_CANDIDATES_PER_SESSION) {
                if (!current.capLogged) {
                    current.capLogged = true
                    SplashLog.info("[探测] 候选已达本次上限（${PROBE_MAX_CANDIDATES_PER_SESSION} 个）")
                }
                break
            }
            val key = probeKey(current, candidate)
            if (!rememberProbeKey(key)) continue
            current.candidatesLogged++
            logProbeCandidate(current, tree, candidate)
        }
    }

    private fun probeKey(
        session: ProbeSession,
        candidate: ProbeMatcher.ProbeCandidate,
    ): String = buildString {
        append(session.packageName).append('|')
        append(session.activity).append('|')
        append(candidate.kind.name).append('|')
        append(candidate.source.name).append('|')
        append(PROBE_DIGITS_REGEX.replace(candidate.text.lowercase(), "#")).append('|')
        append(candidate.className).append('|')
        append(candidate.bounds)
    }

    /** 跨会话去重，最多记住 200 个键；数字归一化避免倒计时重复刷屏。 */
    private fun rememberProbeKey(key: String): Boolean {
        if (!probeReportedKeys.add(key)) return false
        while (probeReportedKeys.size > PROBE_MAX_REMEMBERED_KEYS) {
            val oldest = probeReportedKeys.firstOrNull() ?: break
            probeReportedKeys.remove(oldest)
        }
        return true
    }

    private fun logProbeCandidate(
        session: ProbeSession,
        tree: UiTree,
        candidate: ProbeMatcher.ProbeCandidate,
    ) {
        val ancestor = candidate.clickableAncestorIndex?.let { tree.node(it) }
        val ancestorText = if (ancestor == null) {
            "none"
        } else {
            "${ancestor.className?.substringAfterLast('.') ?: "unknown"} ${ancestor.bounds}"
        }
        SplashLog.info(
            "[探测] 候选：${session.packageName} / ${session.activity ?: "unknown"} " +
                "kind=${candidate.kind.name.lowercase()} " +
                "src=${candidate.source.name.lowercase()} " +
                "text=\"${sanitizeForLog(candidate.text)}\" " +
                "id=${candidate.viewId?.take(120) ?: "none"} " +
                "class=${candidate.className?.substringAfterLast('.') ?: "unknown"} " +
                "clickable=${candidate.clickable} bounds=${candidate.bounds} " +
                "center=${candidate.centerXPercent}%,${candidate.centerYPercent}% " +
                "ancestor=$ancestorText",
        )
    }

    private fun performScan() {
        if (settings.probeAll) return
        val current = session ?: return
        if (current.clicked) return
        if (!scanInFlight.compareAndSet(false, true)) return

        val now = SystemClock.elapsedRealtime()
        if (now - current.startedAtMs > current.maxWindowMs) {
            scanInFlight.set(false)
            endSession("timeout")
            return
        }

        val pkg = foregroundPackage
        if (pkg != current.ruleSet.packageName) {
            scanInFlight.set(false)
            endSession("left_target_package")
            return
        }

        // 同时保留当前前台 Activity 与会话开始时的 Activity：
        // 开屏 Activity 上可能混入后台主界面窗口事件（如 AppTabFragment），
        // 只要其中任意一个在规则白名单里就继续扫描。
        val activityCandidates = buildList {
            foregroundActivity?.takeIf { it.isNotBlank() }?.let(::add)
            current.startActivity?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()
        if (activityCandidates.isEmpty()) {
            scanInFlight.set(false)
            return
        }

        val activityRules = current.ruleSet.rules.filter { rule ->
            rule.enabled &&
                settings.isRuleEnabled(rule.id) &&
                activityCandidates.any { it in rule.activityIds }
        }
        if (activityRules.isEmpty()) {
            // 用于定位“App 有开屏广告、但当前 Activity 不在规则白名单”导致的无日志无命中。
            val activityLabel = activityCandidates.joinToString(" / ")
            if (current.reportedActivityMismatches.add(activityLabel)) {
                val enabledCount = current.ruleSet.rules.count { settings.isRuleEnabled(it.id) }
                SplashLog.info(
                    "Activity 不在规则白名单：${current.ruleSet.packageName} / $activityLabel" +
                        "（已启用 $enabledCount 条规则）",
                )
            }
            scanInFlight.set(false)
            return
        }

        val enabledRules = activityRules.filter { rule ->
            now - current.startedAtMs <= rule.constraints.withinMsAfterLaunch
        }
        if (enabledRules.isEmpty()) {
            scanInFlight.set(false)
            return
        }

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        }
        if (root == null) {
            scanInFlight.set(false)
            return
        }

        val rootPackage = root.packageName?.toString()
        if (rootPackage != null && rootPackage != pkg) {
            scanInFlight.set(false)
            return
        }

        val tree = try {
            NodeMapper.map(root, pkg)?.let(UiTree::fromRoot)
        } catch (t: Throwable) {
            SplashLog.warn("读取界面失败：${t.javaClass.simpleName}")
            null
        }
        if (tree == null) {
            scanInFlight.set(false)
            return
        }

        // 首次扫描可能早于广告内容加载（只有 1 个根节点）；树第一次变丰富时
        // 再记一条，便于确认后续扫描到底看到了什么。
        if (!current.scanDiagnosticLogged || (!current.nonTrivialScanLogged && tree.size > 1)) {
            current.scanDiagnosticLogged = true
            if (tree.size > 1) current.nonTrivialScanLogged = true
            logScanDiagnostic(tree)
        }

        val (width, height) = screenSize()
        scanExecutor.execute {
            val hits = ArrayList<ScanHit>()
            try {
                for (rule in enabledRules) {
                    if (current.attempts >= rule.constraints.maxAttempts) continue
                    if (current.lastAttemptAtMs > 0L &&
                        now - current.lastAttemptAtMs < rule.constraints.cooldownMs
                    ) {
                        continue
                    }
                    val candidate = matcher.findBest(tree, rule, pkg, width, height) ?: continue
                    hits += ScanHit(rule, candidate)
                }
            } catch (t: Throwable) {
                SplashLog.warn("匹配异常：${t.javaClass.simpleName}")
            }

            mainHandler.post {
                scanInFlight.set(false)
                if (destroyed || session !== current) return@post
                handleHits(current, hits)
            }
        }
    }

    private fun handleHits(current: Session, hits: List<ScanHit>) {
        if (settings.probeAll) return
        if (hits.isEmpty()) return

        for (hit in hits) {
            val rule = hit.rule
            val candidate = hit.candidate
            val node = candidate.node
            val key = buildString {
                append(rule.id).append('|')
                append(node.viewId).append('|')
                append(node.className).append('|')
                append(node.bounds)
            }
            if (!current.reportedKeys.add(key)) continue

            val effectiveDryRun = settings.dryRun || rule.constraints.dryRun
            settings.recordHit()
            settings.recordLastHit(rule.id)

            val text = sanitizeForLog(node.text ?: node.contentDescription)
            val viewIdShort = node.viewId?.substringAfterLast('/') ?: "none"
            SplashLog.info(
                "命中 rule=${rule.id} id=$viewIdShort " +
                    "activity=${foregroundActivity ?: "unknown"} " +
                    "startActivity=${current.startActivity ?: "unknown"} " +
                    "class=${node.className} text=\"$text\" bounds=${node.bounds} " +
                    "dryRun=$effectiveDryRun tapByGesture=${rule.tapByGesture}",
            )

            if (effectiveDryRun) {
                SplashLog.info("dry-run：未点击（${rule.id}）")
                continue
            }

            val now = SystemClock.elapsedRealtime()
            if (current.attempts >= rule.constraints.maxAttempts) continue
            if (current.lastAttemptAtMs > 0L &&
                now - current.lastAttemptAtMs < rule.constraints.cooldownMs
            ) {
                continue
            }
            current.attempts++
            current.lastAttemptAtMs = now

            val success = if (rule.action == Action.BACK) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                performClick(candidate, rule)
            }

            if (success) {
                current.clicked = true
                settings.recordClick()
                SplashLog.info("执行成功：${rule.id} / ${rule.action}")
                endSession("success")
                return
            } else {
                SplashLog.warn("执行失败：${rule.id} / ${rule.action}")
                if (current.attempts >= rule.constraints.maxAttempts) {
                    endSession("max_attempts")
                    return
                }
            }
        }
    }

    /**
     * 每个会话只记录一次界面概况：节点数 + 严格符合“跳过”语义的候选。
     * 只记录短文本/描述和 bounds，不记录其它页面文字。
     */
    private fun logScanDiagnostic(tree: UiTree) {
        val highlights = ArrayList<String>()
        var skipLikeCount = 0
        for (index in 0 until tree.size) {
            val node = tree.node(index)
            val text = node.text?.trim().orEmpty()
            val desc = node.contentDescription?.trim().orEmpty()
            val isText = SKIP_HINT_REGEX.matches(text)
            val isDesc = !isText && SKIP_HINT_REGEX.matches(desc)
            if (!isText && !isDesc) continue

            skipLikeCount++
            if (highlights.size < MAX_DIAGNOSTIC_CANDIDATES) {
                val label = if (isText) "text" else "desc"
                val raw = if (isText) text else desc
                highlights += "$label=\"${sanitizeForLog(raw)}\" " +
                    "class=${node.className?.substringAfterLast('.')} " +
                    "clickable=${node.clickable} bounds=${node.bounds}"
            }
        }

        val capped = if (tree.size >= NodeMapper.MAX_NODES) "（已达节点上限）" else ""
        val root = if (tree.size > 0) tree.node(0) else null
        val rootLabel = if (root == null) {
            "root=none"
        } else {
            val rootClass = root.className?.substringAfterLast('.') ?: "?"
            val rootId = root.viewId?.substringAfterLast('/') ?: "none"
            "root=$rootClass id=$rootId bounds=${root.bounds}"
        }
        val detail = if (highlights.isEmpty()) "" else "；候选 " + highlights.joinToString(" | ")
        SplashLog.info(
            "界面扫描：节点 ${tree.size}$capped，跳过类文本 $skipLikeCount 个；$rootLabel$detail",
        )
    }

    private fun performClick(candidate: Candidate, rule: Rule): Boolean {
        if (settings.probeAll) return false
        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        }
        if (root == null) {
            SplashLog.warn("点击失败：无法读取当前界面")
            return false
        }

        val pkg = foregroundPackage
        if (pkg == null) {
            SplashLog.warn("点击失败：前台包未知")
            return false
        }
        val rootPackage = root.packageName?.toString()
        if (rootPackage != pkg) {
            SplashLog.warn("点击失败：界面包不匹配（root=${rootPackage ?: "null"}）")
            return false
        }

        val node = resolveClickNode(root, candidate, rule, pkg) ?: return false
        if (!node.isEnabled || !node.isVisibleToUser) {
            SplashLog.warn(
                "点击失败：节点不可用（enabled=${node.isEnabled} visible=${node.isVisibleToUser}）",
            )
            return false
        }

        if (rule.tapByGesture) {
            return tapNodeCenter(node, rule)
        }
        if (!node.isClickable) {
            SplashLog.warn("点击失败：节点不可点击")
            return false
        }

        val clicked = try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (t: Throwable) {
            false
        }
        if (clicked) return true

        // 部分广告控件 clickable=true 但 ACTION_CLICK 没有返回值，
        // 对仍是“跳过/关闭”语义的节点补一次节点中心手势。
        if (looksLikeSkipControl(node)) {
            SplashLog.info("ACTION_CLICK 未生效，改用节点中心手势")
            return tapNodeCenter(node, rule)
        }
        SplashLog.warn("点击失败：ACTION_CLICK 返回 false")
        return false
    }

    /**
     * 点击前重新定位节点：先按快照路径校验；若倒计时文字或广告树结构已变化，
     * 在当前界面上用同一规则重新匹配一次，避免一次失败就结束会话。
     */
    private fun resolveClickNode(
        root: AccessibilityNodeInfo,
        candidate: Candidate,
        rule: Rule,
        pkg: String,
    ): AccessibilityNodeInfo? {
        val direct = resolveByPath(root, candidate.clickNode.path)
        if (direct != null && verifySnapshot(direct, candidate.clickNode, rule)) {
            return direct
        }

        val tree = try {
            NodeMapper.map(root, pkg)?.let(UiTree::fromRoot)
        } catch (t: Throwable) {
            null
        }
        if (tree == null) {
            SplashLog.warn("点击失败：无法重新读取界面")
            return null
        }
        val (width, height) = screenSize()
        val fresh = matcher.findBest(tree, rule, pkg, width, height)
        if (fresh == null) {
            SplashLog.warn("点击失败：当前界面已无匹配控件")
            return null
        }
        val node = resolveByPath(root, fresh.clickNode.path)
        if (node == null || !verifySnapshot(node, fresh.clickNode, rule)) {
            SplashLog.warn("点击失败：节点快照校验未通过")
            return null
        }
        return node
    }

    private fun looksLikeSkipControl(node: AccessibilityNodeInfo): Boolean {
        val text = buildString {
            node.text?.toString()?.let(::append)
            append(' ')
            node.contentDescription?.toString()?.let(::append)
        }
        if (SKIP_CONTROL_TEXT_REGEX.containsMatchIn(text)) return true
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        return SKIP_CONTROL_ID_REGEX.containsMatchIn(viewId)
    }

    /**
     * 只在已通过安全门的文字节点中心发一次单点手势。
     * 点击前重新读取真实 bounds，并再次校验允许区域与面积，防止快照漂移。
     */
    private fun tapNodeCenter(node: AccessibilityNodeInfo, rule: Rule): Boolean {
        if (settings.probeAll) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false

        val (screenWidth, screenHeight) = screenSize()
        if (screenWidth <= 0 || screenHeight <= 0) return false

        val allowed = rule.match.allowedBounds ?: SafetyGate.DEFAULT_ALLOWED_BOUNDS
        if (!Bounds.contains(allowed, rect.centerX(), rect.centerY(), screenWidth, screenHeight)) {
            return false
        }

        val screenArea = screenWidth.toLong() * screenHeight.toLong()
        val area = rect.width().toLong() * rect.height().toLong()
        if (area > (screenArea * SafetyGate.MAX_CLICK_AREA_RATIO).toLong()) return false

        val path = Path().apply {
            moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, GESTURE_TAP_DURATION_MS))
            .build()

        return try {
            dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            false
        }
    }

    private fun resolveByPath(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo = root
        for (index in path) {
            val child = try {
                node.getChild(index)
            } catch (t: Throwable) {
                null
            } ?: return null
            node = child
        }
        return node
    }

    /**
     * 快照与当前真实节点必须一致才允许点击。
     * 纯 viewId 规则不依赖文字，倒计时文字变化不算快照失效；
     * 依赖文字/描述的规则仍要求文字一致，避免点到另一个控件。
     */
    private fun verifySnapshot(
        node: AccessibilityNodeInfo,
        snapshot: UiNode,
        rule: Rule,
    ): Boolean {
        val bounds = snapshot.bounds ?: return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.left != bounds.left ||
            rect.top != bounds.top ||
            rect.right != bounds.right ||
            rect.bottom != bounds.bottom ||
            node.viewIdResourceName != snapshot.viewId ||
            node.className?.toString() != snapshot.className
        ) {
            return false
        }

        val ruleUsesText = rule.match.textRegex != null || rule.match.texts.isNotEmpty()
        val ruleUsesDesc = rule.match.contentDescriptionRegex != null
        if (!ruleUsesText && !ruleUsesDesc) return true

        return node.text?.toString() == snapshot.text &&
            node.contentDescription?.toString() == snapshot.contentDescription
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = getSystemService(WindowManager::class.java)
            val bounds = windowManager?.currentWindowMetrics?.bounds
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /** 只允许“跳过/关闭”语义的短文本进入日志。 */
    private fun sanitizeForLog(raw: String?): String {
        val value = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (value.isEmpty()) return ""
        val allowed = value.contains("跳过") ||
            value.contains("关闭") ||
            value.contains("skip", ignoreCase = true) ||
            value.contains("close", ignoreCase = true)
        return if (allowed) value.take(20) else "<已隐藏 ${value.length} 字>"
    }

    private companion object {
        const val DISABLED_PACKAGE_FILTER = "com.splashskip.app.none"
        const val CONTENT_DEBOUNCE_MS = 50L
        const val SCAN_AFTER_WINDOW_STATE_MS = 0L
        // 开屏广告通常在 1–3 秒内完成加载；这段时间每 100ms 复查一次，
        // 之后逐步降频。scanInFlight 会丢弃重叠扫描，不会堆积任务。
        val RECHECK_DELAYS_MS = longArrayOf(
            0L, 50L, 100L, 150L, 200L, 300L, 400L, 500L, 600L, 700L, 800L,
            900L, 1_000L, 1_100L, 1_200L, 1_300L, 1_400L,
            1_500L, 1_600L, 1_700L, 1_800L, 1_900L, 2_000L,
            2_200L, 2_400L, 2_600L, 2_800L, 3_000L,
            3_250L, 3_500L, 4_000L, 5_000L, 6_000L, 8_000L,
        )
        const val TIMEOUT_GRACE_MS = 250L
        const val SAME_PACKAGE_RESTART_COOLDOWN_MS = 1_000L
        const val GESTURE_TAP_DURATION_MS = 60L
        const val MAX_DIAGNOSTIC_CANDIDATES = 4

        const val PROBE_WINDOW_MS = 10_000L
        const val PROBE_CONTENT_DEBOUNCE_MS = 150L
        const val PROBE_SCAN_AFTER_WINDOW_STATE_MS = 50L
        const val PROBE_TIMEOUT_GRACE_MS = 250L
        const val PROBE_MAX_SCANS = 20
        const val PROBE_MAX_CANDIDATES_PER_SCAN = 4
        const val PROBE_MAX_CANDIDATES_PER_SESSION = 10
        const val PROBE_MAX_REMEMBERED_KEYS = 200
        val PROBE_RECHECK_MS = longArrayOf(50L, 200L, 800L, 2_000L, 5_000L, 8_000L)
        val PROBE_DIGITS_REGEX = Regex("\\d+")

        /** 探测时当作系统悬浮层/敏感界面忽略的包；不结束当前探测会话。 */
        val PROBE_IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.vivo.systemuiplugin",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.vivo.permissionmanager",
            "com.google.android.gms",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )

        /** 与规则里的探测正则保持一致；只用于日志统计，不参与点击决策。 */
        val SKIP_HINT_REGEX = Regex(
            "^(点击)?\\s*(\\d{1,2}\\s*[sS秒]?\\s*)?(跳过|skip)(\\s*广告)?\\s*[|·]?\\s*\\d*\\s*[sS秒]?$",
            RegexOption.IGNORE_CASE,
        )

        /** ACTION_CLICK 不生效时，只有仍具备跳过/关闭语义的节点才允许改用中心手势。 */
        val SKIP_CONTROL_TEXT_REGEX = Regex(
            "跳过|skip|关闭广告|close\\s*ad",
            RegexOption.IGNORE_CASE,
        )
        val SKIP_CONTROL_ID_REGEX = Regex(
            "skip|close|dismiss",
            RegexOption.IGNORE_CASE,
        )
    }
}
