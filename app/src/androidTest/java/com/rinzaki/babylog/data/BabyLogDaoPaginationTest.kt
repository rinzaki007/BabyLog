package com.rinzaki.babylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BabyLogDaoPaginationTest {
    private lateinit var database: BabyLogDatabase
    private lateinit var dao: BabyLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BabyLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.babyLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun timelinePageAppliesDateTypeAndOffsetFilters() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertEvent(BabyEventEntity("feed-1", "baby", "FEEDING", 100L, "{}", null, 100L))
        dao.upsertEvent(BabyEventEntity("diaper", "baby", "DIAPER", 150L, "{}", null, 150L))
        dao.upsertEvent(BabyEventEntity("feed-2", "baby", "FEEDING", 200L, "{}", null, 200L))
        dao.upsertEvent(BabyEventEntity("note", "baby", "NOTE", 180L, "{}", null, 180L))
        dao.upsertEvent(BabyEventEntity("vaccine", "baby", "VACCINE", 170L, "{}", null, 170L))

        val firstPage = dao.getTimelinePage("baby", 100L, 201L, "FEEDING", 1, 0)
        val secondPage = dao.getTimelinePage("baby", 100L, 201L, "FEEDING", 1, 1)

        assertEquals(listOf("feed-2"), firstPage.map { it.id })
        assertEquals(listOf("feed-1"), secondPage.map { it.id })
        assertEquals(2, dao.countTimelineEvents("baby", 100L, 201L, "FEEDING"))
    }

    @Test
    fun otherCategoryExcludesVaccineEvents() = runBlocking {
        dao.upsertBaby(BabyEntity("baby", "family", "宝宝", 1L, 1L, 1L))
        dao.upsertEvent(BabyEventEntity("note", "baby", "NOTE", 180L, "{}", null, 180L))
        dao.upsertEvent(BabyEventEntity("vaccine", "baby", "VACCINE", 170L, "{}", null, 170L))

        val page = dao.getTimelinePage("baby", null, null, "OTHER", 100, 0)

        assertEquals(listOf("note"), page.map { it.id })
        assertEquals(1, dao.countTimelineEvents("baby", null, null, "OTHER"))
    }
}
