package cn.aradmmo.item.equipment.stat;

import cn.aradmmo.core.text.TextColorService;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 装备物品构建器（流式 API）。
 * 支持 Arad 风格的稀有度、品质等级及强化等级。
 */
public final class StatItemBuilder {

    private static final String SEP = "§8━━━━━━━━━━━━━━━━━━━━";

    private Material   material  = Material.IRON_SWORD;
    private String     name      = "装备";
    private int        reqLevel  = 0;
    private String     reqClass  = "";
    private ItemRarity rarity    = ItemRarity.COMMON;
    private ItemGrade  grade     = ItemGrade.ORDINARY;
    private int        reinforce = 0;
    private AradArmorType armorType = AradArmorType.UNKNOWN;
    private String        armorSetId = "";

    private final Map<ItemStat, Double> stats     = new EnumMap<>(ItemStat.class);
    private final List<String>          extraLore = new ArrayList<>();

    public StatItemBuilder material(Material mat)        { this.material   = mat;   return this; }
    public StatItemBuilder name(String n)                { this.name       = n;     return this; }
    public StatItemBuilder requiredLevel(int lvl)        { this.reqLevel   = lvl;   return this; }
    public StatItemBuilder requiredArchetype(String cls) { this.reqClass   = cls;   return this; }
    public StatItemBuilder rarity(ItemRarity r)          { this.rarity     = r;     return this; }
    public StatItemBuilder grade(ItemGrade g)            { this.grade      = g;     return this; }
    public StatItemBuilder reinforce(int level)          { this.reinforce  = level; return this; }
    public StatItemBuilder armorType(AradArmorType type) { this.armorType  = type == null ? AradArmorType.UNKNOWN : type; return this; }
    public StatItemBuilder armorSetId(String setId)      { this.armorSetId = setId == null ? "" : setId.trim(); return this; }
    public StatItemBuilder stat(ItemStat s, double v)    { stats.put(s, v);         return this; }
    public StatItemBuilder lore(String line)             { extraLore.add(line);     return this; }

