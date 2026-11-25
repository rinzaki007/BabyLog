package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthDataTest {
    private fun baby(
        id: String = "baby-1",
        weight: Int? = 3200,
        length: Double? = 50.0,
        head: Double? = 34.0,
        birthTime: Long = 1_700_000_000_000L
    ) = BabyEntity(
        id = id,
        familyId = "family-1",
        displayName = "宝宝",
        birthTime = birthTime,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        birthWeightGrams = weight,
        birthLengthCm = length,
        birthHeadCircumferenceCm = head
    )

    private fun event(
        type: String,
        time: Long,
        payload: String,
        babyId: String = "baby-1",
        deletedAt: Long? = null
    ) = BabyEventEntity(
        id = "event-$time-$babyId",
        babyId = babyId,
        eventType = type,
        occurredAt = time,
        payloadJson = payload,
        createdBy = null,
        updatedAt = time,
        deletedAt = deletedAt
    )

    @Test
    fun includesBirthMeasurementsAndConvertsWeightToKilograms() {
        val points = growthMetricPoints(
            baby(),
            listOf(event("GROWTH", 1_710_000_000_000L, """{"weightGrams":5200}""")),
            "weightGrams"
        )

        assertEquals(2, points.size)
        assertEquals(3.2f, points[0].second, 0.001f)
        assertEquals(5.2f, points[1].second, 0.001f)
    }

    @Test
    fun ignoresMissingInvalidAndNonGrowthMeasurements() {
        val points = growthMetricPoints(
            baby(weight = null, length = null, head = null),
            listOf(
                event("FEEDING", 1_705_000_000_000L, """{"weightGrams":5000}"""),
                event("GROWTH", 1_710_000_000_000L, """{"weightGrams":0}"""),
                event("GROWTH", 1_720_000_000_000L, """not-json"""),
                event("GROWTH", 1_730_000_000_000L, """{"weightGrams":6000}""")
            ),
            "weightGrams"
        )

        assertEquals(1, points.size)
        assertEquals(6.0f, points.single().second, 0.001f)
        assertTrue(points.single().first > 1_700_000_000_000L)
    }

    @Test
    fun excludesOtherBabiesAndSoftDeletedGrowthMeasurements() {
        val points = growthMetricPoints(
            baby(weight = null, length = null, head = null),
            listOf(
                event("GROWTH", 1_710_000_000_000L, """{"weightGrams":5100}""", babyId = "baby-2"),
                event("GROWTH", 1_720_000_000_000L, """{"weightGrams":5200}""", deletedAt = 1_730_000_000_000L),
                event("GROWTH", 1_740_000_000_000L, """{"weightGrams":5300}""")
            ),
            "weightGrams"
        )

        assertEquals(1, points.size)
        assertEquals(5.3f, points.single().second, 0.001f)
        assertEquals(1_740_000_000_000L, points.single().first)
    }

    @Test
    fun excludesFutureBirthAndGrowthMeasurementsButKeepsMeasurementAtAsOfTime() {
        val asOf = 1_750_000_000_000L
        val points = growthMetricPoints(
            baby(birthTime = 1_800_000_000_000L),
            listOf(
                event("GROWTH", 1_740_000_000_000L, """{"weightGrams":5100}"""),
                event("GROWTH", asOf, """{"weightGrams":5200}"""),
                event("GROWTH", 1_760_000_000_000L, """{"weightGrams":5300}""")
            ),
            "weightGrams",
            asOfMillis = asOf
        )

        assertEquals(2, points.size)
        assertEquals(5.1f, points[0].second, 0.001f)
        assertEquals(5.2f, points[1].second, 0.001f)
        assertEquals(asOf, points.last().first)
    }

    @Test
    fun includesBirthLengthAndHeadCircumferenceInCentimeters() {
        assertEquals(50.0f, growthMetricPoints(baby(), emptyList(), "lengthCm").single().second, 0.001f)
        assertEquals(34.0f, growthMetricPoints(baby(), emptyList(), "headCircumferenceCm").single().second, 0.001f)
    }
}

    @Test
    fun growthChartCentersSingleMeasurementAndSameTimestampPoints() {
        assertEquals(0.5f, growthChartTimeFraction(1_700L, 1_700L, 1_700L, 1), 0.0001f)
        assertEquals(0.5f, growthChartTimeFraction(1_700L, 1_700L, 1_700L, 2), 0.0001f)
    }

    @Test
    fun growthChartMapsChronologicalMeasurementsAcrossFullWidth() {
        assertEquals(0f, growthChartTimeFraction(1_000L, 1_000L, 3_000L, 3), 0.0001f)
        assertEquals(0.5f, growthChartTimeFraction(2_000L, 1_000L, 3_000L, 3), 0.0001f)
        assertEquals(1f, growthChartTimeFraction(3_000L, 1_000L, 3_000L, 3), 0.0001f)
    }
