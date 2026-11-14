package com.rinzaki.babylog.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rinzaki.babylog.AppUpdateSection
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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("BabyLog 默认离线使用。登录同一个账号，即可在多台设备间自动同步数据。") }
    var busy by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(SyncPreferences.token(context)) }
    var accountEmail by remember { mutableStateOf(SyncPreferences.accountEmail(context)) }
    var editingServer by remember { mutableStateOf(SyncPreferences.server(context).isBlank()) }
    var showAccountForm by remember { mutableStateOf(SyncPreferences.token(context).isBlank()) }
    var pendingCount by remember { mutableStateOf(0) }
    var conflictCount by remember { mutableStateOf(0) }

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
                body = JSONObject().put("email", email.trim()).put("password", password)
            )
            SyncPreferences.saveServer(context, base)
            SyncPreferences.saveAccountEmail(context, email)
            val accessToken = result.getString("access_token")
            SyncPreferences.saveSession(context, accessToken)
            val profile = SyncHttp.request(base, "/api/me", token = accessToken)
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
                modifier = Modifier.fillMaxWidth().height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppUpdateSection()

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
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("邮箱") },
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
                        enabled = !busy && email.isNotBlank() && password.isNotBlank()
                    ) { Text(if (busy) "正在登录…" else "登录已有账号") }
                    TextButton(
                        onClick = { authenticate("/api/auth/register", "注册成功，已登录并开启后台自动同步。") },
                        enabled = !busy && email.isNotBlank() && password.length >= 10
                    ) { Text("注册新账号") }
                } else {
                    Text("当前账号", fontWeight = FontWeight.SemiBold)
                    Text(accountEmail.ifBlank { "已登录" })
                    Text(
                        "已开启自动同步。在其他设备登录同一个账号和密码，即可共享宝宝档案、记录和相册。",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
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
