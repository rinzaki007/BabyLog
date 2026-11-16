package com.rinzaki.babylog

import org.json.JSONObject

internal val supplementPresets = listOf("维生素AD", "维生素D3", "铁剂", "水", "DHA", "益生菌", "乳糖酶", "其他")
internal val supplementUnits = listOf("滴", "ml", "粒", "包", "mg", "次")

internal fun supplementDefaultUnit(name: String): String = when (name) {
    "水" -> "ml"
    "铁剂" -> "ml"
    "益生菌" -> "包"
    "DHA" -> "滴"
    else -> "滴"
}

internal fun supplementDescription(payloadJson: String): String {
    val payload = runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() }
    val name = payload.optString("supplementName", "补充项目").ifBlank { "补充项目" }
    val amount = payload.optDouble("amount", Double.NaN)
    val unit = payload.optString("unit")
    val amountLabel = if (!amount.isNaN() && amount > 0.0) {
        val formatted = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
        " · $formatted $unit".trimEnd()
    } else ""
    return name + amountLabel
}
