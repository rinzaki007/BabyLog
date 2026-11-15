package com.rinzaki.babylog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rinzaki.babylog.sync.SyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val RELEASE_API = "https://api.github.com/repos/rinzaki007/BabyLog/releases/latest"
private const val UPDATE_PREFS = "babylog_update_preferences"
private data class BabyRelease(
    val tag: String, val version: String, val notes: String, val url: String,
    val size: Long, val sha256: String?, val source: String
)

private object BabyUpdater {
    fun currentVersion(context: Context): String {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }

    fun newer(latest: String, current: String): Boolean {
        val a = latest.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun checkNas(context: Context): Pair<BabyRelease?, String> = withContext(Dispatchers.IO) {
        val base = SyncPreferences.server(context).trim().trimEnd('/')
        if (base.isBlank()) return@withContext null to "未配置 NAS 服务器地址"
        try {
            val connection = URL("$base/api/app-update/latest").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 7000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/json")
                check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (!json.optBoolean("available")) return@withContext null to "NAS 暂无可用安装包"
                val tag = json.optString("tag")
                val version = json.optString("version", tag.removePrefix("v").removePrefix("V"))
                val path = json.optString("download_path", "/api/app-update/download")
                require(tag.isNotBlank() && path == "/api/app-update/download") { "NAS 更新信息无效" }
                val release = BabyRelease(
                    tag, version, json.optString("notes").ifBlank { "本次更新未提供说明。" },
                    base + path, json.optLong("size"),
                    json.optString("sha256").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) },
                    "内网 NAS"
                )
                release to "NAS 安装包已就绪"
            } finally { connection.disconnect() }
        } catch (error: Exception) {
            null to (error.message ?: "内网更新源连接失败")
        }
    }

    suspend fun checkOfficial(): Pair<BabyRelease?, String> = withContext(Dispatchers.IO) {
        try { fetchOfficialRelease() to "官方版本信息获取成功" }
        catch (error: Exception) { null to (error.message ?: "官方更新源暂不可用") }
    }

    private fun fetchOfficialRelease(): BabyRelease {
        var manifestFailure: Exception? = null
        try {
            return fetchStaticReleaseManifest()
        } catch (error: Exception) {
            manifestFailure = error
        }

        try {
            return fetchOfficialReleaseLegacy()
        } catch (legacyFailure: Exception) {
            throw IllegalStateException(
                "官方更新源暂不可用：静态更新清单失败（${manifestFailure?.message ?: "未知错误"}）；GitHub 回退失败（${legacyFailure.message ?: "未知错误"}）",
                legacyFailure
            )
        }
    }

    /** Read release metadata from the rate-limit-independent raw GitHub update feed. */
    private fun fetchStaticReleaseManifest(): BabyRelease {
        val connection = URL(
            "https://raw.githubusercontent.com/rinzaki007/BabyLog/updates/latest.json"
        ).openConnection() as HttpURLConnection
        val json = try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "BabyLog-Android")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")
            check(connection.responseCode == 200) { "更新清单 HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }

        val tag = json.optString("tag").trim()
        val version = json.optString("version").trim()
        val downloadUrl = json.optString("url").trim()
        require(tag.matches(Regex("[vV]?\\d+(\\.\\d+){1,3}([-.][A-Za-z0-9.-]+)?"))) { "更新清单版本标签无效" }
        require(version.matches(Regex("\\d+(\\.\\d+){1,3}([-.][A-Za-z0-9.-]+)?"))) { "更新清单版本号无效" }
        val parsedUrl = URL(downloadUrl)
        require(parsedUrl.protocol == "https" && parsedUrl.host.equals("github.com", true) &&
            parsedUrl.path.startsWith("/rinzaki007/BabyLog/releases/download/") &&
            parsedUrl.path.endsWith(".apk", true)) { "更新清单下载地址不是官方 APK" }

        return BabyRelease(
            tag, version, json.optString("notes").ifBlank { "本次更新未提供说明。" },
            downloadUrl, json.optLong("size", 0L).coerceAtLeast(0L),
            json.optString("sha256").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) },
            "官方 GitHub"
        )
    }

    /** Compatibility fallback for installations without a published static feed. */
    private fun fetchOfficialReleaseLegacy(): BabyRelease {
        val apiFailure: Exception
        try {
            val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "BabyLog-Android")
                check(connection.responseCode == 200) { "GitHub API HTTP ${connection.responseCode}" }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").trim()
                require(tag.isNotBlank()) { "GitHub Release 缺少版本标签" }
                val assets = json.optJSONArray("assets")
                val asset = assets?.let { list ->
                    (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                        .firstOrNull { it.optString("name").trim().endsWith(".apk", ignoreCase = true) && it.optString("browser_download_url").startsWith("https://") }
                }
                if (asset != null) return BabyRelease(tag, tag.removePrefix("v").removePrefix("V"),
                    json.optString("body").ifBlank { "本次更新未提供说明。" }, asset.optString("browser_download_url"),
                    asset.optLong("size"), asset.optString("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }, "官方 GitHub")
                return fetchPublicReleasePage(tag, json.optString("body"))
            } finally { connection.disconnect() }
        } catch (error: Exception) { apiFailure = error }
        return try { fetchPublicReleasePage() } catch (pageError: Exception) {
            throw IllegalStateException("GitHub API HTTP 受限（${apiFailure.message ?: "API 不可用"}）；网页回退失败（${pageError.message ?: "解析失败"}）", pageError)
        }
    }

    private fun fetchPublicReleasePage(
        knownTag: String? = null,
        notes: String = "可从 GitHub Releases 查看本次更新说明。"
    ): BabyRelease {
        val pageConnection = URL("https://github.com/rinzaki007/BabyLog/releases/latest")
            .openConnection() as HttpURLConnection
        val pageHtml = try {
            pageConnection.connectTimeout = 12000
            pageConnection.readTimeout = 20000
            pageConnection.setRequestProperty("User-Agent", "BabyLog-Android")
            check(pageConnection.responseCode == 200) {
                "更新检查失败（HTTP ${pageConnection.responseCode}），请稍后重试"
            }
            pageConnection.inputStream.bufferedReader().use { it.readText() }
        } finally { pageConnection.disconnect() }

        val canonicalTag = Regex("""/releases/tag/([^"'?#]+)""")
            .find(pageHtml)?.groupValues?.get(1)?.replace("&amp;", "&")
        val tag = knownTag?.takeIf { it.isNotBlank() } ?: canonicalTag
            ?: error("暂时没有可用的正式 Release")
        val decodedTag = runCatching { java.net.URLDecoder.decode(tag, "UTF-8") }.getOrDefault(tag)

        fun findApk(html: String): String? {
            // GitHub release markup varies between HTML, Turbo frames and embedded JSON.
            val normalized = html
                .replace("\\u0026", "&", ignoreCase = true)
                .replace("\\/", "/")
                .replace("&amp;", "&", ignoreCase = true)
                .replace("&#x2F;", "/", ignoreCase = true)
                .replace("&quot;", "\"")
            val candidates = linkedSetOf<String>()
            val attributePattern = Regex(
                """(?:href|data-url|data-download-url)\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            )
            attributePattern.findAll(normalized).forEach { candidates += it.groupValues[1] }
            // Also inspect raw links embedded in GitHub's page JSON.
            val urlPattern = Regex(
                """(?:https?://github\.com)?/[^"'<\s]*?/releases/download/[^"'<\s]*?\.apk(?:\?[^"'<\s]*)?""",
                RegexOption.IGNORE_CASE
            )
            urlPattern.findAll(normalized).forEach { candidates += it.value }

            return candidates.asSequence().mapNotNull { raw ->
                val href = raw.trim()
                val decoded = runCatching {
                    java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
                }.getOrDefault(href)
                val absolute = when {
                    decoded.startsWith("https://") -> decoded
                    decoded.startsWith("/") -> "https://github.com$decoded"
                    else -> null
                } ?: return@mapNotNull null
                val path = absolute.substringBefore('?').substringBefore('#')
                if (path.contains("/releases/download/", ignoreCase = true) &&
                    path.endsWith(".apk", ignoreCase = true)
                ) absolute else null
            }.firstOrNull()
        }

        val expandedConnection = URL(
            "https://github.com/rinzaki007/BabyLog/releases/expanded_assets/" +
                java.net.URLEncoder.encode(decodedTag, "UTF-8").replace("+", "%20")
        ).openConnection() as HttpURLConnection
        val expandedHtml = try {
            expandedConnection.connectTimeout = 12000
            expandedConnection.readTimeout = 20000
            expandedConnection.setRequestProperty("User-Agent", "BabyLog-Android")
            if (expandedConnection.responseCode == 200)
                expandedConnection.inputStream.bufferedReader().use { it.readText() }
            else ""
        } finally { expandedConnection.disconnect() }

        val assetUrl = findApk(expandedHtml) ?: findApk(pageHtml)
            ?: error("GitHub Release（$decodedTag）附件列表中未找到 APK")
        return BabyRelease(
            decodedTag, decodedTag.removePrefix("v").removePrefix("V"),
            notes.ifBlank { "可从 GitHub Releases 查看本次更新说明。" },
            assetUrl, 0L, null, "官方 GitHub"
        )
    }

    suspend fun download(context: Context, release: BabyRelease, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "BabyLog-${release.tag}.apk")
            val temp = File(dir, file.name + ".part")
            val c = URL(release.url).openConnection() as HttpURLConnection
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                c.connectTimeout = 15000; c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "BabyLog-Android")
                check(c.responseCode == 200) { "下载失败（HTTP ${c.responseCode}）" }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
                var count = 0L
                c.inputStream.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); count += n
                            if (total > 0) withContext(Dispatchers.Main) { progress((count.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
                if (release.size > 0 && count != release.size) error("下载文件大小校验失败")
                release.sha256?.let { expected ->
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expected, true)) error("SHA-256 完整性校验失败")
                }
                require(count > 0) { "下载文件为空" }
                if (!temp.renameTo(file)) error("无法保存更新安装包")
                withContext(Dispatchers.Main) { progress(1f) }
                file
            } catch (error: Exception) {
                temp.delete()
                file.delete()
                throw error
            } finally { c.disconnect() }
        }

    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
