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


    @Test
    fun selfPaidPlanIncludesPentavalentVaccineDoses() {
        val birthDate = LocalDate.of(2026, 1, 15)
        val doses = chinaSelfPaidVaccineSchedule(birthDate)
            .filter { it.first.vaccineName.contains("五联") }
        assertEquals(listOf("第 1 剂", "第 2 剂", "第 3 剂", "加强剂"), doses.map { it.first.dose })
        assertEquals(
            listOf(2L, 3L, 4L, 18L),
            doses.map { java.time.temporal.ChronoUnit.MONTHS.between(birthDate, it.second) }
        )
        assertTrue(doses.all { it.first.isSelfPaid })
    }

    @Test
    fun pentavalentVaccineRecordMatchesCommonNamesAndBrandAlias() {
        val item = VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "第 1 剂", 2, isSelfPaid = true)
        assertTrue(vaccineDoseIsRecorded(item, listOf("五联疫苗" to "第1剂")))
        assertTrue(vaccineDoseIsRecorded(item, listOf("DTaP-IPV-Hib" to "第一针")))
        assertTrue(vaccineDoseIsRecorded(item, listOf("潘太欣" to "第1剂")))

        val hibItem = VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true)
        assertTrue(!vaccineDoseIsRecorded(hibItem, listOf("五联疫苗" to "第1剂")))
    }

    @Test
    fun combinationVaccinesDoNotAutomaticallyCompleteOtherProducts() {
        val pentavalent = listOf("五联疫苗" to "第1剂")
        assertTrue(vaccineDoseIsRecorded(VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "第 1 剂", 2, isSelfPaid = true), pentavalent))
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("百白破疫苗", "第 1 剂", 2), pentavalent))
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("脊灰灭活疫苗", "第 1 剂", 2), pentavalent))
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true), pentavalent))

        val quadrivalent = listOf("DTaP-Hib 四联疫苗" to "第1剂")
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("百白破疫苗", "第 1 剂", 2), quadrivalent))
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true), quadrivalent))
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("脊灰灭活疫苗", "第 1 剂", 2), quadrivalent))

        val acHib = listOf("AC-Hib 联合疫苗" to "第1剂")
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true), acHib))
        assertTrue(!vaccineDoseIsRecorded(VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 1 剂", 36), acHib))
    }

    @Test
    fun twentyThreeValentPneumococcalIsNotInRoutineCountdown() {
        val plan = chinaSelfPaidVaccineSchedule(LocalDate.of(2026, 1, 15))
        assertTrue(plan.none { it.first.vaccineName.contains("23 价") })
        assertTrue(chinaOptionalVaccineCatalog().any { it.name.contains("23 价") && it.category == "肺炎球菌系列" })
    }

    @Test
    fun vaccineCategoriesAndOptionalCatalogAreAvailable() {
        assertEquals("国家免规", VaccineScheduleItem("乙肝疫苗", "第 1 剂", 0).category)
        assertEquals("自费常见", VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true).category)
        assertEquals("联合疫苗", VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "第 1 剂", 2, isSelfPaid = true).category)
        assertTrue(chinaOptionalVaccineCatalog().any { it.name.contains("ACYW135") })
        assertTrue(chinaOptionalVaccineCatalog().any { it.category == "特殊风险" })
    }


@Test
    fun compositeVaccineComponentsRequireClinicReviewAndAreNotAutomaticallyCredited() {
        val dtap = VaccineScheduleItem("百白破疫苗", "第 1 剂", 2)
        val ipv = VaccineScheduleItem("脊灰灭活疫苗", "第 1 剂", 2)
        val hib = VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true)

        val pentavalent = listOf("五联疫苗" to "第1剂")
        assertTrue(!vaccineDoseIsRecorded(dtap, pentavalent))
        assertTrue(!vaccineDoseIsRecorded(ipv, pentavalent))
        assertTrue(!vaccineDoseIsRecorded(hib, pentavalent))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(dtap, pentavalent))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(ipv, pentavalent))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(hib, pentavalent))
        assertEquals(
            VaccineDoseStatus.PENDING,
            vaccineDoseStatus(VaccineScheduleItem("百白破疫苗", "第 2 剂", 4), pentavalent)
        )

        val quadrivalent = listOf("DTaP-Hib 四联疫苗" to "第1剂")
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(dtap, quadrivalent))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(hib, quadrivalent))
        assertEquals(VaccineDoseStatus.PENDING, vaccineDoseStatus(ipv, quadrivalent))

        val acHib = listOf("AC-Hib 联合疫苗" to "第1剂")
        val aGroup = VaccineScheduleItem("A 群流脑多糖疫苗", "第 1 剂", 6)
        val acGroup = VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 1 剂", 36)
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(hib, acHib))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(aGroup, acHib))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(acGroup, acHib))
        assertTrue(!vaccineDoseIsRecorded(aGroup, acHib))
        assertTrue(!vaccineDoseIsRecorded(acGroup, acHib))
    }

    @Test
    fun meningococcalConjugateAndQuadrivalentRecordsRequireReviewNotAutomaticDeduction() {
        val aGroup = VaccineScheduleItem("A 群流脑多糖疫苗", "第 1 剂", 6)
        val acGroup = VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 1 剂", 36)

        val acConjugate = listOf("A+C 群流脑结合疫苗" to "第1剂")
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(aGroup, acConjugate))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(acGroup, acConjugate))
        assertTrue(!vaccineDoseIsRecorded(aGroup, acConjugate))
        assertTrue(!vaccineDoseIsRecorded(acGroup, acConjugate))

        val acyw = listOf("ACYW135 四价流脑多糖疫苗" to "第1剂")
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(aGroup, acyw))
        assertEquals(VaccineDoseStatus.REVIEW_REQUIRED, vaccineDoseStatus(acGroup, acyw))
        assertTrue(!vaccineDoseIsRecorded(aGroup, acyw))
        assertTrue(!vaccineDoseIsRecorded(acGroup, acyw))
    }

    @Test
    fun explicitSingleComponentRecordsRemainNormallyCompleted() {
        val dtap = VaccineScheduleItem("百白破疫苗", "第 1 剂", 2)
        val hib = VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, isSelfPaid = true)
        assertEquals(VaccineDoseStatus.RECORDED, vaccineDoseStatus(dtap, listOf("百白破疫苗" to "第1剂")))
        assertEquals(VaccineDoseStatus.RECORDED, vaccineDoseStatus(hib, listOf("Hib 疫苗" to "第1剂")))
        assertEquals(VaccineDoseStatus.PENDING, vaccineDoseStatus(dtap, emptyList()))
    }

    @Test
    fun missingDoseOnCompositeVaccineRequiresClinicReviewForBoosterRows() {
        val hibBooster = VaccineScheduleItem("Hib 疫苗", "加强剂", 18, isSelfPaid = true)
        val pentavalentWithoutDose = listOf("五联疫苗" to "")
        assertEquals(
            VaccineDoseStatus.REVIEW_REQUIRED,
            vaccineDoseStatus(hibBooster, pentavalentWithoutDose)
        )
        assertTrue(!vaccineDoseIsRecorded(hibBooster, pentavalentWithoutDose))
    }
}