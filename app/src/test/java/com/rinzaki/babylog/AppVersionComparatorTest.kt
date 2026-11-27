package com.rinzaki.babylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionComparatorTest {
    @Test
    fun legacy303InstallRecognizes304AsNewer() {
        assertTrue(
            AppVersionComparator.isNewer(
                latestVersion = "3.0.4",
                latestVersionCode = null,
                currentVersion = "3.03",
                currentVersionCode = 303L
            )
        )
    }

    @Test
    fun legacy303IsEquivalentToIntended303Version() {
        assertEquals(0, AppVersionComparator.compareVersionNames("3.03", "3.0.3"))
        assertFalse(
            AppVersionComparator.isNewer(
                latestVersion = "3.0.3",
                latestVersionCode = null,
                currentVersion = "3.03",
                currentVersionCode = 303L
            )
        )
    }

    @Test
    fun versionCodeTakesPriorityOverDisplayVersionName() {
        assertTrue(
            AppVersionComparator.isNewer(
                latestVersion = "3.0.4",
                latestVersionCode = 405L,
                currentVersion = "3.03",
                currentVersionCode = 404L
            )
        )
        assertFalse(
            AppVersionComparator.isNewer(
                latestVersion = "3.0.5",
                latestVersionCode = 403L,
                currentVersion = "3.0.4",
                currentVersionCode = 404L
            )
        )
    }

    @Test
    fun normalSemanticVersionsRemainComparable() {
        assertTrue(
            AppVersionComparator.isNewer(
                latestVersion = "3.1.0",
                latestVersionCode = null,
                currentVersion = "3.0.9",
                currentVersionCode = 900L
            )
        )
        assertFalse(
            AppVersionComparator.isNewer(
                latestVersion = "3.0.4",
                latestVersionCode = null,
                currentVersion = "3.0.4",
                currentVersionCode = 400L
            )
        )
    }
}
