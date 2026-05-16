package cn.aradmmo.item.equipment.classification;

import java.util.Locale;

/**
 * Arad-style top-level item groups.
 */
public enum AradItemGroup {
    EQUIPMENT,
    CONSUMABLE,
    MATERIAL,
    QUEST,
    AVATAR,
    TITLE,
    CREATURE,
    OTHER;

    public static AradItemGroup parse(String raw, AradItemGroup fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return AradItemGroup.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
