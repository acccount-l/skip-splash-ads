package com.example.nodedemo.ui

/** 中英文免责声明，与 README 顶部和末尾保持同一份全文。 */
object Disclaimer {
    val chinese: String = """
        本项目是一个用于学习 Android 无障碍服务（AccessibilityService）、界面节点树读取、边界校验与点击/手势派发原语的技术演示。它不针对任何特定第三方应用，不包含任何应用名单、包名映射或自动化策略，也不会读取或操作任何第三方应用。示例中的点击仅在用户于应用内显式开启一次演示后执行，且只作用于本项目自带的演示页面。

        本项目按“现状”提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性和非侵权担保。使用者应自行确保其使用行为符合当地法律法规、平台政策及第三方服务条款。因使用或无法使用本项目而产生的任何直接或间接损失，作者与贡献者不承担责任。

        本项目不收集、不上传任何个人数据。日志仅保存在应用私有目录，可由用户随时清空。应用不申请网络权限，不读取与演示无关的界面内容，不申请查询全部已安装应用的权限。
    """.trimIndent()

    val english: String = """
        This project is a technical demonstration of Android's AccessibilityService, UI node-tree inspection, bounds validation, and click/gesture dispatch primitives. It is not targeted at any specific third-party app, contains no app lists, package mappings, or automation policies, and does not read or operate any third-party app. The demo click is performed only after the user explicitly arms a single demo run inside the app and only within this project's own demo screen.

        The software is provided "AS IS", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and noninfringement. Users are solely responsible for ensuring that their use complies with applicable laws, platform policies, and third-party terms of service. In no event shall the authors or copyright holders be liable for any claim, damages, or other liability arising from, out of, or in connection with the software or the use or other dealings in the software.

        This project does not collect or upload personal data. Logs are stored only in the app's private storage and can be cleared by the user at any time. The app does not request network access, does not read UI content unrelated to the demo, and does not request permission to query all installed apps.
    """.trimIndent()
}
