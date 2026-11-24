package com.rinzaki.babylog

internal fun timelineEventTypeForFilter(filter: String): String? = when (filter) {
    "喂养" -> "FEEDING"
    "大小便" -> "DIAPER"
    "睡眠" -> "SLEEP"
    "体温记录" -> "TEMPERATURE"
    "补充记录" -> "SUPPLEMENT"
    "辅食记录" -> "SOLID_FOOD"
    "成长测量" -> "GROWTH"
    "成长里程碑" -> "MILESTONE"
    "成长足迹" -> "GROWTH_JOURNEY"
    "其他" -> "OTHER"
    else -> null
}
