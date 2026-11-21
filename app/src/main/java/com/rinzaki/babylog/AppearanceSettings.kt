package com.rinzaki.babylog

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.ui.theme.BabyLogThemeStyle

internal fun updateLauncherIcon(context: Context, style: BabyLogThemeStyle) {
    val packageManager = context.packageManager
    val defaultIcon = ComponentName(context, "${context.packageName}.DefaultLauncher")
    val capybaraIcon = ComponentName(context, "${context.packageName}.CapybaraLauncher")
    val koalaIcon = ComponentName(context, "${context.packageName}.KoalaLauncher")
    val icons = mapOf(
        BabyLogThemeStyle.DEFAULT to defaultIcon,
        BabyLogThemeStyle.CAPYBARA to capybaraIcon,
        BabyLogThemeStyle.KOALA to koalaIcon
    )
    icons.filterKeys { it != style }.values.forEach { component ->
        packageManager.setComponentEnabledSetting(
            component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
    }
    packageManager.setComponentEnabledSetting(
        icons.getValue(style), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
    )
}

@Composable
internal fun AppearanceSettingsCard(
    selectedStyle: BabyLogThemeStyle,
    onSelectStyle: (BabyLogThemeStyle) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoreSectionHeader("外观与皮肤", "选择喜欢的 BabyLog 风格，切换后立即生效", selectedStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(when (selectedStyle) { BabyLogThemeStyle.DEFAULT -> "🎨"; BabyLogThemeStyle.CAPYBARA -> "🐹"; BabyLogThemeStyle.KOALA -> "🐨" }, style = MaterialTheme.typography.headlineLarge)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(selectedStyle.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (selectedStyle) {
                            BabyLogThemeStyle.DEFAULT -> "保留 BabyLog 原有的温暖简洁风格"
                            BabyLogThemeStyle.CAPYBARA -> "奶油米色 · 焦糖棕 · 卡皮巴拉桌面图标"
                            BabyLogThemeStyle.KOALA -> "桉树绿 · 雾霾蓝 · 考拉桌面图标"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showPicker = true }) { Text("更换") }
            }
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择 App 主题") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePreviewOption(
                        title = "默认主题",
                        subtitle = "原有暖色风格",
                        selected = selectedStyle == BabyLogThemeStyle.DEFAULT,
                        colors = listOf(Color(0xFF955D50), Color(0xFF58715D), Color(0xFFFFF9F4)),
                        onClick = { onSelectStyle(BabyLogThemeStyle.DEFAULT); showPicker = false }
                    )
                    ThemePreviewOption(
                        title = "卡皮巴拉主题 🐹",
                        subtitle = "奶油米色、焦糖棕与自然绿",
                        selected = selectedStyle == BabyLogThemeStyle.CAPYBARA,
                        colors = listOf(Color(0xFF9A6845), Color(0xFF71815C), Color(0xFFFFF7E9)),
                        onClick = { onSelectStyle(BabyLogThemeStyle.CAPYBARA); showPicker = false }
                    )
                    ThemePreviewOption(
                        title = "考拉主题 🐨",
                        subtitle = "桉树绿、雾霾蓝与奶油白",
                        selected = selectedStyle == BabyLogThemeStyle.KOALA,
                        colors = listOf(Color(0xFF58776A), Color(0xFF6F8790), Color(0xFFF2F7F2)),
                        onClick = { onSelectStyle(BabyLogThemeStyle.KOALA); showPicker = false }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("完成") } }
        )
    }
}

@Composable
private fun ThemePreviewOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    colors.forEach { color ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(width = 34.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(color)
                        )
                    }
                }
            }
        }
    }
}
