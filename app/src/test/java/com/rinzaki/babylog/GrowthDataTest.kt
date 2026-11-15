package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthDataTest {
    private fun baby(
        weight: Int? = 3200,
        length: Double? = 50.0,
        head: Double? = 34.0
    ) = BabyEntity(
        id = "baby-1",
        familyId = "family-1",
        displayName = "宝宝",
        birthTime = 1_700_000_000_000L,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        birthWeightGrams = weight,
        birthLengthCm = length,
        birthHeadCircumferenceCm = head
    )

    private fun event(type: String, time: Long, payload: String) = BabyEventEntity(
        id = "event-$time",
        babyId = "baby-1",
        eventType = type,
        occurredAt = time,
        payloadJson = payload,
        createdBy = null,
        updatedAt = time
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
    fun includesBirthLengthAndHeadCircumferenceInCentimeters() {
        assertEquals(50.0f, growthMetricPoints(baby(), emptyList(), "lengthCm").single().second, 0.001f)
        assertEquals(34.0f, growthMetricPoints(baby(), emptyList(), "headCircumferenceCm").single().second, 0.001f)
    }
}
