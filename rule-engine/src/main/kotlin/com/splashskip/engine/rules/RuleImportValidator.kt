package com.splashskip.engine.rules

import com.splashskip.engine.engine.RuleParser
import com.splashskip.engine.model.RuleSet

/**
 * 导入模块的第一道校验。模块就是一个普通的 RuleSet JSON 文件。
 *
 * 这里不处理“与其他模块冲突”，那属于整个规则目录的合并逻辑；
 * 本对象只负责单个文件的大小、结构、规则数和目标包名是否安全。
 */
object RuleImportValidator {
    const val MAX_MODULE_BYTES = 256 * 1024
    const val MAX_RULES = 64
    const val MAX_PACKAGE_NAME_LENGTH = 150

    private val BOM = 0xFEFF.toChar().toString()

    /**
     * 这些包不是普通第三方 App：导入规则若命中它们，可能点击系统设置、
     * 权限弹窗或安装器。内置规则不受此限制，导入模块一律拒绝。
     */
    val BLOCKED_PACKAGES: Set<String> = setOf(
        "com.splashskip.app",
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.gms",
        "com.android.vending",
        "com.vivo.permissionmanager",
        "com.vivo.systemuiplugin",
    )

    fun isBlockedPackage(packageName: String): Boolean = packageName in BLOCKED_PACKAGES

    fun normalize(text: String): String = text.removePrefix(BOM)

    fun validateText(text: String, label: String): Result<RuleSet> = runCatching {
        val clean = normalize(text)
        val bytes = clean.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_MODULE_BYTES) {
            "$label: 模块超过 ${MAX_MODULE_BYTES / 1024} KiB"
        }

        val ruleSet = RuleParser.parse(clean).getOrElse { error ->
            throw IllegalArgumentException("$label: ${error.message ?: "解析失败"}")
        }

        require(ruleSet.packageName.length <= MAX_PACKAGE_NAME_LENGTH) {
            "$label: 包名超过 $MAX_PACKAGE_NAME_LENGTH 字符"
        }
        require(!isBlockedPackage(ruleSet.packageName)) {
            "$label: 不允许为受保护包名 ${ruleSet.packageName} 导入规则"
        }
        require(ruleSet.rules.size <= MAX_RULES) {
            "$label: 规则数超过 $MAX_RULES"
        }
        ruleSet
    }

    fun validateBytes(bytes: ByteArray, label: String): Result<RuleSet> {
        if (bytes.size > MAX_MODULE_BYTES) {
            return Result.failure(
                IllegalArgumentException("$label: 模块超过 ${MAX_MODULE_BYTES / 1024} KiB"),
            )
        }
        return validateText(String(bytes, Charsets.UTF_8), label)
    }
}
