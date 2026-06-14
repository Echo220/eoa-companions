package com.echoesofaegis.companions.data;

import java.util.Locale;
import java.util.Random;

public enum PersonalityTrait {
    BRAVE("Brave"),
    LOYAL("Loyal"),
    SCRAPPY("Scrappy"),
    CAREFUL("Careful"),
    LUCKY("Lucky");

    private static final PersonalityTrait[] VALUES = values();
    private final String label;

    PersonalityTrait(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PersonalityTrait random(Random random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    public static PersonalityTrait parse(String value) {
        if (value == null || value.isBlank()) {
            return LOYAL;
        }
        try {
            return PersonalityTrait.valueOf(value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LOYAL;
        }
    }
}
