package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementRecordTest {
    @Test
    fun presetsCoverCommonBabySupplementsAndHydration() {
        assertTrue(supplementPresets.containsAll(listOf("维生素AD", "维生素D3", "铁剂", "水", "DHA", "益生菌", "乳糖酶", "其他")))
    }

    @Test
    fun defaultUnitsAreConvenientButRemainEditable() {
        assertEquals("ml", supplementDefaultUnit("水"))
        assertEquals("ml", supplementDefaultUnit("铁剂"))
        assertEquals("包", supplementDefaultUnit("益生菌"))
        assertEquals("滴", supplementDefaultUnit("维生素AD"))
    }

    @Test
    fun descriptionShowsNameAndRecordedQuantity() {
        assertEquals("维生素AD · 1 滴", supplementDescription("""{"supplementName":"维生素AD","amount":1,"unit":"滴"}"""))
        assertEquals("水 · 30 ml", supplementDescription("""{"supplementName":"水","amount":30,"unit":"ml"}"""))
    }

    @Test
    fun descriptionSupportsNameOnlyAndLegacyMalformedPayload() {
        assertEquals("DHA", supplementDescription("""{"supplementName":"DHA"}"""))
        assertEquals("补充项目", supplementDescription("not-json"))
    }
}
