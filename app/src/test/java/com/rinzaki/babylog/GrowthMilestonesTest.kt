package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GrowthMilestonesTest {
    private fun event(id: String, time: Long, payload: String, deletedAt: Long? = null) =
        BabyEventEntity(id, "baby-1", "MILESTONE", time, payload, null, time, "PENDING", null, deletedAt)

    @Test
    fun catalogHasUniqueIdsAndValidAgeRanges() {
        assertTrue(growthMilestones.isNotEmpty())
        assertEquals(growthMilestones.size, growthMilestones.map { it.id }.toSet().size)
        assertTrue(growthMilestones.all { it.minAgeMonths >= 0 && it.maxAgeMonths >= it.minAgeMonths })
    }

    @Test
    fun completionUsesLatestActiveEventAndIgnoresInvalidPayloads() {
        val completed = completedGrowthMilestones(
            listOf(
                event("first", 100L, """{"milestoneId":"first_steps"}"""),
                event("latest", 200L, """{"milestoneId":"first_steps"}"""),
                event("deleted", 300L, """{"milestoneId":"first_steps"}""", 301L),
                event("invalid", 400L, "not-json"),
                BabyEventEntity("other", "baby-1", "GROWTH", 500L, """{"milestoneId":"sit"}""", null, 500L)
            )
        )
        assertEquals(setOf("first_steps"), completed.keys)
        assertEquals("latest", completed["first_steps"]?.id)
    }

    @Test
    fun ageMonthsUsesCalendarMonthsAndNeverReturnsNegative() {
        val zone = ZoneId.of("Asia/Shanghai")
        val birth = LocalDate.of(2026, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(1L, growthAgeMonths(birth, LocalDate.of(2026, 2, 15), zone))
        assertEquals(0L, growthAgeMonths(birth, LocalDate.of(2026, 1, 1), zone))
    }

    @Test
    fun monthEndBirthdaysUseCompletedCalendarMonthBoundaries() {
        val birth = LocalDate.of(2026, 1, 31)
        assertEquals(0L, growthCompletedMonths(birth, LocalDate.of(2026, 2, 27)))
        assertEquals(1L, growthCompletedMonths(birth, LocalDate.of(2026, 2, 28)))
        assertEquals(2L, growthCompletedMonths(birth, LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun currentStageIncludesCurrentWindowAndNearFutureButNotDistantMilestones() {
        val visible = growthMilestonesForCurrentStage(growthMilestones, 5L)
        assertTrue(visible.any { it.id == "reach_grab" })
        assertTrue(visible.any { it.id == "sit" })
        assertFalse(visible.any { it.id == "first_steps" })
    }

    @Test
    fun currentStageDoesNotMutateCatalogOrCompletedHistory() {
        val allIds = growthMilestones.map { it.id }
        growthMilestonesForCurrentStage(growthMilestones, 8L)
        assertEquals(allIds, growthMilestones.map { it.id })
    }
}
