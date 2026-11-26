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
    fun excludesOutOfRangeImportedAndBirthMeasurementsFromGrowthCharts() {
        val points = growthMetricPoints(
            baby(weight = 90_000, length = 150.0, head = 75.0),
            listOf(
                event("GROWTH", 1_710_000_000_000L, """{"weightGrams":90000}"""),
                event("GROWTH", 1_720_000_000_000L, """{"weightGrams":5200}""")
            ),
            "weightGrams"
        )

        assertEquals(1, points.size)
        assertEquals(5.2f, points.single().second, 0.001f)
    }

    @Test
    fun appliesMetricSpecificBoundsToLengthAndHeadCircumference() {
        val lengthPoints = growthMetricPoints(
            baby(weight = null, length = null, head = null),
            listOf(
                event("GROWTH", 1_710_000_000_000L, """{"lengthCm":19.9}"""),
                event("GROWTH", 1_720_000_000_000L, """{"lengthCm":120.1}"""),
                event("GROWTH", 1_730_000_000_000L, """{"lengthCm":62.5}""")
            ),
            "lengthCm"
        )
        val headPoints = growthMetricPoints(
            baby(weight = null, length = null, head = null),
            listOf(
                event("GROWTH", 1_710_000_000_000L, """{"headCircumferenceCm":19.9}"""),
                event("GROWTH", 1_720_000_000_000L, """{"headCircumferenceCm":70.1}"""),
                event("GROWTH", 1_730_000_000_000L, """{"headCircumferenceCm":34.5}""")
            ),
            "headCircumferenceCm"
        )

        assertEquals(listOf(62.5f), lengthPoints.map { it.second })
        assertEquals(listOf(34.5f), headPoints.map { it.second })
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
    fun excludesGrowthMeasurementsDatedBeforeBirth() {
        val birthTime = 1_700_000_000_000L
        val points = growthMetricPoints(
            baby(weight = null, length = null, head = null, birthTime = birthTime),
            listOf(
                event("GROWTH", birthTime - 1L, """{"weightGrams":2900}"""),
                event("GROWTH", birthTime, """{"weightGrams":3200}"""),
                event("GROWTH", birthTime + 86_400_000L, """{"weightGrams":3400}""")
            ),
            "weightGrams"
        )

        assertEquals(2, points.size)
        assertEquals(listOf(3.2f, 3.4f), points.map { it.second })
        assertTrue(points.all { it.first >= birthTime })
    }

    @Test
    fun excludesFutureBirthAndGrowthMeasurementsButKeepsMeasurementAtAsOfTime() {
        val asOf = 1_750_000_000_000L
        val points = growthMetricPoints(
            // Both retained observations must occur after birth; only the final
            // observation is excluded because it is later than the as-of timestamp.
            baby(weight = null, birthTime = 1_730_000_000_000L),
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
    fun sameTimestampGrowthMeasurementsUseStableEventIdOrdering() {
        val timestamp = 1_710_000_000_000L
        val events = listOf(
            event("GROWTH", timestamp, """{"weightGrams":5200}""").copy(id = "event-b"),
            event("GROWTH", timestamp, """{"weightGrams":5100}""").copy(id = "event-a")
        )

        val first = growthMetricPoints(
            baby(weight = null, length = null, head = null),
            events,
            "weightGrams"
        )
        val reversed = growthMetricPoints(
            baby(weight = null, length = null, head = null),
            events.reversed(),
            "weightGrams"
        )

        assertEquals(listOf(5.1f, 5.2f), first.map { it.second })
        assertEquals(first, reversed)
    }

    @Test
    fun selectedGrowthMeasurementComparesWithPreviousActualObservation() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val points = listOf(
            java.time.LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() to 3.2f,
            java.time.LocalDate.of(2026, 1, 11).atStartOfDay(zone).toInstant().toEpochMilli() to 3.6f,
            java.time.LocalDate.of(2026, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli() to 4.1f
        )
        assertEquals(null, growthMeasurementComparison(points, 0, zone))
        val firstComparison = growthMeasurementComparison(points, 1, zone)
        assertEquals(10L, firstComparison?.elapsedDays)
        assertEquals(0.4f, firstComparison?.change ?: Float.NaN, 0.001f)
        val secondComparison = growthMeasurementComparison(points, 2, zone)
        assertEquals(21L, secondComparison?.elapsedDays)
        assertEquals(0.5f, secondComparison?.change ?: Float.NaN, 0.001f)
        assertEquals(null, growthMeasurementComparison(points, 3, zone))
    }

    @Test
    fun selectedGrowthMeasurementHandlesSameDayAndRejectsNonFiniteValues() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val timestamp = java.time.LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(
            GrowthMeasurementComparison(0, 0.5f),
            growthMeasurementComparison(listOf(timestamp to 3f, timestamp + 60_000L to 3.5f), 1, zone)
        )
        assertEquals(
            null,
            growthMeasurementComparison(listOf(timestamp to Float.NaN, timestamp + 1_000L to 3f), 1, zone)
        )
    }

    @Test
    fun includesBirthLengthAndHeadCircumferenceInCentimeters() {
        assertEquals(50.0f, growthMetricPoints(baby(), emptyList(), "lengthCm").single().second, 0.001f)
        assertEquals(34.0f, growthMetricPoints(baby(), emptyList(), "headCircumferenceCm").single().second, 0.001f)
    }
    @Test
    fun growthMetricPointsDropsValuesThatOverflowFloatRepresentation() {
        val points = growthMetricPoints(
            baby(weight = null, length = 1.0e100, head = null),
            listOf(
                event("GROWTH", 1_710_000_000_000L, """{"lengthCm":1.0e100}"""),
                event("GROWTH", 1_720_000_000_000L, """{"lengthCm":62.5}""")
            ),
            "lengthCm"
        )

        assertEquals(1, points.size)
        assertEquals(62.5f, points.single().second, 0.001f)
    }

    @Test
    fun growthChartTapSkipsNonFinitePointsAndRejectsNonFiniteCoordinates() {
        val points = listOf(1_000L to Float.NaN, 2_000L to 5f)
        assertEquals(
            1,
            growthChartNearestPointIndex(
                points, tapX = 100f, tapY = 25f,
                left = 0f, right = 100f, top = 0f, bottom = 100f,
                minValue = 2f, maxValue = 6f
            )
        )
        assertEquals(
            null,
            growthChartNearestPointIndex(
                points, tapX = Float.NaN, tapY = 25f,
                left = 0f, right = 100f, top = 0f, bottom = 100f,
                minValue = 2f, maxValue = 6f
            )
        )
    }

    @Test
    fun growthChartCentersSingleMeasurementAndSameTimestampPoints() {
        assertEquals(0.5f, growthChartTimeFraction(1_700L, 1_700L, 1_700L, 1), 0.0001f)
        assertEquals(0.5f, growthChartTimeFraction(1_700L, 1_700L, 1_700L, 2), 0.0001f)
    }

    @Test
    fun growthChartTapUsesVerticalDistanceWhenMeasurementsShareTimestamp() {
        val points = listOf(1_000L to 3f, 1_000L to 5f, 2_000L to 4f)
        assertEquals(
            1,
            growthChartNearestPointIndex(
                points, tapX = 10f, tapY = 25f,
                left = 0f, right = 100f, top = 0f, bottom = 100f,
                minValue = 2f, maxValue = 6f
            )
        )
    }

    @Test
    fun growthChartTapIgnoresTouchesOutsideMeasurementHitTarget() {
        val points = listOf(1_000L to 3f, 2_000L to 5f)
        assertEquals(
            1,
            growthChartNearestPointIndex(
                points, tapX = 100f, tapY = 25f,
                left = 0f, right = 100f, top = 0f, bottom = 100f,
                minValue = 2f, maxValue = 6f, maxDistancePx = 12f
            )
        )
        assertEquals(
            null,
            growthChartNearestPointIndex(
                points, tapX = 10f, tapY = 10f,
                left = 0f, right = 100f, top = 0f, bottom = 100f,
                minValue = 2f, maxValue = 6f, maxDistancePx = 12f
            )
        )
    }

    @Test
    fun growthChartTapReturnsNullForEmptyOrInvalidPlotBounds() {
        assertEquals(null, growthChartNearestPointIndex(
            emptyList(), 0f, 0f, 0f, 100f, 0f, 100f, 0f, 10f
        ))
        assertEquals(null, growthChartNearestPointIndex(
            listOf(1L to 3f), 0f, 0f, 10f, 10f, 0f, 100f, 0f, 10f
        ))
    }

    @Test
    fun growthChartMapsChronologicalMeasurementsAcrossFullWidth() {
        assertEquals(0f, growthChartTimeFraction(1_000L, 1_000L, 3_000L, 3), 0.0001f)
        assertEquals(0.5f, growthChartTimeFraction(2_000L, 1_000L, 3_000L, 3), 0.0001f)
        assertEquals(1f, growthChartTimeFraction(3_000L, 1_000L, 3_000L, 3), 0.0001f)
    }
    @Test
    fun growthChartRejectsFiniteBoundsWhoseSpanOverflowsFloat() {
        assertEquals(
            null,
            growthChartNearestPointIndex(
                listOf(1_000L to 0f, 2_000L to 1f),
                tapX = 50f,
                tapY = 50f,
                left = 0f,
                right = 100f,
                top = 0f,
                bottom = 100f,
                minValue = -Float.MAX_VALUE,
                maxValue = Float.MAX_VALUE
            )
        )
    }

    @Test
    fun growthChartHitDistanceDoesNotOverflowForExtremeFiniteTapCoordinates() {
        assertEquals(
            null,
            growthChartNearestPointIndex(
                listOf(1_000L to 3f, 2_000L to 5f),
                tapX = Float.MAX_VALUE,
                tapY = Float.MAX_VALUE,
                left = 0f,
                right = 100f,
                top = 0f,
                bottom = 100f,
                minValue = 2f,
                maxValue = 6f,
                maxDistancePx = Float.MAX_VALUE
            )
        )
    }

}
