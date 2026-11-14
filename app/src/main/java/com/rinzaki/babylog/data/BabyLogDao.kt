package com.rinzaki.babylog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyLogDao {
    @Query("SELECT * FROM babies ORDER BY birthTime")
    fun observeBabies(): Flow<List<BabyEntity>>

    @Query("SELECT * FROM baby_events WHERE babyId = :babyId AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeEvents(babyId: String): Flow<List<BabyEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBaby(baby: BabyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: BabyEventEntity)

    @Query("UPDATE baby_events SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = 'PENDING' WHERE id = :eventId")
    suspend fun softDeleteEvent(eventId: String, deletedAt: Long)

    @Query("UPDATE baby_events SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = 'SYNCED', serverVersion = :version WHERE id = :eventId")
    suspend fun softDeleteEventFromServer(eventId: String, deletedAt: Long, version: Long)

    @Query("SELECT * FROM baby_events WHERE id = :eventId LIMIT 1")
    suspend fun getEvent(eventId: String): BabyEventEntity?

    @Query("SELECT * FROM babies WHERE id = :babyId LIMIT 1")
    suspend fun getBaby(babyId: String): BabyEntity?

    @Query("SELECT * FROM babies")
    suspend fun getAllBabies(): List<BabyEntity>

    @Query("DELETE FROM sync_operations")
    suspend fun clearAllSyncOperations()

    @Query("DELETE FROM baby_photos")
    suspend fun clearAllPhotos()

    @Query("DELETE FROM baby_events")
    suspend fun clearAllEvents()

    @Query("DELETE FROM babies")
    suspend fun clearAllBabies()

    @Query("DELETE FROM babies WHERE id = :babyId")
    suspend fun deleteBabyFromServer(babyId: String)


    @Query("SELECT * FROM baby_events")
    suspend fun getAllEvents(): List<BabyEventEntity>

    @Query("UPDATE babies SET familyId = :familyId WHERE id = :babyId")
    suspend fun setBabyFamily(babyId: String, familyId: String)

    @Query("UPDATE babies SET serverVersion = :version WHERE id = :babyId")
    suspend fun setBabyServerVersion(babyId: String, version: Long)

    @Query("UPDATE baby_events SET serverVersion = :version, syncStatus = 'SYNCED' WHERE id = :eventId")
    suspend fun setEventServerVersion(eventId: String, version: Long)

    @Query("UPDATE baby_events SET serverVersion = :version WHERE id = :eventId")
    suspend fun updateEventServerVersionForRetry(eventId: String, version: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: BabyPhotoEntity)

    @Query("SELECT * FROM baby_photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhoto(photoId: String): BabyPhotoEntity?

    @Query("SELECT * FROM baby_photos")
    suspend fun getAllPhotos(): List<BabyPhotoEntity>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'PENDING'")
    suspend fun pendingOperationCount(): Int

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'CONFLICT'")
    suspend fun conflictOperationCount(): Int

    @Query("SELECT * FROM sync_operations WHERE status = 'CONFLICT' ORDER BY createdAt DESC LIMIT 20")
    suspend fun conflictOperations(): List<SyncOperationEntity>

    @Query("UPDATE baby_photos SET serverVersion = :version, syncStatus = :status WHERE id = :photoId")
    suspend fun setPhotoServerVersion(photoId: String, version: Long, status: String)

    @Query("UPDATE baby_photos SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = 'SYNCED', serverVersion = :version WHERE id = :photoId")
    suspend fun softDeletePhotoFromServer(photoId: String, deletedAt: Long, version: Long)

    @Query("SELECT * FROM baby_photos WHERE babyId = :babyId AND deletedAt IS NULL ORDER BY takenAt DESC")
    fun observePhotos(babyId: String): Flow<List<BabyPhotoEntity>>

    @Query("UPDATE baby_photos SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = 'PENDING' WHERE id = :photoId")
    suspend fun softDeletePhoto(photoId: String, deletedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOperation(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' ORDER BY createdAt LIMIT 200")
    suspend fun pendingOperations(): List<SyncOperationEntity>

    @Query("DELETE FROM sync_operations WHERE entityType = :entityType AND entityId = :entityId AND status = 'PENDING'")
    suspend fun clearPendingOperations(entityType: String, entityId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_operations WHERE entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'CONFLICT'))")
    suspend fun hasLocalOperation(entityType: String, entityId: String): Boolean

    @Query("UPDATE sync_operations SET baseVersion = :baseVersion, status = 'PENDING', error = NULL WHERE operationId = :operationId AND status = 'CONFLICT'")
    suspend fun rebaseConflict(operationId: String, baseVersion: Long)

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId")
    suspend fun removeOperation(operationId: String)

    @Query("UPDATE sync_operations SET status = 'CONFLICT', error = :reason WHERE operationId = :operationId")
    suspend fun markOperationConflict(operationId: String, reason: String)

    @Query("UPDATE sync_operations SET status = 'PENDING', error = NULL WHERE operationId = :operationId AND status = 'CONFLICT'")
    suspend fun retryConflict(operationId: String)
}
