package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyPhotoEntity
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class MonthlyAlbumRecap(
    val month: YearMonth,
    val photos: List<BabyPhotoEntity>,
    val growthEventCount: Int
)

internal fun monthlyAlbumRecaps(
    photos: List<BabyPhotoEntity>,
    growthEvents: List<BabyEventEntity>,
    zone: ZoneId = ZoneId.systemDefault()
): List<MonthlyAlbumRecap> {
    val activePhotos = photos.filter { it.deletedAt == null }
    val activeGrowthEvents = growthEvents.filter {
        it.deletedAt == null && it.eventType in setOf("GROWTH", "MILESTONE")
    }
    val photosByMonth = activePhotos.groupBy {
        Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate().let { YearMonth.from(it) }
    }
    val eventsByMonth = activeGrowthEvents.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().let(YearMonth::from)
    }
    return photosByMonth.map { (month, monthPhotos) ->
        MonthlyAlbumRecap(
            month = month,
            photos = monthPhotos.sortedByDescending { it.takenAt },
            growthEventCount = eventsByMonth[month].orEmpty().size
        )
    }.sortedByDescending { it.month }
}

internal fun monthlyAlbumRecapText(
    babyName: String,
    month: YearMonth,
    photos: List<BabyPhotoEntity>,
    growthEvents: List<BabyEventEntity>,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val monthPhotos = photos.filter {
        it.deletedAt == null &&
            Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate().let(YearMonth::from) == month
    }.sortedBy { it.takenAt }
    val monthEvents = growthEvents.filter {
        it.deletedAt == null &&
            it.eventType in setOf("GROWTH", "MILESTONE") &&
            Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().let(YearMonth::from) == month
    }.sortedBy { it.occurredAt }
    val dateFormat = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    return buildString {
        append(babyName.ifBlank { "宝宝" }).append(" · ")
            .append(month.year).append("年").append(month.monthValue).append("月成长回顾")
        append("\n\n本月收藏了 ").append(monthPhotos.size).append(" 张照片。")
        monthPhotos.mapNotNull { it.caption?.trim()?.takeIf(String::isNotEmpty) }
            .distinct().take(5).forEach { append("\n· ").append(it) }
        if (monthEvents.isNotEmpty()) {
            append("\n\n成长足迹（").append(monthEvents.size).append(" 条）")
            monthEvents.forEach { event ->
                val date = Instant.ofEpochMilli(event.occurredAt).atZone(zone).toLocalDate().format(dateFormat)
                val payload = runCatching { JSONObject(event.payloadJson) }.getOrDefault(JSONObject())
                val detail = when (event.eventType) {
                    "MILESTONE" -> payload.optString("milestoneTitle").ifBlank { "成长里程碑" }
                    else -> buildList {
                        payload.optInt("weightGrams").takeIf { it > 0 }?.let { add("${"%.2f".format(Locale.CHINA, it / 1000.0)} kg") }
                        payload.optDouble("lengthCm").takeIf { it > 0.0 }?.let { add("${"%.1f".format(Locale.CHINA, it)} cm 身长") }
                        payload.optDouble("headCircumferenceCm").takeIf { it > 0.0 }?.let { add("${"%.1f".format(Locale.CHINA, it)} cm 头围") }
                    }.joinToString(" · ").ifBlank { "成长测量" }
                }
                append("\n· ").append(date).append(" ").append(detail)
            }
        } else {
            append("\n\n这个月还没有单独记录成长事件。")
        }
        append("\n\n由 BabyLog 生成 · 记录宝宝每一步成长")
    }
}
