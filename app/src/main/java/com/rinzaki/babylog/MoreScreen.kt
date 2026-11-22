package com.rinzaki.babylog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MorePageHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("更多", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "外观与皮肤、数据管理和家庭同步",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MoreSectionHeader(title: String, description: String, themeStyle: com.rinzaki.babylog.ui.theme.BabyLogThemeStyle = com.rinzaki.babylog.ui.theme.BabyLogThemeStyle.DEFAULT) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ThemeSectionDecoration(themeStyle)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun CollapsibleSection(
    title: String,
    description: String,
    expandedInitially: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember(title) { mutableStateOf(expandedInitially) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (expanded) "收起 －" else "展开 ＋", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun MoreDataManagementCard(
    serverConfigured: Boolean,
    onOpenDataManagement: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoreSectionHeader(
            title = "数据管理",
            description = "备份与恢复统一在 NAS 网页管理面板中完成"
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📦", style = MaterialTheme.typography.headlineMedium)
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (serverConfigured) {
                                "导出宝宝档案和记录；完整迁移时可选择照片与头像。导入前会校验，并以增量方式恢复，不覆盖已有记录。"
                            } else {
                                "连接 NAS 后，可在网页面板导出备份、检查备份内容并安全恢复数据。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(onClick = onOpenDataManagement, modifier = Modifier.fillMaxWidth()) {
                    Text(if (serverConfigured) "打开 NAS 数据管理" else "配置 NAS 连接")
                }
            }
        }
    }
}


@Composable
internal fun AppSettingsDialog(
    selectedStyle: com.rinzaki.babylog.ui.theme.BabyLogThemeStyle,
    onSelectStyle: (com.rinzaki.babylog.ui.theme.BabyLogThemeStyle) -> Unit,
    serverConfigured: Boolean,
    onOpenDataManagement: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = androidx.compose.ui.Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("外观、数据与家庭同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selectedStyle == com.rinzaki.babylog.ui.theme.BabyLogThemeStyle.DEFAULT) {
                        Text("⚙️", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        ThemeCharacterIllustration(selectedStyle, androidx.compose.ui.Modifier.size(44.dp), ThemeCharacterPose.HOME)
                    }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                AppearanceSettingsCard(selectedStyle = selectedStyle, onSelectStyle = onSelectStyle)
                MoreDataManagementCard(serverConfigured = serverConfigured, onOpenDataManagement = onOpenDataManagement)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoreSectionHeader(
                        title = "家庭同步",
                        description = "连接家庭设备，在多台设备间安全同步记录",
                        themeStyle = selectedStyle
                    )
                    Card(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("☁️", style = MaterialTheme.typography.headlineMedium)
                            Column(
                                modifier = androidx.compose.ui.Modifier.weight(1f).padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("家庭数据同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "配置 NAS 服务地址、家庭连接与同步选项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = onOpenSyncSettings,
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                        ) {
                            Text("打开同步设置")
                        }
                    }
                }
            }
        }
    }
}
