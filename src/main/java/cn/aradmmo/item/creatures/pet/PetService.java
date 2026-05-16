package cn.aradmmo.item.creatures.pet;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.item.equipment.PlayerEquipment;
import cn.aradmmo.item.equipment.SlotDef;
import cn.aradmmo.item.equipment.SlotType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Companion runtime service.
 */
public final class PetService {

    public static final String ITEM_KEY = "aradmmo_pet_id";
    public static final String OWNER_KEY = "aradmmo_pet_owner";

    private final AradMmoPlugin plugin;
    private final Map<String, PetDef> defs = new HashMap<>();
    private final Map<String, PetCompanionProfile> profiles = new HashMap<>();
    private final Map<UUID, ActivePetState> activeByOwner = new HashMap<>();

    private File profileDirectory;
    private BukkitTask aiTask;

    public PetService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        defs.clear();
        activeByOwner.clear();

        File file = new File(plugin.getDataFolder(), "creatures/pets.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("[Pet] creatures/pets.yml not found in data folder");
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pets = config.getConfigurationSection("pets");
        if (pets != null) {
            for (String key : pets.getKeys(false)) {
                ConfigurationSection sec = pets.getConfigurationSection(key);
                if (sec != null) defs.put(key, PetDef.load(key, sec));
            }
        }

        profileDirectory = new File(plugin.getDataFolder(), "pets/profiles");
        if (!profileDirectory.exists()) profileDirectory.mkdirs();
    }

