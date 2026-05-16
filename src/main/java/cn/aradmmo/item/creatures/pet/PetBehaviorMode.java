package cn.aradmmo.item.creatures.pet;

import java.util.Locale;

/**
 * Runtime behavior mode for a companion pet.
 */
public enum PetBehaviorMode {
    FOLLOW,
    STAY,
    COMBAT;

    public PetBehaviorMode next() {
        return switch (this) {
            case FOLLOW -> STAY;
            case STAY -> COMBAT;
            case COMBAT -> FOLLOW;
        };
    }

    public static PetBehaviorMode parse(String raw) {
        if (raw == null || raw.isBlank()) return FOLLOW;
        try {
            return PetBehaviorMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FOLLOW;
        }
    }
}
