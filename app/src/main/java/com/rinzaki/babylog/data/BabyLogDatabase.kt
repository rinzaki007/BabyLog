package com.rinzaki.babylog.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BabyEntity::class, BabyEventEntity::class, BabyPhotoEntity::class, SyncOperationEntity::class],
    version = 5,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS baby_photos (id TEXT NOT NULL PRIMARY KEY, babyId TEXT NOT NULL, localPath TEXT NOT NULL, caption TEXT, takenAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, syncStatus TEXT NOT NULL, remotePath TEXT, deletedAt INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_photos_babyId ON baby_photos(babyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_photos_takenAt ON baby_photos(takenAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_photos_syncStatus ON baby_photos(syncStatus)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby_photos ADD COLUMN serverVersion INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE babies ADD COLUMN serverVersion INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_operations (operationId TEXT NOT NULL PRIMARY KEY, entityType TEXT NOT NULL, entityId TEXT NOT NULL, operation TEXT NOT NULL, payloadJson TEXT NOT NULL, baseVersion INTEGER, createdAt INTEGER NOT NULL, status TEXT NOT NULL, error TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_createdAt ON sync_operations(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_status ON sync_operations(status)")
            }
        }
    }
}
