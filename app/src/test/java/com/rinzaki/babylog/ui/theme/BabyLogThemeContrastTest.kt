package com.rinzaki.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class BabyLogThemeContrastTest {
    @Test
    fun allThemesKeepTextAndAccentRolesReadableOnTheirSurfaces() {
        BabyLogThemeStyle.entries.forEach { style ->
            val scheme = babyLogColorScheme(style)

            assertContrast(style, "primary on background", scheme.primary, scheme.background, 4.5)
            assertContrast(style, "primary on surface", scheme.primary, scheme.surface, 4.5)
            assertContrast(style, "primary on surfaceVariant", scheme.primary, scheme.surfaceVariant, 4.5)
            assertContrast(style, "secondary on background", scheme.secondary, scheme.background, 4.5)
            assertContrast(style, "secondary on surfaceVariant", scheme.secondary, scheme.surfaceVariant, 4.5)
            assertContrast(style, "tertiary on background", scheme.tertiary, scheme.background, 4.5)
            assertContrast(style, "tertiary on surfaceVariant", scheme.tertiary, scheme.surfaceVariant, 4.5)

            assertContrast(style, "onPrimary", scheme.onPrimary, scheme.primary, 4.5)
            assertContrast(style, "onSecondary", scheme.onSecondary, scheme.secondary, 4.5)
            assertContrast(style, "onTertiary", scheme.onTertiary, scheme.tertiary, 4.5)
            assertContrast(style, "onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)
            assertContrast(style, "onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)
            assertContrast(style, "onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, 4.5)

            assertContrast(style, "onSurfaceVariant on background", scheme.onSurfaceVariant, scheme.background, 4.5)
            assertContrast(style, "onSurfaceVariant on surface", scheme.onSurfaceVariant, scheme.surface, 4.5)
            assertContrast(style, "onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5)
            assertContrast(style, "outline on background", scheme.outline, scheme.background, 3.0)
            assertContrast(style, "outline on surfaceVariant", scheme.outline, scheme.surfaceVariant, 3.0)
        }
    }

    private fun assertContrast(
        style: BabyLogThemeStyle,
        role: String,
        foreground: Color,
        background: Color,
        minimum: Double
    ) {
        val actual = contrastRatio(foreground, background)
        assertTrue(
            "${style.storageValue}: $role contrast was ${"%.2f".format(actual)}, expected at least $minimum",
            actual >= minimum
        )
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = luminance(foreground)
        val backgroundLuminance = luminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }

        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }
}
