package com.rinzaki.babylog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class HomeStageProfile(
    val label: String,
    val priorityEventTypes: List<String>
)

internal data class HomeShortcut(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val eventType: String
)

internal fun homeStageProfile(
    birthTime: Long,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): HomeStageProfile {
    val birthDate = runCatching {
        Instant.ofEpochMilli(birthTime).atZone(zoneId).toLocalDate()
    }.getOrDefault(today)
    val ageMonths = ChronoUnit.MONTHS.between(birthDate, today).coerceAtLeast(0)

    return when {
        ageMonths < 6 -> HomeStageProfile(
            "0–5个月 · 日常照护",
            listOf("FEEDING", "DIAPER", "SLEEP", "SUPPLEMENT", "GROWTH", "SOLID_FOOD")
        )
        ageMonths < 12 -> HomeStageProfile(
            "6–11个月 · 辅食与成长",
            listOf("SOLID_FOOD", "FEEDING", "SUPPLEMENT", "GROWTH", "DIAPER", "SLEEP")
        )
        ageMonths < 24 -> HomeStageProfile(
            "1–2岁 · 成长与饮食",
            listOf("SOLID_FOOD", "GROWTH", "FEEDING", "SUPPLEMENT", "DIAPER", "SLEEP")
        )
        else -> HomeStageProfile(
            "2岁以上 · 成长记录",
            listOf("GROWTH", "SOLID_FOOD", "FEEDING", "SUPPLEMENT", "DIAPER", "SLEEP")
        )
    }
}

internal fun orderedHomeShortcuts(profile: HomeStageProfile): List<HomeShortcut> {
    val shortcuts = listOf(
        HomeShortcut("🍼", "喂养", "多种喂养方式", "FEEDING"),
        HomeShortcut("🥣", "辅食", "食物与进食情况", "SOLID_FOOD"),
        HomeShortcut("🧴", "补充记录", "AD、D3、饮水等", "SUPPLEMENT"),
        HomeShortcut("🌿", "大小便", "护理情况", "DIAPER"),
        HomeShortcut("🌙", "睡眠", "休息时长", "SLEEP"),
        HomeShortcut("📏", "成长测量", "体重身长", "GROWTH")
    )
    val priorities = profile.priorityEventTypes.withIndex().associate { it.value to it.index }
    return shortcuts.sortedBy { priorities[it.eventType] ?: Int.MAX_VALUE }
}
