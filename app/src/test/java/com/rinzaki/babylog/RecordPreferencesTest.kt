package com.rinzaki.babylog

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordPreferencesTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun missingValuesUseSafeFormDefaults() {
        assertEquals("60", RecordPreferences.get(context, RecordPreferences.SLEEP_DURATION, "60"))
        assertEquals("米粉", RecordPreferences.get(context, RecordPreferences.FOOD_NAME, "米粉"))
        assertTrue(RecordPreferences.getBoolean(context, RecordPreferences.DIAPER_URINE, true))
        assertFalse(RecordPreferences.getBoolean(context, RecordPreferences.DIAPER_STOOL, false))
    }

    @Test
    fun savedChoicesBecomeDefaultsForTheNextNewRecord() {
        RecordPreferences.save(
            context,
            mapOf(
                RecordPreferences.SLEEP_DURATION to "90",
                RecordPreferences.FOOD_NAME to "香蕉",
                RecordPreferences.SUPPLEMENT_UNIT to "粒"
            ),
            mapOf(
                RecordPreferences.DIAPER_URINE to false,
                RecordPreferences.DIAPER_STOOL to true
            )
        )

        assertEquals("90", RecordPreferences.get(context, RecordPreferences.SLEEP_DURATION, "60"))
        assertEquals("香蕉", RecordPreferences.get(context, RecordPreferences.FOOD_NAME, "米粉"))
        assertEquals("粒", RecordPreferences.get(context, RecordPreferences.SUPPLEMENT_UNIT, "滴"))
        assertFalse(RecordPreferences.getBoolean(context, RecordPreferences.DIAPER_URINE, true))
        assertTrue(RecordPreferences.getBoolean(context, RecordPreferences.DIAPER_STOOL, false))
    }

    @Test
    fun blankTextDoesNotEraseAUsefulSavedChoice() {
        RecordPreferences.save(context, mapOf(RecordPreferences.FOOD_NAME to "南瓜"))
        RecordPreferences.save(context, mapOf(RecordPreferences.FOOD_NAME to "  "))
        assertEquals("南瓜", RecordPreferences.get(context, RecordPreferences.FOOD_NAME, "米粉"))
    }
}
