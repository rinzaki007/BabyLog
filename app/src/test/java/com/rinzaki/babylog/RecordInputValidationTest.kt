package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordInputValidationTest {
    @Test
    fun diaperRecordRequiresUrineOrStool() {
        assertFalse(hasDiaperRecordSelection(false, false))
        assertTrue(hasDiaperRecordSelection(true, false))
        assertTrue(hasDiaperRecordSelection(false, true))
        assertTrue(hasDiaperRecordSelection(true, true))
    }

    @Test
    fun sleepDurationAcceptsInclusiveLimitsAndValuesInsideRange() {
        assertEquals(10, parseSleepDuration("10"))
        assertEquals(60, parseSleepDuration(" 60 "))
        assertEquals(720, parseSleepDuration("720"))
    }

    @Test
    fun sleepDurationRejectsBlankMalformedAndOutOfRangeValues() {
        listOf("", " ", "abc", "9", "721", "-10", "10.5").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                parseSleepDuration(raw)
            }
        }
    }

    @Test
    fun temperatureAcceptsDecimalValuesAndCommaDecimalSeparator() {
        assertEquals(36.8, parseTemperatureC("36.8"), 0.0001)
        assertEquals(37.2, parseTemperatureC("37,2"), 0.0001)
        assertEquals(30.0, parseTemperatureC("30"), 0.0001)
        assertEquals(45.0, parseTemperatureC("45"), 0.0001)
    }

    @Test
    fun temperatureRejectsBlankMalformedAndOutOfRangeValues() {
        listOf("", " ", "abc", "29.9", "45.1", "-1", "37.2.1").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { parseTemperatureC(raw) }
        }
    }

    @Test
    fun sleepDurationErrorExplainsAcceptedRange() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseSleepDuration("0")
        }
        assertEquals("睡眠时长请输入 10–720 分钟", error.message)
    }
}
