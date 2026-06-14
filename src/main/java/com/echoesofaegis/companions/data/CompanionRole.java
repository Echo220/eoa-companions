package com.echoesofaegis.companions.data;

import java.util.Locale;

public enum CompanionRole {
    DEFENDER("Defender"),
    SCOUT("Scout"),
    PASSIVE("Passive"),
    AGGRESSIVE("Aggressive"),
    STAY("Stay"),
    GUARD_CLAIM("Guard Claim");

    private final String label;

    CompanionRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CompanionRole parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFENDER;
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if ("FOLLOW".equals(normalized)) {
            return DEFENDER;
        }
        try {
            return CompanionRole.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return DEFENDER;
        }
    }
}