    public void startTicker() {
        if (aiTask != null && !aiTask.isCancelled()) aiTask.cancel();
        aiTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::tickCompanionAi, 20L, 20L);
    }

    public void shutdown() {
        if (aiTask != null) {
            aiTask.cancel();
            aiTask = null;
        }
        for (ActivePetState state : activeByOwner.values()) {
            saveProfile(state.ownerUuid, state.petId, state.profile);
        }
        activeByOwner.clear();
    }

    public ItemStack createPetItem(String defId) {
        PetDef def = defs.get(defId);
        if (def == null) return null;
        ItemStack item = def.createItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, ITEM_KEY);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, defId);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getPetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, ITEM_KEY);
        return pdc.get(key, PersistentDataType.STRING);
    }

    public boolean isPetItem(ItemStack item) {
        return getPetId(item) != null;
    }

    public int attributeBonus(Player player, String attrKey) {
        if (player == null || attrKey == null || attrKey.isBlank()) return 0;
        PetDef def = equippedPetDef(player);
        if (def == null) return 0;

        String petId = equippedPetId(player);
        if (petId == null) return def.attribute(attrKey);

        PetCompanionProfile profile = profile(player.getUniqueId(), petId, def);
        return def.attribute(attrKey) + profile.attribute(attrKey);
    }

    public boolean learnSkill(Player owner, String skillId) {
        if (owner == null || skillId == null || skillId.isBlank()) return false;
        String petId = equippedPetId(owner);
        PetDef def = equippedPetDef(owner);
        if (petId == null || def == null || def.skill(skillId).isEmpty()) return false;

        PetCompanionProfile profile = profile(owner.getUniqueId(), petId, def);
        boolean learned = profile.learnSkill(skillId);
        if (learned) saveProfile(owner.getUniqueId(), petId, profile);
        return learned;
    }

    public List<String> petIds() {
        return defs.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public ActivePetView activePetView(Player owner) {
        if (owner == null) return null;
        String petId = equippedPetId(owner);
        PetDef def = equippedPetDef(owner);
        if (petId == null || def == null) return null;
        PetCompanionProfile profile = profile(owner.getUniqueId(), petId, def);
        return new ActivePetView(petId, def, profile);
    }

    public List<PetSkillDef> skills(Player owner) {
        ActivePetView view = activePetView(owner);
        if (view == null) return List.of();
        return view.def().skills().values().stream()
                .sorted(Comparator.comparing(PetSkillDef::id))
                .toList();
    }

    public boolean learnedSkill(Player owner, String skillId) {
        if (owner == null || skillId == null || skillId.isBlank()) return false;
        ActivePetView view = activePetView(owner);
        if (view == null) return false;
        return view.profile().learnedSkills().contains(skillId.toLowerCase(Locale.ROOT));
    }

    public PetBehaviorMode behaviorMode(Player owner) {
        ActivePetView view = activePetView(owner);
        if (view == null) return PetBehaviorMode.FOLLOW;
        return view.profile().behaviorMode();
    }

    public PetBehaviorMode cycleBehaviorMode(Player owner) {
        ActivePetView view = activePetView(owner);
        if (view == null) return PetBehaviorMode.FOLLOW;
        PetBehaviorMode mode = view.profile().cycleBehaviorMode();
        saveProfile(owner.getUniqueId(), view.petId(), view.profile());
        return mode;
    }

    public PetBehaviorMode setBehaviorMode(Player owner, PetBehaviorMode mode) {
        ActivePetView view = activePetView(owner);
        if (view == null) return PetBehaviorMode.FOLLOW;
        view.profile().setBehaviorMode(mode);
        saveProfile(owner.getUniqueId(), view.petId(), view.profile());
        return view.profile().behaviorMode();
    }

    public void handleOwnerKill(Player killer, LivingEntity killedEntity) {
        ActivePetState state = activeByOwner.get(killer.getUniqueId());
        if (state == null) return;

        double maxHealth = killedEntity.getAttribute(Attribute.MAX_HEALTH) != null
                ? killedEntity.getAttribute(Attribute.MAX_HEALTH).getBaseValue() : 20.0;
        double exp = 2.0 + maxHealth * 0.35;
        boolean leveled = state.profile.gainExperience(exp);
        if (leveled && state.entity != null && !state.entity.isDead()) {
            applyScaledEntityAttributes(state.entity, state.def, state.profile);
            killer.sendMessage("§6[Pet] §a" + state.def.name() + " reached Lv." + state.profile.level());
        }
        saveProfile(state.ownerUuid, state.petId, state.profile);
    }

    public void spawnPet(PlayerEquipment eq, Player player, ItemStack petItem) {
        despawnPet(eq);

        String petId = getPetId(petItem);
        if (petId == null) return;
        PetDef def = defs.get(petId);
        if (def == null) return;

        PetCompanionProfile profile = profile(player.getUniqueId(), petId, def);

        EntityType type = def.entityType();
        if (!type.isAlive()) return;

        LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), type);
        applyScaledEntityAttributes(entity, def, profile);

        if (entity instanceof Tameable tameable) {
            tameable.setOwner(player);
        }

        if (entity instanceof Ageable ageable) {
            if (def.baby()) ageable.setBaby();
            else ageable.setAdult();
        }

        if (entity instanceof Wolf wolf && !def.attackMobs()) {
            wolf.setAggressive(false);
        }

        NamespacedKey ownerKey = new NamespacedKey(plugin, OWNER_KEY);
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());

        entity.setCustomName("§e" + def.name() + " §8[" + player.getName() + "]");
        entity.setCustomNameVisible(true);

        eq.setPet(entity);
        activeByOwner.put(player.getUniqueId(),
                new ActivePetState(player.getUniqueId(), petId, def, profile, entity));
    }

    public void despawnPet(PlayerEquipment eq) {
        activeByOwner.remove(eq.ownerUuid());
        LivingEntity pet = eq.getPet();
        if (pet != null && !pet.isDead()) {
            pet.remove();
        }
        eq.setPet(null);
    }

    public void onPetSlotChange(PlayerEquipment eq, Player player,
                                cn.aradmmo.item.equipment.SlotDef petSlot) {
        org.bukkit.inventory.ItemStack current = eq.getItem(petSlot);
        if (current == null || petSlot.isHolder(current) || current.getType() == org.bukkit.Material.AIR) {
            despawnPet(eq);
        } else if (isPetItem(current)) {
            spawnPet(eq, player, current);
        }
    }

    public UUID getOwner(LivingEntity entity) {
        NamespacedKey ownerKey = new NamespacedKey(plugin, OWNER_KEY);
        String uuidStr = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (uuidStr == null) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void applyAttribute(LivingEntity entity, Attribute attr, double value) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private void applyScaledEntityAttributes(LivingEntity entity, PetDef def, PetCompanionProfile profile) {
        double growth = 1.0 + (profile.level() - 1) * 0.08;
        double vitality = profile.attribute("vitality");
        double strength = profile.attribute("strength");
        double spirit = profile.attribute("spirit");

        double hp = def.health() * growth + vitality * 1.5;
        applyAttribute(entity, Attribute.MAX_HEALTH, hp);
        entity.setHealth(hp);

        if (def.damage() > 0) {
            double dmg = def.damage() * growth + strength * 0.25;
            applyAttribute(entity, Attribute.ATTACK_DAMAGE, dmg);
        }
        applyAttribute(entity, Attribute.MOVEMENT_SPEED, Math.min(0.7, def.speed() + spirit * 0.002));
    }

    public PetDef equippedPetDef(Player player) {
        if (plugin.equipment() == null || !plugin.equipment().isLoaded(player)) return null;
        PlayerEquipment eq = plugin.equipment().get(player);
        if (eq == null) return null;

        for (SlotDef slot : plugin.equipment().slots().getByType(SlotType.PET)) {
            ItemStack item = eq.getItem(slot);
            if (item == null || item.getType().isAir() || slot.isHolder(item)) continue;
            String petId = getPetId(item);
            if (petId == null) continue;
            PetDef def = defs.get(petId);
            if (def != null) return def;
        }
        return null;
    }

    public String equippedPetId(Player player) {
        if (plugin.equipment() == null || !plugin.equipment().isLoaded(player)) return null;
        PlayerEquipment eq = plugin.equipment().get(player);
        if (eq == null) return null;
        for (SlotDef slot : plugin.equipment().slots().getByType(SlotType.PET)) {
            ItemStack item = eq.getItem(slot);
            if (item == null || item.getType().isAir() || slot.isHolder(item)) continue;
            return getPetId(item);
        }
        return null;
    }

    private void tickCompanionAi() {
        for (Player owner : org.bukkit.Bukkit.getOnlinePlayers()) {
            ActivePetState state = activeByOwner.get(owner.getUniqueId());
            if (state == null) continue;

            if (state.entity == null || state.entity.isDead()) {
                activeByOwner.remove(owner.getUniqueId());
                saveProfile(state.ownerUuid, state.petId, state.profile);
                continue;
            }

            PetBehaviorMode mode = state.profile.behaviorMode();
            handleMovementMode(owner, state, mode);
            handleCombatMode(state, mode);

            castSkills(owner, state);
            if (state.def.canLoot()) {
                pickupNearbyItems(owner, state.entity, 4.0);
            }
        }
    }

    private void handleMovementMode(Player owner, ActivePetState state, PetBehaviorMode mode) {
        double distSq = state.entity.getLocation().distanceSquared(owner.getLocation());
        switch (mode) {
            case FOLLOW, COMBAT -> {
                if (distSq > 12.0 * 12.0) {
                    state.entity.teleport(owner.getLocation());
                }
            }
            case STAY -> {
                if (state.entity instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }
        }
    }

    private void handleCombatMode(ActivePetState state, PetBehaviorMode mode) {
        if (!(state.entity instanceof Mob mob)) return;
        if (mode == PetBehaviorMode.STAY) {
            mob.setTarget(null);
            return;
        }

        boolean canFight = mode == PetBehaviorMode.COMBAT || state.def.attackMobs();
        if (!canFight) {
            mob.setTarget(null);
            return;
        }

        if (mob.getTarget() == null || mob.getTarget().isDead()) {
            Monster nearest = findNearestMonster(state.entity, 12.0);
            if (nearest != null) mob.setTarget(nearest);
        }
    }

    private void castSkills(Player owner, ActivePetState state) {
        long now = owner.getWorld().getFullTime();
        for (String skillId : state.profile.learnedSkills()) {
            PetSkillDef skill = state.def.skill(skillId).orElse(null);
            if (skill == null) continue;
            long readyAt = state.skillReadyAt.getOrDefault(skill.id(), 0L);
            if (now < readyAt) continue;

            switch (skill.type()) {
                case ACTIVE -> castActiveSkill(state, skill);
                case PASSIVE -> castPassiveSkill(owner, skill, state);
                case SUPPORT -> castSupportSkill(owner, skill, state);
            }
            state.skillReadyAt.put(skill.id(), now + skill.cooldownTicks());
        }
    }

    private void castActiveSkill(ActivePetState state, PetSkillDef skill) {
        if (!(state.entity instanceof Mob mob)) return;
        LivingEntity target = mob.getTarget();
        if (target == null || target.isDead()) return;

        double base = state.def.damage() > 0 ? state.def.damage() : 2.0;
        double dmg = Math.max(1.0,
                (base + state.profile.attribute("strength") * 0.3)
                        * (1.0 + (state.profile.level() - 1) * 0.06)
                        * skill.power());
        target.damage(dmg, state.entity);
    }

    private void castPassiveSkill(Player owner, PetSkillDef skill, ActivePetState state) {
        double heal = Math.max(0.5, 0.4 * skill.power() + state.profile.attribute("spirit") * 0.03);
        double maxHp = owner.getAttribute(Attribute.MAX_HEALTH) != null
                ? owner.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
        owner.setHealth(Math.min(maxHp, owner.getHealth() + heal));
    }

    private void castSupportSkill(Player owner, PetSkillDef skill, ActivePetState state) {
        if (state.def.canLoot()) {
            pickupNearbyItems(owner, state.entity, 3.5 + skill.power());
        }
    }

    private void pickupNearbyItems(Player owner, LivingEntity pet, double radius) {
        for (Item item : pet.getWorld().getEntitiesByClass(Item.class)) {
            if (item.isDead() || !item.isValid()) continue;
            if (item.getLocation().distanceSquared(pet.getLocation()) > radius * radius) continue;

            Inventory inv = owner.getInventory();
            Map<Integer, ItemStack> left = inv.addItem(item.getItemStack());
            if (left.isEmpty()) item.remove();
            else item.setItemStack(left.values().iterator().next());
        }
    }

    private Monster findNearestMonster(LivingEntity pet, double radius) {
        Monster nearest = null;
        double nearestSq = radius * radius;
        for (Monster m : pet.getWorld().getEntitiesByClass(Monster.class)) {
            if (m.isDead()) continue;
            double d = m.getLocation().distanceSquared(pet.getLocation());
            if (d < nearestSq) {
                nearestSq = d;
                nearest = m;
            }
        }
        return nearest;
    }

    private PetCompanionProfile profile(UUID owner, String petId, PetDef def) {
        String key = owner + ":" + petId;
        return profiles.computeIfAbsent(key, ignored -> loadProfile(owner, petId, def));
    }

    private PetCompanionProfile loadProfile(UUID owner, String petId, PetDef def) {
        if (profileDirectory == null) {
            profileDirectory = new File(plugin.getDataFolder(), "pets/profiles");
            profileDirectory.mkdirs();
        }
        File file = new File(profileDirectory, owner + ".yml");
        if (!file.exists()) return createDefaultProfile(def);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String base = "pets." + petId + ".";
        int level = Math.max(1, yaml.getInt(base + "level", 1));
        double exp = Math.max(0.0, yaml.getDouble(base + "experience", 0.0));
        int points = Math.max(0, yaml.getInt(base + "skill-points", 0));
        PetBehaviorMode mode = PetBehaviorMode.parse(yaml.getString(base + "behavior-mode", "FOLLOW"));

        Map<String, Integer> attrs = new LinkedHashMap<>();
        ConfigurationSection attrSec = yaml.getConfigurationSection(base + "attributes");
        if (attrSec != null) {
            for (String k : attrSec.getKeys(false)) {
                attrs.put(k, Math.max(0, attrSec.getInt(k, 0)));
            }
        }
        if (attrs.isEmpty()) {
            attrs.put("strength", 0);
            attrs.put("intellect", 0);
            attrs.put("spirit", 0);
            attrs.put("vitality", 0);
        }

        Set<String> learned = new LinkedHashSet<>(yaml.getStringList(base + "learned-skills"));
        if (learned.isEmpty()) learned = defaultLearnedSkills(def);
        return new PetCompanionProfile(level, exp, points, attrs, learned, mode);
    }

    private PetCompanionProfile createDefaultProfile(PetDef def) {
        Map<String, Integer> attrs = new LinkedHashMap<>();
        attrs.put("strength", 0);
        attrs.put("intellect", 0);
        attrs.put("spirit", 0);
        attrs.put("vitality", 0);
        return new PetCompanionProfile(1, 0.0, 0, attrs, defaultLearnedSkills(def), PetBehaviorMode.FOLLOW);
    }

    private Set<String> defaultLearnedSkills(PetDef def) {
        Set<String> learned = new LinkedHashSet<>();
        for (PetSkillType type : PetSkillType.values()) {
            def.skills().values().stream()
                    .filter(s -> s.type() == type)
                    .min(Comparator.comparing(PetSkillDef::id))
                    .ifPresent(skill -> learned.add(skill.id()));
        }
        return learned;
    }

    private void saveProfile(UUID owner, String petId, PetCompanionProfile profile) {
        if (profileDirectory == null) {
            profileDirectory = new File(plugin.getDataFolder(), "pets/profiles");
            profileDirectory.mkdirs();
        }
        File file = new File(profileDirectory, owner + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String base = "pets." + petId + ".";
        yaml.set(base + "level", profile.level());
        yaml.set(base + "experience", profile.experience());
        yaml.set(base + "skill-points", profile.skillPoints());
        yaml.set(base + "behavior-mode", profile.behaviorMode().name());
        for (Map.Entry<String, Integer> e : profile.attributes().entrySet()) {
            yaml.set(base + "attributes." + e.getKey(), e.getValue());
        }
        yaml.set(base + "learned-skills", new ArrayList<>(profile.learnedSkills()));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Pet] Failed to save profile for " + owner + ": " + ex.getMessage());
        }
    }

    private static final class ActivePetState {
        private final UUID ownerUuid;
        private final String petId;
        private final PetDef def;
        private final PetCompanionProfile profile;
        private final LivingEntity entity;
        private final Map<String, Long> skillReadyAt = new HashMap<>();

        private ActivePetState(UUID ownerUuid, String petId, PetDef def,
                               PetCompanionProfile profile, LivingEntity entity) {
            this.ownerUuid = ownerUuid;
            this.petId = petId;
            this.def = def;
            this.profile = profile;
            this.entity = entity;
        }
    }

    public record ActivePetView(String petId, PetDef def, PetCompanionProfile profile) {}
}
