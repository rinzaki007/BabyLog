package com.rinzaki.babylog.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.rinzaki.babylog.data.SyncOperationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.rinzaki.babylog.sync.photoSyncPayload
import com.rinzaki.babylog.sync.mimeTypeForPath

@Composable
fun SyncSettingsDialog(dao: BabyLogDao, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(SyncPreferences.server(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var inviteResult by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("未配置服务器时，BabyLog 完全离线运行。") }
    var busy by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(SyncPreferences.token(context)) }
    var familyId by remember { mutableStateOf(SyncPreferences.familyId(context)) }
    var pendingCount by remember { mutableStateOf(0) }
    var conflictCount by remember { mutableStateOf(0) }
    var lastSyncAt by remember { mutableStateOf(SyncPreferences.lastSyncAt(context)) }
    var lastSyncError by remember { mutableStateOf(SyncPreferences.lastSyncError(context)) }

    LaunchedEffect(Unit) {
        pendingCount = dao.pendingOperationCount()
        conflictCount = dao.conflictOperationCount()
    }

    fun runAction(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            message = try { withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) { error.message ?: "操作失败，请检查服务器地址和网络" }
            finally { busy = false }
            token = SyncPreferences.token(context)
            familyId = SyncPreferences.familyId(context)
            pendingCount = withContext(Dispatchers.IO) { dao.pendingOperationCount() }
            conflictCount = withContext(Dispatchers.IO) { dao.conflictOperationCount() }
            lastSyncAt = SyncPreferences.lastSyncAt(context)
            lastSyncError = SyncPreferences.lastSyncError(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("家庭同步", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("默认离线使用。只有填写自己的 NAS 地址并登录后，才会启用同步。")
                AppUpdateSection()
                OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("NAS 同步地址") }, placeholder = { Text("https://sync.example.com") }, singleLine = true)
                Button(
                    onClick = {
                        val normalized = server.trim().trimEnd('/')
                        if (!(normalized.startsWith("https://") || normalized.startsWith("http://10.") ||
                            normalized.startsWith("http://192.168.") || normalized.startsWith("http://127.0.0.1") ||
                            normalized.startsWith("http://localhost"))) {
                            message = "请填写 HTTPS 地址；局域网测试可使用内网 HTTP 地址。"
                        } else {
                            SyncPreferences.saveServer(context, normalized)
                            message = "服务器地址已保存。"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy
                ) { Text("保存服务器地址") }

                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("邮箱") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码（至少 10 位）") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("账号", fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = {
                        runAction {
                            val base = server.trim().trimEnd('/')
                            require(base.startsWith("https://") || base.startsWith("http://10.") ||
                                base.startsWith("http://192.168.") || base.startsWith("http://127.0.0.1") ||
                                base.startsWith("http://localhost")) { "请先填写有效的服务器地址" }
                            val result = SyncHttp.request(base, "/api/auth/register", "POST", body =
                                JSONObject().put("email", email.trim()).put("password", password))
                            SyncPreferences.saveServer(context, base)
                            val accessToken = result.getString("access_token")
                            SyncPreferences.saveSession(context, accessToken)
                            val profile = SyncHttp.request(base, "/api/me", token = accessToken)
                            val linkedFamilies = profile.optJSONArray("families")
                            if (linkedFamilies != null && linkedFamilies.length() == 1) {
                                SyncPreferences.saveFamily(context, linkedFamilies.getJSONObject(0).getString("id"))
                                SyncScheduler.schedule(context)
                            }
                            "注册成功，账号已登录。"
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy
                ) { Text("注册新账号") }
                OutlinedButton(
                    onClick = {
                        runAction {
                            val base = server.trim().trimEnd('/')
                            require(base.isNotBlank()) { "请先填写服务器地址" }
                            val result = SyncHttp.request(base, "/api/auth/login", "POST", body =
                                JSONObject().put("email", email.trim()).put("password", password))
                            SyncPreferences.saveServer(context, base)
                            val accessToken = result.getString("access_token")
                            SyncPreferences.saveSession(context, accessToken)
                            val profile = SyncHttp.request(base, "/api/me", token = accessToken)
                            val linkedFamilies = profile.optJSONArray("families")
                            if (linkedFamilies != null && linkedFamilies.length() == 1) {
                                SyncPreferences.saveFamily(context, linkedFamilies.getJSONObject(0).getString("id"))
                                SyncScheduler.schedule(context)
                            }
                            "登录成功。"
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy
                ) { Text("登录已有账号") }

                if (token.isNotBlank()) {
                    Text("家庭空间", fontWeight = FontWeight.SemiBold)
                    Text(if (familyId.isBlank()) "当前账号尚未加入家庭" else "已连接家庭：$familyId")
                    Button(
                        onClick = {
                            runAction {
                                val base = SyncPreferences.server(context)
                                val access = SyncPreferences.token(context)
                                val result = SyncHttp.request(base, "/api/families", "POST", access, JSONObject().put("name", "我的家庭"))
                                val fid = result.getString("family_id")
                                SyncPreferences.saveFamily(context, fid)
                                val babies = dao.getAllBabies()
                                for (baby in babies) {
                                    val updated = baby.copy(familyId = fid, serverVersion = null)
                                    dao.upsertBaby(updated)
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "baby", updated.id, "upsert", babySyncPayload(updated).toString(), null, System.currentTimeMillis()))
                                }
                                for (event in dao.getAllEvents().filter { it.deletedAt == null }) {
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "event", event.id, "upsert", eventSyncPayload(event).toString(), null, System.currentTimeMillis()))
                                }
                                for (photo in dao.getAllPhotos().filter { it.deletedAt == null }) {
                                    dao.enqueueOperation(SyncOperationEntity(UUID.randomUUID().toString(), "photo", photo.id, "upsert", photoSyncPayload(photo, mimeTypeForPath(photo.localPath)).toString(), photo.serverVersion, System.currentTimeMillis()))
                                }
                                SyncScheduler.schedule(context)
                                "家庭创建成功，现有宝宝档案和记录已加入首次同步队列。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && familyId.isBlank()
                    ) { Text("创建家庭并同步本机数据") }
                    OutlinedTextField(inviteCode, { inviteCode = it }, Modifier.fillMaxWidth(), label = { Text("家庭邀请码") }, singleLine = true)
                    OutlinedButton(
                        onClick = {
                            runAction {
                                val result = SyncHttp.request(
                                    SyncPreferences.server(context), "/api/families/join", "POST",
                                    SyncPreferences.token(context), JSONObject().put("code", inviteCode.trim())
                                )
                                SyncPreferences.saveFamily(context, result.getString("family_id"))
                                SyncScheduler.schedule(context)
                                "加入家庭成功，正在拉取家庭数据。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && familyId.isBlank() && inviteCode.isNotBlank()
                    ) { Text("使用邀请码加入家庭") }
                    OutlinedButton(
                        onClick = {
                            runAction {
                                val fid = SyncPreferences.familyId(context)
                                val result = SyncHttp.request(
                                    SyncPreferences.server(context), "/api/families/$fid/invites",
                                    "POST", SyncPreferences.token(context)
                                )
                                inviteResult = result.getString("invite_code")
                                "邀请码已生成，有效期 72 小时，请发给另一位家长。"
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && familyId.isNotBlank()
                    ) { Text("生成家庭邀请码") }
                    if (inviteResult.isNotBlank()) {
                        Text("邀请码：$inviteResult", fontWeight = FontWeight.Bold)
                    }
                    Text("同步状态", fontWeight = FontWeight.SemiBold)
                    Text("待同步 $pendingCount 项 · 冲突 $conflictCount 项")
                    Text(if (lastSyncAt > 0L) "上次成功同步：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt)) else "尚未成功同步")
                    if (!lastSyncError.isNullOrBlank()) Text("最近错误：$lastSyncError")
                    Text("记录变更和每次打开 App 时都会自动同步；离线时会保留在本机，联网后自动补传。", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        scope.launch {
                            pendingCount = withContext(Dispatchers.IO) { dao.pendingOperationCount() }
                            conflictCount = withContext(Dispatchers.IO) { dao.conflictOperationCount() }
                            lastSyncAt = SyncPreferences.lastSyncAt(context)
                            lastSyncError = SyncPreferences.lastSyncError(context)
                        }
                    }) { Text("刷新同步状态") }
                    TextButton(onClick = {
                        SyncPreferences.clearSession(context)
                        token = ""
                        familyId = ""
                        message = "已退出同步账号。本机数据不会删除。"
                    }, enabled = !busy) { Text("退出同步账号") }
                }
                Spacer(Modifier.height(4.dp))
                Text(message)
                if (busy) Text("正在处理…")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
