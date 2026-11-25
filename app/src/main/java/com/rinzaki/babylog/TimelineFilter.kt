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


internal fun timelineDateRangeForPreset(
    preset: String,
    today: java.time.LocalDate
): Pair<java.time.LocalDate?, java.time.LocalDate?> = when (preset) {
    "今天" -> today to today
    "昨天" -> today.minusDays(1) to today.minusDays(1)
    "本周" -> today.with(java.time.DayOfWeek.MONDAY) to today
    "近7天" -> today.minusDays(6) to today
    "近30天" -> today.minusDays(29) to today
    "近90天" -> today.minusDays(89) to today
    "全部日期" -> null to null
    else -> null to null
}


internal fun timelineDatePresetIsSelected(
    preset: String,
    today: java.time.LocalDate,
    selectedStartDate: java.time.LocalDate?,
    selectedEndDate: java.time.LocalDate?
): Boolean {
    val range = timelineDateRangeForPreset(preset, today)
    return range.first == selectedStartDate && range.second == selectedEndDate
}
