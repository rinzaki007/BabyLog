package com.rinzaki.babylog

import com.rinzaki.babylog.ui.theme.BabyLogThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStyleTest {
    @Test
    fun unknownStoredThemeFallsBackToDefault() {
        assertEquals(BabyLogThemeStyle.DEFAULT, BabyLogThemeStyle.fromStorage("unknown"))
        assertEquals(BabyLogThemeStyle.DEFAULT, BabyLogThemeStyle.fromStorage(null))
    }

    @Test
    fun eachThemeEnablesExactlyOneLauncherComponent() {
        BabyLogThemeStyle.entries.forEach { style ->
            val enabled = launcherComponentEnabledStates(style)
            assertEquals(3, enabled.size)
            assertEquals(1, enabled.count { it })
            assertEquals(style.ordinal, enabled.indexOf(true))
        }
    }

    @Test
    fun allSupportedThemesRestoreFromStorage() {
        assertEquals(BabyLogThemeStyle.DEFAULT, BabyLogThemeStyle.fromStorage("default"))
        assertEquals(BabyLogThemeStyle.CAPYBARA, BabyLogThemeStyle.fromStorage("capybara"))
        assertEquals(BabyLogThemeStyle.KOALA, BabyLogThemeStyle.fromStorage("koala"))
    }
}
