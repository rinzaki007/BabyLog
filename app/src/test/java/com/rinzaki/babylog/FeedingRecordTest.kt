package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedingRecordTest {
    @Test
    fun breastfeedingCanBeSavedWithoutMeasuredVolume() {
        val payload = buildFeedingPayload("母乳", "", "")
        assertEquals("母乳", payload.getString("method"))
        assertFalse(payload.has("amountMl"))
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
    fun rejectsMissingFormulaVolumeAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("配方奶", "", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("母乳", "1001", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildFeedingPayload("母乳", "", "181")
        }
    }

    @Test
    fun legacyFeedingPayloadDefaultsToFormulaMilk() {
        assertEquals("配方奶 · 120 ml", feedingDescription("""{"amountMl":120}"""))
        assertTrue(feedingDescription("""{"method":"母乳","breastMinutes":10}""").contains("亲喂 10 分钟"))
    }
}
