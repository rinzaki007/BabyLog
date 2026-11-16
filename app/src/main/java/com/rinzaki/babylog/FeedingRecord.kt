package com.rinzaki.babylog

import org.json.JSONObject

internal fun buildFeedingPayload(
    method: String,
    amountText: String,
    breastMinutesText: String
): JSONObject {
    require(method in setOf("母乳", "配方奶", "混合喂养")) { "请选择有效的喂养方式" }
    val payload = JSONObject().put("method", method)
    if (method != "母乳" || amountText.isNotBlank()) {
        val amountMl = amountText.toIntOrNull()
        require(amountMl != null && amountMl in 1..1000) { "请输入 1–1000 ml 的奶量" }
        payload.put("amountMl", amountMl)
    }
    if (method != "配方奶" && breastMinutesText.isNotBlank()) {
        val minutes = breastMinutesText.toIntOrNull()
        require(minutes != null && minutes in 1..180) { "亲喂时长请填写 1–180 分钟" }
        payload.put("breastMinutes", minutes)
    }
    return payload
}

internal fun feedingDescription(payloadJson: String): String {
    val payload = runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() }
    val method = payload.optString("method", "配方奶").ifBlank { "配方奶" }
    val amount = payload.optInt("amountMl").takeIf { it > 0 }?.let { "${it} ml" }
    val duration = payload.optInt("breastMinutes").takeIf { it > 0 }?.let { "亲喂 ${it} 分钟" }
    return listOfNotNull(method, amount, duration).joinToString(" · ")
}
