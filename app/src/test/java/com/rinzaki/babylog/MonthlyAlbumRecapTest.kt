package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyPhotoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthlyAlbumRecapTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun photo(id: String, date: LocalDate, caption: String? = null, deletedAt: Long? = null) =
        BabyPhotoEntity(id, "baby-1", "/tmp/$id.jpg", caption, millis(date), millis(date), millis(date), deletedAt = deletedAt)

    private fun event(id: String, type: String, date: LocalDate, payload: String = "{}") =
        BabyEventEntity(id, "baby-1", type, millis(date), payload, null, millis(date))

    @Test
    fun groupsPhotosByMonthAndCountsOnlyActiveGrowthEvents() {
        val recaps = monthlyAlbumRecaps(
            listOf(
                photo("nov", LocalDate.of(2026, 11, 2)),
                photo("oct", LocalDate.of(2026, 10, 28)),
                photo("deleted", LocalDate.of(2026, 11, 3), deletedAt = millis(LocalDate.of(2026, 11, 4)))
            ),
            listOf(
                event("milestone", "MILESTONE", LocalDate.of(2026, 11, 1)),
                event("growth", "GROWTH", LocalDate.of(2026, 11, 5)),
                event("feeding", "FEEDING", LocalDate.of(2026, 11, 6))
            ),
            zone
        )
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), recaps.map { it.month })
        assertEquals(1, recaps[0].photos.size)
        assertEquals(2, recaps[0].growthEventCount)
    }

    @Test
    fun recapTextIncludesCaptionsAndGrowthMilestones() {
        val month = YearMonth.of(2026, 11)
        val text = monthlyAlbumRecapText(
            "小宝",
            month,
            listOf(photo("p1", LocalDate.of(2026, 11, 2), "第一次去公园")),
            listOf(event("m1", "MILESTONE", LocalDate.of(2026, 11, 3), """{"milestoneTitle":"第一次独坐"}""")),
            zone
        )
        assertTrue(text.contains("2026年11月成长回顾"))
        assertTrue(text.contains("第一次去公园"))
        assertTrue(text.contains("第一次独坐"))
    }
}
