package com.rinzaki.babylog

import java.time.LocalDate

internal data class VaccineScheduleItem(
    val vaccineName: String,
    val dose: String,
    val ageMonths: Long,
    val note: String = "",
    val offsetDays: Long = 0,
    val group: String = vaccineName
)

internal fun chinaNationalVaccineSchedule(birthDate: LocalDate, sex: String?): List<Pair<VaccineScheduleItem, LocalDate>> {
    // National Immunization Program schedule, 2026 edition (NHC/NDCPA).
    // The live/inactivated JE and HepA products have alternative schedules;
    // The UI selects the route from the first recorded product; otherwise it shows the live-vaccine reference route.
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
        VaccineScheduleItem("乙脑灭活疫苗", "第 1 剂", 8, "与第 2 剂间隔 7—10 天", group = "乙脑"),
        VaccineScheduleItem("乙脑灭活疫苗", "第 2 剂", 8, "第 1 剂后 7—10 天", 7, "乙脑"),
        VaccineScheduleItem("A 群流脑多糖疫苗", "第 2 剂", 9),
        VaccineScheduleItem("百白破疫苗", "第 4 剂", 18),
        VaccineScheduleItem("麻腮风疫苗", "第 2 剂", 18),
        VaccineScheduleItem("甲肝减毒活疫苗", "第 1 剂", 18, group = "甲肝"),
        VaccineScheduleItem("甲肝灭活疫苗", "第 1 剂", 18, group = "甲肝"),
        VaccineScheduleItem("甲肝灭活疫苗", "第 2 剂", 24, "与第 1 剂间隔不少于 6 个月", group = "甲肝"),
        VaccineScheduleItem("乙脑减毒活疫苗", "第 2 剂", 24, group = "乙脑"),
        VaccineScheduleItem("乙脑灭活疫苗", "第 3 剂", 24, group = "乙脑"),
        VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 1 剂", 36),
        VaccineScheduleItem("脊灰减毒活疫苗", "第 2 剂", 48),
        VaccineScheduleItem("百白破疫苗", "第 5 剂", 72),
        VaccineScheduleItem("A 群 C 群流脑多糖疫苗", "第 2 剂", 72),
        VaccineScheduleItem("乙脑灭活疫苗", "第 4 剂", 72, group = "乙脑")
    ) + if (sex == "女") listOf(
        VaccineScheduleItem("双价 HPV 疫苗", "第 1 剂", 156, "国家免疫规划适龄女孩程序"),
        VaccineScheduleItem("双价 HPV 疫苗", "第 2 剂", 168, "第 1 剂后 12 个月内完成；此日期为最迟提醒节点")
    ) else emptyList()

    return items.map { it to birthDate.plusMonths(it.ageMonths).plusDays(it.offsetDays) }
}

internal fun vaccineDoseIsRecorded(item: VaccineScheduleItem, recordedNamesAndDoses: List<Pair<String, String>>): Boolean {
    fun normalize(value: String) = value.lowercase()
        .replace(" ", "").replace("（", "(").replace("）", ")")
        .replace("疫苗", "").replace("接种", "")
    fun doseNumber(value: String): Int? {
        Regex("\\d+").find(value)?.value?.toIntOrNull()?.let { return it }
        val chinese = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val text = value.replace("第", "").replace("剂", "").replace("针", "").replace("次", "").trim()
        if (text == "十") return 10
        if (text.length == 2 && text[0] == '十') return 10 + (chinese[text[1]] ?: return null)
        if (text.length == 2 && text[1] == '十') return (chinese[text[0]] ?: return null) * 10
        return text.singleOrNull()?.let { chinese[it] }
    }
    val target = normalize(item.vaccineName)
    val matches = recordedNamesAndDoses.filter { (rawName, _) ->
        val name = normalize(rawName)
        when (item.group) {
            "乙脑" -> name.contains("乙脑") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")
            "甲肝" -> name.contains("甲肝") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")
            else -> name.contains(target) || target.contains(name)
        }
    }
    val expectedDose = doseNumber(item.dose) ?: return false
    return matches.any { (_, rawDose) -> doseNumber(rawDose) == expectedDose } ||
        matches.filter { doseNumber(it.second) == null }.size >= expectedDose
}
