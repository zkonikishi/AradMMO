package cn.aradmmo.item.equipment.stat;

import cn.aradmmo.item.equipment.PlayerEquipment;
import cn.aradmmo.item.equipment.SlotDef;
import cn.aradmmo.item.equipment.SlotManager;
import cn.aradmmo.item.equipment.SlotType;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 负责读取装备面板中所有物品的 Arad 属性并应用到玩家身上
 * <p>
 * {@link ItemStat#vanillaKey()} null 的属性，
 * 以命名空{@code aradmmo:equip_<stat>} 添加 {@link AttributeModifier}
 * 其余属性仅写入 {@link EquipmentStats} 供战技能系统读取
 */
public final class StatApplier {

    /** 本系AttributeModifier 命名空间*/
    private static final String NS = "aradmmo";

    private final SlotManager slotManager;
    private final Function<Player, String> archetypeResolver;
    /** UUID EquipmentStats（含全部属性总量，包括原版映射属性）*/
    private final Map<UUID, EquipmentStats> cache = new HashMap<>();
    /** 防具类型件数加成配置：Type -> (Threshold -> Stat map). */
    private final Map<AradArmorType, Map<Integer, Map<ItemStat, Double>>> armorTypeBonuses = new EnumMap<>(AradArmorType.class);
    /** 套装件数加成配置：setId -> (Threshold -> Stat map). */
    private final Map<String, Map<Integer, Map<ItemStat, Double>>> armorSetBonuses = new HashMap<>();
    /** 兼容旧配置：所有套装通用件数加成。 */
    private final Map<Integer, Map<ItemStat, Double>> armorSetGlobalBonuses = new HashMap<>();
    /** 职业精通防具映射：archetype -> armorType. */
    private final Map<String, AradArmorType> classArmorMastery = new HashMap<>();
    /** 精通件数加成（按精通类型件数触发）。 */
    private final Map<Integer, Map<ItemStat, Double>> masteryBonuses = new HashMap<>();
    /** 防具精通分型加成：MasteryType -> (Threshold -> Stat map). */
    private final Map<AradArmorType, Map<Integer, Map<ItemStat, Double>>> masteryTypeBonuses = new EnumMap<>(AradArmorType.class);
    /** 职业阶段 -> 护甲精通被动技能等级。 */
    private final Map<Integer, Integer> passiveSkillLevelByStage = new HashMap<>();
    /** 护甲精通被动技能等级 -> 加成倍率。 */
    private final Map<Integer, Double> passiveSkillMultiplierByLevel = new HashMap<>();
    /** 非精通混穿惩罚（按非精通件数触发，建议配置负数）。 */
    private final Map<Integer, Map<ItemStat, Double>> mismatchPenalties = new HashMap<>();
    private boolean masteryEnabled;
    private boolean strictTypeBonusByMastery;

    public StatApplier(SlotManager slotManager) {
        this(slotManager, player -> "");
    }

    public StatApplier(SlotManager slotManager, Function<Player, String> archetypeResolver) {
        this.slotManager = slotManager;
        this.archetypeResolver = archetypeResolver == null ? player -> "" : archetypeResolver;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** 获取玩家的属性缓存（永不null）*/
    public EquipmentStats statsOf(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> new EquipmentStats());
    }

    /**
     * 全量重算玩家装备属性：
     * <ol>
     *   <li>移除所有旧的本系统 AttributeModifier</li>
     *   <li>清空 EquipmentStats</li>
     *   <li>遍历所有装备槽，累积每件物品的 ARAD_STAT_* NBT</li>
     *   <li>应用新的 AttributeModifier（含原版属性）</li>
     * </ol>
     */
    public void recalculate(Player player, PlayerEquipment eq) {
        removeAllModifiers(player);
        EquipmentStats stats = statsOf(player);
        stats.clear();

        Map<AradArmorType, Integer> armorTypeCounts = new EnumMap<>(AradArmorType.class);
        Map<String, Integer> armorSetCounts = new HashMap<>();

        for (SlotDef slot : slotManager.getAll()) {
            if (!isEquipSlot(slot.type())) continue;
            ItemStack item = eq.getItem(slot);
            if (item == null || item.getType().isAir() || slot.isHolder(item)) continue;
            accumulateStats(item, stats);
            collectArmorContext(slot, item, armorTypeCounts, armorSetCounts);
        }

        applyArmorBonuses(stats, armorTypeCounts, armorSetCounts, resolveArchetype(player));

        applyVanillaModifiers(player, stats);
    }

    public void reloadArmorSystem(YamlConfiguration config) {
        armorTypeBonuses.clear();
        armorSetGlobalBonuses.clear();
        armorSetBonuses.clear();
        classArmorMastery.clear();
        masteryBonuses.clear();
        masteryTypeBonuses.clear();
        passiveSkillLevelByStage.clear();
        passiveSkillMultiplierByLevel.clear();
        mismatchPenalties.clear();
        masteryEnabled = false;
        strictTypeBonusByMastery = false;
        if (config == null) return;

        ConfigurationSection typeSection = config.getConfigurationSection("armor-type-bonuses");
        if (typeSection != null) {
            for (String typeKey : typeSection.getKeys(false)) {
                AradArmorType type = AradArmorType.fromString(typeKey);
                if (type == AradArmorType.UNKNOWN) continue;

                ConfigurationSection bonusSection = typeSection.getConfigurationSection(typeKey);
                if (bonusSection == null) continue;

                Map<Integer, Map<ItemStat, Double>> thresholdMap = new HashMap<>();
                for (String thresholdKey : bonusSection.getKeys(false)) {
                    Integer threshold = parseThreshold(thresholdKey);
                    if (threshold == null) continue;
                    ConfigurationSection statSection = bonusSection.getConfigurationSection(thresholdKey);
                    Map<ItemStat, Double> statMap = parseStatMap(statSection);
                    if (!statMap.isEmpty()) {
                        thresholdMap.put(threshold, statMap);
                    }
                }

                if (!thresholdMap.isEmpty()) {
                    armorTypeBonuses.put(type, thresholdMap);
                }
            }
        }

        ConfigurationSection setSection = config.getConfigurationSection("set-bonuses");
        if (setSection != null) {
            for (String setOrThresholdKey : setSection.getKeys(false)) {
                Integer legacyThreshold = parseThreshold(setOrThresholdKey);
                if (legacyThreshold != null) {
                    ConfigurationSection statSection = setSection.getConfigurationSection(setOrThresholdKey);
                    Map<ItemStat, Double> statMap = parseStatMap(statSection);
                    if (!statMap.isEmpty()) {
                        armorSetGlobalBonuses.put(legacyThreshold, statMap);
                    }
                    continue;
                }

                ConfigurationSection oneSetSection = setSection.getConfigurationSection(setOrThresholdKey);
                if (oneSetSection == null) continue;
                Map<Integer, Map<ItemStat, Double>> thresholds = parseThresholdBonuses(oneSetSection);
                if (!thresholds.isEmpty()) {
                    armorSetBonuses.put(setOrThresholdKey.trim().toLowerCase(Locale.ROOT), thresholds);
                }
            }
        }

        ConfigurationSection masterySection = config.getConfigurationSection("class-armor-mastery");
        if (masterySection != null) {
            masteryEnabled = masterySection.getBoolean("enabled", false);
            strictTypeBonusByMastery = masterySection.getBoolean("strict-type-bonus", false);

            ConfigurationSection archetypes = masterySection.getConfigurationSection("archetypes");
            if (archetypes != null) {
                for (String archetype : archetypes.getKeys(false)) {
                    AradArmorType type = AradArmorType.fromString(archetypes.getString(archetype));
                    if (type == AradArmorType.UNKNOWN) continue;
                    classArmorMastery.put(archetype.trim().toLowerCase(Locale.ROOT), type);
                }
            }

            ConfigurationSection passiveLevelByStage = masterySection.getConfigurationSection("passive-skill-level-by-stage");
            if (passiveLevelByStage != null) {
                for (String stageKey : passiveLevelByStage.getKeys(false)) {
                    Integer stage = parseThreshold(stageKey);
                    if (stage == null) continue;
                    int level = passiveLevelByStage.getInt(stageKey, 0);
                    if (level > 0) {
                        passiveSkillLevelByStage.put(stage, level);
                    }
                }
            }

            ConfigurationSection passiveMultiplierByLevel = masterySection.getConfigurationSection("passive-skill-multiplier-by-level");
            if (passiveMultiplierByLevel != null) {
                for (String levelKey : passiveMultiplierByLevel.getKeys(false)) {
                    Integer level = parseThreshold(levelKey);
                    if (level == null) continue;
                    double multiplier = passiveMultiplierByLevel.getDouble(levelKey, 1.0D);
                    if (multiplier > 0) {
                        passiveSkillMultiplierByLevel.put(level, multiplier);
                    }
                }
            }

            masteryBonuses.putAll(parseThresholdBonuses(masterySection.getConfigurationSection("bonuses")));

            ConfigurationSection typeBonuses = masterySection.getConfigurationSection("type-bonuses");
            if (typeBonuses != null) {
                for (String typeKey : typeBonuses.getKeys(false)) {
                    AradArmorType type = AradArmorType.fromString(typeKey);
                    if (type == AradArmorType.UNKNOWN) continue;
                    Map<Integer, Map<ItemStat, Double>> thresholds =
                            parseThresholdBonuses(typeBonuses.getConfigurationSection(typeKey));
                    if (!thresholds.isEmpty()) {
                        masteryTypeBonuses.put(type, thresholds);
                    }
                }
            }

            mismatchPenalties.putAll(parseThresholdBonuses(masterySection.getConfigurationSection("mismatch-penalties")));
        }
    }

    /** 玩家下线时移除所有本系统 AttributeModifier 并清除缓存*/
    public void unload(Player player) {
        removeAllModifiers(player);
        cache.remove(player.getUniqueId());
    }

    public ArmorDebugSnapshot debugArmor(Player player, PlayerEquipment eq) {
        Map<AradArmorType, Integer> armorTypeCounts = new EnumMap<>(AradArmorType.class);
        Map<String, Integer> armorSetCounts = new HashMap<>();

        for (SlotDef slot : slotManager.getAll()) {
            if (!isEquipSlot(slot.type())) continue;
            ItemStack item = eq.getItem(slot);
            if (item == null || item.getType().isAir() || slot.isHolder(item)) continue;
            collectArmorContext(slot, item, armorTypeCounts, armorSetCounts);
        }

        String archetype = resolveArchetype(player);
        MasteryPassiveContext context = parseMasteryContext(archetype);
        AradArmorType masteredType = resolveMasteredArmorType(context.masteryKey());
        int passiveSkillLevel = resolvePassiveSkillLevel(context.classStage());
        double passiveSkillMultiplier = resolvePassiveSkillMultiplier(passiveSkillLevel);
        int masteredCount = masteredType == AradArmorType.UNKNOWN
                ? 0 : armorTypeCounts.getOrDefault(masteredType, 0);
        int totalArmorCount = armorTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
        int mismatchCount = Math.max(0, totalArmorCount - masteredCount);

        List<String> typeApplied = new ArrayList<>();
        for (Map.Entry<AradArmorType, Integer> entry : armorTypeCounts.entrySet()) {
            if (shouldSkipTypeBonus(entry.getKey(), masteredType)) continue;
            Map<Integer, Map<ItemStat, Double>> thresholds = armorTypeBonuses.get(entry.getKey());
            if (thresholds == null || thresholds.isEmpty()) continue;
            collectTriggeredThresholds(typeApplied, "type:" + entry.getKey().name(), entry.getValue(), thresholds);
        }

        List<String> setApplied = new ArrayList<>();
        for (Map.Entry<String, Integer> setEntry : armorSetCounts.entrySet()) {
            Map<Integer, Map<ItemStat, Double>> thresholds = armorSetBonuses.get(setEntry.getKey());
            if (thresholds != null && !thresholds.isEmpty()) {
                collectTriggeredThresholds(setApplied, "set:" + setEntry.getKey(), setEntry.getValue(), thresholds);
            } else if (!armorSetGlobalBonuses.isEmpty()) {
                collectTriggeredThresholds(setApplied, "set:*", setEntry.getValue(), armorSetGlobalBonuses);
            }
        }

        List<String> masteryApplied = new ArrayList<>();
        List<String> masteryTypeApplied = new ArrayList<>();
        List<String> mismatchApplied = new ArrayList<>();
        if (masteryEnabled && masteredType != AradArmorType.UNKNOWN) {
            collectTriggeredThresholds(masteryApplied, "mastery:" + masteredType.name(), masteredCount, masteryBonuses);
            Map<Integer, Map<ItemStat, Double>> masteryTypeThresholds = masteryTypeBonuses.get(masteredType);
            if (masteryTypeThresholds != null && !masteryTypeThresholds.isEmpty()) {
                collectTriggeredThresholds(masteryTypeApplied, "mastery-type:" + masteredType.name(), masteredCount, masteryTypeThresholds);
            }
            collectTriggeredThresholds(mismatchApplied, "mismatch", mismatchCount, mismatchPenalties);
        }

        return new ArmorDebugSnapshot(
                archetype,
                masteredType,
                masteryEnabled,
                strictTypeBonusByMastery,
                context.classStage(),
                passiveSkillLevel,
                passiveSkillMultiplier,
                totalArmorCount,
                masteredCount,
                mismatchCount,
                Map.copyOf(armorTypeCounts),
                Map.copyOf(armorSetCounts),
                List.copyOf(typeApplied),
                List.copyOf(setApplied),
                List.copyOf(masteryApplied),
                List.copyOf(masteryTypeApplied),
                List.copyOf(mismatchApplied)
        );
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static boolean isEquipSlot(SlotType type) {
        return switch (type) {
            case ARMOR, ACTIVE, SHIELD, PASSIVE, GENERIC, ELYTRA -> true;
            default -> false;
        };
    }

    /**
     * 读取物品 NBT 中所有 ARAD_STAT_* 标签，并应用品质（grade）和强化（reinforce）加成后累加到 stats。
     * <ul>
     *   <li>品质影响 PHYS_DEF / MAGIC_DEF / TOUGHNESS：有效值 = base × grade.multiplier</li>
     *   <li>强化影响 PHYS_ATK / MAGIC_ATK：有效值 = base × (1 + atkBonus)</li>
     *   <li>强化影响 PHYS_DEF / MAGIC_DEF / TOUGHNESS：有效值 = base × grade.mult × (1 + defBonus)</li>
     * </ul>
     */
    private void accumulateStats(ItemStack item, EquipmentStats stats) {
        try {
            NBTItem nbt = NBTItem.get(item);

            ItemGrade grade = ItemGrade.fromString(
                    nbt.hasTag(ItemGrade.NBT_KEY) ? nbt.getString(ItemGrade.NBT_KEY) : null);
            int reinforce   = nbt.hasTag("ARAD_REINFORCE") ? nbt.getInteger("ARAD_REINFORCE") : 0;
            double atkBonus = reinforce > 0 ? StatItemBuilder.reinforceAttackBonus(reinforce) : 0;
            double defBonus = atkBonus * 0.5;

            for (ItemStat stat : ItemStat.values()) {
                if (!nbt.hasTag(stat.nbtKey())) continue;
                double v = nbt.getDouble(stat.nbtKey());
                if (v == 0) continue;

                boolean isDef = stat == ItemStat.PHYS_DEF || stat == ItemStat.MAGIC_DEF || stat == ItemStat.TOUGHNESS;
                boolean isAtk = stat == ItemStat.PHYS_ATK || stat == ItemStat.MAGIC_ATK;

                if (isDef) v *= grade.multiplier();
                if (isAtk && reinforce > 0) v *= (1.0 + atkBonus);
                if (isDef && reinforce > 0) v *= (1.0 + defBonus);

                stats.add(stat, v);
            }
        } catch (Throwable ignored) {
            // MythicLib 未加载时静默跳过
        }
    }

    private void collectArmorContext(SlotDef slot, ItemStack item,
                                     Map<AradArmorType, Integer> armorTypeCounts,
                                     Map<String, Integer> armorSetCounts) {
        boolean armorLike = slot.type() == SlotType.ARMOR || slot.name().equalsIgnoreCase("belt");
        if (!armorLike) {
            return;
        }
        try {
            NBTItem nbt = NBTItem.get(item);
            AradArmorType type = AradArmorType.fromString(
                    nbt.hasTag(AradArmorType.NBT_KEY) ? nbt.getString(AradArmorType.NBT_KEY) : null);
            if (type != AradArmorType.UNKNOWN) {
                armorTypeCounts.merge(type, 1, Integer::sum);
            }

            if (nbt.hasTag(AradArmorType.SET_NBT_KEY)) {
                String setId = nbt.getString(AradArmorType.SET_NBT_KEY);
                if (setId != null && !setId.isBlank()) {
                    armorSetCounts.merge(setId.trim().toLowerCase(), 1, Integer::sum);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyArmorBonuses(EquipmentStats stats,
                                   Map<AradArmorType, Integer> armorTypeCounts,
                                   Map<String, Integer> armorSetCounts,
                                   String playerArchetype) {
        MasteryPassiveContext context = parseMasteryContext(playerArchetype);
        AradArmorType masteredType = resolveMasteredArmorType(context.masteryKey());
        int passiveLevel = resolvePassiveSkillLevel(context.classStage());
        double passiveMultiplier = resolvePassiveSkillMultiplier(passiveLevel);

        for (Map.Entry<AradArmorType, Integer> entry : armorTypeCounts.entrySet()) {
            if (shouldSkipTypeBonus(entry.getKey(), masteredType)) continue;
            Map<Integer, Map<ItemStat, Double>> thresholds = armorTypeBonuses.get(entry.getKey());
            if (thresholds == null || thresholds.isEmpty()) continue;
            applyThresholdBonuses(stats, entry.getValue(), thresholds);
        }

        for (Map.Entry<String, Integer> setEntry : armorSetCounts.entrySet()) {
            int count = setEntry.getValue();
            if (count <= 0) continue;

            Map<Integer, Map<ItemStat, Double>> thresholds = armorSetBonuses.get(setEntry.getKey());
            if (thresholds != null && !thresholds.isEmpty()) {
                applyThresholdBonuses(stats, count, thresholds);
            } else if (!armorSetGlobalBonuses.isEmpty()) {
                applyThresholdBonuses(stats, count, armorSetGlobalBonuses);
            }
        }

        if (masteryEnabled && masteredType != AradArmorType.UNKNOWN) {
            int masteredCount = armorTypeCounts.getOrDefault(masteredType, 0);
            if (masteredCount > 0 && !masteryBonuses.isEmpty()) {
                applyThresholdBonusesScaled(stats, masteredCount, masteryBonuses, passiveMultiplier);
            }

            Map<Integer, Map<ItemStat, Double>> masteryTypeThresholds = masteryTypeBonuses.get(masteredType);
            if (masteredCount > 0 && masteryTypeThresholds != null && !masteryTypeThresholds.isEmpty()) {
                applyThresholdBonusesScaled(stats, masteredCount, masteryTypeThresholds, passiveMultiplier);
            }

            int totalArmorCount = armorTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
            int mismatchCount = Math.max(0, totalArmorCount - masteredCount);
            if (mismatchCount > 0 && !mismatchPenalties.isEmpty()) {
                applyThresholdBonuses(stats, mismatchCount, mismatchPenalties);
            }
        }
    }

    private String resolveArchetype(Player player) {
        try {
            String archetype = archetypeResolver.apply(player);
            return archetype == null ? "" : archetype.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private AradArmorType resolveMasteredArmorType(String archetype) {
        if (archetype != null) {
            int split = archetype.indexOf('|');
            if (split >= 0) {
                archetype = archetype.substring(0, split);
            }
        }
        if (archetype != null && archetype.startsWith("armortype:")) {
            String raw = archetype.substring("armortype:".length());
            AradArmorType direct = AradArmorType.fromString(raw);
            if (direct != AradArmorType.UNKNOWN) {
                return direct;
            }
        }
        if (archetype == null || archetype.isBlank()) {
            return classArmorMastery.getOrDefault("default", AradArmorType.UNKNOWN);
        }
        AradArmorType specific = classArmorMastery.get(archetype.trim().toLowerCase(Locale.ROOT));
        if (specific != null) return specific;
        return classArmorMastery.getOrDefault("default", AradArmorType.UNKNOWN);
    }

    private boolean shouldSkipTypeBonus(AradArmorType equippedType, AradArmorType masteredType) {
        return masteryEnabled
                && strictTypeBonusByMastery
                && masteredType != AradArmorType.UNKNOWN
                && equippedType != masteredType;
    }

    private void applyThresholdBonuses(EquipmentStats stats, int pieceCount,
                                       Map<Integer, Map<ItemStat, Double>> thresholds) {
        List<Integer> sorted = new ArrayList<>(thresholds.keySet());
        sorted.sort(Integer::compareTo);
        for (Integer threshold : sorted) {
            if (pieceCount < threshold) continue;
            Map<ItemStat, Double> map = thresholds.get(threshold);
            if (map == null) continue;
            for (Map.Entry<ItemStat, Double> statEntry : map.entrySet()) {
                stats.add(statEntry.getKey(), statEntry.getValue());
            }
        }
    }

    private void applyThresholdBonusesScaled(EquipmentStats stats, int pieceCount,
                                             Map<Integer, Map<ItemStat, Double>> thresholds,
                                             double multiplier) {
        List<Integer> sorted = new ArrayList<>(thresholds.keySet());
        sorted.sort(Integer::compareTo);
        for (Integer threshold : sorted) {
            if (pieceCount < threshold) continue;
            Map<ItemStat, Double> map = thresholds.get(threshold);
            if (map == null) continue;
            for (Map.Entry<ItemStat, Double> statEntry : map.entrySet()) {
                stats.add(statEntry.getKey(), statEntry.getValue() * multiplier);
            }
        }
    }

    private MasteryPassiveContext parseMasteryContext(String rawArchetype) {
        if (rawArchetype == null || rawArchetype.isBlank()) {
            return new MasteryPassiveContext("", 0);
        }
        String key = rawArchetype;
        int stage = 0;
        int split = rawArchetype.indexOf('|');
        if (split >= 0) {
            key = rawArchetype.substring(0, split);
            String[] parts = rawArchetype.substring(split + 1).split("\\|");
            for (String p : parts) {
                if (!p.startsWith("stage:")) continue;
                try {
                    stage = Integer.parseInt(p.substring("stage:".length()).trim());
                } catch (Exception ignored) {
                    stage = 0;
                }
            }
        }
        return new MasteryPassiveContext(key, stage);
    }

    private int resolvePassiveSkillLevel(int stage) {
        return passiveSkillLevelByStage.getOrDefault(stage, 1);
    }

    private double resolvePassiveSkillMultiplier(int level) {
        return passiveSkillMultiplierByLevel.getOrDefault(level, 1.0D);
    }

    private void collectTriggeredThresholds(List<String> out, String source, int pieceCount,
                                            Map<Integer, Map<ItemStat, Double>> thresholds) {
        if (pieceCount <= 0 || thresholds == null || thresholds.isEmpty()) return;
        List<Integer> sorted = new ArrayList<>(thresholds.keySet());
        sorted.sort(Integer::compareTo);
        for (Integer threshold : sorted) {
            if (pieceCount < threshold) continue;
            Map<ItemStat, Double> map = thresholds.get(threshold);
            if (map == null || map.isEmpty()) continue;
            out.add(source + "@" + threshold + " => " + formatStatMap(map));
        }
    }

    private String formatStatMap(Map<ItemStat, Double> map) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<ItemStat, Double> entry : map.entrySet()) {
            String key = entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-');
            entries.add(key + "=" + trimDouble(entry.getValue()));
        }
        entries.sort(String::compareToIgnoreCase);
        return String.join(", ", entries);
    }

    private String trimDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private Map<ItemStat, Double> parseStatMap(ConfigurationSection statSection) {
        Map<ItemStat, Double> out = new HashMap<>();
        if (statSection == null) return out;
        for (String key : statSection.getKeys(false)) {
            ItemStat stat = ItemStat.fromYamlKey(key);
            if (stat == null) continue;
            double value = statSection.getDouble(key);
            if (value != 0) {
                out.put(stat, value);
            }
        }
        return out;
    }

    private Map<Integer, Map<ItemStat, Double>> parseThresholdBonuses(ConfigurationSection section) {
        Map<Integer, Map<ItemStat, Double>> out = new HashMap<>();
        if (section == null) return out;
        for (String thresholdKey : section.getKeys(false)) {
            Integer threshold = parseThreshold(thresholdKey);
            if (threshold == null) continue;
            ConfigurationSection statSection = section.getConfigurationSection(thresholdKey);
            Map<ItemStat, Double> statMap = parseStatMap(statSection);
            if (!statMap.isEmpty()) {
                out.put(threshold, statMap);
            }
        }
        return out;
    }

    private Integer parseThreshold(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record MasteryPassiveContext(String masteryKey, int classStage) {
    }

    public static final class ArmorDebugSnapshot {
        private final String archetype;
        private final AradArmorType masteredType;
        private final boolean masteryEnabled;
        private final boolean strictTypeBonusByMastery;
        private final int classStage;
        private final int passiveSkillLevel;
        private final double passiveSkillMultiplier;
        private final int totalArmorCount;
        private final int masteredCount;
        private final int mismatchCount;
        private final Map<AradArmorType, Integer> armorTypeCounts;
        private final Map<String, Integer> armorSetCounts;
        private final List<String> armorTypeBonuses;
        private final List<String> armorSetBonuses;
        private final List<String> masteryBonuses;
        private final List<String> masteryTypeBonuses;
        private final List<String> mismatchPenalties;

        private ArmorDebugSnapshot(String archetype,
                                   AradArmorType masteredType,
                                   boolean masteryEnabled,
                                   boolean strictTypeBonusByMastery,
                                   int classStage,
                                   int passiveSkillLevel,
                                   double passiveSkillMultiplier,
                                   int totalArmorCount,
                                   int masteredCount,
                                   int mismatchCount,
                                   Map<AradArmorType, Integer> armorTypeCounts,
                                   Map<String, Integer> armorSetCounts,
                                   List<String> armorTypeBonuses,
                                   List<String> armorSetBonuses,
                                   List<String> masteryBonuses,
                                   List<String> masteryTypeBonuses,
                                   List<String> mismatchPenalties) {
            this.archetype = archetype;
            this.masteredType = masteredType;
            this.masteryEnabled = masteryEnabled;
            this.strictTypeBonusByMastery = strictTypeBonusByMastery;
            this.classStage = classStage;
            this.passiveSkillLevel = passiveSkillLevel;
            this.passiveSkillMultiplier = passiveSkillMultiplier;
            this.totalArmorCount = totalArmorCount;
            this.masteredCount = masteredCount;
            this.mismatchCount = mismatchCount;
            this.armorTypeCounts = armorTypeCounts;
            this.armorSetCounts = armorSetCounts;
            this.armorTypeBonuses = armorTypeBonuses;
            this.armorSetBonuses = armorSetBonuses;
            this.masteryBonuses = masteryBonuses;
            this.masteryTypeBonuses = masteryTypeBonuses;
            this.mismatchPenalties = mismatchPenalties;
        }

        public String archetype() { return archetype; }
        public AradArmorType masteredType() { return masteredType; }
        public boolean masteryEnabled() { return masteryEnabled; }
        public boolean strictTypeBonusByMastery() { return strictTypeBonusByMastery; }
        public int classStage() { return classStage; }
        public int passiveSkillLevel() { return passiveSkillLevel; }
        public double passiveSkillMultiplier() { return passiveSkillMultiplier; }
        public int totalArmorCount() { return totalArmorCount; }
        public int masteredCount() { return masteredCount; }
        public int mismatchCount() { return mismatchCount; }
        public Map<AradArmorType, Integer> armorTypeCounts() { return armorTypeCounts; }
        public Map<String, Integer> armorSetCounts() { return armorSetCounts; }
        public List<String> armorTypeBonuses() { return armorTypeBonuses; }
        public List<String> armorSetBonuses() { return armorSetBonuses; }
        public List<String> masteryBonuses() { return masteryBonuses; }
        public List<String> masteryTypeBonuses() { return masteryTypeBonuses; }
        public List<String> mismatchPenalties() { return mismatchPenalties; }
    }

    /** 移除玩家身上所有命名空间为 aradmmo、键equip_ 开头的 AttributeModifier*/
    private void removeAllModifiers(Player player) {
        for (ItemStat stat : ItemStat.values()) {
            if (stat.vanillaKey() == null) continue;
            Attribute attr = Bukkit.getRegistry(Attribute.class).get(stat.vanillaKey());
            if (attr == null) continue;
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;
            NamespacedKey targetKey = new NamespacedKey(NS, "equip_" + stat.name().toLowerCase());
            inst.getModifiers().stream()
                    .filter(m -> m.getKey() != null && targetKey.equals(m.getKey()))
                    .toList()
                    .forEach(inst::removeModifier);
        }
    }

    /** stats 中有原版映射的属性作AttributeModifier 写入玩家*/
    private void applyVanillaModifiers(Player player, EquipmentStats stats) {
        for (ItemStat stat : ItemStat.values()) {
            if (stat.vanillaKey() == null) continue;
            double value = stats.get(stat);
            if (value == 0) continue;
            Attribute attr = Bukkit.getRegistry(Attribute.class).get(stat.vanillaKey());
            if (attr == null) continue;
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;

            NamespacedKey modKey = new NamespacedKey(NS, "equip_" + stat.name().toLowerCase());
            double modValue = stat.isPercent() ? value / 100.0 : value;
            AttributeModifier.Operation op = stat.isPercent()
                    ? AttributeModifier.Operation.MULTIPLY_SCALAR_1
                    : AttributeModifier.Operation.ADD_NUMBER;
            inst.addModifier(new AttributeModifier(modKey, modValue, op));
        }
    }
}

