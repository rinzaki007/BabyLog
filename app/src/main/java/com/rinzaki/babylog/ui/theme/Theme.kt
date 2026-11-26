package com.rinzaki.babylog.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class BabyLogThemeStyle(val storageValue: String, val displayName: String) {
    DEFAULT("default", "默认主题"),
    CAPYBARA("capybara", "卡皮巴拉主题"),
    KOALA("koala", "考拉主题");

    companion object {
        fun fromStorage(value: String?): BabyLogThemeStyle =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

object BabyLogThemePreferences {
    private const val PREFS_NAME = "babylog_appearance"
    private const val KEY_THEME = "theme_style"

    fun read(context: Context): BabyLogThemeStyle {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, BabyLogThemeStyle.DEFAULT.storageValue)
        return BabyLogThemeStyle.fromStorage(value)
    }

    fun save(context: Context, style: BabyLogThemeStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, style.storageValue)
            .apply()
    }
}

/**
 * Define every semantic container role for each theme. Without explicit container
 * values Material's default light palette leaks purple/pink surfaces into themed cards.
 * Accent and supporting text colors also maintain readable contrast on tinted cards.
 */
internal fun babyLogColorScheme(style: BabyLogThemeStyle): ColorScheme = when (style) {
    BabyLogThemeStyle.DEFAULT -> lightColorScheme(
        primary = Color(0xFF945C4F), onPrimary = Color.White,
        primaryContainer = Color(0xFFF1DDD6), onPrimaryContainer = Color(0xFF4A2820),
        secondary = Color(0xFF58715D), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1E9DF), onSecondaryContainer = Color(0xFF293E2D),
        tertiary = Color(0xFF86613A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF1E2CE), onTertiaryContainer = Color(0xFF49331C),
        background = Color(0xFFFFF9F4), onBackground = Color(0xFF352E2A),
        surface = Color(0xFFFFFEFC), onSurface = Color(0xFF352E2A),
        surfaceVariant = Color(0xFFF3EBE4), onSurfaceVariant = Color(0xFF71655D),
        outline = Color(0xFF8E7E73), outlineVariant = Color(0xFFECE3DC)
    )

    BabyLogThemeStyle.CAPYBARA -> lightColorScheme(
        primary = Color(0xFF8A5A3A), onPrimary = Color.White,
        primaryContainer = Color(0xFFF2E2D0), onPrimaryContainer = Color(0xFF4B2D18),
        secondary = Color(0xFF5A6A43), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5EBD8), onSecondaryContainer = Color(0xFF334126),
        tertiary = Color(0xFF885E33), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF5E4CC), onTertiaryContainer = Color(0xFF50351B),
        background = Color(0xFFFFF7E9), onBackground = Color(0xFF493729),
        surface = Color(0xFFFFFCF4), onSurface = Color(0xFF493729),
        surfaceVariant = Color(0xFFF3E6D1), onSurfaceVariant = Color(0xFF735F4C),
        outline = Color(0xFF8A745E), outlineVariant = Color(0xFFE9D9C2)
    )

    BabyLogThemeStyle.KOALA -> lightColorScheme(
        primary = Color(0xFF4F7063), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEBE3), onPrimaryContainer = Color(0xFF24483A),
        secondary = Color(0xFF526D76), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFEAEF), onSecondaryContainer = Color(0xFF2D4750),
        tertiary = Color(0xFF79644F), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFECE3D8), onTertiaryContainer = Color(0xFF4C3B2A),
        background = Color(0xFFF2F7F2), onBackground = Color(0xFF2F4038),
        surface = Color(0xFFFCFEFA), onSurface = Color(0xFF2F4038),
        surfaceVariant = Color(0xFFE5EEE7), onSurfaceVariant = Color(0xFF5E6E64),
        outline = Color(0xFF75857A), outlineVariant = Color(0xFFD8E3DA)
    )
}

private val BabyLogTypography = Typography().copy(
    displayLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
)

@Composable
fun BabyLogTheme(style: BabyLogThemeStyle = BabyLogThemeStyle.DEFAULT, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = babyLogColorScheme(style),
        typography = BabyLogTypography,
        content = content
    )
}
