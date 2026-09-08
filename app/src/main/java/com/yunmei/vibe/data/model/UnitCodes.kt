package com.yunmei.vibe.data.model

/**
 * 单位编号对照表（官方顺序，001 起；含重复项，原样保留）。
 * 「单位」包含高校、医院、公司等，不能统一表述为「学校」。
 */
object UnitCodes {

    val UNITS: List<String> = listOf(
        "中国计量大学",
        "云莓公寓",
        "杭州职业技术学院",
        "云莓科技",
        "浙江师范大学",
        "浙江工商大学",
        "浙江财经大学",
        "浙江传媒学院",
        "湖州师范大学",
        "温州商学院",
        "浙江水利水电学院",
        "西湖大学",
        "花园田氏医院",
        "上海高校",
        "云莓测试",
        "福州工商学院",
        "湖州艺校",
        "云莓大学",
        "湖州健康职业学院",
        "浙江工业大学",
        "浙江财经学院东方学院",
        "浙江工商大学",
        "浙江财经大学春潮校区",
        "福州工商学院",
    )

    /** 仅返回单位名称："001" → "中国计量大学"；无法识别时返回 null。 */
    fun name(schoolNo: String): String? = schoolNo.toIntOrNull()?.let { UNITS.getOrNull(it - 1) }

    /** "001" → "001 中国计量大学"；无法识别时原样返回。 */
    fun format(schoolNo: String): String {
        val name = name(schoolNo)
        return if (name == null) schoolNo else "$schoolNo $name"
    }
}
