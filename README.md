# Accessibility Node Demo / 无障碍节点演示

一个用于学习 Android 无障碍服务（`AccessibilityService`）的中性技术演示：订阅本应用窗口事件，读取界面节点树，执行边界与状态校验，并在用户显式授权后派发一次点击或手势。项目不针对任何特定第三方应用，也不包含应用名单、包名映射或自动化策略。

## 免责声明 / Disclaimer

本项目是一个用于学习 Android 无障碍服务（AccessibilityService）、界面节点树读取、边界校验与点击/手势派发原语的技术演示。它不针对任何特定第三方应用，不包含任何应用名单、包名映射或自动化策略，也不会读取或操作任何第三方应用。示例中的点击仅在用户于应用内显式开启一次演示后执行，且只作用于本项目自带的演示页面。

本项目按“现状”提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性和非侵权担保。使用者应自行确保其使用行为符合当地法律法规、平台政策及第三方服务条款。因使用或无法使用本项目而产生的任何直接或间接损失，作者与贡献者不承担责任。

本项目不收集、不上传任何个人数据。日志仅保存在应用私有目录，可由用户随时清空。应用不申请网络权限，不读取与演示无关的界面内容，不申请查询全部已安装应用的权限。

---

This project is a technical demonstration of Android's AccessibilityService, UI node-tree inspection, bounds validation, and click/gesture dispatch primitives. It is not targeted at any specific third-party app, contains no app lists, package mappings, or automation policies, and does not read or operate any third-party app. The demo click is performed only after the user explicitly arms a single demo run inside the app and only within this project's own demo screen.

The software is provided "AS IS", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and noninfringement. Users are solely responsible for ensuring that their use complies with applicable laws, platform policies, and third-party terms of service. In no event shall the authors or copyright holders be liable for any claim, damages, or other liability arising from, out of, or in connection with the software or the use or other dealings in the software.

This project does not collect or upload personal data. Logs are stored only in the app's private storage and can be cleared by the user at any time. The app does not request network access, does not read UI content unrelated to the demo, and does not request permission to query all installed apps.

## 项目说明

- 应用名：`节点演示` / `Node Demo`
- applicationId / namespace：`com.example.nodedemo`
- 版本：`0.1.0`（versionCode 1）
- 最低系统：Android 8.0（API 26）
- 编译 / 目标系统：Android 16（API 36）
- 无网络权限、无存储权限、无 `QUERY_ALL_PACKAGES`、无包可见性查询
- 无障碍事件过滤只包含本应用包名
- 唯一允许操作的目标：本应用演示页的 `demo_target_button`

## 技术原理

### 1. 事件驱动

`NodeDemoAccessibilityService` 订阅 `TYPE_WINDOW_STATE_CHANGED` 和 `TYPE_WINDOW_CONTENT_CHANGED`。服务在 XML 配置和运行时都把 `packageNames` 固定为 `com.example.nodedemo`，因此不会接收其他应用的事件。事件到达后，服务依次校验事件包名、活动窗口包名和目标节点包名，三者必须完全一致。

### 2. 节点树读取

`NodeMapper` 把 Android 的 `AccessibilityNodeInfo` 树转换为纯 Kotlin 的 `UiNode` 快照，限制最大深度和节点数。`UiTree` 再把嵌套节点展平成带父节点索引的数组，便于遍历和定位。快照类型刻意重写 `toString()`，不输出 `text` 或 `contentDescription`。

### 3. 边界与状态校验

点击前会重新按节点路径定位真实节点，并复核：

- 包名、viewId、className 与快照一致；
- 节点可见、启用、可点击；
- bounds 有效且位于屏幕范围内；
- bounds 与首次快照相比没有大幅漂移（`Bounds.near`）。

任一校验失败都会放弃动作并记录原因，不会回退到坐标盲点。

### 4. 点击与手势派发

用户点击“开启一次点击演示”后，`DemoArmStore` 写入一次性授权，有效期 60 秒。服务找到演示按钮后优先调用 `ACTION_CLICK`；若节点不支持该动作，则使用节点中心的一次短手势。授权在完成读取或点击后立即清除，60 秒内未执行也会自动失效。服务不做后台循环扫描。

### 5. 本地日志

日志只追加到应用私有目录，支持 128 KB × 2 轮转，可由用户在应用内清空。记录内容仅包括包名、viewId、className、bounds、节点数量和拒绝原因；不记录节点文本、内容描述、截图或界面图像。应用不联网，日志不会上传。

## 安全边界

- 只订阅本应用包名的事件，不读取其他应用。
- 事件包名、活动窗口包名、目标节点包名三重校验。
- 只处理自带演示页的 `demo_target_button`；找不到目标就不动作。
- 用户显式授权一次，60 秒有效，执行后立即失效。
- 不使用 `QUERY_ALL_PACKAGES`，不查询已安装应用列表。
- 不申请网络、存储、悬浮窗、通知或媒体投影权限。
- 不进行截图、录屏、OCR 或坐标盲点。
- 不包含规则、应用名单、包名映射、导入导出或自动化策略。
- 日志仅保存在本机私有目录，不记录节点文本。

## 构建与测试

环境要求：JDK 17 或更高版本、Android SDK 36、Gradle Wrapper。

```bash
./gradlew :accessibility-core:test :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

## 项目结构

```text
app/
  src/main/kotlin/com/example/nodedemo/
    NodeDemoApp.kt
    data/         一次性授权、本地日志与日志编解码
    service/      无障碍服务、节点映射、服务状态检查
    ui/           主界面、演示页、日志页、关于页
  src/main/res/   主题、字符串、图标、演示页布局
  src/test/       日志编解码单元测试
accessibility-core/
  src/main/kotlin/com/example/nodedemo/core/
    engine/       与平台无关的几何校验
    model/        节点快照与树结构
  src/test/       几何与树结构单元测试
```

## 许可证

MIT License

Copyright (c) 2026 Project Maintainers

完整许可文本见 [LICENSE](LICENSE)。

## 免责声明 / Disclaimer

本项目是一个用于学习 Android 无障碍服务（AccessibilityService）、界面节点树读取、边界校验与点击/手势派发原语的技术演示。它不针对任何特定第三方应用，不包含任何应用名单、包名映射或自动化策略，也不会读取或操作任何第三方应用。示例中的点击仅在用户于应用内显式开启一次演示后执行，且只作用于本项目自带的演示页面。

本项目按“现状”提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性和非侵权担保。使用者应自行确保其使用行为符合当地法律法规、平台政策及第三方服务条款。因使用或无法使用本项目而产生的任何直接或间接损失，作者与贡献者不承担责任。

本项目不收集、不上传任何个人数据。日志仅保存在应用私有目录，可由用户随时清空。应用不申请网络权限，不读取与演示无关的界面内容，不申请查询全部已安装应用的权限。

---

This project is a technical demonstration of Android's AccessibilityService, UI node-tree inspection, bounds validation, and click/gesture dispatch primitives. It is not targeted at any specific third-party app, contains no app lists, package mappings, or automation policies, and does not read or operate any third-party app. The demo click is performed only after the user explicitly arms a single demo run inside the app and only within this project's own demo screen.

The software is provided "AS IS", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and noninfringement. Users are solely responsible for ensuring that their use complies with applicable laws, platform policies, and third-party terms of service. In no event shall the authors or copyright holders be liable for any claim, damages, or other liability arising from, out of, or in connection with the software or the use or other dealings in the software.

This project does not collect or upload personal data. Logs are stored only in the app's private storage and can be cleared by the user at any time. The app does not request network access, does not read UI content unrelated to the demo, and does not request permission to query all installed apps.
