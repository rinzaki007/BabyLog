package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEventAssociationTest {
    private fun event(payload: String = """{"note":"first step"}""") = BabyEventEntity(
        id = "event-1",
        babyId = "baby-1",
        eventType = "MILESTONE",
        occurredAt = 100L,
        payloadJson = payload,
        createdBy = null,
        updatedAt = 200L
    )

    @Test fun linksPhotoWithoutDiscardingExistingEventFields() {
        val updated = PhotoEventAssociation.withPhotoLink(event(), "photo-1", true)
        assertEquals(setOf("photo-1"), PhotoEventAssociation.photoIds(updated))
        assertEquals("first step", org.json.JSONObject(updated.payloadJson).getString("note"))
    }

    @Test fun addingSamePhotoTwiceIsIdempotentAndIdsAreDeterministic() {
        val once = PhotoEventAssociation.withPhotoLink(event(), "photo-2", true)
        val twice = PhotoEventAssociation.withPhotoLink(once, "photo-2", true)
        val withAnother = PhotoEventAssociation.withPhotoLink(twice, "photo-1", true)
        assertEquals(once.payloadJson, twice.payloadJson)
        assertEquals(listOf("photo-1", "photo-2"), PhotoEventAssociation.photoIds(withAnother).toList())
    }

    @Test fun unlinkingLastPhotoRemovesOptionalField() {
        val linked = PhotoEventAssociation.withPhotoLink(event(), "photo-1", true)
        val unlinked = PhotoEventAssociation.withPhotoLink(linked, "photo-1", false)
        assertTrue(PhotoEventAssociation.photoIds(unlinked).isEmpty())
        assertFalse(org.json.JSONObject(unlinked.payloadJson).has("photoIds"))
    }

    @Test fun malformedAndLegacyPayloadsRemainReadable() {
        assertTrue(PhotoEventAssociation.photoIds(event("{broken")).isEmpty())
        assertTrue(PhotoEventAssociation.photoIds(event()).isEmpty())
    }
}
