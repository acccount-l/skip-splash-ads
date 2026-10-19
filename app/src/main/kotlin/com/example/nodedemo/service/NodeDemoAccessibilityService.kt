package com.example.nodedemo.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.nodedemo.core.engine.Bounds
import com.example.nodedemo.core.model.RectI
import com.example.nodedemo.core.model.UiNode
import com.example.nodedemo.core.model.UiTree
import com.example.nodedemo.data.DemoArmStore
import com.example.nodedemo.data.DemoLog

/**
 * 无障碍节点读取与一次点击派发演示。
 *
 * 安全边界：
 * - 运行时的 packageNames 固定为本应用包名；
 * - 事件包名、活动窗口包名、目标节点包名必须三重一致；
 * - 只有用户在应用内显式授权一次（60 秒有效）后才会读取或点击；
 * - 只操作自带演示页的 demo_target_button，不读取或操作其他应用；
 * - 日志不记录节点 text/contentDescription。
 */
class NodeDemoAccessibilityService : AccessibilityService() {

    private lateinit var armStore: DemoArmStore
    private var lastRejection: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        armStore = DemoArmStore(this)
        lastRejection = null

        // 双重保险：XML 配置已限制包名，运行时再次收紧事件过滤。
        serviceInfo = serviceInfo.apply {
            packageNames = arrayOf(packageName)
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100L
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        DemoLog.info("无障碍服务已连接：事件过滤仅限本应用")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (!this::armStore.isInitialized) return

        // 第一重校验：事件必须来自本应用。
        if (current.packageName?.toString() != packageName) return
        when (current.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleEvent()
        }
    }

    override fun onInterrupt() {
        DemoLog.warn("无障碍服务被中断")
    }

    private fun handleEvent() {
        val arm = armStore.peek() ?: return

        // 第二重校验：活动窗口必须属于本应用。
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != packageName) return

        val located = locateTarget(root) ?: return

        // 第三重校验：目标节点必须属于本应用。
        if (located.node.packageName?.toString() != packageName) {
            reject("目标节点包名不匹配")
            return
        }

        when (arm.mode) {
            DemoArmStore.Mode.READ -> consumeAndRead(root)
            DemoArmStore.Mode.CLICK -> consumeAndClick(root, located)
        }
    }

    /** 只读一次：把本应用演示页的节点树映射为纯 Kotlin 快照并记录摘要。 */
    private fun consumeAndRead(root: AccessibilityNodeInfo) {
        if (armStore.consume() == null) return

        val snapshot = NodeMapper.map(root, packageName)
        if (snapshot == null) {
            DemoLog.warn("只读演示：节点树读取失败")
            armStore.recordResult("只读失败：节点树不可读")
            return
        }

        val tree = UiTree.fromRoot(snapshot)
        val target = findTargetInTree(tree)
        DemoLog.info(
            "只读演示完成：节点=${tree.size}, viewId=${target?.viewId ?: "未找到"}, " +
                "class=${target?.className}, bounds=${target?.bounds}",
        )
        armStore.recordResult("只读完成：${tree.size} 个节点")
        lastRejection = null
    }

    /** 点击一次：重新按 path 定位节点，复核状态和 bounds 后再派发。 */
    private fun consumeAndClick(root: AccessibilityNodeInfo, located: Located) {
        val firstCheck = validateTarget(located.node)
        if (firstCheck != null) {
            reject(firstCheck)
            return
        }

        val resolved = resolvePath(root, located.path)
        if (resolved == null) {
            reject("按 path 重新定位失败")
            return
        }

        val secondCheck = validateTarget(resolved)
        if (secondCheck != null) {
            reject(secondCheck)
            return
        }
        if (resolved.className?.toString() != located.className) {
            reject("className 与快照不一致")
            return
        }
        if (!Bounds.near(boundsOf(resolved), located.bounds, minSlackPx = 8, ratio = 0.1f)) {
            reject("bounds 与快照不一致")
            return
        }

        if (armStore.consume() == null) return
        lastRejection = null
        dispatchClick(resolved)
    }

    private fun dispatchClick(node: AccessibilityNodeInfo) {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            DemoLog.info("点击演示：ACTION_CLICK 已派发")
            armStore.recordResult("ACTION_CLICK 已派发")
            return
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) {
            DemoLog.warn("点击演示：目标 bounds 无效，未派发手势")
            armStore.recordResult("点击失败：bounds 无效")
            return
        }

        val path = Path().apply {
            moveTo(rect.exactCenterX(), rect.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    DemoLog.info("点击演示：中心手势已完成")
                    armStore.recordResult("中心手势已完成")
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    DemoLog.warn("点击演示：中心手势被取消")
                    armStore.recordResult("中心手势被取消")
                }
            },
            null,
        )

        if (dispatched) {
            DemoLog.info("点击演示：ACTION_CLICK 不可用，已派发一次中心手势")
        } else {
            DemoLog.warn("点击演示：手势派发失败")
            armStore.recordResult("点击失败：手势派发失败")
        }
    }

    private fun validateTarget(node: AccessibilityNodeInfo): String? {
        if (node.packageName?.toString() != packageName) return "节点包名不匹配"
        if (node.viewIdResourceName != TARGET_VIEW_ID) return "viewId 不匹配"
        if (node.className?.toString()?.contains("Button") != true) return "className 不是按钮"
        if (!node.isVisibleToUser) return "节点不可见"
        if (!node.isEnabled) return "节点未启用"
        if (!node.isClickable) return "节点不可点击"

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return "bounds 无效"
        val metrics = resources.displayMetrics
        if (rect.left < 0 || rect.top < 0 ||
            rect.right > metrics.widthPixels || rect.bottom > metrics.heightPixels
        ) {
            return "bounds 超出屏幕"
        }
        return null
    }

    private fun reject(reason: String) {
        if (lastRejection == reason) return
        lastRejection = reason
        DemoLog.warn("已拒绝演示动作：$reason")
        armStore.recordResult("已拒绝：$reason")
    }

    private data class Located(
        val node: AccessibilityNodeInfo,
        val path: List<Int>,
        val className: String?,
        val bounds: RectI,
    )

    private fun locateTarget(root: AccessibilityNodeInfo): Located? {
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, path: List<Int>, depth: Int): Located? {
            if (visited++ >= NodeMapper.MAX_NODES) return null
            if (node.viewIdResourceName == TARGET_VIEW_ID) {
                return Located(
                    node = node,
                    path = path,
                    className = node.className?.toString(),
                    bounds = boundsOf(node),
                )
            }
            if (depth >= NodeMapper.MAX_DEPTH) return null

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                visit(child, path + index, depth + 1)?.let { return it }
            }
            return null
        }

        return visit(root, emptyList(), 0)
    }

    private fun resolvePath(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? {
        var current = root
        for (index in path) {
            current = runCatching { current.getChild(index) }.getOrNull() ?: return null
        }
        return current
    }

    private fun boundsOf(node: AccessibilityNodeInfo): RectI {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return RectI(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun findTargetInTree(tree: UiTree): UiNode? {
        for (index in 0 until tree.size) {
            val node = tree.node(index)
            if (node.viewId == TARGET_VIEW_ID) return node
        }
        return null
    }

    companion object {
        private const val TARGET_VIEW_ID = "com.example.nodedemo:id/demo_target_button"
    }
}
