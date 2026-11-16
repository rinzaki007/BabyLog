package com.rinzaki.babylog.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.AppUpdateSection
import com.rinzaki.babylog.FeedingPreferences
import com.rinzaki.babylog.data.BabyLogDao
import com.rinzaki.babylog.data.SyncOperationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncSettingsDialog(dao: BabyLogDao, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(SyncPreferences.server(context)) }
    var accountIdentifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("BabyLog 默认离线使用。登录同一个账号，即可在多台设备间自动同步数据。") }
    var busy by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(SyncPreferences.token(context)) }
    var accountEmail by remember { mutableStateOf(SyncPreferences.accountEmail(context)) }
    var editingServer by remember { mutableStateOf(SyncPreferences.server(context).isBlank()) }
    var showAccountForm by remember { mutableStateOf(SyncPreferences.token(context).isBlank()) }
    var pendingCount by remember { mutableStateOf(0) }
    var conflictCount by remember { mutableStateOf(0) }
    var conflicts by remember { mutableStateOf(emptyList<SyncOperationEntity>()) }
    var statusLoaded by remember { mutableStateOf(false) }
    var retryingConflictId by remember { mutableStateOf<String?>(null) }
    var showSecurity by remember { mutableStateOf(false) }
    var editedAccountName by remember { mutableStateOf(accountEmail) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showBreastMilk by remember { mutableStateOf(FeedingPreferences.isEnabled(context, FeedingPreferences.BREAST_MILK)) }
    var showFormula by remember { mutableStateOf(FeedingPreferences.isEnabled(context, FeedingPreferences.FORMULA)) }
    var showMixed by remember { mutableStateOf(FeedingPreferences.isEnabled(context, FeedingPreferences.MIXED)) }

    fun updateFeedingMethod(method: String, enabled: Boolean) {
        val enabledCount = listOf(showBreastMilk, showFormula, showMixed).count { it }
        if (!enabled && enabledCount <= 1) {
            message = "至少保留一种喂养方式，才能继续记录喂养。"
            return
        }
        when (method) {
            FeedingPreferences.BREAST_MILK -> showBreastMilk = enabled
            FeedingPreferences.FORMULA -> showFormula = enabled
            FeedingPreferences.MIXED -> showMixed = enabled
        }
        FeedingPreferences.setEnabled(context, method, enabled)
        message = if (enabled) "已显示${method}选项。" else "已隐藏${method}选项；历史记录不会受影响。"
    }

    suspend fun refreshSyncStatus() {
        val snapshot = withContext(Dispatchers.IO) {
            Triple(dao.pendingOperationCount(), dao.conflictOperations(), dao.conflictOperationCount())
        }
        pendingCount = snapshot.first
        conflicts = snapshot.second
        conflictCount = snapshot.third
        statusLoaded = true
    }

    LaunchedEffect(Unit) { refreshSyncStatus() }

    fun runAction(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) { action() }
            } catch (error: Exception) {
                error.message ?: "操作失败，请检查服务器地址和网络"
            } finally {
                busy = false
            }
            token = SyncPreferences.token(context)
            accountEmail = SyncPreferences.accountEmail(context)
            server = SyncPreferences.server(context)
            if (token.isNotBlank()) {
                showAccountForm = false
                editingServer = false
                password = ""
            }
            refreshSyncStatus()
        }
    }

    fun authenticate(path: String, successMessage: String) {
        runAction {
            val base = server.trim().trimEnd('/')
            require(base.startsWith("https://") || base.startsWith("http://10.") ||
                base.startsWith("http://192.168.") || base.startsWith("http://127.0.0.1") ||
                base.startsWith("http://localhost")) { "请先填写有效的 NAS 同步地址" }
            val result = SyncHttp.request(
                base, path, "POST",
                body = JSONObject()
                    .put(if (path == "/api/auth/register" && accountIdentifier.contains("@")) "email" else "username", accountIdentifier.trim())
                    .put("password", password)
            )
            SyncPreferences.saveServer(context, base)
            SyncPreferences.saveAccountEmail(context, accountIdentifier)
            val accessToken = result.getString("access_token")
            SyncPreferences.saveSession(context, accessToken)
            val profile = SyncHttp.request(base, "/api/me", token = accessToken)
            SyncPreferences.saveAccountEmail(context, profile.optString("username", accountIdentifier.trim()))
            val linkedFamilies = profile.optJSONArray("families")
            require(linkedFamilies != null && linkedFamilies.length() > 0) {
                "账号初始化失败，请检查 NAS 服务端版本后重试。"
            }
            // One account maps to one shared data space. Every device signing in
            // with this account receives the same family ID from /api/me.
            val familyId = linkedFamilies.getJSONObject(0).getString("id")
            SyncPreferences.saveFamily(context, familyId)
            val restoredConflicts = dao.restoreFamilyOwnershipConflicts(
                familyId,
                FAMILY_OWNERSHIP_CONFLICT_REASON
            )
            SyncScheduler.schedule(context)
            if (restoredConflicts > 0) {
                "$successMessage 已恢复 $restoredConflicts 条属于当前家庭的冲突记录，正在重新同步。"
            } else {
                successMessage
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppUpdateSection()

                Text("喂养方式", fontWeight = FontWeight.SemiBold)
                Text("关闭不使用的方式后，新增喂养记录时会隐藏对应选项；已有历史记录不会被删除或修改。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("母乳", modifier = Modifier.weight(1f))
                    Switch(checked = showBreastMilk, onCheckedChange = { updateFeedingMethod(FeedingPreferences.BREAST_MILK, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("配方奶", modifier = Modifier.weight(1f))
                    Switch(checked = showFormula, onCheckedChange = { updateFeedingMethod(FeedingPreferences.FORMULA, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("混合喂养", modifier = Modifier.weight(1f))
                    Switch(checked = showMixed, onCheckedChange = { updateFeedingMethod(FeedingPreferences.MIXED, it) })
                }

                Text("同步状态", fontWeight = FontWeight.SemiBold)
                Text(
                    if (pendingCount == 0 && conflictCount == 0) "本机同步队列已清空，没有待处理项目。"
                    else "待同步 $pendingCount 项 · 冲突 $conflictCount 项",
                    color = if (conflictCount > 0) androidx.compose.material3.MaterialTheme.colorScheme.error
                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "记录会先保存在本机；联网后自动同步到 NAS。普通待同步项目需先完成同步；若冲突明确属于其他家庭，队列会保留，登录原家庭账号后才会恢复。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )

                if (conflicts.isNotEmpty()) {
                    Text("同步冲突详情", fontWeight = FontWeight.SemiBold)
                    Text(
                        "这些项目仍保留在本机，没有被删除。请根据原因选择重试，或切回原家庭账号恢复。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    conflicts.take(20).forEach { operation ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(syncConflictTitle(operation), fontWeight = FontWeight.SemiBold)
                                Text(syncConflictSummary(operation), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                Text(
                                    operation.error ?: "同步冲突，尚未上传。",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "产生时间：${formatSyncOperationTime(operation.createdAt)}",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isFamilyOwnershipConflict(operation.error)) {
                                    Text(
                                        "此项目属于其他家庭空间。可安全退出当前账号并登录原家庭账号；匹配后系统会自动恢复该项目，不会把它上传到当前家庭。",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text(
                                        "重试会保留本机内容；若只是服务器版本冲突，系统会重新核对版本后提交。",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (retryingConflictId == null && canRetrySyncConflict(operation.error)) {
                                                retryingConflictId = operation.operationId
                                                scope.launch {
                                                    message = try {
                                                        withContext(Dispatchers.IO) {
                                                            dao.retryConflict(operation.operationId)
                                                        }
                                                        SyncScheduler.enqueue(context)
                                                        "已将这条本机记录重新加入同步队列，正在重试。"
                                                    } catch (error: Exception) {
                                                        error.message ?: "重试失败，请稍后再试。"
                                                    } finally {
                                                        retryingConflictId = null
                                                        refreshSyncStatus()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !busy && retryingConflictId == null && canRetrySyncConflict(operation.error)
                                    ) {
                                        Text(if (retryingConflictId == operation.operationId) "正在重试…" else "保留本机版本并重试")
                                    }
                                }
                            }
                        }
                    }
                    if (conflicts.size > 20) {
                        Text(
                            "还有 ${conflicts.size - 20} 条冲突未展开，以上显示最近 20 条。",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text("NAS 同步服务器", fontWeight = FontWeight.SemiBold)
                if (server.isNotBlank() && !editingServer) {
                    Text(server, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { editingServer = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text("更换服务器地址") }
                } else {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务器地址") },
                        placeholder = { Text("https://sync.example.com") },
                        singleLine = true,
                        enabled = !busy
                    )
                    Button(
                        onClick = {
                            val normalized = server.trim().trimEnd('/')
                            if (!(normalized.startsWith("https://") || normalized.startsWith("http://10.") ||
                                normalized.startsWith("http://192.168.") || normalized.startsWith("http://127.0.0.1") ||
                                normalized.startsWith("http://localhost"))) {
                                message = "请填写 HTTPS 地址；局域网测试可使用内网 HTTP 地址。"
                            } else {
                                val serverChanged = SyncPreferences.server(context).trimEnd('/') != normalized
                                if (serverChanged && !canSwitchAccountWithConflicts(pendingCount, conflicts.map { it.error })) {
                                    message = if (pendingCount > 0) {
                                        "本机还有待同步项目，请先联网完成同步，再更换服务器。"
                                    } else {
                                        "还有非家庭归属类冲突，请先查看详情并处理后再更换服务器。"
                                    }
                                } else {
                                    SyncPreferences.saveServer(context, normalized)
                                    server = normalized
                                    editingServer = false
                                    if (serverChanged && token.isNotBlank()) {
                                        SyncScheduler.cancel(context)
                                        SyncPreferences.clearSession(context)
                                        token = ""
                                        accountEmail = ""
                                        showAccountForm = true
                                        message = "服务器地址已更换，请重新登录。"
                                    } else {
                                        message = "服务器地址已保存。"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && statusLoaded
                    ) { Text("保存服务器地址") }
                    if (SyncPreferences.server(context).isNotBlank()) {
                        TextButton(onClick = {
                            server = SyncPreferences.server(context)
                            editingServer = false
                        }) { Text("取消修改") }
                    }
                }

                if (token.isBlank() || showAccountForm) {
                    Text("账号登录", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = accountIdentifier,
                        onValueChange = { accountIdentifier = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("账号名或邮箱") },
                        supportingText = { Text("新账号可使用 3-32 位字母、数字、点、下划线或短横线") },
                        singleLine = true,
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码（至少 10 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy
                    )
                    OutlinedButton(
                        onClick = { authenticate("/api/auth/login", "登录成功，已开启后台自动同步。") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && accountIdentifier.isNotBlank() && password.isNotBlank()
                    ) { Text(if (busy) "正在登录…" else "登录已有账号") }
                    TextButton(
                        onClick = { authenticate("/api/auth/register", "注册成功，已登录并开启后台自动同步。") },
                        enabled = !busy && accountIdentifier.isNotBlank() && password.length >= 10
                    ) { Text("注册新账号") }
                } else {
                    Text("当前账号", fontWeight = FontWeight.SemiBold)
                    Text(accountEmail.ifBlank { "已登录" })
                    Text(
                        "已开启自动同步。在其他设备登录同一个账号和密码，即可共享宝宝档案、记录和相册。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { editedAccountName = accountEmail; showSecurity = !showSecurity },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text(if (showSecurity) "收起账号与安全" else "账号与安全") }
                    if (showSecurity) {
                        Text("修改账号名", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = editedAccountName,
                            onValueChange = { editedAccountName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("新账号名") },
                            singleLine = true,
                            enabled = !busy
                        )
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("当前密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy
                        )
                        Button(
                            onClick = {
                                runAction {
                                    val result = SyncHttp.request(
                                        server.trim().trimEnd('/'), "/api/account/username", "POST", token,
                                        JSONObject().put("username", editedAccountName.trim()).put("current_password", currentPassword)
                                    )
                                    val saved = result.optString("username", editedAccountName.trim())
                                    SyncPreferences.saveAccountEmail(context, saved)
                                    currentPassword = ""
                                    "账号名已更新。其他设备下次登录请使用新账号名。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && editedAccountName.trim().length >= 3 && currentPassword.length >= 10
                        ) { Text("保存账号名") }
                        Spacer(Modifier.height(4.dp))
                        Text("修改密码", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("新密码（至少 10 位）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy
                        )
                        Button(
                            onClick = {
                                runAction {
                                    SyncHttp.request(
                                        server.trim().trimEnd('/'), "/api/account/password", "POST", token,
                                        JSONObject().put("current_password", currentPassword).put("new_password", newPassword)
                                    )
                                    currentPassword = ""
                                    newPassword = ""
                                    "密码已更新。其他设备下次登录请使用新密码。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && currentPassword.length >= 10 && newPassword.length >= 10
                        ) { Text("保存新密码") }
                    }
                    OutlinedButton(
                        onClick = {
                            if (!canSwitchAccountWithConflicts(pendingCount, conflicts.map { it.error })) {
                                message = if (pendingCount > 0) {
                                    "本机还有待同步项目，请联网完成同步后再退出登录。"
                                } else {
                                    "还有非家庭归属类冲突，请先查看详情并处理后再切换账号。"
                                }
                            } else {
                                SyncScheduler.cancel(context)
                                SyncPreferences.clearSession(context)
                                token = ""
                                accountEmail = ""
                                showAccountForm = true
                                message = if (conflicts.isNotEmpty()) {
                                    "已退出账号。本机数据和冲突队列均已保留；登录原家庭账号后，匹配的项目会自动恢复。"
                                } else {
                                    "已退出账号。本机数据不会删除。"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && statusLoaded
                    ) { Text("退出登录 / 切换账号") }
                }

                Spacer(Modifier.height(4.dp))
                Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (busy) Text("正在处理…")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}


internal fun syncConflictTitle(operation: SyncOperationEntity): String {
    val entity = when (operation.entityType) {
        "baby" -> "宝宝档案"
        "event" -> "日常记录"
        "photo" -> "成长相册"
        else -> "同步项目"
    }
    val action = if (operation.operation == "delete") "删除" else "更新"
    return "${entity} · ${action}"
}

internal fun syncConflictSummary(operation: SyncOperationEntity): String {
    val payload = runCatching { JSONObject(operation.payloadJson) }.getOrElse { JSONObject() }
    return when (operation.entityType) {
        "baby" -> payload.optString("displayName").ifBlank { "宝宝档案 ${operation.entityId.take(8)}" }
        "event" -> {
            val eventType = payload.optString("eventType")
            val label = when (eventType) {
                "FEEDING" -> "喂养"
                "DIAPER" -> "大小便护理"
                "SLEEP" -> "睡眠"
                "GROWTH" -> "成长测量"
                "VACCINE" -> "疫苗接种"
                else -> "其他记录"
            }
            val timestamp = payload.optLong("occurredAt")
            val time = if (timestamp > 0L) formatSyncOperationTime(timestamp) else ""
            val eventPayload = runCatching {
                JSONObject(payload.optString("payloadJson", "{}"))
            }.getOrElse { JSONObject() }
            val detail = when (eventType) {
                "FEEDING" -> {
                    val method = eventPayload.optString("method", "配方奶").ifBlank { "配方奶" }
                    val amount = eventPayload.optInt("amountMl").takeIf { it > 0 }?.let { "${it} ml" }
                    val duration = eventPayload.optInt("breastMinutes").takeIf { it > 0 }?.let { "亲喂 ${it} 分钟" }
                    listOfNotNull(method, amount, duration).joinToString(" · ")
                }
                "SLEEP" -> eventPayload.optInt("durationMinutes").takeIf { it > 0 }?.let { "${it} 分钟" }.orEmpty()
                "DIAPER" -> buildList {
                    if (eventPayload.optBoolean("urine")) add("小便")
                    if (eventPayload.optBoolean("stool")) add("大便")
                }.joinToString("、")
                else -> ""
            }
            listOf(label, time, detail).filter { it.isNotBlank() }.joinToString(" · ")
        }
        "photo" -> payload.optString("caption").ifBlank { "照片 ${operation.entityId.take(8)}" }
        else -> "项目标识：${operation.entityId.take(12)}"
    }
}

private fun formatSyncOperationTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
