package cn.aradmmo.item.equipment.stat;

import java.util.Locale;

/**
 * Arad 风格装备品质等级（Grade）。
 * <p>
 * 品质影响 <b>防御类属性</b>（{@link ItemStat#PHYS_DEF}、{@link ItemStat#MAGIC_DEF}、
 * {@link ItemStat#TOUGHNESS}）的实际生效倍率。攻击类属性不受品质影响，与 Arad 体系一致。
 * <p>
 * 具体来说：{@code effective_def = base_def × multiplier}。
 */
public enum ItemGrade {

    INFERIOR    ("下品", "§8", 0.80),
    WEAK        ("弱",   "§7", 0.90),
    ORDINARY    ("普通", "§f", 1.00),
    EXCEPTIONAL ("精良", "§e", 1.10),
    SUPERIOR    ("上品", "§6", 1.20);

    /** ItemStack NBT 中存储品质的 key。*/
    public static final String NBT_KEY = "ARAD_GRADE";

    private final String displayName;
    private final String color;
    /** 防御属性生效倍率（0.80 ~ 1.20）。*/
    private final double multiplier;

    ItemGrade(String displayName, String color, double multiplier) {
        this.displayName = displayName;
        this.color       = color;
        this.multiplier  = multiplier;
    }

    public String displayName() { return displayName; }
    public String color()       { return color; }
    public double multiplier()  { return multiplier; }

    /** 从字符串（不区分大小写）解析品质，失败返回 {@link #ORDINARY}。*/
    public static ItemGrade fromString(String s) {
        if (s == null) return ORDINARY;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return ORDINARY; }
    }

    /**
     * 根据稀有度随机掷出一个品质。稀有度越高，品质分布越偏向上品。
     */
    public static ItemGrade rollForRarity(ItemRarity rarity) {
        double r = Math.random();
        return switch (rarity) {
            case COMMON, UNCOMMON -> {
                if (r < 0.05) yield SUPERIOR;
                if (r < 0.20) yield EXCEPTIONAL;
                if (r < 0.50) yield ORDINARY;
                if (r < 0.80) yield WEAK;
                yield INFERIOR;
            }
            case RARE -> {
                if (r < 0.10) yield SUPERIOR;
                if (r < 0.35) yield EXCEPTIONAL;
                if (r < 0.65) yield ORDINARY;
                if (r < 0.90) yield WEAK;
                yield INFERIOR;
            }
            case UNIQUE, LEGENDARY, EPIC, MYTHIC, PRIMEVAL -> {
                if (r < 0.20) yield SUPERIOR;
                if (r < 0.50) yield EXCEPTIONAL;
                if (r < 0.80) yield ORDINARY;
                if (r < 0.95) yield WEAK;
                yield INFERIOR;
            }
        };
    }
}
