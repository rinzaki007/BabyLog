package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GrowthMilestonesTest {
    private fun event(id: String, time: Long, payload: String, deletedAt: Long? = null) = BabyEventEntity(
        id = id,
        babyId = "baby-1",
        eventType = "MILESTONE",
        occurredAt = time,
        payloadJson = payload,
        createdBy = null,
        updatedAt = time,
        deletedAt = deletedAt
    )

    @Test
    fun milestoneCatalogHasStableUniqueIdsAndAgeRanges() {
        assertTrue(growthMilestones.isNotEmpty())
        assertEquals(growthMilestones.size, growthMilestones.map { it.id }.distinct().size)
        assertTrue(growthMilestones.all { it.minAgeMonths >= 0 && it.maxAgeMonths >= it.minAgeMonths })
    }

    @Test
    fun completionMapIgnoresDeletedAndMalformedEventsAndKeepsLatestPerMilestone() {
        val events = listOf(
            event("first", 100L, """{"milestoneId":"first_steps"}"""),
            event("latest", 200L, """{"milestoneId":"first_steps"}"""),
            event("deleted", 300L, """{"milestoneId":"first_steps"}""", deletedAt = 301L),
            event("invalid", 400L, "not-json"),
            event("other-type", 500L, """{"milestoneId":"sit"}""").copy(eventType = "GROWTH")
        )

        val completed = completedGrowthMilestones(events)

        assertEquals(setOf("first_steps"), completed.keys)
        assertEquals("latest", completed.getValue("first_steps").id)
    }

    @Test
    fun babyAgeMonthsUsesCalendarMonthsAndNeverReturnsNegativeAge() {
        val zone = ZoneId.of("Asia/Shanghai")
        val birthDate = LocalDate.of(2026, 1, 15)
        val birthTime = birthDate.atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(1L, babyAgeMonths(birthTime, LocalDate.of(2026, 2, 15), zone))
        assertEquals(0L, babyAgeMonths(birthTime, LocalDate.of(2026, 1, 1), zone))
    }
}
