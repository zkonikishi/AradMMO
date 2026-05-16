package cn.aradmmo.item.equipment.classification;

import java.util.Locale;

/**
 * Classification source based on resources/item/equipment directory structure.
 */
public enum AradEquipmentSource {
    WEAPONS,
    ARMOR,
    ACCESSORIES,
    SPECIAL_EQUIPMENT,
    TITLES,
    EQUIPMENT_FUSION,
    OTHER;

    public static AradEquipmentSource parse(String raw, AradEquipmentSource fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return AradEquipmentSource.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
