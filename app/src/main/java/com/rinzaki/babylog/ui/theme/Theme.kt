package com.rinzaki.babylog.ui.theme

import android.content.Context
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

private val DefaultColors = lightColorScheme(
    primary = Color(0xFF955D50), onPrimary = Color.White,
    secondary = Color(0xFF58715D), onSecondary = Color.White,
    tertiary = Color(0xFF86613A), background = Color(0xFFFFF9F4),
    surface = Color(0xFFFFFEFC), surfaceVariant = Color(0xFFF3EBE4),
    onSurface = Color(0xFF352E2A), onSurfaceVariant = Color(0xFF71655D),
    outline = Color(0xFF96877D), outlineVariant = Color(0xFFECE3DC)
)

private val CapybaraColors = lightColorScheme(
    primary = Color(0xFF9A6845), onPrimary = Color.White,
    secondary = Color(0xFF71815C), onSecondary = Color.White,
    tertiary = Color(0xFFC18A52), background = Color(0xFFFFF7E9),
    surface = Color(0xFFFFFCF4), surfaceVariant = Color(0xFFF3E6D1),
    onSurface = Color(0xFF493729), onSurfaceVariant = Color(0xFF806C58),
    outline = Color(0xFFAA9278), outlineVariant = Color(0xFFE9D9C2)
)

private val KoalaColors = lightColorScheme(
    primary = Color(0xFF58776A), onPrimary = Color.White,
    secondary = Color(0xFF6F8790), onSecondary = Color.White,
    tertiary = Color(0xFF9A8065), background = Color(0xFFF2F7F2),
    surface = Color(0xFFFCFEFA), surfaceVariant = Color(0xFFE5EEE7),
    onSurface = Color(0xFF2F4038), onSurfaceVariant = Color(0xFF68786E),
    outline = Color(0xFF8B9B8F), outlineVariant = Color(0xFFD8E3DA)
)

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
        colorScheme = when (style) {
            BabyLogThemeStyle.CAPYBARA -> CapybaraColors
            BabyLogThemeStyle.KOALA -> KoalaColors
            BabyLogThemeStyle.DEFAULT -> DefaultColors
        },
        typography = BabyLogTypography,
        content = content
    )
}
