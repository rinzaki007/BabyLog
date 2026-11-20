package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class GrowthMeasurementValidationTest {
    @Test
    fun blankMeasurementIsOptional() {
        assertNull(parseOptionalMeasurement("", "体重", 0.1, 80.0))
        assertNull(parseOptionalMeasurement("   ", "身长", 20.0, 120.0))
    }

    @Test
    fun validMeasurementIsParsedWithinInclusiveRange() {
        assertEquals(3.25, parseOptionalMeasurement(" 3.25 ", "体重", 0.1, 80.0)!!, 0.0001)
        assertEquals(20.0, parseOptionalMeasurement("20", "身长", 20.0, 120.0)!!, 0.0001)
        assertEquals(70.0, parseOptionalMeasurement("70", "头围", 20.0, 70.0)!!, 0.0001)
    }

    @Test
    fun malformedMeasurementIsNotSilentlyIgnored() {
        assertFailure("体重请输入有效数字") {
            parseOptionalMeasurement("3..2", "体重", 0.1, 80.0)
        }
    }

    @Test
    fun outOfRangeMeasurementIsRejected() {
        assertFailure("请检查身长是否在合理范围内") {
            parseOptionalMeasurement("19.9", "身长", 20.0, 120.0)
        }
    }

    private fun assertFailure(expectedMessage: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertEquals(expectedMessage, error.message)
        }
    }
}
