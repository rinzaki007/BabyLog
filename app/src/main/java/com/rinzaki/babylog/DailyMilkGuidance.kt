package com.rinzaki.babylog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Daily milk-intake reference checkpoints from the National Health Commission's
 * Infant and Young Child Nutrition and Feeding Assessment Service Guide (trial).
 *
 * Values are only published at the guide's assessment ages; do not interpolate
 * them into unsupported month-by-month targets.
 */
internal data class DailyMilkGuidance(
    val assessmentAgeMonths: Int,
    val minimumMl: Int,
    val maximumMl: Int,
    val isExactAssessmentAge: Boolean
)

private val nationalMilkGuidanceByMonth = linkedMapOf(
    1 to (600 to 700),
    3 to (800 to 900),
    6 to (800 to 1000),
    8 to (700 to 800),
    12 to (600 to 700),
    18 to (400 to 600),
    24 to (400 to 600)
)

internal fun dailyMilkGuidanceForDate(
    birthTime: Long,
    selectedDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): DailyMilkGuidance? {
    val birthDate = Instant.ofEpochMilli(birthTime).atZone(zoneId).toLocalDate()
    if (selectedDate.isBefore(birthDate)) return null

    val ageMonths = ChronoUnit.MONTHS.between(birthDate, selectedDate).toInt()
    val checkpoint = nationalMilkGuidanceByMonth.keys.firstOrNull { it >= ageMonths }
        ?: return null
    val range = nationalMilkGuidanceByMonth.getValue(checkpoint)
    return DailyMilkGuidance(
        assessmentAgeMonths = checkpoint,
        minimumMl = range.first,
        maximumMl = range.second,
        isExactAssessmentAge = ageMonths == checkpoint
    )
}
