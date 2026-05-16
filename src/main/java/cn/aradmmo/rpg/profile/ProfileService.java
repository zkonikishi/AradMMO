package cn.aradmmo.rpg.profile;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.item.equipment.stat.EquipmentStats;
import cn.aradmmo.item.equipment.stat.ItemStat;
import cn.aradmmo.rpg.classes.ClassDefinition;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ProfileService {
    private static final String DEFAULT_VIP = "standard";
    private static final String DEFAULT_ARCHETYPE = "adventurer";

    private final AradMmoPlugin plugin;
    private final Map<UUID, PlayerProfile> profiles;
    private File dataDirectory;

    public ProfileService(AradMmoPlugin plugin) {
        this.plugin = plugin;
        this.profiles = new HashMap<>();
    }

    public void reload() {
        this.dataDirectory = new File(plugin.getDataFolder(), "profiles");
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create profile storage directory");
        }

        // Save all online players before evicting to prevent data loss on /am reload
        for (UUID uuid : new java.util.HashSet<>(profiles.keySet())) {
            org.bukkit.entity.Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                PlayerProfile p = profiles.get(uuid);
                if (p != null) save(online, p);
            }
        }
        profiles.clear();
    }

    public PlayerProfile profile(OfflinePlayer player) {
        return profiles.computeIfAbsent(player.getUniqueId(), ignored -> load(player));
    }

    /** Eagerly loads the profile into memory on join so the first combat hit has no I/O delay. */
    public void preload(org.bukkit.entity.Player player) {
        profiles.computeIfAbsent(player.getUniqueId(), ignored -> load(player));
    }

    /** Saves the in-memory profile and evicts it from the cache on quit. */
    public void saveAndEvict(UUID uuid) {
        PlayerProfile profile = profiles.remove(uuid);
        if (profile == null) return;
        org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
        save(op, profile);
    }

    /** Returns an unmodifiable snapshot of all currently cached profiles (used for leaderboard). */
    public java.util.Collection<PlayerProfile> cachedProfiles() {
        return java.util.Collections.unmodifiableCollection(profiles.values());
    }

    /**
     * Reads every YAML file in the profiles directory and returns a list sorted by the
     * given comparator. Profiles already in memory are used directly; offline ones are
     * loaded transiently without caching.
     */
    public List<PlayerProfile> leaderboard(java.util.Comparator<PlayerProfile> order, int limit) {
        File[] files = dataDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        List<PlayerProfile> all = new java.util.ArrayList<>();
        for (File file : files) {
            String uuidStr = file.getName().replace(".yml", "");
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (profiles.containsKey(uuid)) {
                all.add(profiles.get(uuid));
            } else {
                all.add(loadFromFile(file, uuidStr));
            }
        }
        all.sort(order);
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    public ProfileMutationResult addExperience(OfflinePlayer player, double amount) {
        PlayerProfile profile = profile(player);
        profile.experience(profile.experience() + Math.max(0D, amount));

        int levelsGained = 0;
        int statPointsGained = 0;
        int skillPointsGained = 0;
        double remaining = profile.experience();
        double threshold = threshold(profile.level());
        while (remaining >= threshold) {
            remaining -= threshold;
            profile.level(profile.level() + 1);
            levelsGained++;
            int statGain = plugin.getConfig().getInt("progression.points.stat-per-level", 3);
            int skillGain = plugin.getConfig().getInt("progression.points.skill-per-level", 1);
            profile.statPoints(profile.statPoints() + statGain);
            profile.skillPoints(profile.skillPoints() + skillGain);
            statPointsGained += statGain;
            skillPointsGained += skillGain;
            threshold = threshold(profile.level());
        }

        profile.experience(remaining);

        // Auto-advance to external (外传) class at level 20 if still adventurer
        if (profile.level() >= 20 && profile.archetype().equals(DEFAULT_ARCHETYPE)
                && !profile.gender().isEmpty()) {
            String externalId = profile.gender().equals("male") ? "dark-warrior" : "creator";
            if (availableArchetypes().contains(externalId)) {
                profile.archetype(externalId);
                resetAttributes(profile, externalId);
            }
        }

        save(player, profile);
        return new ProfileMutationResult(profile, levelsGained > 0, levelsGained, statPointsGained, skillPointsGained);
    }

    public PlayerProfile setBalance(OfflinePlayer player, double value) {
        PlayerProfile profile = profile(player);
        profile.balance(value);
        save(player, profile);
        return profile;
    }

    public PlayerProfile addBalance(OfflinePlayer player, double value) {
        PlayerProfile profile = profile(player);
        profile.balance(profile.balance() + value);
        save(player, profile);
        return profile;
    }

    public PlayerProfile takeBalance(OfflinePlayer player, double value) {
        PlayerProfile profile = profile(player);
        profile.balance(profile.balance() - value);
        save(player, profile);
        return profile;
    }

    public PlayerProfile setVipTier(OfflinePlayer player, String vipTier) {
        PlayerProfile profile = profile(player);
        profile.vipTier(vipTier.toLowerCase(Locale.ROOT));
        save(player, profile);
        return profile;
    }

    public PlayerProfile setNameGradient(OfflinePlayer player, String gradientSpec) {
        PlayerProfile profile = profile(player);
        profile.nameGradient(gradientSpec);
        save(player, profile);
        return profile;
    }

    public PlayerProfile setChatGradient(OfflinePlayer player, String gradientSpec) {
        PlayerProfile profile = profile(player);
        profile.chatGradient(gradientSpec);
        save(player, profile);
        return profile;
    }

    public PlayerProfile setElementAttack(OfflinePlayer player, String elemKey, int value) {
        PlayerProfile profile = profile(player);
        profile.elementAttack(elemKey.toLowerCase(Locale.ROOT), value);
        save(player, profile);
        return profile;
    }

    public PlayerProfile setElementResist(OfflinePlayer player, String elemKey, int value) {
        PlayerProfile profile = profile(player);
        profile.elementResist(elemKey.toLowerCase(Locale.ROOT), value);
        save(player, profile);
        return profile;
    }

    public PlayerProfile setArchetype(OfflinePlayer player, String archetype) {
        String normalized = archetype.toLowerCase(Locale.ROOT);
        if (!availableArchetypes().contains(normalized)) {
            throw new IllegalArgumentException("Unknown archetype: " + archetype);
        }

        PlayerProfile profile = profile(player);
        profile.archetype(normalized);
        resetAttributes(profile, normalized);
        save(player, profile);
        return profile;
    }

    /**
     * 将属性分配重置为职业默认值，返还已消耗的属性点
     * 点数退还量 = 当前各属- 职业默认属之和
     */
    public PlayerProfile resetStats(OfflinePlayer player) {
        PlayerProfile profile = profile(player);
        String archetype = profile.archetype();

        // 计算已投入的属性点总量
        int invested = 0;
        for (String key : plugin.attributeKeys()) {
            int current  = profile.attribute(key);
            int defaults = defaultAttribute(archetype, key);
            invested += Math.max(0, current - defaults);
        }

        // 重置属性至职业默认
        for (String key : plugin.attributeKeys()) {
            profile.attribute(key, defaultAttribute(archetype, key));
        }

        // 退还属性点
        profile.statPoints(profile.statPoints() + invested);
        save(player, profile);
        return profile;
    }

    public PlayerProfile allocateAttribute(OfflinePlayer player, String attrKey, int amount) {
        PlayerProfile profile = profile(player);
        int sanitized = Math.max(0, amount);
        if (profile.statPoints() < sanitized) {
            throw new IllegalArgumentException("Not enough stat points");
        }

        profile.attribute(attrKey, profile.attribute(attrKey) + sanitized);
        profile.statPoints(profile.statPoints() - sanitized);
        save(player, profile);
        return profile;
    }

    public PlayerProfile allocateSkill(OfflinePlayer player, String skillId, int amount) {
        PlayerProfile profile = profile(player);
        String normalized = skillId.toLowerCase(Locale.ROOT);
        int sanitized = Math.max(0, amount);
        int maxLevel = skillCap(normalized);
        if (!availableSkills().contains(normalized)) {
            throw new IllegalArgumentException("Unknown skill: " + skillId);
        }
        if (profile.skillPoints() < sanitized) {
            throw new IllegalArgumentException("Not enough skill points");
        }
        if (profile.skillLevel(normalized) + sanitized > maxLevel) {
            throw new IllegalArgumentException("Skill cap exceeded");
        }

        profile.skillLevel(normalized, profile.skillLevel(normalized) + sanitized);
        profile.skillPoints(profile.skillPoints() - sanitized);
        save(player, profile);
        return profile;
    }

    public Set<String> availableArchetypes() {
        Set<String> ids = plugin.classes().ids();
        return ids.isEmpty() ? Set.of(DEFAULT_ARCHETYPE) : ids;
    }

    public List<String> availableSkills() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skills");
        return section == null ? List.of() : section.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public int skillCap(String skillId) {
        return plugin.getConfig().getInt("skills." + skillId + ".max-level", 5);
    }

    /**
     * 返回玩家某项“最终属性”数值：职业/加点基础 + 装备加成 + 宠物加成。
     *
     * <p>该方法用于运行时战斗、HP/MP/ST 公式；不会写回 profile 文件。</p>
     */
    public int effectiveAttribute(Player player, String attrKey) {
        String key = attrKey == null ? "" : attrKey.toLowerCase(Locale.ROOT);
        if (key.isBlank()) return 0;

        PlayerProfile profile = profile(player);
        int base = profile.attribute(key);
        int equip = equipmentAttributeBonus(player, key);
        int pet = plugin.equipment() == null ? 0 : plugin.equipment().pets().attributeBonus(player, key);
        return base + equip + pet;
    }

    public double threshold(int level) {
        double base = plugin.getConfig().getDouble("progression.base-exp", 100D);
        double scale = plugin.getConfig().getDouble("progression.exp-scale", 25D);
        return base + ((Math.max(1, level) - 1D) * scale);
    }

    private int equipmentAttributeBonus(Player player, String key) {
        ItemStat stat = switch (key) {
            case "strength" -> ItemStat.STRENGTH;
            case "intellect" -> ItemStat.INTELLECT;
            case "spirit" -> ItemStat.SPIRIT;
            case "vitality" -> ItemStat.VITALITY;
            default -> null;
        };
        if (stat == null || plugin.equipment() == null) return 0;
        EquipmentStats stats = plugin.equipment().stats().statsOf(player);
        return (int) Math.round(stats.get(stat));
    }

    private PlayerProfile load(OfflinePlayer player) {
        File file = file(player.getUniqueId());
        if (!file.exists()) {
            PlayerProfile profile = new PlayerProfile(
                    safeName(player),
                    1,
                    0D,
                    0D,
                    DEFAULT_VIP,
                    "",
                    "",
                    DEFAULT_ARCHETYPE,
                    "",
                    0,
                    0,
                    defaultAttributes(DEFAULT_ARCHETYPE),
                    new HashMap<>(),
                    defaultElementAttacks(DEFAULT_ARCHETYPE),
                    defaultElementResists(DEFAULT_ARCHETYPE)
            );
            save(player, profile);
            return profile;
        }
        return loadFromFile(file, safeName(player));
    }

    private PlayerProfile loadFromFile(File file, String fallbackName) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String archetype = yaml.getString("archetype", DEFAULT_ARCHETYPE).toLowerCase(Locale.ROOT);
        Map<String, Integer> attributes = new LinkedHashMap<>();
        for (String key : plugin.attributeKeys()) {
            int configured = defaultAttribute(archetype, key);
            attributes.put(key, Math.max(0, yaml.getInt("attributes." + key, configured)));
        }
        Map<String, Integer> skills = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("skills");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                skills.put(key.toLowerCase(Locale.ROOT), Math.max(0, section.getInt(key, 0)));
            }
        }

        Map<String, Integer> elemAtk = new LinkedHashMap<>();
        Map<String, Integer> elemRes = new LinkedHashMap<>();
        for (String elem : plugin.elementKeys()) {
            int defAtk = defaultElementAttack(archetype, elem);
            int defRes = defaultElementResist(archetype, elem);
            elemAtk.put(elem, yaml.getInt("element-attack." + elem, defAtk));
            elemRes.put(elem, yaml.getInt("element-resist." + elem, defRes));
        }

        return new PlayerProfile(
                yaml.getString("name", fallbackName),
                Math.max(1, yaml.getInt("level", 1)),
                Math.max(0D, yaml.getDouble("experience", 0D)),
                Math.max(0D, yaml.getDouble("balance", 0D)),
                yaml.getString("vip-tier", DEFAULT_VIP),
            yaml.getString("cosmetic.name-gradient", ""),
            yaml.getString("cosmetic.chat-gradient", ""),
                archetype,
                yaml.getString("gender", ""),
                Math.max(0, yaml.getInt("points.stat", 0)),
                Math.max(0, yaml.getInt("points.skill", 0)),
                attributes,
                skills,
                elemAtk,
                elemRes
        );
    }

    public void save(OfflinePlayer player, PlayerProfile profile) {
        File file = file(player.getUniqueId());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", safeName(player));
        yaml.set("level", profile.level());
        yaml.set("experience", profile.experience());
        yaml.set("balance", profile.balance());
        yaml.set("vip-tier", profile.vipTier());
        yaml.set("cosmetic.name-gradient", profile.nameGradient());
        yaml.set("cosmetic.chat-gradient", profile.chatGradient());
        yaml.set("archetype", profile.archetype());
        yaml.set("gender", profile.gender());
        yaml.set("points.stat", profile.statPoints());
        yaml.set("points.skill", profile.skillPoints());
        for (Map.Entry<String, Integer> entry : profile.attributes().entrySet()) {
            yaml.set("attributes." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : profile.elementAttacks().entrySet()) {
            yaml.set("element-attack." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : profile.elementResists().entrySet()) {
            yaml.set("element-resist." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : profile.skills().entrySet()) {
            yaml.set("skills." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save profile for " + safeName(player), exception);
        }
    }

    private File file(UUID uniqueId) {
        return new File(dataDirectory, uniqueId + ".yml");
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private void resetAttributes(PlayerProfile profile, String archetype) {
        for (String key : plugin.attributeKeys()) {
            profile.attribute(key, defaultAttribute(archetype, key));
        }
        for (String elem : plugin.elementKeys()) {
            profile.elementAttack(elem, defaultElementAttack(archetype, elem));
            profile.elementResist(elem, defaultElementResist(archetype, elem));
        }
        profile.skillPoints(0);
        profile.statPoints(0);
        for (String skillId : profile.skills().keySet()) {
            profile.skillLevel(skillId, 0);
        }
    }

    private Map<String, Integer> defaultAttributes(String archetype) {
        Map<String, Integer> attributes = new LinkedHashMap<>();
        for (String key : plugin.attributeKeys()) {
            attributes.put(key, defaultAttribute(archetype, key));
        }
        return attributes;
    }

    private int defaultAttribute(String archetype, String key) {
        ClassDefinition cls = plugin.classes().get(archetype);
        if (cls == null) return 5;
        return cls.baseAttributes().getOrDefault(key, 5);
    }

    private Map<String, Integer> defaultElementAttacks(String archetype) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String elem : plugin.elementKeys()) {
            map.put(elem, defaultElementAttack(archetype, elem));
        }
        return map;
    }

    private Map<String, Integer> defaultElementResists(String archetype) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String elem : plugin.elementKeys()) {
            map.put(elem, defaultElementResist(archetype, elem));
        }
        return map;
    }

    private int defaultElementAttack(String archetype, String elem) {
        ClassDefinition cls = plugin.classes().get(archetype);
        if (cls == null) return 0;
        return cls.baseElementAttack().getOrDefault(elem, 0);
    }

    private int defaultElementResist(String archetype, String elem) {
        ClassDefinition cls = plugin.classes().get(archetype);
        if (cls == null) return 0;
        return cls.baseElementResist().getOrDefault(elem, 0);
    }

    // ── class-tree helpers ────────────────────────────────────────────────

    public int classStage(String classId) {
        ClassDefinition cls = plugin.classes().get(classId);
        return cls == null ? 0 : cls.stage();
    }

    public String classParent(String classId) {
        ClassDefinition cls = plugin.classes().get(classId);
        return cls == null ? "" : cls.parent();
    }

    public String classGender(String classId) {
        ClassDefinition cls = plugin.classes().get(classId);
        return cls == null ? "any" : cls.gender();
    }

    public int classLevelReq(String classId) {
        ClassDefinition cls = plugin.classes().get(classId);
        return cls == null ? 1 : cls.requiresLevel();
    }

    public String classDisplay(String classId) {
        ClassDefinition cls = plugin.classes().get(classId);
        return cls == null ? classId : cls.display();
    }

    /** Returns the class IDs the player can currently advance into (respects level, gender, parent). */
    public List<String> availableClassesFor(PlayerProfile profile) {
        int stage = classStage(profile.archetype());
        if (stage >= 2) return List.of();

        List<String> result = new java.util.ArrayList<>();
        for (ClassDefinition cls : plugin.classes().all()) {
            if (cls.stage() != stage + 1) continue;
            if (!cls.parent().equals(profile.archetype())) continue;
            if (!cls.gender().equals("any") && !cls.gender().equals(profile.gender())) continue;
            if (profile.level() < cls.requiresLevel()) continue;
            if (cls.isExternal()) continue;
            result.add(cls.id());
        }
        return result;
    }

    public PlayerProfile setGender(org.bukkit.entity.Player player, String gender) {
        String normalized = gender.toLowerCase(Locale.ROOT);
        if (!normalized.equals("male") && !normalized.equals("female")) {
            throw new IllegalArgumentException("Invalid gender: " + gender);
        }
        PlayerProfile profile = profile(player);
        profile.gender(normalized);
        save(player, profile);
        return profile;
    }

    /** Advances a player's class (validates prerequisites). */
    public PlayerProfile advanceClass(org.bukkit.entity.Player player, String classId) {
        String normalized = classId.toLowerCase(Locale.ROOT);
        PlayerProfile profile = profile(player);
        if (!availableClassesFor(profile).contains(normalized)) {
            throw new IllegalArgumentException("Class not available: " + classId);
        }
        profile.archetype(normalized);
        resetAttributes(profile, normalized);
        save(player, profile);
        return profile;
    }
}

