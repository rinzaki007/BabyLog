package com.rinzaki.babylog

import java.time.LocalDate

internal data class VaccineScheduleItem(
    val vaccineName: String,
    val dose: String,
    val ageMonths: Long,
    val note: String = "",
    val group: String = vaccineName
)

internal fun chinaNationalVaccineSchedule(birthDate: LocalDate, sex: String?): List<Pair<VaccineScheduleItem, LocalDate>> {
    // National Immunization Program schedule, 2026 edition (NHC/NDCPA).
    // The live/inactivated JE and HepA products have alternative schedules;
    // show both until a clinic-selected route is reflected in the records.
    val items = listOf(
        VaccineScheduleItem("乙肝疫苗", "第 1 剂", 0),
        VaccineScheduleItem("卡介苗", "第 1 剂", 0),
        VaccineScheduleItem("乙肝疫苗", "第 2 剂", 1),
        VaccineScheduleItem("脊灰灭活疫苗", "第 1 剂", 2),
        VaccineScheduleItem("百白破疫苗", "第 1 剂", 2),
        VaccineScheduleItem("脊灰灭活疫苗", "第 2 剂", 3),
        VaccineScheduleItem("百白破疫苗", "第 2 剂", 4),
        VaccineScheduleItem("脊灰减毒活疫苗", "第 1 剂", 4),
        VaccineScheduleItem("百白破疫苗", "第 3 剂", 6),
        VaccineScheduleItem("乙肝疫苗", "第 3 剂", 6),
        VaccineScheduleItem("A 群流脑多糖疫苗", "第 1 剂", 6),
        VaccineScheduleItem("麻腮风疫苗", "第 1 剂", 8),
        VaccineScheduleItem("乙脑减毒活疫苗", "第 1 剂", 8, group = "乙脑"),
        VaccineScheduleItem("乙脑灭活疫苗", "第 1 剂", 8, "与第 2 剂间隔 7—10 天", "乙脑"),
        VaccineScheduleItem("乙脑灭活疫苗", "第 2 剂", 8, "第 1 剂后 7—10 天", "乙脑"),
        VaccineScheduleItem("A 群流脑多糖疫苗", "第 2 剂", 9),
        VaccineScheduleItem("百白破疫苗", "第 4 剂", 18),
        VaccineScheduleItem("麻腮风疫苗", "第 2 剂", 18),
        VaccineScheduleItem("甲肝减毒活疫苗", "第 1 剂", 18, group = "甲肝"),
        VaccineScheduleItem("甲肝灭活疫苗", "第 1 剂", 18, group = "甲肝"),
        VaccineScheduleItem("甲肝灭活疫苗", "第 2 剂", 24, "与第 1 剂间隔不少于 6 个月", "甲肝"),
        VaccineScheduleItem("乙脑减毒活疫苗", "第 2 剂", 24, group = "乙脑"),
        VaccineScheduleItem("乙脑灭活疫苗", "第 3 剂", 24, group = "乙脑"),
        VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 1 剂", 36),
        VaccineScheduleItem("脊灰减毒活疫苗", "第 2 剂", 48),
        VaccineScheduleItem("百白破疫苗", "第 5 剂", 72),
        VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 2 剂", 72),
        VaccineScheduleItem("乙脑灭活疫苗", "第 4 剂", 72, group = "乙脑")
    ) + if (sex == "女") listOf(
        VaccineScheduleItem("双价 HPV 疫苗", "第 1 剂", 156, "国家免疫规划适龄女孩程序"),
        VaccineScheduleItem("双价 HPV 疫苗", "第 2 剂", 168, "第 1 剂后 12 个月内")
    ) else emptyList()

    return items.map { it to birthDate.plusMonths(it.ageMonths) }
}

private fun itemsForRoute(
    group: String,
    liveName: String,
    inactivatedName: String,
    birthDate: LocalDate,
    records: List<String>
): List<String> = listOf(group, liveName, inactivatedName, birthDate.toString()) + records

internal fun vaccineDoseIsRecorded(item: VaccineScheduleItem, recordedNamesAndDoses: List<Pair<String, String>>): Boolean {
    return recordedNamesAndDoses.any { (rawName, rawDose) ->
        val name = rawName.replace(" ", "").replace("（", "(").replace("）", ")")
        val target = item.vaccineName.replace(" ", "")
        val nameMatches = name.contains(target) || target.contains(name) ||
            (item.group == "乙脑" && name.contains("乙脑") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")) ||
            (item.group == "甲肝" && name.contains("甲肝") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活"))
        val doseNumber = Regex("\\d+").find(item.dose)?.value
        val recordedDoseNumber = Regex("\\d+").find(rawDose)?.value
        nameMatches && doseNumber != null && recordedDoseNumber == doseNumber
    }
}
