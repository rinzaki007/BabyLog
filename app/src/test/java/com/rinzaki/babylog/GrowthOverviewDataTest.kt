package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthOverviewDataTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val birthDate = LocalDate.of(2024, 1, 1)
    private val birthTime = birthDate.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun baby(sex: String? = "男宝") = BabyEntity(
        id = "baby-1",
        familyId = "family-1",
        displayName = "宝宝",
        birthTime = birthTime,
        createdAt = birthTime,
        updatedAt = birthTime,
        sex = sex,
        birthWeightGrams = 3200,
        birthLengthCm = 50.0,
        birthHeadCircumferenceCm = 34.0
    )

    private fun event(date: LocalDate, payload: String, id: String) : BabyEventEntity {
        val time = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return BabyEventEntity(
            id = id,
            babyId = "baby-1",
            eventType = "GROWTH",
            occurredAt = time,
            payloadJson = payload,
            createdBy = null,
            updatedAt = time
        )
    }

    @Test
    fun summarizesLatestObservationAndPreviousMeasurementForEachMetric() {
        val events = listOf(
            event(birthDate.plusMonths(1), """{"weightGrams":4200,"lengthCm":54.0,"headCircumferenceCm":36.0}""", "first"),
            event(birthDate.plusMonths(2), """{"weightGrams":5100,"lengthCm":57.0,"headCircumferenceCm":38.0}""", "second")
        )
        val asOf = birthDate.plusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()

        val weight = growthMetricOverview(baby(), events, "weightGrams", asOf, zone)
        val length = growthMetricOverview(baby(), events, "lengthCm", asOf, zone)
        val head = growthMetricOverview(baby(), events, "headCircumferenceCm", asOf, zone)

        assertEquals(5.1f, weight.value!!, 0.001f)
        assertEquals(57.0f, length.value!!, 0.001f)
        assertEquals(38.0f, head.value!!, 0.001f)
        assertTrue(listOf(weight, length, head).all { it.percentileBand != null })
        assertEquals(29L, weight.comparison?.elapsedDays)
        assertEquals(0.9f, weight.comparison?.change ?: Float.NaN, 0.001f)
        assertEquals(null, weight.referenceHint)
    }

    @Test
    fun explainsMissingSexWithoutInventingPercentile() {
        val asOf = birthDate.plusMonths(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val overview = growthMetricOverview(
            baby(sex = null),
            listOf(event(birthDate.plusMonths(1), """{"weightGrams":4200}""", "one")),
            "weightGrams",
            asOf,
            zone
        )

        assertNull(overview.percentileBand)
        assertTrue(overview.referenceHint!!.contains("设置宝宝性别"))
    }

    @Test
    fun explainsWhenHeadCircumferenceReferenceAgeHasEnded() {
        val measuredDate = birthDate.plusMonths(36).plusDays(1)
        val asOf = measuredDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val overview = growthMetricOverview(
            baby(),
            listOf(event(measuredDate, """{"headCircumferenceCm":48.0}""", "late")),
            "headCircumferenceCm",
            asOf,
            zone
        )

        assertNull(overview.percentileBand)
        assertTrue(overview.referenceHint!!.contains("头围标准覆盖至36月龄"))
    }

    @Test
    fun emptyMetricHasNoSyntheticObservationOrComparison() {
        val asOf = birthDate.plusMonths(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val overview = growthMetricOverview(
            baby().copy(birthWeightGrams = null),
            emptyList(),
            "weightGrams",
            asOf,
            zone
        )

        assertNull(overview.measuredAt)
        assertNull(overview.value)
        assertNull(overview.comparison)
        assertNull(overview.percentileBand)
        assertNull(overview.referenceHint)
    }
}
