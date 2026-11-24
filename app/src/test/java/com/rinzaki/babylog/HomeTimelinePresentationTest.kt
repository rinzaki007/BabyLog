package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTimelinePresentationTest {
    private fun event(type: String, payload: String, occurredAt: Long = 1_700_000_000_000L, id: String = "event-1") = BabyEventEntity(
        id = id,
        babyId = "baby-1",
        eventType = type,
        occurredAt = occurredAt,
        payloadJson = payload,
        createdBy = null,
        updatedAt = occurredAt
    )

    @Test
    fun latestTimelineEventsAreFirstAndLimitedToFive() {
        val events = (1L..7L).map { index ->
            event("FEEDING", """{"amountMl":${index * 10}}""", occurredAt = index, id = "event-${index}")
        }

        val result = latestHomeTimelineEvents(events)

        assertEquals(listOf("event-7", "event-6", "event-5", "event-4", "event-3"), result.map { it.id })
    }

    @Test
    fun latestTimelineEventsRespectZeroLimit() {
        val events = listOf(event("FEEDING", """{"amountMl":120}"""))

        assertEquals(emptyList<BabyEventEntity>(), latestHomeTimelineEvents(events, limit = 0))
    }

    @Test
    fun feedingShowsRecordedMilkAmount() {
        val result = homeTimelinePresentation(event("FEEDING", """{"amountMl":120}"""))
        assertEquals("喂奶", result.title)
        assertEquals("120 ml", result.detail)
    }

    @Test
    fun sleepShowsDurationInHoursAndMinutes() {
        val result = homeTimelinePresentation(event("SLEEP", """{"durationMinutes":85}"""))
        assertEquals("睡眠", result.title)
        assertEquals("1 小时 25 分钟", result.detail)
    }

    @Test
    fun temperatureShowsValueAndMeasurementMethod() {
        val result = homeTimelinePresentation(event("TEMPERATURE", """{"temperatureC":36.8,"measurementMethod":"腋温"}"""))
        assertEquals("体温记录", result.title)
        assertEquals("36.8 ℃ · 腋温", result.detail)
        assertEquals("🌡️", result.emoji)
    }

    @Test
    fun diaperShowsOnlyRecordedCareTypes() {
        val result = homeTimelinePresentation(event("DIAPER", """{"urine":true,"stool":false}"""))
        assertEquals("换尿布", result.title)
        assertEquals("小便", result.detail)
    }
}
