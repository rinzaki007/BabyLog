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
    fun vaccineRecordMatchesNameAndDose() {
        val item = VaccineScheduleItem("乙肝疫苗", "第 1 剂", 0)
        assertTrue(vaccineDoseIsRecorded(item, listOf("重组乙肝疫苗" to "第1剂")))
    }
    @Test
    fun vaccineRecordAcceptsChineseDoseAndNeedleLabels() {
        val item = VaccineScheduleItem("乙肝疫苗", "第 1 剂", 0)
        assertTrue(vaccineDoseIsRecorded(item, listOf("乙肝疫苗", "第一针")))
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
