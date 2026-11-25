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
    fun allTimeTrendWindowUsesEarliestRecordAcrossCategories() {
        val earlierDiaper = event("d1", "DIAPER", today.minusDays(10), """{"urine":true}""")
        val laterFeeding = event("f1", "FEEDING", today.minusDays(2), """{"amountMl":100}""")
        val selectedCategoryEvents = listOf(laterFeeding)
        val allBabyEvents = listOf(earlierDiaper, laterFeeding)

        assertEquals(11, dailyTrendWindowDays("全部", allBabyEvents, today, zone))
        assertEquals(3, dailyTrendWindowDays("全部", selectedCategoryEvents, today, zone))
        assertEquals(30, dailyTrendWindowDays("近30天", selectedCategoryEvents, today, zone))
        assertEquals(90, dailyTrendWindowDays("近90天", allBabyEvents, today, zone))
    }

    @Test
    fun categorySeriesKeepTheSameAllTimeDateAxis() {
        val earlierDiaper = event("d1", "DIAPER", today.minusDays(10), """{"urine":true}""")
        val laterFeeding = event("f1", "FEEDING", today.minusDays(2), """{"amountMl":100}""")
        val allBabyEvents = listOf(earlierDiaper, laterFeeding)
        val days = dailyTrendWindowDays("全部", allBabyEvents, today, zone)

        val feedingPoints = dailyTrendPoints(listOf(laterFeeding), "喂养次数", days, today, zone)
        val diaperPoints = dailyTrendPoints(listOf(earlierDiaper), "小便次数", days, today, zone)

        assertEquals(diaperPoints.map { it.date }, feedingPoints.map { it.date })
        assertEquals(today.minusDays(10), feedingPoints.first().date)
        assertEquals(List(8) { 0f } + listOf(1f, 0f, 0f), feedingPoints.map { it.value })
        assertEquals(listOf(1f) + List(10) { 0f }, diaperPoints.map { it.value })
    }

    @Test
    fun allTimeTrendWindowIsLimitedTo366Days() {
        val oldRecord = event("d1", "DIAPER", today.minusDays(500), """{"urine":true}""")
        assertEquals(366, dailyTrendWindowDays("全部", listOf(oldRecord), today, zone))
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

    @Test
    fun countsSupplementRecordsSeparatelyFromFeeding() {
        val events = listOf(
            event("a1", "SUPPLEMENT", today, """{"supplementName":"维生素AD","amount":1,"unit":"滴"}"""),
            event("a2", "SUPPLEMENT", today, """{"supplementName":"水","amount":30,"unit":"ml"}"""),
            event("f1", "FEEDING", today, """{"amountMl":120}""")
        )
        assertEquals(2f, dailyTrendPoints(events, "补充次数", 1, today, zone).single().value)
        assertEquals(1f, dailyTrendPoints(events, "喂养次数", 1, today, zone).single().value)
    }

    @Test
    fun countsSolidFoodSeparatelyFromFeedingAndSupplements() {
        val events = listOf(
            event("sf1", "SOLID_FOOD", today, """{"foodName":"米粉","amount":10,"unit":"g"}"""),
            event("sf2", "SOLID_FOOD", today, """{"foodName":"苹果泥","eatingStatus":"少量尝试"}"""),
            event("a1", "SUPPLEMENT", today, """{"supplementName":"维生素AD"}"""),
            event("f1", "FEEDING", today, """{"amountMl":120}""")
        )
        assertEquals(2f, dailyTrendPoints(events, "辅食次数", 1, today, zone).single().value)
        assertEquals(1f, dailyTrendPoints(events, "补充次数", 1, today, zone).single().value)
        assertEquals(1f, dailyTrendPoints(events, "喂养次数", 1, today, zone).single().value)
    }


    @Test
    fun countsTemperatureRecordsSeparatelyFromOtherEvents() {
        val events = listOf(
            event("t1", "TEMPERATURE", today, """{"temperatureC":36.8,"measurementMethod":"腋温"}"""),
            event("t2", "TEMPERATURE", today, """{"temperatureC":37.1,"measurementMethod":"耳温"}"""),
            event("f1", "FEEDING", today, """{"amountMl":120}""")
        )
        assertEquals(2f, dailyTrendPoints(events, "体温记录次数", 1, today, zone).single().value)
        assertEquals(0f, dailyTrendPoints(events, "体温记录次数", 1, today.minusDays(1), zone).single().value)
    }

    @Test
    fun countsMilestonesSeparatelyFromGrowthMeasurements() {
        val events = listOf(
            event("m1", "MILESTONE", today, """{"milestoneTitle":"第一次翻身"}"""),
            event("m2", "MILESTONE", today.minusDays(1), """{"milestoneTitle":"第一次坐稳"}"""),
            event("g1", "GROWTH", today, """{"weightGrams":6500}""")
        )
        assertEquals(1f, dailyTrendPoints(events, "里程碑次数", 1, today, zone).single().value)
        assertEquals(2f, dailyTrendPoints(events, "里程碑次数", 2, today, zone).sumOf { it.value.toDouble() }.toFloat())
        assertEquals(listOf("里程碑次数"), trendMetricsForCategory("里程碑"))
    }

    @Test
    fun dailyTrendMetricsFollowSelectedStatisticsCategory() {
        assertEquals(listOf("喂养奶量", "喂养次数"), trendMetricsForCategory("喂养"))
        assertEquals(listOf("辅食次数"), trendMetricsForCategory("辅食"))
        assertEquals(listOf("补充次数"), trendMetricsForCategory("补充"))
        assertEquals(listOf("小便次数", "大便次数"), trendMetricsForCategory("护理"))
        assertEquals(listOf("睡眠小时"), trendMetricsForCategory("睡眠"))
        assertEquals(listOf("记录总数"), trendMetricsForCategory("成长"))
        assertEquals(listOf("体温记录次数"), trendMetricsForCategory("体温"))
        assertEquals(listOf("里程碑次数"), trendMetricsForCategory("里程碑"))
        assertEquals(10, trendMetricsForCategory("全部").size)
    }

    @Test
    fun recentAverageWindowStartsAtBirthWhenBabyIsYoungerThanSelectedRange() {
        val birth = today.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(4, statisticsDaysCovered(emptyList(), 7, birth, today, zone))
    }

    @Test
    fun recentAverageWindowUsesFullRangeWhenBabyWasBornEarlier() {
        val birth = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(7, statisticsDaysCovered(emptyList(), 7, birth, today, zone))
    }

    @Test
    fun allTimeAverageWindowStartsAtBirthAndFallsBackToFirstRecord() {
        val birth = today.minusDays(10).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(11, statisticsDaysCovered(emptyList(), null, birth, today, zone))
        val first = event("f1", "FEEDING", today.minusDays(2), """{"amountMl":100}""")
        assertEquals(3, statisticsDaysCovered(listOf(first), null, null, today, zone))
    }
    @Test
    fun allTimeAverageFallbackUsesEarliestRecordAcrossCategories() {
        val earlierDiaper = event("d1", "DIAPER", today.minusDays(10), """{"urine":true}""")
        val laterFeeding = event("f1", "FEEDING", today.minusDays(2), """{"amountMl":100}""")
        val allBabyEvents = listOf(earlierDiaper, laterFeeding)

        // Even when the selected category has no record until later, the denominator
        // remains based on the baby's earliest valid record across all categories.
        assertEquals(11, statisticsDaysCovered(allBabyEvents, null, null, today, zone))
    }

}
