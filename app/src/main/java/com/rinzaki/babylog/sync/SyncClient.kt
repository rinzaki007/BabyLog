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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object SyncPreferences {
    private const val FILE = "babylog_sync"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun server(context: Context) = prefs(context).getString("server", "") ?: ""
    fun token(context: Context) = prefs(context).getString("token", "") ?: ""
    fun familyId(context: Context) = prefs(context).getString("family_id", "") ?: ""
    fun cursor(context: Context) = prefs(context).getLong("cursor", 0L)
    fun saveServer(context: Context, value: String) { prefs(context).edit().putString("server", value.trim().trimEnd('/')).apply() }
    fun saveSession(context: Context, token: String) { prefs(context).edit().putString("token", token).apply() }
    fun saveFamily(context: Context, id: String) { prefs(context).edit().putString("family_id", id).putLong("cursor", 0L).apply() }
    fun saveCursor(context: Context, value: Long) { prefs(context).edit().putLong("cursor", value).apply() }
    fun clearSession(context: Context) { prefs(context).edit().remove("token").remove("family_id").putLong("cursor", 0L).apply() }
}

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
}

object SyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<BabyLogSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork("babylog-sync-now", ExistingWorkPolicy.REPLACE, request)
    }
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BabyLogSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("babylog-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
        enqueue(context)
    }
}

class BabyLogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val base = SyncPreferences.server(applicationContext)
        val token = SyncPreferences.token(applicationContext)
        val familyId = SyncPreferences.familyId(applicationContext)
        if (base.isBlank() || token.isBlank() || familyId.isBlank()) return Result.success()
        return try {
            val dao = RoomAccess.database(applicationContext).babyLogDao()
            val pending = dao.pendingOperations()
            if (pending.isNotEmpty()) {
                val changes = JSONArray()
                pending.forEach { operation ->
                    val item = JSONObject()
                        .put("operation_id", operation.operationId)
                        .put("entity_type", operation.entityType)
                        .put("entity_id", operation.entityId)
                        .put("operation", operation.operation)
                        .put("payload", JSONObject(operation.payloadJson))
                    operation.baseVersion?.let { item.put("base_version", it) }
                    changes.put(item)
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
                        }
                    }
                    dao.removeOperation(opId)
                }
                val conflicts = result.optJSONArray("conflicts") ?: JSONArray()
                for (i in 0 until conflicts.length()) {
                    val item = conflicts.getJSONObject(i)
                    dao.markOperationConflict(item.optString("operation_id"), item.optString("reason", "sync conflict"))
                }
            }
            var cursor = SyncPreferences.cursor(applicationContext)
            repeat(10) {
                val pulled = SyncHttp.request(base, "/api/sync/pull?family_id=$familyId&cursor=$cursor&limit=500", token = token)
                val items = pulled.optJSONArray("items") ?: JSONArray()
                if (items.length() == 0) return@repeat
                for (i in 0 until items.length()) applyRemote(dao, items.getJSONObject(i), familyId)
                cursor = pulled.optLong("next_cursor", cursor)
                SyncPreferences.saveCursor(applicationContext, cursor)
                if (items.length() < 500) return@repeat
            }
            Result.success()
        } catch (_: java.net.SocketTimeoutException) {
            Result.retry()
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            if (error.message?.contains("HTTP 401") == true) Result.failure() else Result.retry()
        }
    }

    private suspend fun applyRemote(dao: com.rinzaki.babylog.data.BabyLogDao, item: JSONObject, familyId: String) {
        val type = item.optString("entity_type")
        val id = item.optString("entity_id")
        val operation = item.optString("operation")
        val payload = item.optJSONObject("payload") ?: JSONObject()
        val version = item.optLong("version", 0L)
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
                    avatarUri = payload.optString("avatarUri").takeIf { it.isNotBlank() && it != "null" },
                    birthWeightGrams = if (payload.isNull("birthWeightGrams")) null else payload.optInt("birthWeightGrams"),
                    birthLengthCm = if (payload.isNull("birthLengthCm")) null else payload.optDouble("birthLengthCm"),
                    birthHeadCircumferenceCm = if (payload.isNull("birthHeadCircumferenceCm")) null else payload.optDouble("birthHeadCircumferenceCm"),
                    serverVersion = version
                ))
            }
            "event" -> {
                if (operation == "delete") {
                    dao.softDeleteEventFromServer(id, item.optLong("created_at", System.currentTimeMillis()))
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
        }
    }
}

private object RoomAccess {
    @Volatile private var instance: BabyLogDatabase? = null
    fun database(context: Context): BabyLogDatabase = instance ?: synchronized(this) {
        instance ?: androidx.room.Room.databaseBuilder(context, BabyLogDatabase::class.java, "babylog.db")
            .addMigrations(BabyLogDatabase.MIGRATION_1_2, BabyLogDatabase.MIGRATION_2_3, BabyLogDatabase.MIGRATION_3_4)
            .build().also { instance = it }
    }
}
