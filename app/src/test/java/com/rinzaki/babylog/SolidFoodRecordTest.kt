package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidFoodRecordTest {
    private fun event(id: String, time: Long, payload: String, deletedAt: Long? = null) =
        BabyEventEntity(id, "baby-1", "SOLID_FOOD", time, payload, null, time, "PENDING", null, deletedAt)

    @Test
    fun groupsFoodAttemptsAndKeepsFirstAndLatestDetails() {
        val entries = solidFoodJourney(
            listOf(
                event("later", 300L, """{"foodName":"鸡蛋","foodCategory":"蛋奶","foodStage":"再次尝试","foodReaction":"未见明显异常"}"""),
                event("first", 100L, """{"foodName":"鸡蛋","foodCategory":"蛋奶","foodStage":"首次尝试","foodReaction":"待观察"}"""),
                event("other", 200L, """{"foodName":"南瓜","foodCategory":"蔬菜","foodStage":"首次尝试","foodReaction":"待观察"}""")
            )
        )
        assertEquals(2, entries.size)
        assertEquals("鸡蛋", entries[0].name)
        assertEquals(100L, entries[0].firstTriedAt)
        assertEquals(300L, entries[0].latestAt)
        assertEquals(2, entries[0].attempts)
        assertEquals("再次尝试", entries[0].latestStage)
        assertEquals("未见明显异常", entries[0].latestReaction)
    }

    @Test
    fun ignoresDeletedMalformedAndEmptyFoodNames() {
        val entries = solidFoodJourney(
            listOf(
                event("deleted", 100L, """{"foodName":"米粉"}""", 101L),
                event("invalid", 200L, "not-json"),
                event("empty", 300L, """{"foodName":"  "}""")
            )
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun descriptionPreservesLegacyFieldsAndAddsNewObservationFields() {
        val legacy = solidFoodDescription("""{"foodName":"米粉","amount":10,"unit":"g","eatingStatus":"少量尝试"}""")
        assertTrue(legacy.contains("米粉"))
        assertTrue(legacy.contains("10 g"))
        assertTrue(legacy.contains("少量尝试"))

        val updated = solidFoodDescription("""{"foodName":"鸡蛋","foodStage":"首次尝试","foodReaction":"待观察"}""")
        assertTrue(updated.contains("首次尝试"))
        assertTrue(updated.contains("待观察"))
    }
}
