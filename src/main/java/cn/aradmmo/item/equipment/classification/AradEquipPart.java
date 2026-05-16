package cn.aradmmo.item.equipment.classification;

import java.util.Locale;

/**
 * Arad equipment part/slot dimension.
 */
public enum AradEquipPart {
    WEAPON,
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
    BELT,
    TITLE,
    NECKLACE,
    BRACELET,
    RING,
    SUB_EQUIPMENT,
    EARRING,
    MAGIC_STONE,
    PET,
    MOUNT,
    OTHER;

    public static AradEquipPart parse(String raw, AradEquipPart fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return AradEquipPart.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
