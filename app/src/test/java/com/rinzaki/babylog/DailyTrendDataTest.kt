package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTrendDataTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 15)

    private fun event(
        id: String,
        type: String,
        date: LocalDate,
        payload: String,
        hour: Int = 12,
        deletedAt: Long? = null
    ) = BabyEventEntity(
        id = id,
        babyId = "baby-1",
        eventType = type,
        occurredAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        payloadJson = payload,
        createdBy = null,
        updatedAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        deletedAt = deletedAt
    )

    @Test
    fun includesEveryDayAndFillsMissingDatesWithZero() {
        val points = dailyTrendPoints(
            listOf(event("f1", "FEEDING", today, """{"amountMl":120}""")),
            "喂养奶量", 3, today, zone
        )
        assertEquals(listOf(0f, 0f, 120f), points.map { it.value })
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), points.map { it.date })
    }

    @Test
    fun aggregatesFeedingVolumeAndCountSeparately() {
        val events = listOf(
            event("f1", "FEEDING", today, """{"amountMl":120}"""),
            event("f2", "FEEDING", today, """{"amountMl":150}"""),
            event("d1", "DIAPER", today, """{"urine":true}""")
        )
        assertEquals(270f, dailyTrendPoints(events, "喂养奶量", 1, today, zone).single().value)
        assertEquals(2f, dailyTrendPoints(events, "喂养次数", 1, today, zone).single().value)
    }

    @Test
    fun countsUrineAndStoolFlagsIndependently() {
        val events = listOf(
            event("d1", "DIAPER", today, """{"urine":true,"stool":true}"""),
            event("d2", "DIAPER", today, """{"urine":true,"stool":false}"""),
            event("f1", "FEEDING", today, """{"amountMl":100}""")
        )
        assertEquals(2f, dailyTrendPoints(events, "小便次数", 1, today, zone).single().value)
        assertEquals(1f, dailyTrendPoints(events, "大便次数", 1, today, zone).single().value)
    }

    @Test
    fun convertsSleepMinutesToHoursAndIgnoresInvalidPayloads() {
        val events = listOf(
            event("s1", "SLEEP", today, """{"durationMinutes":90}"""),
            event("s2", "SLEEP", today, """{"durationMinutes":30}"""),
            event("s3", "SLEEP", today, "not-json")
        )
        assertEquals(2f, dailyTrendPoints(events, "睡眠小时", 1, today, zone).single().value)
    }

    @Test
    fun respectsLocalDateBoundaryAndIgnoresDeletedEvents() {
        val yesterdayLate = event("f1", "FEEDING", today.minusDays(1), """{"amountMl":80}""", 23)
        val todayEarly = event("f2", "FEEDING", today, """{"amountMl":100}""", 0)
        val deleted = event("f3", "FEEDING", today, """{"amountMl":500}""", 8, deletedAt = 1L)
        val points = dailyTrendPoints(listOf(yesterdayLate, todayEarly, deleted), "喂养奶量", 2, today, zone)
        assertEquals(listOf(80f, 100f), points.map { it.value })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRange() {
        dailyTrendPoints(emptyList(), "记录总数", 0, today, zone)
    }
    @Test
    fun countsAllTimeAverageByLocalCalendarDays() {
        val first = event("f1", "FEEDING", today.minusDays(2), """{"amountMl":100}""", 23)
        val latest = event("f2", "FEEDING", today, """{"amountMl":120}""", 0)
        assertEquals(3, calendarDaysCovered(listOf(first, latest), today, zone))
    }

    @Test
    fun calendarDayCoverageIgnoresDeletedAndFutureEvents() {
        val deleted = event("f1", "FEEDING", today.minusDays(8), """{"amountMl":100}""", deletedAt = 1L)
        val future = event("f2", "FEEDING", today.plusDays(3), """{"amountMl":120}""")
        val current = event("f3", "FEEDING", today, """{"amountMl":80}""")
        assertEquals(1, calendarDaysCovered(listOf(deleted, future, current), today, zone))
        assertEquals(1, calendarDaysCovered(emptyList(), today, zone))
    }

}
