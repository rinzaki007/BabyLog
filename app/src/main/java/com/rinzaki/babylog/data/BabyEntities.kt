package com.rinzaki.babylog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "babies")
data class BabyEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val displayName: String,
    val birthTime: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val sex: String? = null,
    val avatarUri: String? = null,
    val birthWeightGrams: Int? = null,
    val birthLengthCm: Double? = null,
    val birthHeadCircumferenceCm: Double? = null,
    val serverVersion: Long? = null
)

@Entity(
    tableName = "baby_events",
    indices = [Index(value = ["babyId", "deletedAt", "occurredAt", "id"], name = "index_baby_events_babyId_deletedAt_occurredAt_id"), Index("occurredAt"), Index("syncStatus")]
)
data class BabyEventEntity(
    @PrimaryKey val id: String,
    val babyId: String,
    val eventType: String,
    val occurredAt: Long,
    val payloadJson: String,
    val createdBy: String?,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
    val serverVersion: Long? = null,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "baby_photos",
    indices = [Index(value = ["babyId", "deletedAt", "takenAt", "id"], name = "index_baby_photos_babyId_deletedAt_takenAt_id"), Index("takenAt"), Index("syncStatus")]
)
data class BabyPhotoEntity(
    @PrimaryKey val id: String,
    val babyId: String,
    val localPath: String,
    val caption: String?,
    val takenAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
    val remotePath: String? = null,
    val deletedAt: Long? = null,
    val serverVersion: Long? = null,
    @ColumnInfo(defaultValue = "1") val originalOnDevice: Boolean = true,
    val photoMimeType: String? = null
)

@Entity(tableName = "sync_operations", indices = [Index("createdAt"), Index("status")])
data class SyncOperationEntity(
    @PrimaryKey val operationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadJson: String,
    val baseVersion: Long?,
    val createdAt: Long,
    val status: String = "PENDING",
    val error: String? = null
)