    public ItemStack build() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextColorService.component(buildDisplayName(rarity, reinforce, name)));
        meta.lore(buildLoreLines(rarity, grade, reinforce, armorType, armorSetId,
            computeEffective(stats, grade, reinforce), reqLevel, reqClass, extraLore)
            .stream().map(TextColorService::component).toList());
        item.setItemMeta(meta);

        try {
            NBTItem nbt = NBTItem.get(item);
            for (Map.Entry<ItemStat, Double> e : stats.entrySet()) {
                nbt.setDouble(e.getKey().nbtKey(), e.getValue());
            }
            if (reqLevel > 0)        nbt.setInteger("ARAD_REQUIRED_LEVEL", reqLevel);
            if (!reqClass.isBlank()) nbt.setString("ARAD_REQUIRED_CLASS",  reqClass);
            nbt.setString(ItemRarity.NBT_KEY, rarity.name());
            nbt.setString(ItemGrade.NBT_KEY,  grade.name());
            if (armorType != AradArmorType.UNKNOWN) nbt.setString(AradArmorType.NBT_KEY, armorType.name());
            if (!armorSetId.isBlank()) nbt.setString(AradArmorType.SET_NBT_KEY, armorSetId);
            if (reinforce > 0)       nbt.setInteger("ARAD_REINFORCE", reinforce);
            nbt.setString("ARAD_BASE_NAME", name);
            if (!extraLore.isEmpty()) {
                nbt.setString("ARAD_EXTRA_LORE", String.join("\n", extraLore));
            }
            return nbt.toItem();
        } catch (Throwable ignored) {
            return item;
        }
    }

    /**
     * 从物品 NBT 中重新生成 lore 和显示名，通常在强化或品质改变后调用。
     */
    public static ItemStack refreshLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        try {
            NBTItem nbt = NBTItem.get(item);

            ItemRarity rarity    = ItemRarity.fromString(nbt.hasTag(ItemRarity.NBT_KEY) ? nbt.getString(ItemRarity.NBT_KEY) : null);
            ItemGrade  grade     = ItemGrade.fromString(nbt.hasTag(ItemGrade.NBT_KEY)   ? nbt.getString(ItemGrade.NBT_KEY)  : null);
            int        reinforce = nbt.hasTag("ARAD_REINFORCE")      ? nbt.getInteger("ARAD_REINFORCE")      : 0;
            String     baseName  = nbt.hasTag("ARAD_BASE_NAME")      ? nbt.getString("ARAD_BASE_NAME")       : "装备";
            int        reqLevel  = nbt.hasTag("ARAD_REQUIRED_LEVEL") ? nbt.getInteger("ARAD_REQUIRED_LEVEL") : 0;
            String     reqClass  = nbt.hasTag("ARAD_REQUIRED_CLASS") ? nbt.getString("ARAD_REQUIRED_CLASS")  : "";
            AradArmorType armorType = AradArmorType.fromString(nbt.hasTag(AradArmorType.NBT_KEY)
                ? nbt.getString(AradArmorType.NBT_KEY) : null);
            String armorSetId = nbt.hasTag(AradArmorType.SET_NBT_KEY) ? nbt.getString(AradArmorType.SET_NBT_KEY) : "";

            List<String> extra = new ArrayList<>();
            if (nbt.hasTag("ARAD_EXTRA_LORE")) {
                String raw = nbt.getString("ARAD_EXTRA_LORE");
                if (raw != null && !raw.isBlank()) {
                    for (String line : raw.split("\n", -1)) extra.add(line);
                }
            }

            Map<ItemStat, Double> baseStats = new EnumMap<>(ItemStat.class);
            for (ItemStat stat : ItemStat.values()) {
                if (nbt.hasTag(stat.nbtKey())) {
                    double v = nbt.getDouble(stat.nbtKey());
                    if (v != 0) baseStats.put(stat, v);
                }
            }

            item = nbt.toItem();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextColorService.component(buildDisplayName(rarity, reinforce, baseName)));
                meta.lore(buildLoreLines(rarity, grade, reinforce, armorType, armorSetId,
                    computeEffective(baseStats, grade, reinforce), reqLevel, reqClass, extra)
                    .stream().map(TextColorService::component).toList());
                item.setItemMeta(meta);
            }
            return item;
        } catch (Throwable ignored) {
            return item;
        }
    }

    /**
     * 累计强化攻击加成倍率（例如 +3 → 0.09，即 +9%）。防御加成 = 返回值 × 0.5。
     */
    public static double reinforceAttackBonus(int level) {
        double bonus = 0;
        for (int i = 1; i <= level; i++) {
            if      (i <= 3) bonus += 0.03;
            else if (i <= 6) bonus += 0.05;
            else if (i <= 9) bonus += 0.07;
            else             bonus += 0.10;
        }
        return bonus;
    }

    /** 计算用于 lore 展示的有效属性值（grade 对防御加乘，reinforce 对攻防加乘）。*/
    static Map<ItemStat, Double> computeEffective(Map<ItemStat, Double> base,
                                                   ItemGrade grade, int reinforce) {
        Map<ItemStat, Double> out = new EnumMap<>(ItemStat.class);
        double atkBonus = reinforce > 0 ? reinforceAttackBonus(reinforce) : 0;
        double defBonus = atkBonus * 0.5;
        for (Map.Entry<ItemStat, Double> e : base.entrySet()) {
            ItemStat stat = e.getKey();
            double v = e.getValue();
            boolean isDef = stat == ItemStat.PHYS_DEF || stat == ItemStat.MAGIC_DEF || stat == ItemStat.TOUGHNESS;
            boolean isAtk = stat == ItemStat.PHYS_ATK || stat == ItemStat.MAGIC_ATK;
            if (isDef) v *= grade.multiplier();
            if (isAtk && reinforce > 0) v *= (1.0 + atkBonus);
            if (isDef && reinforce > 0) v *= (1.0 + defBonus);
            out.put(stat, v);
        }
        return out;
    }

    static String buildDisplayName(ItemRarity rarity, int reinforce, String baseName) {
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', baseName));
        String gradient = rarity.nameGradient();
        if (gradient != null && !gradient.isBlank()) {
            String name = "<gradient:" + gradient + ">" + stripped + "</gradient>";
            return reinforce > 0 ? "<gray>+" + reinforce + " </gray>" + name : name;
        }

        String colored  = rarity.nameColor() + stripped;
        return reinforce > 0 ? "§7+" + reinforce + " " + colored : colored;
    }

    static List<String> buildLoreLines(ItemRarity rarity, ItemGrade grade, int reinforce,
                                        AradArmorType armorType, String armorSetId,
                                        Map<ItemStat, Double> stats,
                                        int reqLevel, String reqClass, List<String> extra) {
        List<String> lore = new ArrayList<>();
        lore.add(SEP);
        lore.add(rarityTag(rarity));
        lore.add("§7品级: " + grade.color() + grade.displayName());
        if (reinforce > 0) lore.add("§7强化: §e+" + reinforce);

        if (reqLevel > 0 || (reqClass != null && !reqClass.isBlank())) {
            if (reqLevel > 0) lore.add("§8Lv." + reqLevel + "  §7装备条件");
            if (reqClass != null && !reqClass.isBlank()) lore.add("§7职业限制: §b" + reqClass);
        }

        if (armorType != null && armorType != AradArmorType.UNKNOWN) {
            lore.add("§7防具类型: §f" + armorType.displayName());
        }
        if (armorSetId != null && !armorSetId.isBlank()) {
            lore.add("§7套装: §d" + armorSetId);
        }

        appendGroup(lore, "§c[攻击属性]", stats, ItemStat.PHYS_ATK, ItemStat.MAGIC_ATK,
                ItemStat.ATK_SPEED, ItemStat.CRIT_CHANCE, ItemStat.CRIT_DMG, ItemStat.LIFE_STEAL);
        appendGroup(lore, "§b[防御属性]", stats, ItemStat.PHYS_DEF, ItemStat.MAGIC_DEF, ItemStat.TOUGHNESS);
        appendGroup(lore, "§a[生命属性]", stats, ItemStat.MAX_HP, ItemStat.MAX_MP);
        appendGroup(lore, "§e[机动属性]", stats, ItemStat.MOVE_SPEED, ItemStat.CAST_SPEED);
        appendGroup(lore, "§6[基础属性]", stats, ItemStat.STRENGTH, ItemStat.INTELLECT, ItemStat.SPIRIT, ItemStat.VITALITY);

        if (!extra.isEmpty()) {
            lore.add("§8[物品说明]");
            for (String line : extra) lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        lore.add(SEP);
        return lore;
    }

    private static String rarityTag(ItemRarity rarity) {
        String gradient = rarity.nameGradient();
        if (gradient != null && !gradient.isBlank()) {
            return "<gradient:" + gradient + ">[" + rarity.displayName() + "]</gradient>";
        }
        return rarity.labelColor() + "[" + rarity.displayName() + "]";
    }

    private static void appendGroup(List<String> lore, String header,
                                     Map<ItemStat, Double> stats, ItemStat... inGroup) {
        List<String> lines = new ArrayList<>();
        for (ItemStat s : inGroup) {
            Double v = stats.get(s);
            if (v != null && v != 0) lines.add(formatLine(s, v));
        }
        if (!lines.isEmpty()) {
            lore.add(" ");
            lore.add(header);
            lore.addAll(lines);
        }
    }

    private static String formatLine(ItemStat stat, double v) {
        String prefix = v >= 0 ? "§a+" : "§c";
        String valStr;
        if (stat.isPercent()) {
            valStr = prefix + String.format("%.1f%%", v);
        } else {
            long iVal = (long) v;
            valStr = prefix + (v == iVal ? String.valueOf(iVal) : String.valueOf(v));
        }
        return "  §7" + stat.displayName() + ": " + valStr;
    }

    public static StatItemBuilder fromConfig(ConfigurationSection sec) {
        StatItemBuilder b = new StatItemBuilder();

        String matStr = sec.getString("material", "IRON_SWORD");
        Material mat = Material.matchMaterial(matStr);
        if (mat != null) b.material(mat);

        b.name(sec.getString("name", "装备"));
        b.requiredLevel(sec.getInt("required-level", 0));
        b.requiredArchetype(sec.getString("required-archetype", ""));
        b.rarity(ItemRarity.fromString(sec.getString("rarity", "COMMON")));
        b.grade(ItemGrade.fromString(sec.getString("grade", "ORDINARY")));
        b.armorType(AradArmorType.fromString(sec.getString("arad-armor-type", "UNKNOWN")));
        b.armorSetId(sec.getString("arad-set-id", ""));

        ConfigurationSection statsSection = sec.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String key : statsSection.getKeys(false)) {
                ItemStat stat = ItemStat.fromYamlKey(key);
                if (stat != null) b.stat(stat, statsSection.getDouble(key));
            }
        }

        for (String line : sec.getStringList("lore")) b.lore(line);
        return b;
    }
}
