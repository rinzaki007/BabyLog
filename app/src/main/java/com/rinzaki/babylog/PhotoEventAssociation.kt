package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores photo links inside the existing event payload so older installations,
 * Room schemas and NAS sync records remain readable without a database migration.
 */
internal object PhotoEventAssociation {
    private const val PHOTO_IDS = "photoIds"

    fun photoIds(event: BabyEventEntity): Set<String> =
        runCatching {
            val values = JSONObject(event.payloadJson).optJSONArray(PHOTO_IDS) ?: JSONArray()
            buildSet {
                for (index in 0 until values.length()) {
                    values.optString(index).takeIf { it.isNotBlank() && it != "null" }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())

    fun withPhotoLink(event: BabyEventEntity, photoId: String, linked: Boolean): BabyEventEntity {
        require(photoId.isNotBlank()) { "照片标识不能为空" }
        val payload = runCatching { JSONObject(event.payloadJson) }.getOrElse { JSONObject() }
        val ids = photoIds(event).toMutableSet()
        if (linked) ids.add(photoId) else ids.remove(photoId)
        val normalized = ids.sorted()
        if (normalized.isEmpty()) payload.remove(PHOTO_IDS)
        else payload.put(PHOTO_IDS, JSONArray(normalized))
        return event.copy(payloadJson = payload.toString())
    }
}
