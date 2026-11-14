package com.rinzaki.babylog

import java.time.LocalDate

internal data class VaccineScheduleItem(
    val vaccineName: String,
    val dose: String,
    val ageMonths: Long,
    val note: String = "",
    val offsetDays: Long = 0,
    val group: String = vaccineName,
    val isSelfPaid: Boolean = false,
    val requires28DayInterval: Boolean = false
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
        VaccineScheduleItem("麻腮风疫苗", "第 1 剂", 8, requires28DayInterval = true),
        VaccineScheduleItem("乙脑减毒活疫苗", "第 1 剂", 8, group = "乙脑", requires28DayInterval = true),
        VaccineScheduleItem("乙脑灭活疫苗", "第 1 剂", 8, "与第 2 剂间隔 7—10 天", group = "乙脑"),
        VaccineScheduleItem("乙脑灭活疫苗", "第 2 剂", 8, "第 1 剂后 7—10 天", 7, "乙脑"),
        VaccineScheduleItem("A 群流脑多糖疫苗", "第 2 剂", 9),
        VaccineScheduleItem("百白破疫苗", "第 4 剂", 18),
        VaccineScheduleItem("麻腮风疫苗", "第 2 剂", 18, requires28DayInterval = true),
        VaccineScheduleItem("甲肝减毒活疫苗", "第 1 剂", 18, group = "甲肝", requires28DayInterval = true),
        VaccineScheduleItem("甲肝灭活疫苗", "第 1 剂", 18, group = "甲肝"),
        VaccineScheduleItem("甲肝灭活疫苗", "第 2 剂", 24, "与第 1 剂间隔不少于 6 个月", group = "甲肝"),
        VaccineScheduleItem("乙脑减毒活疫苗", "第 2 剂", 24, group = "乙脑", requires28DayInterval = true),
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

internal fun chinaSelfPaidVaccineSchedule(birthDate: LocalDate): List<Pair<VaccineScheduleItem, LocalDate>> {
    // Common non-National-Immunization-Program vaccines in mainland China.
    // Product-specific dose counts and age windows vary; these are planning reminders, not prescriptions.
    val items = listOf(
        // 五联疫苗（DTaP-IPV-Hib）：常见程序为 2、3、4 月龄基础免疫及 18 月龄加强；
        // 不同产品/既往接种程序可能不同，最终以具体产品说明书和接种门诊安排为准。
        VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "第 1 剂", 2, "自费；常见程序为 2、3、4 月龄基础免疫及 18 月龄加强", isSelfPaid = true),
        VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "第 2 剂", 3, "与前剂间隔及适用月龄以产品说明书为准", isSelfPaid = true),
        VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "第 3 剂", 4, "完成基础免疫；具体程序以产品说明书为准", isSelfPaid = true),
        VaccineScheduleItem("五联疫苗（DTaP-IPV-Hib）", "加强剂", 18, "常见加强节点；是否适用及时间以产品说明书和门诊安排为准", isSelfPaid = true),
        VaccineScheduleItem("Hib 疫苗", "第 1 剂", 2, "自费；常见程序为 2、3、4 月龄及 18 月龄加强", isSelfPaid = true),
        VaccineScheduleItem("13 价肺炎球菌结合疫苗", "第 1 剂", 2, "自费；常见程序为 2、4、6 月龄及 12–15 月龄加强", isSelfPaid = true),
        VaccineScheduleItem("口服轮状病毒疫苗", "第 1 剂", 2, "自费；不同价型和厂家剂次数不同，以说明书为准", isSelfPaid = true),
        VaccineScheduleItem("Hib 疫苗", "第 2 剂", 3, isSelfPaid = true),
        VaccineScheduleItem("13 价肺炎球菌结合疫苗", "第 2 剂", 4, isSelfPaid = true),
        VaccineScheduleItem("口服轮状病毒疫苗", "第 2 剂", 3, "与前剂间隔按产品说明书执行", isSelfPaid = true),
        VaccineScheduleItem("口服轮状病毒疫苗", "第 3 剂", 4, "部分产品为 3 剂程序，按产品说明书执行", isSelfPaid = true),
        VaccineScheduleItem("Hib 疫苗", "第 3 剂", 4, isSelfPaid = true),
        VaccineScheduleItem("13 价肺炎球菌结合疫苗", "第 3 剂", 6, isSelfPaid = true),
        VaccineScheduleItem("EV71 手足口病疫苗", "第 1 剂", 6, "自费；适龄儿童通常接种 2 剂，间隔不少于 1 个月", isSelfPaid = true),
        VaccineScheduleItem("流感疫苗", "年度接种", 6, "满 6 月龄后按流行季每年接种；首次接种儿童可能需 2 剂", isSelfPaid = true),
        VaccineScheduleItem("EV71 手足口病疫苗", "第 2 剂", 7, "与第 1 剂间隔不少于 1 个月", isSelfPaid = true),
        VaccineScheduleItem("水痘疫苗", "第 1 剂", 12, "自费；第 2 剂时间以当地程序和产品说明为准", isSelfPaid = true, requires28DayInterval = true),
        VaccineScheduleItem("Hib 疫苗", "加强剂", 18, "是否需要加强取决于前期接种程序", isSelfPaid = true),
        VaccineScheduleItem("13 价肺炎球菌结合疫苗", "加强剂", 12, "常见加强窗口为 12–15 月龄", isSelfPaid = true),
        VaccineScheduleItem("水痘疫苗", "第 2 剂", 48, "常见加强节点；具体以当地接种门诊安排为准", isSelfPaid = true, requires28DayInterval = true),
        VaccineScheduleItem("23 价肺炎球菌多糖疫苗", "评估接种", 24, "仅适用于符合条件的高风险儿童，需医生评估", isSelfPaid = true)
    )
    return items.map { it to birthDate.plusMonths(it.ageMonths).plusDays(it.offsetDays) }
}

