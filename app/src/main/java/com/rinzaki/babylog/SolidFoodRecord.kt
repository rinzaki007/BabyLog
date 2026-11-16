package com.rinzaki.babylog

import org.json.JSONObject

val solidFoodPresets = listOf("米粉", "蔬菜泥", "水果泥", "肉泥", "蛋黄", "其他")
val solidFoodUnits = listOf("g", "勺", "ml", "口")
val solidFoodStatuses = listOf("正常", "少量尝试", "未吃完", "拒绝")

fun solidFoodDescription(payloadJson: String): String {
    val payload = runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() }
    val name = payload.optString("foodName").ifBlank { "辅食" }
    val amount = payload.optDouble("amount", Double.NaN)
    val unit = payload.optString("unit")
    val quantity = if (amount.isFinite() && amount > 0.0 && unit.isNotBlank()) {
        val number = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
        " · $number $unit"
    } else ""
    val status = payload.optString("eatingStatus")
    return name + quantity + if (status.isNotBlank() && status != "正常") " · $status" else ""
}
