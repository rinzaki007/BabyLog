package com.rinzaki.babylog.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BabyEntity::class, BabyEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class BabyLogDatabase : RoomDatabase() {
    abstract fun babyLogDao(): BabyLogDao
}