internal fun liveVaccineScheduleConflicts(items: List<Pair<VaccineScheduleItem, LocalDate>>): List<String> {
    val live = items.filter { it.first.requires28DayInterval }
    val warnings = mutableListOf<String>()
    for (i in live.indices) {
        for (j in i + 1 until live.size) {
            val first = live[i]
            val second = live[j]
            val days = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(first.second, second.second))
            if (days in 1..27) {
                warnings += "${first.first.vaccineName}（${first.first.dose}）与${second.first.vaccineName}（${second.first.dose}）计划相隔 ${days} 天"
            }
        }
    }
    return warnings.distinct()
}

internal fun recordedLiveVaccineConflicts(records: List<Pair<String, LocalDate>>): List<String> {
    fun restricted(name: String): Boolean {
        val value = name.replace(" ", "")
        return value.contains("麻腮风") ||
            (value.contains("乙脑") && value.contains("减毒活")) ||
            (value.contains("甲肝") && value.contains("减毒活")) ||
            value.contains("水痘")
    }
    val live = records.filter { restricted(it.first) }
    val warnings = mutableListOf<String>()
    for (i in live.indices) {
        for (j in i + 1 until live.size) {
            val first = live[i]
            val second = live[j]
            if (first.first == second.first) continue
            val days = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(first.second, second.second))
            if (days in 1..27) warnings += "${first.first} 与${second.first}实际接种相隔 ${days} 天"
        }
    }
    return warnings.distinct()
}

internal fun vaccineDoseIsRecorded(item: VaccineScheduleItem, recordedNamesAndDoses: List<Pair<String, String>>): Boolean {
    fun normalize(value: String) = value.lowercase()
        .replace(" ", "").replace("（", "(").replace("）", ")")
        .replace("-", "").replace("_", "").replace("/", "")
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
        when {
            item.vaccineName.contains("五联") -> name.contains("五联") || name.contains("dtapipvhib") || name.contains("pentaxim") || name.contains("潘太欣")
            item.group == "乙脑" -> name.contains("乙脑") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")
            item.group == "甲肝" -> name.contains("甲肝") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")
            item.vaccineName.startsWith("Hib") -> name.contains("hib") || name.contains("流感嗜血杆菌")
            item.vaccineName.contains("肺炎球菌") -> name.contains("肺炎球菌") && (Regex("\\d+").find(item.vaccineName)?.value?.let { name.contains(it) } ?: true)
            item.vaccineName.contains("轮状病毒") -> name.contains("轮状病毒")
            item.vaccineName.contains("EV71") -> name.contains("ev71") || name.contains("手足口") || name.contains("肠道病毒71")
            item.vaccineName.contains("水痘") -> name.contains("水痘")
            item.vaccineName.contains("流感") -> name.contains("流感")
            else -> name.contains(target) || target.contains(name)
        }
    }
    val expectedDose = doseNumber(item.dose) ?: return matches.isNotEmpty()
    val explicitDoses = matches.mapNotNull { doseNumber(it.second) }.toSet()
    return expectedDose in explicitDoses || (explicitDoses.isEmpty() && matches.size >= expectedDose)
}
