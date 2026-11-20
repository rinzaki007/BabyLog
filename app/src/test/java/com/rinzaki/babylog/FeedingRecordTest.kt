package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedingRecordTest {
    @Test
    fun breastfeedingCanBeSavedWithDurationOnly() {
        val payload = buildFeedingPayload("母乳", "", "15")
        assertEquals("母乳", payload.getString("method"))
        assertFalse(payload.has("amountMl"))
        assertEquals(15, payload.getInt("breastMinutes"))
    }

    @Test
    fun breastfeedingCanBeSavedWithBottleVolumeOnly() {
        val payload = buildFeedingPayload("母乳", "120", "")
        assertEquals("母乳", payload.getString("method"))
        assertEquals(120, payload.getInt("amountMl"))
        assertFalse(payload.has("breastMinutes"))
    }

    @Test
    fun formulaFeedingRequiresVolumeAndKeepsLegacyShape() {
        val payload = buildFeedingPayload("配方奶", "120", "")
        assertEquals("配方奶", payload.getString("method"))
        assertEquals(120, payload.getInt("amountMl"))
        assertFalse(payload.has("breastMinutes"))
    }

    @Test
    fun mixedFeedingStoresFormulaVolumeAndOptionalBreastDuration() {
        val payload = buildFeedingPayload("混合喂养", "90", "15")
        assertEquals("混合喂养", payload.getString("method"))
        assertEquals(90, payload.getInt("amountMl"))
        assertEquals(15, payload.getInt("breastMinutes"))
    }

    @Test
    fun feedingAmountAcceptsInclusiveMinimumAndMaximum() {
        assertEquals(1, buildFeedingPayload("配方奶", "1", "").getInt("amountMl"))
        assertEquals(1000, buildFeedingPayload("配方奶", "1000", "").getInt("amountMl"))
    }

    @Test
    fun breastfeedingDurationAcceptsInclusiveMinimumAndMaximum() {
        assertEquals(1, buildFeedingPayload("母乳", "", "1").getInt("breastMinutes"))
        assertEquals(180, buildFeedingPayload("母乳", "", "180").getInt("breastMinutes"))
    }

    @Test
    fun mixedFeedingRequiresVolumeButAllowsDurationToBeOmitted() {
        val payload = buildFeedingPayload("混合喂养", "90", "")
        assertEquals(90, payload.getInt("amountMl"))
        assertFalse(payload.has("breastMinutes"))
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("混合喂养", "", "15")
        }
    }

    @Test
    fun rejectsUnsupportedFeedingMethod() {
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("其他", "120", "")
        }
    }

    @Test
    fun rejectsMissingRequiredValuesAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("配方奶", "", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("母乳", "", "")
        }
        listOf("0", "1001", "abc", "1.5").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                buildFeedingPayload("配方奶", amount, "")
            }
        }
        listOf("0", "181", "abc", "1.5").forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) {
                buildFeedingPayload("母乳", "", duration)
            }
        }
    }

    @Test
    fun legacyFeedingPayloadDefaultsToFormulaMilk() {
        assertEquals("配方奶 · 120 ml", feedingDescription("""{"amountMl":120}"""))
        assertTrue(feedingDescription("""{"method":"母乳","breastMinutes":10}""").contains("亲喂 10 分钟"))
    }
}
