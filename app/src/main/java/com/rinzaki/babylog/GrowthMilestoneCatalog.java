package com.rinzaki.babylog;

import com.rinzaki.babylog.data.BabyEventEntity;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GrowthMilestoneCatalog {
    private static final List<GrowthMilestone> MILESTONES;
    static {
        List<GrowthMilestone> items = new ArrayList<>();
        items.add(new GrowthMilestone("social_smile", "出现有意识的社交微笑", "约 1–3 个月", 1, 3, "互动"));
        items.add(new GrowthMilestone("head_control", "趴着时能短暂抬头", "约 1–4 个月", 1, 4, "大运动"));
        items.add(new GrowthMilestone("reach_grab", "主动伸手触碰或抓握玩具", "约 3–6 个月", 3, 6, "精细动作"));
        items.add(new GrowthMilestone("roll_over", "完成自主翻身", "约 4–7 个月", 4, 7, "大运动"));
        items.add(new GrowthMilestone("sit", "能够独坐一会儿", "约 6–9 个月", 6, 9, "大运动"));
        items.add(new GrowthMilestone("babble", "出现重复音节或丰富咿呀声", "约 6–10 个月", 6, 10, "语言"));
        items.add(new GrowthMilestone("crawl", "开始以自己的方式移动或爬行", "约 7–12 个月", 7, 12, "大运动"));
        items.add(new GrowthMilestone("pull_to_stand", "能够扶物站立", "约 8–12 个月", 8, 12, "大运动"));
        items.add(new GrowthMilestone("first_steps", "迈出独立行走的步伐", "约 10–18 个月", 10, 18, "大运动"));
        items.add(new GrowthMilestone("first_words", "开始有意义地说出单词", "约 10–18 个月", 10, 18, "语言"));
        items.add(new GrowthMilestone("run", "能够独立跑起来", "约 18–24 个月", 18, 24, "大运动"));
        items.add(new GrowthMilestone("two_word", "开始把两个词组合表达", "约 18–30 个月", 18, 30, "语言"));
        items.add(new GrowthMilestone("jump", "尝试双脚离地跳跃", "约 24–36 个月", 24, 36, "大运动"));
        MILESTONES = Collections.unmodifiableList(items);
    }
    private GrowthMilestoneCatalog() {}
    public static List<GrowthMilestone> getMilestones() { return MILESTONES; }

    public static Map<String, BabyEventEntity> completed(List<BabyEventEntity> events) {
        Map<String, BabyEventEntity> completed = new LinkedHashMap<>();
        if (events == null) return completed;
        for (BabyEventEntity event : events) {
            if (event == null || !"MILESTONE".equals(event.getEventType()) || event.getDeletedAt() != null) continue;
            String milestoneId;
            try { milestoneId = new JSONObject(event.getPayloadJson()).optString("milestoneId", ""); }
            catch (Exception ignored) { continue; }
            if (milestoneId == null || milestoneId.trim().isEmpty()) continue;
            BabyEventEntity previous = completed.get(milestoneId);
            if (previous == null || event.getOccurredAt() > previous.getOccurredAt()) completed.put(milestoneId, event);
        }
        return completed;
    }

    public static long ageMonths(long birthTime, LocalDate today, ZoneId zoneId) {
        LocalDate birthDate;
        try { birthDate = Instant.ofEpochMilli(birthTime).atZone(zoneId).toLocalDate(); }
        catch (RuntimeException ignored) { return 0L; }
        return Math.max(0L, ChronoUnit.MONTHS.between(birthDate, today));
    }
    public static long ageMonths(long birthTime) {
        return ageMonths(birthTime, LocalDate.now(), ZoneId.systemDefault());
    }
    public static String formatRecordedDate(long occurredAt) {
        return Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA));
    }
}
