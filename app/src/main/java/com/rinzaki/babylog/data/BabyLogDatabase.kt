package com.rinzaki.babylog.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BabyEntity::class, BabyEventEntity::class],
    version = 2,
    exportSchema = true
)
abstract class BabyLogDatabase : RoomDatabase() {
    abstract fun babyLogDao(): BabyLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE babies ADD COLUMN sex TEXT")
                db.execSQL("ALTER TABLE babies ADD COLUMN avatarUri TEXT")
                db.execSQL("ALTER TABLE babies ADD COLUMN birthWeightGrams INTEGER")
                db.execSQL("ALTER TABLE babies ADD COLUMN birthLengthCm REAL")
                db.execSQL("ALTER TABLE babies ADD COLUMN birthHeadCircumferenceCm REAL")
            }
        }
    }
}
