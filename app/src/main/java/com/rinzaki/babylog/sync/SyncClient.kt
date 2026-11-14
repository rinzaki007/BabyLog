package com.rinzaki.babylog.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyLogDatabase
import com.rinzaki.babylog.data.BabyPhotoEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

object SyncPreferences {
    private const val FILE = "babylog_sync"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun server(context: Context) = prefs(context).getString("server", "") ?: ""
    fun token(context: Context) = prefs(context).getString("token", "") ?: ""
    fun accountEmail(context: Context) = prefs(context).getString("account_email", "") ?: ""
    fun saveAccountEmail(context: Context, value: String) { prefs(context).edit().putString("account_email", value.trim().lowercase()).apply() }
    fun familyId(context: Context) = prefs(context).getString("family_id", "") ?: ""
    fun cursor(context: Context) = prefs(context).getLong("cursor", 0L)
    fun saveServer(context: Context, value: String) { prefs(context).edit().putString("server", value.trim().trimEnd('/')).apply() }
    fun saveSession(context: Context, token: String) { prefs(context).edit().putString("token", token).remove("family_id").putLong("cursor", 0L).apply() }
    fun saveFamily(context: Context, id: String) { prefs(context).edit().putString("family_id", id).putLong("cursor", 0L).apply() }
    fun saveCursor(context: Context, value: Long) { prefs(context).edit().putLong("cursor", value).apply() }
    fun clearSession(context: Context) { prefs(context).edit().remove("token").remove("family_id").remove("account_email").putLong("cursor", 0L).apply() }
    fun lastSyncAt(context: Context) = prefs(context).getLong("last_sync_at", 0L)
    fun lastSyncError(context: Context) = prefs(context).getString("last_sync_error", null)
    fun markSyncSuccess(context: Context) { prefs(context).edit().putLong("last_sync_at", System.currentTimeMillis()).remove("last_sync_error").apply() }
    fun markSyncFailure(context: Context, reason: String) { prefs(context).edit().putString("last_sync_error", reason.take(240)).apply() }
}

class SyncHttpException(val statusCode: Int, message: String) : IllegalStateException(message)

object SyncHttp {
    fun request(base: String, path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): JSONObject {
        val connection = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code: " + runCatching { JSONObject(response).optString("detail") }.getOrDefault(response.take(180)))
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun uploadAvatar(base: String, familyId: String, babyId: String, token: String, file: File) {
        val boundary = "BabyLogAvatar-${java.util.UUID.randomUUID()}"
        val connection = (URL(base.trimEnd('/') + "/api/babies/$babyId/avatar?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=" + 34.toChar() + "file" + 34.toChar() + "; filename=" + 34.toChar() + "avatar.jpg" + 34.toChar() + "\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                file.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncHttpException(code, "头像上传失败 HTTP $code: $response")
            }
        } finally { connection.disconnect() }
    }

    fun downloadAvatar(base: String, familyId: String, babyId: String, token: String, destination: File) {
        val connection = (URL(base.trimEnd('/') + "/api/babies/$babyId/avatar?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncHttpException(code, "头像下载失败 HTTP $code: $response")
            }
            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, destination.name + ".part")
            try {
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                if (temp.length() <= 0L) throw java.io.IOException("头像下载内容为空")
                if (destination.exists() && !destination.delete()) throw java.io.IOException("无法替换旧头像")
                if (!temp.renameTo(destination)) throw java.io.IOException("无法保存头像")
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        } finally { connection.disconnect() }
    }

    fun deleteAvatar(base: String, familyId: String, babyId: String, token: String) {
        val connection = (URL(base.trimEnd('/') + "/api/babies/$babyId/avatar?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw SyncHttpException(code, "头像删除失败 HTTP $code")
        } finally { connection.disconnect() }
    }

    fun uploadPhoto(base: String, familyId: String, photoId: String, token: String, file: File, mimeType: String) {
        val boundary = "BabyLog-${java.util.UUID.randomUUID()}"
        val connection = (URL(base.trimEnd('/') + "/api/photos/upload?family_id=$familyId&photo_id=$photoId").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=" + 34.toChar() + "file" + 34.toChar() + "; filename=" + 34.toChar() + file.name + 34.toChar() + "\r\n")
                output.writeBytes("Content-Type: $mimeType\r\n\r\n")
                file.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw SyncHttpException(code, "照片上传失败 HTTP $code: " + runCatching { JSONObject(response).optString("detail") }.getOrDefault(response.take(160)))
        } finally { connection.disconnect() }
    }

    fun downloadPhoto(base: String, familyId: String, photoId: String, token: String, destination: File) {
        val connection = (URL(base.trimEnd('/') + "/api/photos/$photoId?family_id=$familyId").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncHttpException(code, "照片下载失败 HTTP $code: $message")
            }
            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, destination.name + ".part")
            try {
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                val expectedLength = connection.contentLengthLong
                if (expectedLength >= 0L && temp.length() != expectedLength) {
                    throw java.io.IOException("照片下载不完整：期望 ${expectedLength} 字节，实际 ${temp.length()} 字节")
                }
                if (temp.length() <= 0L) throw java.io.IOException("照片下载内容为空")
                if (destination.exists() && !destination.delete()) throw java.io.IOException("无法替换旧照片文件")
                if (!temp.renameTo(destination)) throw java.io.IOException("无法保存下载的照片")
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        } finally { connection.disconnect() }
    }
}

object SyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<BabyLogSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork("babylog-sync-now", ExistingWorkPolicy.KEEP, request)
    }
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BabyLogSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("babylog-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
        enqueue(context)
    }
}

