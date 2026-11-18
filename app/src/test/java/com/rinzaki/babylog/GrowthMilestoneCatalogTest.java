package com.rinzaki.babylog;

import com.rinzaki.babylog.data.BabyEventEntity;
import org.junit.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class GrowthMilestoneCatalogTest {
    private BabyEventEntity event(String id, long time, String payload, Long deletedAt) {
        return new BabyEventEntity(id, "baby-1", "MILESTONE", time, payload, null, time, "PENDING", null, deletedAt);
    }
    @Test public void catalogHasStableUniqueIdsAndValidAgeRanges() {
        assertFalse(GrowthMilestoneCatalog.getMilestones().isEmpty());
        Set<String> ids = GrowthMilestoneCatalog.getMilestones().stream().map(GrowthMilestone::getId).collect(Collectors.toSet());
        assertEquals(GrowthMilestoneCatalog.getMilestones().size(), ids.size());
        assertTrue(GrowthMilestoneCatalog.getMilestones().stream().allMatch(m -> m.getMinAgeMonths() >= 0 && m.getMaxAgeMonths() >= m.getMinAgeMonths()));
    }
    @Test public void completionIgnoresDeletedMalformedAndOtherTypesAndKeepsLatest() {
        BabyEventEntity other = new BabyEventEntity("other", "baby-1", "GROWTH", 500L, "{\"milestoneId\":\"sit\"}", null, 500L, null);
        Map<String, BabyEventEntity> completed = GrowthMilestoneCatalog.completed(Arrays.asList(
                event("first", 100L, "{\"milestoneId\":\"first_steps\"}", null),
                event("latest", 200L, "{\"milestoneId\":\"first_steps\"}", null),
                event("deleted", 300L, "{\"milestoneId\":\"first_steps\"}", 301L),
                event("invalid", 400L, "not-json", null), other));
        assertEquals(Set.of("first_steps"), completed.keySet());
        assertEquals("latest", completed.get("first_steps").getId());
    }
    @Test public void ageUsesCalendarMonthsAndNeverReturnsNegative() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long birth = LocalDate.of(2026, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli();
        assertEquals(1L, GrowthMilestoneCatalog.ageMonths(birth, LocalDate.of(2026, 2, 15), zone));
        assertEquals(0L, GrowthMilestoneCatalog.ageMonths(birth, LocalDate.of(2026, 1, 1), zone));
    }
}
