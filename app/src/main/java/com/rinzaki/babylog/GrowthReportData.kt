package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class GrowthReportMeasurement(
    val occurredAt: Long,
    val weightKg: Double?,
    val lengthCm: Double?,
    val headCircumferenceCm: Double?,
    val isBirthMeasurement: Boolean = false
)

internal data class GrowthReportEntry(
    val occurredAt: Long,
    val title: String,
    val detail: String
)

internal data class GrowthReportData(
    val babyName: String,
    val sex: String,
    val birthDate: String,
    val reportDate: String,
    val ageLabel: String,
    val eventCount: Int,
    val feedingCount: Int,
    val recordedMilkMl: Int,
    val breastfeedingMinutes: Int,
    val sleepMinutes: Int,
    val diaperCount: Int,
    val solidFoodCount: Int,
    val supplementCount: Int,
    val vaccineCount: Int,
    val growthMeasurements: List<GrowthReportMeasurement>,
    val milestones: List<GrowthReportEntry>,
    val foodExploration: List<String>,
    val recentRecords: List<GrowthReportEntry>
)

private val reportDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

internal fun buildGrowthReportData(
    baby: BabyEntity,
    events: List<BabyEventEntity>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): GrowthReportData {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val birthDate = Instant.ofEpochMilli(baby.birthTime).atZone(zone).toLocalDate()
    val months = growthCompletedMonths(birthDate, today)
    val ageLabel = when {
        months < 1 -> "${ChronoUnit.DAYS.between(birthDate, today).coerceAtLeast(0)} 天"
        months < 12 -> "${months} 个月"
        else -> "${months / 12} 岁${months % 12} 个月"
    }
    val active = events.asSequence()
        .filter { it.babyId == baby.id && it.deletedAt == null && it.occurredAt <= now }
        .sortedBy { it.occurredAt }
        .toList()
    val payloads = active.map { it to (runCatching { JSONObject(it.payloadJson) }.getOrElse { JSONObject() }) }
    fun records(type: String) = payloads.filter { it.first.eventType == type }

    val measurements = buildList {
        if (baby.birthWeightGrams != null || baby.birthLengthCm != null || baby.birthHeadCircumferenceCm != null) {
            add(
                GrowthReportMeasurement(
                    occurredAt = baby.birthTime,
                    weightKg = baby.birthWeightGrams?.takeIf { it > 0 }?.div(1000.0),
                    lengthCm = baby.birthLengthCm?.takeIf { it.isFinite() && it > 0.0 },
                    headCircumferenceCm = baby.birthHeadCircumferenceCm?.takeIf { it.isFinite() && it > 0.0 },
                    isBirthMeasurement = true
                )
            )
        }
        records("GROWTH").forEach { (event, payload) ->
            val weight = payload.optDouble("weightGrams", Double.NaN)
            val length = payload.optDouble("lengthCm", Double.NaN)
            val head = payload.optDouble("headCircumferenceCm", Double.NaN)
            val weightKg = weight.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
            val lengthCm = length.takeIf { it.isFinite() && it > 0.0 }
            val headCm = head.takeIf { it.isFinite() && it > 0.0 }
            if (weightKg != null || lengthCm != null || headCm != null) {
                add(GrowthReportMeasurement(event.occurredAt, weightKg, lengthCm, headCm))
            }
        }
    }.sortedBy { it.occurredAt }

    val milestones = records("MILESTONE").map { (event, payload) ->
        GrowthReportEntry(
            event.occurredAt,
            payload.optString("milestoneTitle").ifBlank { "成长里程碑" },
            payload.optString("category").ifBlank { "家庭记录" }
        )
    }.sortedByDescending { it.occurredAt }

    val foodExploration = solidFoodJourney(active)
        .map { entry ->
            "${entry.name} · ${entry.attempts} 次尝试 · ${entry.latestReaction}"
        }

    val recent = active.asSequence()
        .sortedByDescending { it.occurredAt }
        .take(20)
        .map { event ->
            val payload = runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() }
            val title: String
            val detail: String
            when (event.eventType) {
                "FEEDING" -> {
                    title = "喂养"
                    detail = feedingDescription(event.payloadJson)
                }
                "DIAPER" -> {
                    title = "日常护理"
                    detail = listOfNotNull(
                        if (payload.optBoolean("urine")) "小便" else null,
                        if (payload.optBoolean("stool")) "大便" else null,
                        payload.optString("stoolShape").takeIf { it.isNotBlank() },
                        payload.optString("stoolColor").takeIf { it.isNotBlank() }
                    ).joinToString(" · ").ifBlank { "护理记录" }
                }
                "SLEEP" -> {
                    title = "睡眠"
                    detail = "${payload.optInt("durationMinutes").coerceAtLeast(0)} 分钟"
                }
                "GROWTH" -> {
                    title = "成长测量"
                    detail = listOfNotNull(
                        payload.optDouble("weightGrams", Double.NaN).takeIf { it.isFinite() && it > 0 }?.let { "${"%.2f".format(Locale.CHINA, it / 1000.0)} kg" },
                        payload.optDouble("lengthCm", Double.NaN).takeIf { it.isFinite() && it > 0 }?.let { "${"%.1f".format(Locale.CHINA, it)} cm 身长" },
                        payload.optDouble("headCircumferenceCm", Double.NaN).takeIf { it.isFinite() && it > 0 }?.let { "${"%.1f".format(Locale.CHINA, it)} cm 头围" }
                    ).joinToString(" · ").ifBlank { "测量记录" }
                }
                "SOLID_FOOD" -> {
                    title = "辅食"
                    detail = solidFoodDescription(event.payloadJson)
                }
                "SUPPLEMENT" -> {
                    title = "营养补充"
                    detail = listOfNotNull(
                        payload.optString("supplementName").takeIf { it.isNotBlank() },
                        payload.optDouble("amount", Double.NaN).takeIf { it.isFinite() && it > 0 }?.let {
                            val amount = if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                            "$amount ${payload.optString("unit")}".trim()
                        }
                    ).joinToString(" · ").ifBlank { "补充记录" }
                }
                "MILESTONE" -> {
                    title = "成长里程碑"
                    detail = payload.optString("milestoneTitle").ifBlank { "成长记录" }
                }
                "VACCINE" -> {
                    title = "疫苗接种"
                    detail = listOfNotNull(
                        payload.optString("vaccineName").takeIf { it.isNotBlank() },
                        payload.optString("dose").takeIf { it.isNotBlank() },
                        payload.optString("clinic").takeIf { it.isNotBlank() }
                    ).joinToString(" · ").ifBlank { "接种记录" }
                }
                else -> {
                    title = "其他记录"
                    detail = event.eventType
                }
            }
            GrowthReportEntry(event.occurredAt, title, detail)
        }.toList()

    return GrowthReportData(
        babyName = baby.displayName.ifBlank { "宝宝" },
        sex = baby.sex?.takeIf { it.isNotBlank() } ?: "未记录",
        birthDate = birthDate.format(reportDateFormatter),
        reportDate = today.format(reportDateFormatter),
        ageLabel = ageLabel,
        eventCount = active.size,
        feedingCount = records("FEEDING").size,
        recordedMilkMl = records("FEEDING").sumOf { (_, payload) -> payload.optInt("amountMl").coerceAtLeast(0) },
        breastfeedingMinutes = records("FEEDING").sumOf { (_, payload) -> payload.optInt("breastMinutes").coerceAtLeast(0) },
        sleepMinutes = records("SLEEP").sumOf { (_, payload) -> payload.optInt("durationMinutes").coerceAtLeast(0) },
        diaperCount = records("DIAPER").size,
        solidFoodCount = records("SOLID_FOOD").size,
        supplementCount = records("SUPPLEMENT").size,
        vaccineCount = records("VACCINE").size,
        growthMeasurements = measurements,
        milestones = milestones,
        foodExploration = foodExploration,
        recentRecords = recent
    )
}
