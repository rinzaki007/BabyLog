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

    @Query("""
        SELECT * FROM baby_events
        WHERE babyId = :babyId
          AND deletedAt IS NULL
          AND eventType != 'VACCINE'
          AND occurredAt >= :startAt
          AND occurredAt <= :endAt
        ORDER BY occurredAt DESC, id DESC
    """)
    fun observeNonVaccineEventsInRange(babyId: String, startAt: Long, endAt: Long): Flow<List<BabyEventEntity>>

    @Query("""
        SELECT * FROM baby_events
        WHERE babyId = :babyId
          AND deletedAt IS NULL
          AND eventType = :eventType
        ORDER BY occurredAt DESC, id DESC
    """)
    fun observeEventsByType(babyId: String, eventType: String): Flow<List<BabyEventEntity>>

    @Query("SELECT CAST(COUNT(*) AS TEXT) || ':' || CAST(COALESCE(MAX(updatedAt), 0) AS TEXT) FROM baby_events WHERE babyId = :babyId")
    fun observeEventRevision(babyId: String): Flow<String>
    @Query("""
        SELECT * FROM baby_events
        WHERE babyId = :babyId
          AND deletedAt IS NULL
          AND eventType != 'VACCINE'
          AND (:startAt IS NULL OR occurredAt >= :startAt)
          AND (:endAt IS NULL OR occurredAt < :endAt)
          AND (
              :eventType IS NULL
              OR (:eventType = 'OTHER' AND eventType NOT IN ('FEEDING', 'DIAPER', 'SLEEP', 'GROWTH', 'SUPPLEMENT', 'SOLID_FOOD', 'VACCINE'))
              OR eventType = :eventType
          )
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getTimelinePage(babyId: String, startAt: Long?, endAt: Long?, eventType: String?, limit: Int, offset: Int): List<BabyEventEntity>

    @Query("""
        SELECT COUNT(*) FROM baby_events
        WHERE babyId = :babyId
          AND deletedAt IS NULL
          AND eventType != 'VACCINE'
          AND (:startAt IS NULL OR occurredAt >= :startAt)
          AND (:endAt IS NULL OR occurredAt < :endAt)
          AND (
              :eventType IS NULL
              OR (:eventType = 'OTHER' AND eventType NOT IN ('FEEDING', 'DIAPER', 'SLEEP', 'GROWTH', 'SUPPLEMENT', 'SOLID_FOOD', 'VACCINE'))
              OR eventType = :eventType
          )
    """)
    suspend fun countTimelineEvents(babyId: String, startAt: Long?, endAt: Long?, eventType: String?): Int

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

    // Never clear operations created while an authoritative snapshot is in flight.
    @Query(
        """
        DELETE FROM sync_operations
        WHERE createdAt < :snapshotStartedAt
          AND (
              (entityType = 'baby' AND entityId IN (
                  SELECT id FROM babies WHERE familyId = :familyId
              ))
              OR (entityType = 'event' AND entityId IN (
                  SELECT e.id FROM baby_events e
                  INNER JOIN babies b ON b.id = e.babyId
                  WHERE b.familyId = :familyId
              ))
              OR (entityType = 'photo' AND entityId IN (
                  SELECT p.id FROM baby_photos p
                  INNER JOIN babies b ON b.id = p.babyId
                  WHERE b.familyId = :familyId
              ))
          )
        """
    )
    suspend fun clearSyncOperationsForFamily(familyId: String, snapshotStartedAt: Long): Int


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

    @Query("SELECT * FROM babies WHERE TRIM(familyId) = '' OR familyId = :unassignedFamilyId")
    suspend fun getUnassignedBabies(unassignedFamilyId: String): List<BabyEntity>

    @Query(
        """
        UPDATE babies
        SET familyId = :familyId
        WHERE TRIM(familyId) = '' OR familyId = :unassignedFamilyId
        """
    )
    suspend fun assignUnassignedBabiesToFamily(familyId: String, unassignedFamilyId: String): Int

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

    @Query("SELECT * FROM sync_operations WHERE status = 'CONFLICT' ORDER BY createdAt DESC")
    suspend fun conflictOperations(): List<SyncOperationEntity>

    @Query(
        """
        UPDATE sync_operations
        SET status = 'PENDING', error = NULL
        WHERE status = 'CONFLICT'
          AND error = :ownershipReason
          AND (
              (entityType = 'baby' AND entityId IN (
                  SELECT id FROM babies WHERE familyId = :familyId
              ))
              OR (entityType = 'event' AND entityId IN (
                  SELECT e.id FROM baby_events e
                  INNER JOIN babies b ON b.id = e.babyId
                  WHERE b.familyId = :familyId
              ))
              OR (entityType = 'photo' AND entityId IN (
                  SELECT p.id FROM baby_photos p
                  INNER JOIN babies b ON b.id = p.babyId
                  WHERE b.familyId = :familyId
              ))
          )
        """
    )
    suspend fun restoreFamilyOwnershipConflicts(familyId: String, ownershipReason: String): Int


    @Query("UPDATE baby_photos SET originalOnDevice = :available, photoMimeType = :mimeType WHERE id = :photoId")
    suspend fun setPhotoOriginalState(photoId: String, available: Boolean, mimeType: String?)

    @Query("UPDATE baby_photos SET serverVersion = :version, syncStatus = :status WHERE id = :photoId")
    suspend fun setPhotoServerVersion(photoId: String, version: Long, status: String)

    @Query("UPDATE baby_photos SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = 'SYNCED', serverVersion = :version WHERE id = :photoId")
    suspend fun softDeletePhotoFromServer(photoId: String, deletedAt: Long, version: Long)

    @Query("SELECT * FROM baby_photos WHERE babyId = :babyId AND deletedAt IS NULL ORDER BY takenAt DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observePhotoPage(babyId: String, limit: Int, offset: Int): Flow<List<BabyPhotoEntity>>

    @Query("SELECT * FROM baby_photos WHERE babyId = :babyId AND deletedAt IS NULL ORDER BY takenAt DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPhotoPage(babyId: String, limit: Int, offset: Int): List<BabyPhotoEntity>

    @Query("""
        SELECT * FROM baby_photos
        WHERE babyId = :babyId
          AND deletedAt IS NULL
          AND takenAt >= :startAt
          AND takenAt < :endAt
        ORDER BY takenAt DESC, id DESC
        LIMIT :limit
    """)
    suspend fun getPhotosInRange(babyId: String, startAt: Long, endAt: Long, limit: Int): List<BabyPhotoEntity>

    @Query("SELECT COUNT(*) FROM baby_photos WHERE babyId = :babyId AND deletedAt IS NULL")
    fun observePhotoCount(babyId: String): Flow<Int>

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

    @Query(
        """
        UPDATE sync_operations
        SET payloadJson = :payloadJson
        WHERE entityType = 'baby'
          AND entityId = :babyId
          AND status IN ('PENDING', 'CONFLICT')
        """
    )
    suspend fun updateBabyOperationPayload(babyId: String, payloadJson: String): Int

    @Query("UPDATE sync_operations SET baseVersion = :baseVersion, status = 'PENDING', error = NULL WHERE operationId = :operationId AND status = 'CONFLICT'")
    suspend fun rebaseConflict(operationId: String, baseVersion: Long)

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId")
    suspend fun removeOperation(operationId: String)

    @Query("UPDATE sync_operations SET status = 'CONFLICT', error = :reason WHERE operationId = :operationId")
    suspend fun markOperationConflict(operationId: String, reason: String)

    @Query("UPDATE sync_operations SET status = 'PENDING', error = NULL WHERE operationId = :operationId AND status = 'CONFLICT'")
    suspend fun retryConflict(operationId: String)
}
