package com.rinzaki.babylog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rinzaki.babylog.ui.theme.BabyLogThemeStyle

/** Distinct little activities for the mascot in each primary destination. */
internal enum class ThemeCharacterPose(
    val accessory: String,
    val capybaraRotation: Float,
    val koalaRotation: Float,
    val scaleX: Float,
    val scaleY: Float
) {
    HOME("💤", -2f, 1f, 1f, 1f),
    TIMELINE("✏️", -8f, 7f, 1.02f, 0.96f),
    GROWTH("🌱", 5f, -5f, 0.94f, 1.08f),
    ALBUM("📷", 7f, -8f, 1.04f, 0.98f),
    VACCINE("🩹", -4f, 4f, 0.98f, 1.02f)
}

/**
 * Shared character artwork used by themed pages. The default theme intentionally
 * stays visually identical to the original BabyLog appearance.
 */
@Composable
internal fun ThemeCharacterIllustration(
    style: BabyLogThemeStyle,
    modifier: Modifier = Modifier,
    pose: ThemeCharacterPose = ThemeCharacterPose.HOME
) {
    val image = when (style) {
        BabyLogThemeStyle.CAPYBARA -> R.drawable.theme_capybara_mascot
        BabyLogThemeStyle.KOALA -> R.drawable.theme_koala_mascot
        BabyLogThemeStyle.DEFAULT -> null
    }
    if (image != null) {
        val rotation = if (style == BabyLogThemeStyle.CAPYBARA) pose.capybaraRotation else pose.koalaRotation
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(image),
                contentDescription = if (style == BabyLogThemeStyle.CAPYBARA) "卡皮巴拉主题插画" else "考拉主题插画",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = rotation
                    scaleX = pose.scaleX
                    scaleY = pose.scaleY
                },
                contentScale = ContentScale.Fit
            )
            Text(
                text = pose.accessory,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), CircleShape)
                    .padding(2.dp),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun ThemePageBanner(
    style: BabyLogThemeStyle,
    title: String,
    subtitle: String,
    pose: ThemeCharacterPose = ThemeCharacterPose.HOME
) {
    if (style == BabyLogThemeStyle.DEFAULT) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(pose.accessory, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        return
    }

    val motif = if (style == BabyLogThemeStyle.CAPYBARA) "🐾" else "🍃"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(motif, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(78.dp).background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = CircleShape
                    )
                )
                ThemeCharacterIllustration(style, Modifier.size(84.dp), pose)
            }
        }
    }
}

@Composable
internal fun ThemeSectionDecoration(style: BabyLogThemeStyle) {
    if (style == BabyLogThemeStyle.DEFAULT) return
    Text(
        text = if (style == BabyLogThemeStyle.CAPYBARA) "🐾" else "🍃",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
