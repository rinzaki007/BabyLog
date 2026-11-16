package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidFoodRecordTest {
    @Test
    fun presetsCoverCommonFirstFoodsAndCustomEntry() {
        assertTrue(solidFoodPresets.containsAll(listOf("米粉", "蔬菜泥", "水果泥", "肉泥", "蛋黄", "其他")))
    }

    @Test
    fun unitsAndEatingStatusesAreAvailable() {
        assertTrue(solidFoodUnits.containsAll(listOf("g", "勺", "ml", "口")))
        assertTrue(solidFoodStatuses.containsAll(listOf("正常", "少量尝试", "未吃完", "拒绝")))
    }

    @Test
    fun descriptionIncludesFoodAmountAndNonDefaultStatus() {
        assertEquals("米粉 · 10 g", solidFoodDescription("""{"foodName":"米粉","amount":10,"unit":"g","eatingStatus":"正常"}"""))
        assertEquals("苹果泥 · 少量尝试", solidFoodDescription("""{"foodName":"苹果泥","eatingStatus":"少量尝试"}"""))
    }

    @Test
    fun descriptionHandlesMalformedAndLegacyPayloads() {
        assertEquals("辅食", solidFoodDescription("not-json"))
        assertEquals("南瓜泥", solidFoodDescription("""{"foodName":"南瓜泥"}"""))
    }
}
