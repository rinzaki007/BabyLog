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
    @Query("SELECT * FROM baby_photos WHERE babyId = :babyId AND deletedAt IS NULL ORDER BY takenAt DESC")
    fun observePhotos(babyId: String): Flow<List<BabyPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: BabyPhotoEntity)

    @Query("UPDATE baby_photos SET deletedAt = :deletedAt, updatedAt = :deletedAt, syncStatus = 'PENDING' WHERE id = :photoId")
    suspend fun softDeletePhoto(photoId: String, deletedAt: Long)
}
