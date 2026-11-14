package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VaccineScheduleTest {
    @Test
    fun nationalScheduleUsesBabyBirthDateForCountdownDates() {
        val birthDate = LocalDate.of(2026, 1, 15)
        val schedule = chinaNationalVaccineSchedule(birthDate, "男")

        val firstDtp = schedule.first { it.first.vaccineName == "百白破疫苗" && it.first.dose == "第 1 剂" }
        assertEquals(LocalDate.of(2026, 3, 15), firstDtp.second)

        val firstHepB = schedule.first { it.first.vaccineName == "乙肝疫苗" && it.first.dose == "第 1 剂" }
        assertEquals(birthDate, firstHepB.second)
    }

    @Test
    fun inactivatedEncephalitisSecondDoseHasSevenDayInterval() {
        val birthDate = LocalDate.of(2026, 1, 15)
        val schedule = chinaNationalVaccineSchedule(birthDate, "男")
        val first = schedule.first { it.first.vaccineName == "乙脑灭活疫苗" && it.first.dose == "第 1 剂" }
        val second = schedule.first { it.first.vaccineName == "乙脑灭活疫苗" && it.first.dose == "第 2 剂" }

        assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(first.second, second.second))
    }

    @Test
    fun selfPaidPlanIncludesCommonOptionalVaccines() {
        val plan = chinaSelfPaidVaccineSchedule(LocalDate.of(2026, 1, 15))
        assertTrue(plan.any { it.first.vaccineName.contains("肺炎球菌") && it.first.isSelfPaid })
        assertTrue(plan.any { it.first.vaccineName.contains("轮状病毒") && it.first.isSelfPaid })
        assertTrue(plan.any { it.first.vaccineName.contains("手足口") && it.first.isSelfPaid })
    }

    @Test
    fun liveVaccineIntervalsOfLessThan28DaysAreFlagged() {
        val first = VaccineScheduleItem("麻腮风疫苗", "第 1 剂", 8, requires28DayInterval = true)
        val second = VaccineScheduleItem("水痘疫苗", "第 1 剂", 9, requires28DayInterval = true)
        val warnings = liveVaccineScheduleConflicts(listOf(first to LocalDate.of(2026, 8, 1), second to LocalDate.of(2026, 8, 20)))
        assertEquals(1, warnings.size)
    }

    @Test
    fun recordedLiveVaccinesWithShortIntervalsAreFlagged() {
        val warnings = recordedLiveVaccineConflicts(listOf(
            "麻腮风疫苗" to LocalDate.of(2026, 8, 1),
            "水痘疫苗" to LocalDate.of(2026, 8, 20)
        ))
        assertEquals(1, warnings.size)
    }

    @Test
    fun sameDayLiveVaccinesAreNotFlaggedAsIntervalConflict() {
        val first = VaccineScheduleItem("麻腮风疫苗", "第 1 剂", 8, requires28DayInterval = true)
        val second = VaccineScheduleItem("水痘疫苗", "第 1 剂", 8, requires28DayInterval = true)
        assertTrue(liveVaccineScheduleConflicts(listOf(first to LocalDate.of(2026, 8, 1), second to LocalDate.of(2026, 8, 1))).isEmpty())
    }

    @Test
    fun vaccineRecordMatchesNameAndDose() {
        val item = VaccineScheduleItem("乙肝疫苗", "第 1 剂", 0)
        assertTrue(vaccineDoseIsRecorded(item, listOf("重组乙肝疫苗" to "第1剂")))
    }
    @Test
    fun vaccineRecordAcceptsChineseDoseAndNeedleLabels() {
        val item = VaccineScheduleItem("乙肝疫苗", "第 1 剂", 0)
        assertTrue(vaccineDoseIsRecorded(item, listOf("乙肝疫苗" to "第一针")))
    }

    @Test
    fun unnumberedRepeatedVaccineRecordsFillDosesInSequence() {
        val first = VaccineScheduleItem("百白破疫苗", "第 1 剂", 2)
        val second = VaccineScheduleItem("百白破疫苗", "第 2 剂", 4)
        val records = listOf("百白破疫苗" to "", "百白破疫苗" to "")
        assertTrue(vaccineDoseIsRecorded(first, records))
        assertTrue(vaccineDoseIsRecorded(second, records))
    }

}