internal fun syncOperationBelongsToFamily(ownerFamilyId: String?, activeFamilyId: String): Boolean =
    activeFamilyId.isNotBlank() && ownerFamilyId != null && ownerFamilyId == activeFamilyId

class BabyLogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val base = SyncPreferences.server(applicationContext)
        val token = SyncPreferences.token(applicationContext)
        val familyId = SyncPreferences.familyId(applicationContext)
        if (base.isBlank() || token.isBlank() || familyId.isBlank()) return Result.success()
        return try {
            val dao = RoomAccess.database(applicationContext).babyLogDao()
            var batch = 0
            while (batch < 10) {
                val pending = dao.pendingOperations()
                if (pending.isEmpty()) break
                val changes = JSONArray()
                pending.forEach { operation ->
                    val ownerFamilyId = when (operation.entityType) {
                        "baby" -> dao.getBaby(operation.entityId)?.familyId
                        "event" -> dao.getEvent(operation.entityId)?.let { dao.getBaby(it.babyId)?.familyId }
                        "photo" -> dao.getPhoto(operation.entityId)?.let { dao.getBaby(it.babyId)?.familyId }
                        else -> familyId
                    }
                    if (!syncOperationBelongsToFamily(ownerFamilyId, familyId)) {
                        dao.markOperationConflict(operation.operationId, "这条数据属于其他家庭空间，已暂停上传。请切回原家庭完成同步，或先核对本机数据归属。")
                        return@forEach
                    }
                    if (operation.entityType == "baby" && operation.operation == "upsert") {
                        val baby = dao.getBaby(operation.entityId)
                        if (baby != null) {
                            val avatarFile = baby.avatarUri?.let { File(it) }
                            if (avatarFile != null && avatarFile.isFile) {
                                SyncHttp.uploadAvatar(base, familyId, baby.id, token, avatarFile)
                            } else {
                                SyncHttp.deleteAvatar(base, familyId, baby.id, token)
                            }
                        }
                    }
                    if (operation.entityType == "photo" && operation.operation == "upsert") {
                        val photo = dao.getPhoto(operation.entityId)
                        if (photo == null) {
                            dao.markOperationConflict(operation.operationId, "照片记录不存在，无法上传")
                            return@forEach
                        }
                        val localFile = File(photo.localPath)
                        if (!localFile.isFile) {
                            // A previous upload may already exist on the NAS even if the local cache was removed.
                            // Restore it before deciding that this queued operation is unrecoverable.
                            try {
                                SyncHttp.downloadPhoto(base, familyId, photo.id, token, localFile)
                            } catch (restoreError: Exception) {
                                dao.markOperationConflict(operation.operationId, "本地照片和 NAS 备份均不可用：${restoreError.message ?: "照片不存在"}")
                                dao.setPhotoServerVersion(photo.id, -1L, "CONFLICT")
                                return@forEach
                            }
                        }
                        try {
                            SyncHttp.uploadPhoto(base, familyId, photo.id, token, localFile, mimeTypeForPath(photo.localPath))
                        } catch (error: SyncHttpException) {
                            if (error.statusCode in 400..499) {
                                dao.markOperationConflict(operation.operationId, error.message ?: "照片上传被服务器拒绝")
                                dao.setPhotoServerVersion(photo.id, -1L, "CONFLICT")
                                return@forEach
                            }
                            throw error
                        }
                    }
                    val item = JSONObject()
                        .put("operation_id", operation.operationId)
                        .put("entity_type", operation.entityType)
                        .put("entity_id", operation.entityId)
                        .put("operation", operation.operation)
                        .put("payload", JSONObject(operation.payloadJson))
                    operation.baseVersion?.let { item.put("base_version", it) }
                    changes.put(item)
                }
                if (changes.length() == 0) {
                    batch++
                    continue
                }
                val result = SyncHttp.request(base, "/api/sync/push", "POST", token,
                    JSONObject().put("family_id", familyId).put("changes", changes))
                val accepted = result.optJSONArray("accepted") ?: JSONArray()
                for (i in 0 until accepted.length()) {
                    val item = accepted.getJSONObject(i)
                    val opId = item.optString("operation_id")
                    val version = item.optLong("version", -1L)
                    val operation = pending.firstOrNull { it.operationId == opId }
                    if (operation != null && version >= 0) {
                        when (operation.entityType) {
                            "baby" -> dao.setBabyServerVersion(operation.entityId, version)
                            "event" -> dao.setEventServerVersion(operation.entityId, version)
                            "photo" -> dao.setPhotoServerVersion(operation.entityId, version, "SYNCED")
                        }
                    }
                    dao.removeOperation(opId)
                }
                val conflicts = result.optJSONArray("conflicts") ?: JSONArray()
                for (i in 0 until conflicts.length()) {
                    val item = conflicts.getJSONObject(i)
                    val operationId = item.optString("operation_id")
                    val operation = pending.firstOrNull { it.operationId == operationId }
                    val serverVersion = item.optLong("server_version", -1L)
                    if (operation != null && serverVersion >= 0L) {
                        // Rebase the local write onto the server's current version.
                        // Keep the local payload; never replace it with stale remote data.
                        when (operation.entityType) {
                            "baby" -> dao.setBabyServerVersion(operation.entityId, serverVersion)
                            "event" -> dao.setEventServerVersion(operation.entityId, serverVersion)
                            "photo" -> dao.setPhotoServerVersion(operation.entityId, serverVersion, "PENDING")
                        }
                        dao.rebaseConflict(operationId, serverVersion)
                    } else {
                        dao.markOperationConflict(operationId, item.optString("reason", "sync conflict"))
                    }
                }
                batch++
            }
            var cursor = SyncPreferences.cursor(applicationContext)
            var page = 0
            while (page < 10) {
                val pulled = SyncHttp.request(base, "/api/sync/pull?family_id=$familyId&cursor=$cursor&limit=500", token = token)
                val items = pulled.optJSONArray("items") ?: JSONArray()
                val next = pulled.optLong("next_cursor", cursor)
                if (next <= cursor) break
                for (i in 0 until items.length()) applyRemote(dao, items.getJSONObject(i), familyId)
                cursor = next
                SyncPreferences.saveCursor(applicationContext, cursor)
                page++
                // The server may filter obsolete photo-upserts while retaining
                // their cursor positions. Use scanned_count, not visible items,
                // to decide whether another page is available.
                if (pulled.optInt("scanned_count", items.length()) < 500) break
            }
            SyncPreferences.markSyncSuccess(applicationContext)
            Result.success()
        } catch (error: java.net.SocketTimeoutException) {
            SyncPreferences.markSyncFailure(applicationContext, error.message ?: "连接超时")
            Result.retry()
        } catch (error: java.io.IOException) {
            SyncPreferences.markSyncFailure(applicationContext, error.message ?: "网络连接失败")
            Result.retry()
        } catch (error: Exception) {
            SyncPreferences.markSyncFailure(applicationContext, error.message ?: "同步失败")
            val message = error.message.orEmpty()
            if (message.contains("HTTP 401") || Regex("HTTP 4[0-9]{2}").containsMatchIn(message)) Result.failure() else Result.retry()
        }
    }

    private suspend fun applyRemote(dao: com.rinzaki.babylog.data.BabyLogDao, item: JSONObject, familyId: String) {
        val type = item.optString("entity_type")
        val id = item.optString("entity_id")
        val operation = item.optString("operation")
        val payload = item.optJSONObject("payload") ?: JSONObject()
        val version = item.optLong("version", 0L)
        if (dao.hasLocalOperation(type, id)) return
        when (type) {
            "baby" -> {
                if (operation == "delete") return
                dao.upsertBaby(BabyEntity(
                    id = payload.optString("id", id),
                    familyId = familyId,
                    displayName = payload.optString("displayName", "宝宝"),
                    birthTime = payload.optLong("birthTime"),
                    createdAt = payload.optLong("createdAt"),
                    updatedAt = payload.optLong("updatedAt"),
                    sex = payload.optString("sex").takeIf { it.isNotBlank() && it != "null" },
                    avatarUri = if (payload.optBoolean("hasAvatar", false)) {
                        val avatarFile = File(applicationContext.filesDir, "baby_avatars/$id.jpg")
                        SyncHttp.downloadAvatar(SyncPreferences.server(applicationContext), familyId, id,
                            SyncPreferences.token(applicationContext), avatarFile)
                        avatarFile.absolutePath
                    } else {
                        File(applicationContext.filesDir, "baby_avatars/$id.jpg").delete()
                        null
                    },
                    birthWeightGrams = if (payload.isNull("birthWeightGrams")) null else payload.optInt("birthWeightGrams"),
                    birthLengthCm = if (payload.isNull("birthLengthCm")) null else payload.optDouble("birthLengthCm"),
                    birthHeadCircumferenceCm = if (payload.isNull("birthHeadCircumferenceCm")) null else payload.optDouble("birthHeadCircumferenceCm"),
                    serverVersion = version
                ))
            }
            "event" -> {
                if (operation == "delete") {
                    dao.softDeleteEventFromServer(id, item.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L, version)
                } else {
                    dao.upsertEvent(BabyEventEntity(
                        id = payload.optString("id", id),
                        babyId = payload.optString("babyId"),
                        eventType = payload.optString("eventType"),
                        occurredAt = payload.optLong("occurredAt"),
                        payloadJson = payload.optString("payloadJson", "{}"),
                        createdBy = payload.optString("createdBy").takeIf { it.isNotBlank() && it != "null" },
                        updatedAt = payload.optLong("updatedAt"),
                        syncStatus = "SYNCED",
                        serverVersion = version,
                        deletedAt = if (payload.isNull("deletedAt")) null else payload.optLong("deletedAt")
                    ))
                }
            }
            "photo" -> {
                val existing = dao.getPhoto(id)
                if (operation == "delete") {
                    dao.softDeletePhotoFromServer(id, item.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L, version)
                    existing?.let { File(it.localPath).delete() }
                } else {
                    val mime = payload.optString("mimeType", "image/jpeg")
                    val extension = extensionForMime(mime)
                    val localFile = existing?.let { File(it.localPath) }
                    val target = if (localFile != null && localFile.isFile && existing?.serverVersion == version) localFile
                        else File(applicationContext.filesDir, "baby_album/$id$extension")
                    if (localFile == null || !localFile.isFile || existing?.serverVersion != version) {
                        try {
                            SyncHttp.downloadPhoto(SyncPreferences.server(applicationContext), familyId, id,
                                SyncPreferences.token(applicationContext), target)
                        } catch (error: SyncHttpException) {
                            // The server may retain an old photo-upsert change after its
                            // photo row/file has disappeared (for example after a restore
                            // or an incomplete historical sync). Treat only 404 as a
                            // missing remote photo, record its tombstone locally, and
                            // continue pulling later changes instead of retrying forever.
                            if (error.statusCode == 404) {
                                dao.softDeletePhotoFromServer(
                                    id,
                                    item.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L,
                                    version
                                )
                                return
                            }
                            throw error
                        }
                    }
                    dao.upsertPhoto(BabyPhotoEntity(
                        id = id,
                        babyId = payload.optString("babyId"),
                        localPath = target.absolutePath,
                        caption = payload.optString("caption").takeIf { it.isNotBlank() && it != "null" },
                        takenAt = payload.optLong("takenAt"),
                        createdAt = payload.optLong("createdAt"),
                        updatedAt = payload.optLong("updatedAt"),
                        syncStatus = "SYNCED",
                        remotePath = "/api/photos/$id",
                        deletedAt = null,
                        serverVersion = version
                    ))
                }
            }
        }
    }
}

