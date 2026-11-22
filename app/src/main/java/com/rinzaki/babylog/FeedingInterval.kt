package com.rinzaki.babylog

import com.rinzaki.babylog.data.BabyEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Returns the latest valid feeding timestamp from the current local calendar day.
 * Yesterday's records and future-dated records are intentionally ignored.
 */
internal fun lastFeedingTimeToday(
    events: List<BabyEventEntity>,
    nowEpochMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return events.asSequence()
        .filter { it.eventType == "FEEDING" }
        .map { it.occurredAt }
        .filter { it >= start && it < end && it <= nowEpochMs }
        .maxOrNull()
}
