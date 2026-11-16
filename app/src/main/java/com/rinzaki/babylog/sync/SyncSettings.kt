package com.rinzaki.babylog.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    LaunchedEffect(Unit) {
        pendingCount = dao.pendingOperationCount()
        conflictCount = dao.conflictOperationCount()
    }

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
            pendingCount = withContext(Dispatchers.IO) { dao.pendingOperationCount() }
            conflictCount = withContext(Dispatchers.IO) { dao.conflictOperationCount() }
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
            SyncPreferences.saveFamily(context, linkedFamilies.getJSONObject(0).getString("id"))
            SyncScheduler.schedule(context)
            successMessage
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
                    "记录会先保存在本机；联网后自动同步到 NAS。切换服务器或退出账号前，请先处理未完成的同步项目。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )

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
                                if (serverChanged && (pendingCount > 0 || conflictCount > 0)) {
                                    message = "本机还有未完成的同步变更。请先联网完成同步，再更换服务器。"
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
                        enabled = !busy
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
                            if (pendingCount > 0 || conflictCount > 0) {
                                message = "本机还有未完成的同步变更，请联网完成同步后再退出登录。"
                            } else {
                                SyncScheduler.cancel(context)
                                SyncPreferences.clearSession(context)
                                token = ""
                                accountEmail = ""
                                showAccountForm = true
                                message = "已退出账号。本机数据不会删除。"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
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
