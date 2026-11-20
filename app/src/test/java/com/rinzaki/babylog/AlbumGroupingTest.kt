package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyPhotoEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumGroupingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun photo(id: String, date: LocalDate, deletedAt: Long? = null) = BabyPhotoEntity(
        id = id,
        babyId = "baby-1",
        localPath = "/photos/$id.jpg",
        caption = null,
        takenAt = at(date),
        createdAt = at(date),
        updatedAt = at(date),
        deletedAt = deletedAt
    )

    private val birthTime = at(LocalDate.of(2026, 1, 15))

    @Test
    fun groupsPhotosByCalendarMonthAndShowsAgeRange() {
        val groups = groupAlbumPhotos(
            listOf(
                photo("march", LocalDate.of(2026, 3, 16)),
                photo("february", LocalDate.of(2026, 2, 14)),
                photo("january", LocalDate.of(2026, 1, 20))
            ),
            birthTime,
            byAge = false,
            zone
        )

        assertEquals(listOf("2026年3月", "2026年2月", "2026年1月"), groups.map { it.title })
        assertEquals("月龄 2 个月", groups.first().subtitle)
        assertEquals(3, groups.sumOf { it.photos.size })
    }

    @Test
    fun groupsPhotosByCompletedAgeMonthsAndKeepsPrenatalPhotos() {
        val groups = groupAlbumPhotos(
            listOf(
                photo("age-two", LocalDate.of(2026, 3, 15)),
                photo("age-one", LocalDate.of(2026, 2, 15)),
                photo("age-zero", LocalDate.of(2026, 2, 14)),
                photo("before", LocalDate.of(2026, 1, 14))
            ),
            birthTime,
            byAge = true,
            zone
        )

        assertEquals(
            listOf("月龄 2 个月", "月龄 1 个月", "月龄 0 个月", "出生前"),
            groups.map { it.title }
        )
        assertEquals(4, groups.sumOf { it.photos.size })
        assertTrue(groups.last().photos.single().id == "before")
    }

    @Test
    fun excludesDeletedPhotosWithoutDroppingAnyActivePhoto() {
        val groups = groupAlbumPhotos(
            listOf(
                photo("active", LocalDate.of(2026, 1, 20)),
                photo("deleted", LocalDate.of(2026, 1, 21), deletedAt = at(LocalDate.of(2026, 1, 22)))
            ),
            birthTime,
            byAge = false,
            zone
        )

        assertEquals(1, groups.sumOf { it.photos.size })
        assertEquals("active", groups.single().photos.single().id)
    }
}
