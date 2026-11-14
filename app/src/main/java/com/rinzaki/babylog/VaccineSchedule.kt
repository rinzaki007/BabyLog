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
    val requires28DayInterval: Boolean = false,
    val category: String = when {
        vaccineName.contains("流脑") -> "流脑系列"
        vaccineName.contains("五联") || vaccineName.contains("四联") || vaccineName.contains("AC-Hib") || vaccineName.contains("甲乙肝联合") -> "联合疫苗"
        isSelfPaid -> "自费常见"
        else -> "国家免规"
    }
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

internal data class VaccineCatalogEntry(
    val category: String,
    val name: String,
    val description: String
)

// 这些产品不使用统一月龄倒计时：接种年龄、剂次及能否替代免规剂次均取决于具体产品说明书和既往接种史。
internal fun chinaOptionalVaccineCatalog(): List<VaccineCatalogEntry> = listOf(
    VaccineCatalogEntry("流脑系列", "A+C 群流脑结合疫苗", "可作为部分流脑程序的替代选择；是否抵扣国家免规剂次需按年龄、接种日期和当地接种门诊规则核对。"),
    VaccineCatalogEntry("流脑系列", "ACYW135 四价流脑结合疫苗", "不同产品适用月龄和剂次差异较大，不统一生成倒计时。"),
    VaccineCatalogEntry("流脑系列", "ACYW135 四价流脑多糖疫苗", "通常用于符合年龄条件的人群；是否适用需结合产品说明书和风险评估。"),
    VaccineCatalogEntry("联合疫苗", "DTaP-Hib 四联疫苗", "含百白破和 Hib 成分；记录可用于对应成分核对，具体剂次仍需门诊确认。"),
    VaccineCatalogEntry("联合疫苗", "AC-Hib 联合疫苗", "含 A、C 群流脑和 Hib 成分；不能仅凭名称自动判定所有剂次已完成。"),
    VaccineCatalogEntry("联合疫苗", "甲乙肝联合疫苗", "可选产品，接种程序依产品说明书及既往甲肝、乙肝接种情况确定。"),
    VaccineCatalogEntry("特殊风险", "狂犬病疫苗", "仅在动物暴露等明确情形下按暴露后处置规范及时就医，不作为常规月龄提醒。"),
    VaccineCatalogEntry("特殊风险", "戊肝、伤寒、霍乱等疫苗", "按旅行、暴露风险或医生评估选择，不纳入普通婴幼儿常规倒计时。"),
    VaccineCatalogEntry("特殊风险", "RSV 单抗预防", "属于单克隆抗体预防措施，不是传统疫苗；具体适用人群和时机由医生评估。")
)

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
    fun isPentavalent(name: String) =
        name.contains("五联") || name.contains("dtapipvhib") || name.contains("pentaxim") || name.contains("潘太欣")
    fun isQuadrivalent(name: String) =
        name.contains("四联") || name.contains("dtaphib")
    fun isAcHib(name: String) =
        name.contains("ac hib") || name.contains("achib") || name.contains("ac-hib")
    val matches = recordedNamesAndDoses.filter { (rawName, _) ->
        val name = normalize(rawName)
        val pentavalent = isPentavalent(name)
        val quadrivalent = isQuadrivalent(name)
        val acHib = isAcHib(name)
        when {
            item.vaccineName.contains("五联") -> pentavalent
            item.vaccineName.startsWith("Hib") ->
                name.contains("hib") || name.contains("流感嗜血杆菌") || pentavalent || quadrivalent || acHib
            item.vaccineName.contains("百白破") ->
                name.contains("百白破") || name.contains("dtap") || pentavalent || quadrivalent
            item.vaccineName.contains("脊灰灭活") ->
                name.contains("脊灰灭活") || name.contains("ipv") || pentavalent
            item.vaccineName.contains("A 群 C 群流脑") ->
                name.contains("a+c") || (name.contains("a群") && name.contains("c群")) || acHib
            item.vaccineName.contains("A 群流脑") ->
                name.contains("a群") && !name.contains("c群")
            item.group == "乙脑" -> name.contains("乙脑") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")
            item.group == "甲肝" -> name.contains("甲肝") && name.contains(if (item.vaccineName.contains("减毒活")) "减毒活" else "灭活")
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
