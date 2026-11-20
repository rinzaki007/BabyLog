package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GrowthReportDataTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 11, 19).atStartOfDay(zone).toInstant().toEpochMilli()
    private val baby = BabyEntity(
        id = "baby-1",
        familyId = "family-1",
        displayName = "小宝",
        birthTime = LocalDate.of(2026, 1, 19).atStartOfDay(zone).toInstant().toEpochMilli(),
        createdAt = now,
        updatedAt = now,
        sex = "未设置",
        birthWeightGrams = 3200,
        birthLengthCm = 50.0,
        birthHeadCircumferenceCm = null
    )

    private fun event(
        id: String,
        type: String,
        payload: String,
        occurredAt: Long = now,
        babyId: String = baby.id,
        deletedAt: Long? = null
    ) = BabyEventEntity(id, babyId, type, occurredAt, payload, null, now, deletedAt = deletedAt)

    @Test
    fun reportIncludesBirthDataAndValidGrowthMeasurements() {
        val report = buildGrowthReportData(
            baby,
            listOf(event("growth", "GROWTH", """{"weightGrams":6500,"lengthCm":63.5}""")),
            now,
            zone
        )

        assertEquals("10 个月", report.ageLabel)
        assertEquals(2, report.growthMeasurements.size)
        assertTrue(report.growthMeasurements.first().isBirthMeasurement)
        assertEquals(3.2, report.growthMeasurements.first().weightKg!!, 0.001)
        assertEquals(6.5, report.growthMeasurements.last().weightKg!!, 0.001)
        assertEquals(1, report.eventCount)
    }

    @Test
    fun reportAgeUsesCompletedCalendarMonthsForMonthEndBirthdays() {
        val monthEndBaby = baby.copy(
            birthTime = LocalDate.of(2026, 1, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        )
        val beforeFirstCompletedMonth = buildGrowthReportData(
            monthEndBaby,
            emptyList(),
            LocalDate.of(2026, 2, 27).atStartOfDay(zone).toInstant().toEpochMilli(),
            zone
        )
        val onFirstCompletedMonth = buildGrowthReportData(
            monthEndBaby,
            emptyList(),
            LocalDate.of(2026, 2, 28).atStartOfDay(zone).toInstant().toEpochMilli(),
            zone
        )

        assertEquals("27 天", beforeFirstCompletedMonth.ageLabel)
        assertEquals("1 个月", onFirstCompletedMonth.ageLabel)
    }

    @Test
    fun reportExcludesDeletedFutureAndOtherBabyEvents() {
        val report = buildGrowthReportData(
            baby,
            listOf(
                event("valid", "FEEDING", """{"amountMl":120}"""),
                event("deleted", "FEEDING", """{"amountMl":200}""", deletedAt = now),
                event("future", "FEEDING", """{"amountMl":300}""", occurredAt = now + 60_000),
                event("other", "FEEDING", """{"amountMl":400}""", babyId = "baby-2")
            ),
            now,
            zone
        )

        assertEquals(1, report.eventCount)
        assertEquals(120, report.recordedMilkMl)
        assertEquals(1, report.feedingCount)
    }

    @Test
    fun reportKeepsMissingHistoricalFieldsUnspecified() {
        val report = buildGrowthReportData(
            baby.copy(birthWeightGrams = null, birthLengthCm = null),
            listOf(event("old", "GROWTH", """{"weightGrams":0,"lengthCm":-1}""")),
            now,
            zone
        )

        assertTrue(report.growthMeasurements.isEmpty())
        assertEquals(0, report.recordedMilkMl)
        assertFalse(report.sex.isBlank())
    }

    @Test
    fun reportSummarizesDailyCareMilestonesAndFood() {
        val report = buildGrowthReportData(
            baby,
            listOf(
                event("feed", "FEEDING", """{"amountMl":100,"breastMinutes":10}"""),
                event("sleep", "SLEEP", """{"durationMinutes":90}"""),
                event("food", "SOLID_FOOD", """{"foodName":"米粉","foodStage":"首次尝试","foodReaction":"未见明显异常"}"""),
                event("mile", "MILESTONE", """{"milestoneTitle":"第一次翻身","category":"大运动"}"""),
                event("vaccine", "VACCINE", """{"vaccineName":"疫苗A"}""")
            ),
            now,
            zone
        )

        assertEquals(100, report.recordedMilkMl)
        assertEquals(10, report.breastfeedingMinutes)
        assertEquals(90, report.sleepMinutes)
        assertEquals(1, report.solidFoodCount)
        assertEquals(1, report.vaccineCount)
        assertEquals("第一次翻身", report.milestones.first().title)
        assertTrue(report.foodExploration.any { it.contains("米粉") })
    }
}
