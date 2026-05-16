package cn.aradmmo.item.equipment.classification;

import java.util.Locale;

/**
 * Arad equipment secondary categories.
 */
public enum AradEquipCategory {
    WEAPON,
    ARMOR,
    ACCESSORY,
    SPECIAL,
    NONE;

    public static AradEquipCategory parse(String raw, AradEquipCategory fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return AradEquipCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
