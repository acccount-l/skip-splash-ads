package com.splashskip.engine.rules

import com.splashskip.engine.model.RuleSet
import kotlinx.serialization.json.Json

/**
 * 规则模块的编码器。RuleParser 里的 Json 只用于解码，且刻意不写默认字段；
 * 导出时使用独立的编码配置，让文件保持完整、可读、可再次导入。
 */
object RuleJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(ruleSet: RuleSet): String =
        json.encodeToString(RuleSet.serializer(), ruleSet)
}
