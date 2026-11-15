package com.rinzaki.babylog.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BabyLogMigrationTest {
    @Test
    fun migratesVersionOneDatabaseToLatestWithoutLosingExistingRecords() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "babylog-migration-v1-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """CREATE TABLE babies (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    birthTime INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE baby_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    babyId TEXT NOT NULL,
                    eventType TEXT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    payloadJson TEXT NOT NULL,
                    createdBy TEXT,
                    updatedAt INTEGER NOT NULL,
                    syncStatus TEXT NOT NULL,
                    serverVersion INTEGER,
                    deletedAt INTEGER
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_events_babyId ON baby_events(babyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_events_occurredAt ON baby_events(occurredAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_events_syncStatus ON baby_events(syncStatus)")
            db.execSQL(
                """INSERT INTO babies (id, familyId, displayName, birthTime, createdAt, updatedAt)
                    VALUES ('baby-1', 'family-1', '测试宝宝', 1000, 1000, 1000)"""
            )
            db.execSQL(
                """INSERT INTO baby_events
                    (id, babyId, eventType, occurredAt, payloadJson, createdBy, updatedAt, syncStatus, serverVersion, deletedAt)
                    VALUES ('event-1', 'baby-1', 'FEEDING', 2000, '{"amountMl":120}', 'parent', 2000, 'SYNCED', 7, NULL)"""
            )
            db.version = 1
        }

        val database = Room.databaseBuilder(context, BabyLogDatabase::class.java, name)
            .addMigrations(
                BabyLogDatabase.MIGRATION_1_2,
                BabyLogDatabase.MIGRATION_2_3,
                BabyLogDatabase.MIGRATION_3_4,
                BabyLogDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()

        try {
            val sqlDb = database.openHelper.writableDatabase
            sqlDb.query("SELECT displayName, sex, serverVersion FROM babies WHERE id = 'baby-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("测试宝宝", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
            sqlDb.query("SELECT payloadJson, serverVersion FROM baby_events WHERE id = 'event-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{\"amountMl\":120}", cursor.getString(0))
                assertEquals(7L, cursor.getLong(1))
            }
            sqlDb.query("SELECT serverVersion FROM baby_photos LIMIT 0").use { cursor ->
                assertEquals(0, cursor.count)
            }
            assertEquals(5, sqlDb.version)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
