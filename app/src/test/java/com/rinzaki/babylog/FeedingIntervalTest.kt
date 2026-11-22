package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FeedingIntervalTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.atTime(15, 30).atZone(zone).toInstant().toEpochMilli()

    private fun event(type: String, time: LocalDate, hour: Int, minute: Int = 0, id: String = "${type}-${hour}-${minute}") =
        BabyEventEntity(
            id = id,
            babyId = "baby",
            eventType = type,
            occurredAt = time.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
            payloadJson = "{}",
            createdBy = null,
            updatedAt = now
        )

    @Test
    fun selectsLatestFeedingFromToday() {
        val events = listOf(
            event("FEEDING", today, 8),
            event("DIAPER", today, 14),
            event("FEEDING", today, 13, 45)
        )
        assertEquals(today.atTime(13, 45).atZone(zone).toInstant().toEpochMilli(), lastFeedingTimeToday(events, now, zone))
    }

    @Test
    fun ignoresYesterdayEvenWhenItIsTheLatestAvailableFeeding() {
        val events = listOf(event("FEEDING", today.minusDays(1), 23, 55))
        assertNull(lastFeedingTimeToday(events, now, zone))
    }

    @Test
    fun ignoresFutureDatedFeedingRecords() {
        val events = listOf(event("FEEDING", today, 16))
        assertNull(lastFeedingTimeToday(events, now, zone))
    }

    @Test
    fun midnightStartsANewDailyInterval() {
        val justAfterMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000L
        val events = listOf(
            event("FEEDING", today, 23, 50),
            event("FEEDING", today.plusDays(1), 0, 0)
        )
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), lastFeedingTimeToday(events, justAfterMidnight, zone))
    }

    @Test
    fun returnsNullWhenThereAreNoFeedingEvents() {
        assertNull(lastFeedingTimeToday(listOf(event("SLEEP", today, 12)), now, zone))
    }

    @Test
    fun choosesLatestTimestampRegardlessOfInputOrder() {
        val events = listOf(event("FEEDING", today, 9), event("FEEDING", today, 14, 20))
        assertEquals(today.atTime(14, 20).atZone(zone).toInstant().toEpochMilli(), lastFeedingTimeToday(events, now, zone))
    }
}
