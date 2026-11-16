package com.rinzaki.babylog.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rinzaki.babylog.data.BabyEntity
import com.rinzaki.babylog.data.BabyEventEntity
import com.rinzaki.babylog.data.BabyLogDatabase
import com.rinzaki.babylog.data.BabyPhotoEntity
import com.rinzaki.babylog.data.SyncOperationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncConflictRecoveryTest {
    @Test
    fun restoresOnlyOwnershipConflictsWhoseEntitiesBelongToTheActiveFamily() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.babyLogDao()
            dao.upsertBaby(BabyEntity("baby-a", "family-a", "A 宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertBaby(BabyEntity("baby-b", "family-b", "B 宝宝", 1_000L, 1_000L, 1_000L))
            dao.upsertEvent(BabyEventEntity("event-a", "baby-a", "FEEDING", 2_000L, "{}", null, 2_000L))
            dao.upsertPhoto(BabyPhotoEntity("photo-a", "baby-a", "/tmp/photo-a.jpg", null, 3_000L, 3_000L, 3_000L))

            fun conflict(
                operationId: String,
                entityType: String,
                entityId: String,
                reason: String
            ) = SyncOperationEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                operation = "upsert",
                payloadJson = "{}",
                baseVersion = null,
                createdAt = 4_000L,
                status = "CONFLICT",
                error = reason
            )

            dao.enqueueOperation(conflict("baby-a-op", "baby", "baby-a", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("event-a-op", "event", "event-a", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("photo-a-op", "photo", "photo-a", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("baby-b-op", "baby", "baby-b", FAMILY_OWNERSHIP_CONFLICT_REASON))
            dao.enqueueOperation(conflict("event-a-version-op", "event", "event-a", "HTTP 409: version conflict"))

            val restored = dao.restoreFamilyOwnershipConflicts(
                "family-a",
                FAMILY_OWNERSHIP_CONFLICT_REASON
            )

            assertEquals(3, restored)
            assertEquals(
                setOf("baby-a-op", "event-a-op", "photo-a-op"),
                dao.pendingOperations().map { it.operationId }.toSet()
            )
            assertEquals(
                setOf("baby-b-op", "event-a-version-op"),
                dao.conflictOperations().map { it.operationId }.toSet()
            )

            assertEquals(4, dao.clearSyncOperationsForFamily("family-a"))
            assertEquals(
                setOf("baby-b-op"),
                dao.conflictOperations().map { it.operationId }.toSet()
            )
            assertEquals(0, dao.pendingOperationCount())
        } finally {
            database.close()
        }
    }
}
