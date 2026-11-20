package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyMemoriesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun event(id: String, date: LocalDate, title: String, deletedAt: Long? = null) =
        BabyEventEntity(id, "baby", "MILESTONE", date.atStartOfDay(zone).toInstant().toEpochMilli(),
            """{"milestoneTitle":"$title"}""", null, 1L, deletedAt = deletedAt)

    @Test
    fun upcomingMomentsAreOrderedAndExcludePastDates() {
        val birth = LocalDate.of(2026, 1, 1)
        val moments = familyMemoryMoments(birth, LocalDate.of(2026, 4, 1))
        assertEquals("百日纪念日", moments.first().title)
        assertTrue(moments.all { !it.date.isBefore(LocalDate.of(2026, 4, 1)) })
        assertEquals(moments.sortedBy { it.date }, moments)
    }

    @Test
    fun includesTodayAndHandlesLeapDayBirthday() {
        val birth = LocalDate.of(2024, 2, 29)
        val moments = familyMemoryMoments(birth, LocalDate.of(2025, 2, 28))
        assertTrue(moments.any { it.title == "一周岁" && it.daysRemaining == 0L })
    }

    @Test
    fun upcomingBirthdaysContinueAfterTheFirstTenYears() {
        val birth = LocalDate.of(2010, 1, 1)
        val moments = familyMemoryMoments(birth, LocalDate.of(2021, 1, 2))
        assertEquals(listOf("12 周岁", "13 周岁", "14 周岁"), moments.map { it.title })
        assertEquals(listOf(
            LocalDate.of(2022, 1, 1),
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2024, 1, 1)
        ), moments.map { it.date })
    }

    @Test
    fun recapCountsOnlyActiveMilestonesAndShowsNewestFirst() {
        val events = listOf(
            event("1", LocalDate.of(2026, 1, 1), "第一次翻身"),
            event("2", LocalDate.of(2026, 3, 1), "第一次爬行"),
            event("3", LocalDate.of(2026, 2, 1), "第一次独坐", deletedAt = 10L)
        )
        val recap = familyMemoryRecap(events, zone)
        assertEquals(2, recap.milestoneCount)
        assertEquals("第一次爬行", recap.latestMilestones.first().first)
        assertEquals(2, recap.latestMilestones.size)
    }

    @Test
    fun recapIgnoresMalformedOrBlankMilestoneTitles() {
        val valid = event("1", LocalDate.of(2026, 1, 1), "第一次微笑")
        val malformed = valid.copy(id = "2", payloadJson = "{")
        val blank = valid.copy(id = "3", payloadJson = """{"milestoneTitle":"  "}""")
        val recap = familyMemoryRecap(listOf(valid, malformed, blank), zone)
        assertEquals(1, recap.milestoneCount)
    }
}
