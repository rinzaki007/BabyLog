package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HomeStageTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun birth(monthsAgo: Long, today: LocalDate): Long =
        today.minusMonths(monthsAgo).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun stageChangesAtExpectedAgeBoundaries() {
        val today = LocalDate.of(2026, 11, 17)

        assertEquals("0–5个月 · 日常照护", homeStageProfile(birth(5, today), today, zone).label)
        assertEquals("6–11个月 · 辅食与成长", homeStageProfile(birth(6, today), today, zone).label)
        assertEquals("1–2岁 · 成长与饮食", homeStageProfile(birth(12, today), today, zone).label)
        assertEquals("2岁以上 · 成长记录", homeStageProfile(birth(24, today), today, zone).label)
    }

    @Test
    fun futureBirthDateUsesYoungestStageSafely() {
        val today = LocalDate.of(2026, 11, 17)
        val futureBirth = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals("0–5个月 · 日常照护", homeStageProfile(futureBirth, today, zone).label)
    }

    @Test
    fun shortcutsFollowStagePriorityWithoutRemovingAnyAction() {
        val today = LocalDate.of(2026, 11, 17)
        val profile = homeStageProfile(birth(8, today), today, zone)

        assertEquals(
            listOf("SOLID_FOOD", "FEEDING", "SUPPLEMENT", "GROWTH", "DIAPER", "SLEEP"),
            orderedHomeShortcuts(profile).map { it.eventType }
        )
        assertEquals(6, orderedHomeShortcuts(profile).map { it.eventType }.distinct().size)
    }
}
