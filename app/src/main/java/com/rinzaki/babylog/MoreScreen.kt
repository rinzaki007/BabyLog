package com.rinzaki.babylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MorePageHeader(
    syncStatus: String,
    syncDescription: String,
    syncHasError: Boolean,
    pendingCount: Int,
    conflictCount: Int,
    lastSyncLabel: String,
    consistency: String?,
    syncError: String?,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onOpenSyncSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("更多", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "家庭设置与健康管理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☁️", style = MaterialTheme.typography.headlineMedium)
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("家庭同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            syncDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        syncStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (syncHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (pendingCount == 0 && conflictCount == 0) "同步队列已清空"
                        else "待同步 $pendingCount 项 · 冲突 $conflictCount 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (conflictCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最近成功同步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(lastSyncLabel, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("本地与云端校验", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            consistency ?: "尚未校验",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (consistency == "一致") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!syncError.isNullOrBlank()) {
                        Text("同步提示：$syncError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(onClick = onSyncNow, enabled = !syncing && syncStatus != "未配置", modifier = Modifier.fillMaxWidth()) {
                    Text(if (syncing) "同步中…" else "立即同步")
                }
                Button(onClick = onOpenSyncSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("管理家庭同步与冲突")
                }
            }
        }
        MoreSectionHeader(
            title = "健康管理",
            description = "查看疫苗接种计划与宝宝的接种记录"
        )
    }
}

@Composable
internal fun MoreSectionHeader(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                                "可导出宝宝档案和记录；完整迁移时可选择包含照片与头像。导入前会先校验，并以增量方式恢复，不覆盖已有记录。"
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
