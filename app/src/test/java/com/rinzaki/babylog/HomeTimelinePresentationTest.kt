package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTimelinePresentationTest {
    private fun event(type: String, payload: String) = BabyEventEntity(
        id = "event-1",
        babyId = "baby-1",
        eventType = type,
        occurredAt = 1_700_000_000_000L,
        payloadJson = payload,
        createdBy = null,
        updatedAt = 1_700_000_000_000L
    )

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
    fun diaperShowsOnlyRecordedCareTypes() {
        val result = homeTimelinePresentation(event("DIAPER", """{"urine":true,"stool":false}"""))
        assertEquals("换尿布", result.title)
        assertEquals("小便", result.detail)
    }
}
