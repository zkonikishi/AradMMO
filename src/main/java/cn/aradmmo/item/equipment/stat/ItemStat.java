package cn.aradmmo.item.equipment.stat;

import java.util.Locale;
import org.bukkit.NamespacedKey;

public enum ItemStat {

    PHYS_ATK("物理攻击", false, null),
    MAGIC_ATK("魔法攻击", false, null),
    ATK_SPEED("攻击速度%", true, NamespacedKey.minecraft("generic.attack_speed")),
    CRIT_CHANCE("暴击率%", true, null),
    CRIT_DMG("暴击伤害%", true, null),
    LIFE_STEAL("生命吸取%", true, null),

    PHYS_DEF("物理防御", false, NamespacedKey.minecraft("generic.armor")),
    MAGIC_DEF("魔法防御", false, null),
    TOUGHNESS("护甲韧性", false, NamespacedKey.minecraft("generic.armor_toughness")),

    MAX_HP("最大生命", false, NamespacedKey.minecraft("generic.max_health")),
    MAX_MP("最大魔力", false, null),

    MOVE_SPEED("移动速度%", true, NamespacedKey.minecraft("generic.movement_speed")),
    CAST_SPEED("技能冷却缩减%", true, null),

    STRENGTH("力量加成", false, null),
    INTELLECT("智力加成", false, null),
    SPIRIT("精神加成", false, null),
    VITALITY("体力加成", false, null);

    private final String displayName;
    private final boolean percent;
    private final NamespacedKey vanillaKey;

    ItemStat(String displayName, boolean percent, NamespacedKey vanillaKey) {
        this.displayName = displayName;
        this.percent = percent;
        this.vanillaKey = vanillaKey;
    }

    public String nbtKey() {
        return "ARAD_STAT_" + name();
    }

    public String displayName() {
        return displayName;
    }

    public boolean isPercent() {
        return percent;
    }

    public NamespacedKey vanillaKey() {
        return vanillaKey;
    }

    public static ItemStat fromYamlKey(String key) {
        if (key == null) return null;
        String normalized = key.replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}