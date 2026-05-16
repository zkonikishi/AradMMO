package cn.aradmmo.item.equipment.stat;

import java.util.Locale;

/**
 * DFO style armor types.
 */
public enum AradArmorType {
    CLOTH("布甲"),
    LEATHER("皮甲"),
    LIGHT("轻甲"),
    HEAVY("重甲"),
    PLATE("板甲"),
    UNKNOWN("未知");

    public static final String NBT_KEY = "ARAD_ARMOR_TYPE";
    public static final String SET_NBT_KEY = "ARAD_ARMOR_SET";

    private final String displayName;

    AradArmorType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static AradArmorType fromString(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        try {
            return AradArmorType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
