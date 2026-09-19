package com.yunmei.vibe.data.model

/**
 * 单位编号对照表（官方顺序，001 起）。
 *
 * 改动前请先读这段约定（来源于官方 App 与既有推断）：
 * - **以编号为主键**做正向映射：后端给出的任意编号（001-024）都必须能唯一解析出单位名称；
 * - 同一单位可能占用多个编号（例如 006/022 同为「浙江工商大学」，016/024 同为「福州工商学院」），
 *   这些条目必须**各自保留**；不要用「名称 → 编号」的 Map 做去重，否则会丢映射；
 * - 反向查找（名称 → 编号）允许多个结果，统一返回 `List`，调用方不要只取第一个。
 *
 * 「单位」包含高校、医院、公司等，不能统一表述为「学校」。
 */
object UnitCodes {

    /** 编号 → 单位名称，主键为三位编号字符串（001-024）。 */
    val BY_CODE: Map<String, String> = linkedMapOf(
        "001" to "中国计量大学",
        "002" to "云莓公寓",
        "003" to "杭州职业技术学院",
        "004" to "云莓科技",
        "005" to "浙江师范大学",
        "006" to "浙江工商大学",
        "007" to "浙江财经大学",
        "008" to "浙江传媒学院",
        "009" to "湖州师范大学",
        "010" to "温州商学院",
        "011" to "浙江水利水电学院",
        "012" to "西湖大学",
        "013" to "花园田氏医院",
        "014" to "上海高校",
        "015" to "云莓测试",
        "016" to "福州工商学院",
        "017" to "湖州艺校",
        "018" to "云莓大学",
        "019" to "湖州健康职业学院",
        "020" to "浙江工业大学",
        "021" to "浙江财经学院东方学院",
        "022" to "浙江工商大学",
        "023" to "浙江财经大学春潮校区",
        "024" to "福州工商学院",
    )

    /** 单位名称 → 编号列表（多值：同一单位对应多个编号时全部返回，不丢任何一条）。 */
    val CODES_BY_NAME: Map<String, List<String>> = BY_CODE.entries
        .groupBy({ it.value }, { it.key })
        .mapValues { (_, codes) -> codes.sorted() }

    /** 仅返回单位名称："001" → "中国计量大学"、"1" → "中国计量大学"；无法识别时返回 null。 */
    fun name(schoolNo: String): String? {
        val index = schoolNo.toIntOrNull() ?: return null
        return BY_CODE[index.toString().padStart(3, '0')]
    }

    /** "001" → "001 中国计量大学"；无法识别时原样返回。 */
    fun format(schoolNo: String): String {
        val name = name(schoolNo)
        return if (name == null) schoolNo else "$schoolNo $name"
    }

    /** 名称 → 编号列表（可能多个，如「浙江工商大学」→ [006, 022]）；无法识别时返回空列表。 */
    fun codesOf(name: String): List<String> = CODES_BY_NAME[name].orEmpty()

    /** 名称 → 单个编号，仅用于确知该名称唯一对应一个编号的场景；多编号场景请用 [codesOf]。 */
    fun codeOf(name: String): String? = CODES_BY_NAME[name]?.firstOrNull()
}
