package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedingPreferencesTest {
    @Test
    fun newRecordUsesSavedMethodWhenItIsStillEnabled() {
        assertEquals(
            FeedingPreferences.BREAST_MILK,
            resolveDefaultFeedingMethod(
                existingMethod = null,
                savedMethod = FeedingPreferences.BREAST_MILK,
                enabledMethods = listOf(FeedingPreferences.BREAST_MILK, FeedingPreferences.FORMULA)
            )
        )
    }

    @Test
    fun disabledSavedMethodFallsBackToFirstEnabledMethod() {
        assertEquals(
            FeedingPreferences.BREAST_MILK,
            resolveDefaultFeedingMethod(
                existingMethod = null,
                savedMethod = FeedingPreferences.FORMULA,
                enabledMethods = listOf(FeedingPreferences.BREAST_MILK)
            )
        )
    }

    @Test
    fun editingRecordKeepsItsOriginalMethod() {
        assertEquals(
            FeedingPreferences.MIXED,
            resolveDefaultFeedingMethod(
                existingMethod = FeedingPreferences.MIXED,
                savedMethod = FeedingPreferences.FORMULA,
                enabledMethods = listOf(FeedingPreferences.MIXED, FeedingPreferences.FORMULA)
            )
        )
    }

    @Test
    fun emptyEnabledListFallsBackToFormula() {
        assertEquals(
            FeedingPreferences.FORMULA,
            resolveDefaultFeedingMethod(null, FeedingPreferences.BREAST_MILK, emptyList())
        )
    }
}
