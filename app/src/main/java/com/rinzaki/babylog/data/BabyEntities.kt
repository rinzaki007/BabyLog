package com.rinzaki.babylog.data

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
    val birthHeadCircumferenceCm: Double? = null
)

@Entity(
    tableName = "baby_events",
    indices = [Index("babyId"), Index("occurredAt"), Index("syncStatus")]
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
    indices = [Index("babyId"), Index("takenAt"), Index("syncStatus")]
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
    val deletedAt: Long? = null
)