@Composable
internal fun AppUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nasRelease by remember { mutableStateOf<BabyRelease?>(null) }
    var officialRelease by remember { mutableStateOf<BabyRelease?>(null) }
    var nasMessage by remember { mutableStateOf("尚未检查") }
    var officialMessage by remember { mutableStateOf("尚未检查") }
    var selectedSource by remember {
        mutableStateOf("")
    }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var checkingSource by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = remember { BabyUpdater.currentVersion(context) }
    val selectedRelease = when (selectedSource) { "nas" -> nasRelease; "official" -> officialRelease; else -> null }
    val hasUpdate = selectedRelease?.let { BabyUpdater.newer(it.version, current) } == true

    fun checkUpdate() {
        if (checkingSource != null || downloading) return
        scope.launch {
            checkingSource = "both"
            error = null
            downloaded = null
            selectedSource = ""
            context.getSharedPreferences(UPDATE_PREFS, 0).edit().remove("selected_source").apply()
            nasMessage = "正在检查内网版本…"
            officialMessage = "正在检查官方版本…"
            try {
                val results = coroutineScope {
                    val nas = async { BabyUpdater.checkNas(context) }
                    val official = async { BabyUpdater.checkOfficial() }
                    nas.await() to official.await()
                }
                nasRelease = results.first.first
                nasMessage = results.first.second
                officialRelease = results.second.first
                officialMessage = results.second.second
                val now = System.currentTimeMillis()
                context.getSharedPreferences(UPDATE_PREFS, 0).edit()
                    .putLong("last_check_nas", now)
                    .putLong("last_check_official", now)
                    .apply()
            } catch (e: Exception) {
                error = e.message ?: "检查更新时发生错误"
            } finally {
                checkingSource = null
            }
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("应用更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("当前 v$current", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text("先检查两个渠道的最新版本，再选择要下载的安装包。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateSourceCard(
                title = "内网更新 · NAS",
                version = nasRelease?.version,
                status = nasMessage,
                selected = selectedSource == "nas",
                enabled = checkingSource == null && !downloading && nasRelease != null,
                onClick = {
                    selectedSource = "nas"
                    context.getSharedPreferences(UPDATE_PREFS, 0).edit().putString("selected_source", "nas").apply()
                    downloaded = null
                    error = null
                },
                current = current
            )
            UpdateSourceCard(
                title = "官方更新 · GitHub",
                version = officialRelease?.version,
                status = officialMessage,
                selected = selectedSource == "official",
                enabled = checkingSource == null && !downloading && officialRelease != null,
                onClick = {
                    selectedSource = "official"
                    context.getSharedPreferences(UPDATE_PREFS, 0).edit().putString("selected_source", "official").apply()
                    downloaded = null
                    error = null
                },
                current = current
            )
            val release = selectedRelease
            if (release != null) {
                Text(
                    when {
                        BabyUpdater.newer(release.version, current) -> "所选来源有新版本：v${release.version}"
                        release.version == current -> "所选来源与当前安装版本一致"
                        else -> "所选来源版本低于当前安装版本"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                if (hasUpdate) Text(release.notes.take(360), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5)
            } else {
                Text(
                    if (nasRelease == null && officialRelease == null && nasMessage == "尚未检查" && officialMessage == "尚未检查") "请先点击“检查更新”，获取两个渠道的版本信息。"
                    else "请选择一个检查成功的更新渠道。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("正在下载 ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { checkUpdate() },
                    enabled = checkingSource == null && !downloading,
                    modifier = Modifier.weight(1f)
                ) { Text(if (checkingSource == "both") "检查中…" else "检查更新") }
                if (hasUpdate && downloaded == null && selectedRelease != null) {
                    Button(
                        enabled = checkingSource == null && !downloading,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val target = selectedRelease ?: return@Button
                            scope.launch {
                                downloading = true
                                progress = 0f
                                error = null
                                try {
                                    downloaded = BabyUpdater.download(context, target) { progress = it }
                                } catch (e: Exception) {
                                    error = e.message ?: "下载失败，请重试"
                                } finally {
                                    downloading = false
                                }
                            }
                        }
                    ) {
                        Text("下载" + (if (selectedSource == "nas") "NAS" else "GitHub") + "版本 v" + selectedRelease.version)
                    }
                }
                downloaded?.let { file ->
                    Button(
                        onClick = {
                            runCatching { BabyUpdater.install(context, file) }
                                .onFailure { error = it.message ?: "无法启动系统安装器" }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("安装更新") }
                }
            }
            Text("版本检查会同时查询 NAS 和 GitHub；选择渠道后才会下载对应安装包，不会自动切换来源。安装仍需 Android 系统确认，不会删除本地记录。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateSourceCard(
    title: String,
    version: String?,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    current: String
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (version.isNullOrBlank()) "版本：暂不可用" else "版本：v$version" +
                        if (BabyUpdater.newer(version, current)) " · 有更新" else " · 无需更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!version.isNullOrBlank() && BabyUpdater.newer(version, current))
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}
