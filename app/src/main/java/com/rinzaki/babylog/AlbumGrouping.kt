package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyPhotoEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class AlbumPhotoGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val photos: List<BabyPhotoEntity>
)

/**
 * Groups photos by capture date by default, without changing album contents or pagination cursor.
 * Age groups use completed calendar months; photos taken before birth remain visible in their
 * own group rather than being assigned a negative age.
 */
internal fun groupAlbumPhotos(
    photos: List<BabyPhotoEntity>,
    birthTime: Long,
    byAge: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): List<AlbumPhotoGroup> {
    val birthDate = Instant.ofEpochMilli(birthTime).atZone(zone).toLocalDate()
    val activePhotos = photos.asSequence()
        .filter { it.deletedAt == null }
        .sortedWith(compareByDescending<BabyPhotoEntity> { it.takenAt }.thenByDescending { it.id })
        .toList()

    return activePhotos.groupBy { photo ->
        val date = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
        if (byAge) {
            if (date.isBefore(birthDate)) "before-birth"
            else "age:${ChronoUnit.MONTHS.between(birthDate, date)}"
        } else {
            "date:$date"
        }
    }.map { (key, groupedPhotos) ->
        val dates = groupedPhotos.map { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        val ageMonths = dates.filter { !it.isBefore(birthDate) }
            .map { ChronoUnit.MONTHS.between(birthDate, it) }
        val ageLabel = when {
            ageMonths.isEmpty() -> "出生前"
            ageMonths.min() == ageMonths.max() -> "月龄 ${ageMonths.first()} 个月"
            else -> "月龄 ${ageMonths.min()}–${ageMonths.max()} 个月"
        }
        val title = when {
            key == "before-birth" -> "出生前"
            key.startsWith("age:") -> "月龄 ${key.removePrefix("age:")} 个月"
            key.startsWith("date:") -> {
                val date = LocalDate.parse(key.removePrefix("date:"))
                "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
            }
            else -> key
        }
        val subtitle = if (byAge) {
            val first = dates.minOrNull()
            val last = dates.maxOrNull()
            if (first != null && last != null && first != last) {
                "${first.monthValue}月${first.dayOfMonth}日 – ${last.monthValue}月${last.dayOfMonth}日"
            } else {
                dates.firstOrNull()?.let { "${it.monthValue}月${it.dayOfMonth}日" }.orEmpty()
            }
        } else {
            ageLabel
        }
        AlbumPhotoGroup(key, title, subtitle, groupedPhotos)
    }
}
