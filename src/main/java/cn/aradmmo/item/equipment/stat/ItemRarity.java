package cn.aradmmo.item.equipment.stat;

import java.util.Locale;

/**
 * Arad 风格装备稀有度，决定物品名称颜色及 lore 边框颜色。
 * <p>
 * 稀有度越高，装备设计上基础属性越强（由 stat-templates 或掉落表控制），
 * 但本枚举本身不直接施加属性倍率——属性强度应在物品创建时设定。
 */
public enum ItemRarity {

    /** 白色 — 普通白装，商店/副本低阶掉落 */
    COMMON    ("普通",  "§f",  "§7",  ""),
    /** 绿色 — 稀有绿装 */
    UNCOMMON  ("稀有",  "§a",  "§a",  ""),
    /** 蓝色 — 精良蓝装 */
    RARE      ("精良",  "§9",  "§9",  ""),
    /** 紫色 — 独特紫装 */
    UNIQUE    ("独特",  "§5",  "§d",  ""),
    /** 橙色 — 传说橙装 */
    LEGENDARY ("传说",  "§6",  "§e",  ""),
    /** 红色加粗 — 史诗红装 */
    EPIC      ("史诗",  "§c§l","§c",  ""),
    /** 品红发光感 — 神话 */
    MYTHIC    ("神话",  "&#ff5ac8", "&#ff83d8", "#ff5ac8:#ffd36e"),
    /** Primeval（太初）— 参考色 #6B9080 */
    PRIMEVAL  ("太初",  "&#6b9080", "&#9bb8ab", "#6b9080:#cfe7db");

    /** ItemStack NBT 中存储稀有度的 key。*/
    public static final String NBT_KEY = "ARAD_RARITY";

    private final String displayName;
    /** 物品名称颜色代码（包含前置格式化符号）。*/
    private final String nameColor;
    /** lore 中"稀有度"标签颜色代码。*/
    private final String labelColor;
    /** 装备名称渐变（#RRGGBB:#RRGGBB），为空表示使用 nameColor 单色。 */
    private final String nameGradient;

    ItemRarity(String displayName, String nameColor, String labelColor, String nameGradient) {
        this.displayName = displayName;
        this.nameColor   = nameColor;
        this.labelColor  = labelColor;
        this.nameGradient = nameGradient;
    }

    public String displayName() { return displayName; }
    public String nameColor()   { return nameColor; }
    public String labelColor()  { return labelColor; }
    public String nameGradient() { return nameGradient; }

    /** 从字符串（不区分大小写）解析稀有度，失败返回 {@link #COMMON}。*/
    public static ItemRarity fromString(String s) {
        if (s == null) return COMMON;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return COMMON; }
    }
}
