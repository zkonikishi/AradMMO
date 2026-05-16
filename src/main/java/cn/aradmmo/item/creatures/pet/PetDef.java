package cn.aradmmo.item.creatures.pet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * pets.yml 加载的宠物类型定义（不可变）
 */
public final class PetDef {

    private final String     id;
    private final String     name;
    private final Material   material;
    private final EntityType entityType;
    private final double     health;
    private final double     damage;
    private final double     speed;
    private final boolean    attackMobs;
    private final boolean    revival;
    private final boolean    baby;
    private final boolean    canLoot;
    private final boolean    canOpenChests;
    private final boolean    canGather;
    private final Map<String, Integer> attributes;
    private final Map<String, PetSkillDef> skills;
    private final List<String> lore;

    private PetDef(String id, String name, Material material, EntityType entityType,
                   double health, double damage, double speed,
                   boolean attackMobs, boolean revival, boolean baby,
                   boolean canLoot, boolean canOpenChests, boolean canGather,
                   Map<String, Integer> attributes,
                   Map<String, PetSkillDef> skills,
                   List<String> lore) {
        this.id         = id;
        this.name       = name;
        this.material   = material;
        this.entityType = entityType;
        this.health     = health;
        this.damage     = damage;
        this.speed      = speed;
        this.attackMobs = attackMobs;
        this.revival    = revival;
        this.baby       = baby;
        this.canLoot    = canLoot;
        this.canOpenChests = canOpenChests;
        this.canGather  = canGather;
        this.attributes = Map.copyOf(attributes);
        this.skills     = Map.copyOf(skills);
        this.lore       = List.copyOf(lore);
    }

    public String     id()         { return id; }
    public String     name()       { return name; }
    public Material   material()   { return material; }
    public EntityType entityType() { return entityType; }
    public double     health()     { return health; }
    public double     damage()     { return damage; }
    public double     speed()      { return speed; }
    public boolean    attackMobs() { return attackMobs; }
    public boolean    revival()    { return revival; }
    public boolean    baby()       { return baby; }
    public boolean    canLoot()    { return canLoot; }
    public boolean    canOpenChests() { return canOpenChests; }
    public boolean    canGather()  { return canGather; }
    public int        attribute(String key) {
        if (key == null) return 0;
        return attributes.getOrDefault(key.toLowerCase(Locale.ROOT), 0);
    }
    public Map<String, Integer> attributes() { return attributes; }
    public Map<String, PetSkillDef> skills() { return skills; }
    public Optional<PetSkillDef> skill(String skillId) {
        if (skillId == null) return Optional.empty();
        return Optional.ofNullable(skills.get(skillId.toLowerCase(Locale.ROOT)));
    }

    /**
     * 创建对应的宠物物品（NBT 标签{@link PetService} 写入）
     */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> finalLore = new ArrayList<>(lore);
            if (!attributes.isEmpty()) {
                finalLore.add("§8");
                finalLore.add("§6▶ 宠物属性");
                for (Map.Entry<String, Integer> entry : attributes.entrySet()) {
                    int value = entry.getValue();
                    if (value == 0) continue;
                    finalLore.add("§7" + attrDisplay(entry.getKey()) + ": §a+" + value);
                }
            }
            if (!finalLore.isEmpty()) meta.setLore(finalLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** YAML 配置节加载宠物类型定义*/
    public static PetDef load(String id, ConfigurationSection section) {
        String     name     = section.getString("name", "&f宠物");
        String     matName  = section.getString("material", "BONE");
        Material   mat      = Material.matchMaterial(matName.toUpperCase());
        if (mat == null) mat = Material.BONE;

        String     entName  = section.getString("entity", "WOLF").toUpperCase();
        EntityType entType;
        try { entType = EntityType.valueOf(entName); }
        catch (IllegalArgumentException e) { entType = EntityType.WOLF; }

        double  health     = section.getDouble("health", 20.0);
        double  damage     = section.getDouble("damage", 4.0);
        double  speed      = section.getDouble("speed", 0.3);
        boolean attackMobs = section.getBoolean("attack-mobs", true);
        boolean revival    = section.getBoolean("revival", true);
        boolean baby       = section.getBoolean("baby", false);
        boolean canLoot    = section.getBoolean("capabilities.loot", true);
        boolean canOpenChests = section.getBoolean("capabilities.open-chests", false);
        boolean canGather  = section.getBoolean("capabilities.gather", false);

        Map<String, Integer> attrs = defaultAttributes(attackMobs);
        ConfigurationSection attrSec = section.getConfigurationSection("attributes");
        if (attrSec != null) {
            for (String key : attrSec.getKeys(false)) {
                attrs.put(key.toLowerCase(Locale.ROOT), attrSec.getInt(key, 0));
            }
        }

        Map<String, PetSkillDef> skills = defaultSkills(attackMobs);
        ConfigurationSection skillsSec = section.getConfigurationSection("skills");
        if (skillsSec != null) {
            for (String skillId : skillsSec.getKeys(false)) {
                ConfigurationSection skill = skillsSec.getConfigurationSection(skillId);
                if (skill == null) continue;
                skills.put(skillId.toLowerCase(Locale.ROOT),
                        PetSkillDef.load(skillId, skill, fallbackType(skillId)));
            }
        }

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        return new PetDef(id,
                ChatColor.translateAlternateColorCodes('&', name),
            mat, entType, health, damage, speed,
            attackMobs, revival, baby,
            canLoot, canOpenChests, canGather,
            attrs, skills, lore);
    }

    private static Map<String, Integer> defaultAttributes(boolean attackMobs) {
        Map<String, Integer> attrs = new LinkedHashMap<>();
        // 默认模板：战斗型偏力量/体力，辅助型偏智力/精神
        if (attackMobs) {
            attrs.put("strength", 6);
            attrs.put("vitality", 4);
            attrs.put("intellect", 2);
            attrs.put("spirit", 2);
        } else {
            attrs.put("strength", 1);
            attrs.put("vitality", 2);
            attrs.put("intellect", 4);
            attrs.put("spirit", 4);
        }
        return attrs;
    }

    private static Map<String, PetSkillDef> defaultSkills(boolean attackMobs) {
        Map<String, PetSkillDef> skills = new LinkedHashMap<>();
        if (attackMobs) {
            skills.put("claw_strike", new PetSkillDef("claw_strike", "撕裂爪击", PetSkillType.ACTIVE, 100L, 1.2));
        } else {
            skills.put("magic_pulse", new PetSkillDef("magic_pulse", "灵能脉冲", PetSkillType.ACTIVE, 120L, 1.0));
        }
        skills.put("guardian_heart", new PetSkillDef("guardian_heart", "守护之心", PetSkillType.PASSIVE, 80L, 1.0));
        skills.put("scavenge", new PetSkillDef("scavenge", "战利品回收", PetSkillType.SUPPORT, 40L, 1.0));
        return skills;
    }

    private static PetSkillType fallbackType(String skillId) {
        String id = skillId.toLowerCase(Locale.ROOT);
        if (id.contains("passive") || id.contains("guard")) return PetSkillType.PASSIVE;
        if (id.contains("support") || id.contains("loot") || id.contains("scavenge")) return PetSkillType.SUPPORT;
        return PetSkillType.ACTIVE;
    }

    private static String attrDisplay(String key) {
        return switch (key) {
            case "strength" -> "力量";
            case "intellect" -> "智力";
            case "spirit" -> "精神";
            case "vitality" -> "体力";
            case "stamina" -> "耐力";
            default -> key;
        };
    }
}

