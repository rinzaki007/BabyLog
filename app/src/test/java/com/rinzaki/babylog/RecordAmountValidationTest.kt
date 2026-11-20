package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordAmountValidationTest {
    @Test
    fun blankAmountRemainsOptional() {
        assertNull(parseOptionalRecordAmount("", "辅食数量", 10000.0))
        assertNull(parseOptionalRecordAmount("   ", "补充数量", 100000.0))
    }

    @Test
    fun acceptsPositiveIntegerAndDecimalAtUpperBoundary() {
        assertEquals(12.0, parseOptionalRecordAmount("12", "辅食数量", 10000.0)!!, 0.0)
        assertEquals(0.5, parseOptionalRecordAmount(" 0.5 ", "补充数量", 100000.0)!!, 0.0)
        assertEquals(10000.0, parseOptionalRecordAmount("10000", "辅食数量", 10000.0)!!, 0.0)
    }

    @Test
    fun rejectsMalformedNonFiniteZeroNegativeAndOverLimitValues() {
        listOf("abc", ".", "NaN", "Infinity", "0", "-1", "10000.01").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                parseOptionalRecordAmount(raw, "辅食数量", 10000.0)
            }
        }
    }

    @Test
    fun errorMessageIdentifiesTheField() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseOptionalRecordAmount("0", "补充数量", 100000.0)
        }
        assertEquals("请检查补充数量", error.message)
    }
}
