package com.payflow.common.domain.enums;

public enum ProviderType {
    PROVIDER_A("ProviderA", "Tier 1 High-Availability Gateway", 0.95, 60),
    PROVIDER_B("ProviderB", "Tier 2 Resilient Fallback Gateway", 0.80, 180),
    PROVIDER_C("ProviderC", "Tier 3 Cost-Effective Gateway", 0.60, 350);

    private final String displayName;
    private final String description;
    private final double baseSuccessRate;
    private final int expectedLatencyMs;

    ProviderType(String displayName, String description, double baseSuccessRate, int expectedLatencyMs) {
        this.displayName = displayName;
        this.description = description;
        this.baseSuccessRate = baseSuccessRate;
        this.expectedLatencyMs = expectedLatencyMs;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getBaseSuccessRate() {
        return baseSuccessRate;
    }

    public int getExpectedLatencyMs() {
        return expectedLatencyMs;
    }
}