private object RoomAccess {
    @Volatile private var instance: BabyLogDatabase? = null
    fun database(context: Context): BabyLogDatabase = instance ?: synchronized(this) {
        instance ?: androidx.room.Room.databaseBuilder(context, BabyLogDatabase::class.java, "babylog.db")
            .addMigrations(BabyLogDatabase.MIGRATION_1_2, BabyLogDatabase.MIGRATION_2_3, BabyLogDatabase.MIGRATION_3_4, BabyLogDatabase.MIGRATION_4_5)
            .enableMultiInstanceInvalidation().build().also { instance = it }
    }
}

fun babySyncPayload(baby: BabyEntity): JSONObject = JSONObject()
    .put("id", baby.id).put("familyId", baby.familyId).put("displayName", baby.displayName)
    .put("birthTime", baby.birthTime).put("createdAt", baby.createdAt).put("updatedAt", baby.updatedAt)
    .put("sex", baby.sex)
    .put("hasAvatar", !baby.avatarUri.isNullOrBlank())
    .put("birthWeightGrams", baby.birthWeightGrams)
    .put("birthLengthCm", baby.birthLengthCm)
    .put("birthHeadCircumferenceCm", baby.birthHeadCircumferenceCm)

fun eventSyncPayload(event: BabyEventEntity): JSONObject = JSONObject()
    .put("id", event.id).put("babyId", event.babyId).put("eventType", event.eventType)
    .put("occurredAt", event.occurredAt).put("payloadJson", event.payloadJson)
    .put("createdBy", event.createdBy).put("updatedAt", event.updatedAt)
    .put("deletedAt", event.deletedAt)

fun mimeTypeForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    else -> "image/jpeg"
}

fun extensionForMime(mime: String): String = when (mime.lowercase()) {
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "image/heic" -> ".heic"
    "image/heif" -> ".heif"
    else -> ".jpg"
}

fun photoSyncPayload(photo: BabyPhotoEntity, mimeType: String): JSONObject = JSONObject()
    .put("id", photo.id)
    .put("babyId", photo.babyId)
    .put("caption", photo.caption)
    .put("takenAt", photo.takenAt)
    .put("createdAt", photo.createdAt)
    .put("updatedAt", photo.updatedAt)
    .put("mimeType", mimeType)
