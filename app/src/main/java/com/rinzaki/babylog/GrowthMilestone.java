package com.rinzaki.babylog;

import java.util.Objects;

public final class GrowthMilestone {
    private final String id;
    private final String title;
    private final String ageRange;
    private final int minAgeMonths;
    private final int maxAgeMonths;
    private final String category;

    public GrowthMilestone(String id, String title, String ageRange, int minAgeMonths, int maxAgeMonths, String category) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.ageRange = Objects.requireNonNull(ageRange);
        this.minAgeMonths = minAgeMonths;
        this.maxAgeMonths = maxAgeMonths;
        this.category = Objects.requireNonNull(category);
    }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAgeRange() { return ageRange; }
    public int getMinAgeMonths() { return minAgeMonths; }
    public int getMaxAgeMonths() { return maxAgeMonths; }
    public String getCategory() { return category; }
}
