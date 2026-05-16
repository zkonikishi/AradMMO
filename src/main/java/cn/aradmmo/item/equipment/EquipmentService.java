package cn.aradmmo.item.equipment;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.item.equipment.classification.AradEquipCategory;
import cn.aradmmo.item.equipment.classification.AradEquipPart;
import cn.aradmmo.item.equipment.classification.AradEquipmentSource;
import cn.aradmmo.item.equipment.classification.AradItemClass;
import cn.aradmmo.item.equipment.classification.AradItemGroup;
import cn.aradmmo.item.equipment.classification.AradTemplateCatalog;
import cn.aradmmo.item.equipment.backpack.BackpackService;
import cn.aradmmo.item.creatures.pet.PetService;
import cn.aradmmo.item.equipment.stat.AradArmorType;
import cn.aradmmo.item.equipment.stat.StatApplier;
import cn.aradmmo.item.equipment.stat.StatItemBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * 装备系统核心服务
 * <p>
 * 职责
 * <ul>
 *   <li>equipment/slots.yml 加载槽位定义</li>
 *   <li>管理每个在线玩家{@link PlayerEquipment}</li>
 *   <li>同步装备面板 玩家原版护甲/快捷副手</li>
 *   <li>定期刷新 INFO 槽位（实时显HP/MP/ST/等级/li>
 *   <li>协调背包服务和宠物服/li>
 * </ul>
 */
public final class EquipmentService {

    private final AradMmoPlugin plugin;
    private final SlotManager   slotManager  = new SlotManager();
    private final Map<UUID, PlayerEquipment> equipments = new HashMap<>();

    private final BackpackService backpackService;
    private final PetService      petService;
    private final StatApplier     statApplier;

    private ItemStack          fillItem;
    private BukkitTask         infoTask;
    /** stat-templates.yml 加载的物品模板配置（可能null）*/
    private YamlConfiguration  statTemplatesConfig;
    private AradTemplateCatalog aradTemplateCatalog = AradTemplateCatalog.fromTemplates(null);

    public EquipmentService(AradMmoPlugin plugin) {
        this.plugin          = plugin;
        this.backpackService = new BackpackService(plugin);
        this.petService      = new PetService(plugin);
        this.statApplier     = new StatApplier(slotManager, player -> {
            if (plugin.profiles() == null) return "";
            try {
                String archetype = plugin.profiles().profile(player).archetype();
                if (archetype == null || archetype.isBlank()) return "";

                String key = archetype.trim().toLowerCase();
                int classStage = 0;
                var rootCls = plugin.classes() == null ? null : plugin.classes().get(key);
                if (rootCls != null) classStage = Math.max(0, rootCls.stage());
                for (int i = 0; i < 8; i++) {
                    var cls = plugin.classes() == null ? null : plugin.classes().get(key);
                    if (cls != null && cls.armorMastery() != null && !cls.armorMastery().isBlank()) {
                        AradArmorType direct = AradArmorType.fromString(cls.armorMastery());
                        if (direct != AradArmorType.UNKNOWN) {
                            return "armortype:" + direct.name() + "|stage:" + classStage;
                        }
                    }
                    if (cls == null || cls.stage() <= 1) return key + "|stage:" + classStage;
                    String parent = cls.parent();
                    if (parent == null || parent.isBlank()) {
                        String style = cls.combatStyle();
                        if (style != null && !style.isBlank()) {
                            return "style:" + style.trim().toLowerCase() + "|stage:" + classStage;
                        }
                        return key + "|stage:" + classStage;
                    }
                    key = parent.trim().toLowerCase();
                }
                return key + "|stage:" + classStage;
            } catch (Exception ignored) {
                return "";
            }
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void reload() {
        // 提取默认配置
        extractDefault("item/equipment/slots.yml",     "equipment/slots.yml");
        extractDefault("item/equipment/backpacks.yml", "equipment/backpacks.yml");
        extractDefault("item/creatures/pet/pets.yml",  "creatures/pets.yml");
        extractDefault("item/equipment/armor/system.yml", "equipment/armor/system.yml");

        // 加载槽位定义
        File slotsFile = new File(plugin.getDataFolder(), "equipment/slots.yml");
        YamlConfiguration slotsConfig = YamlConfiguration.loadConfiguration(slotsFile);
        slotManager.load(slotsConfig);

        // 构建填充物品
        String fillMat = slotsConfig.getString("fill", "GRAY_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(fillMat);
        if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
        fillItem = new ItemStack(mat);
        ItemMeta fillMeta = fillItem.getItemMeta();
        if (fillMeta != null) {
            fillMeta.setDisplayName(" ");
            fillItem.setItemMeta(fillMeta);
        }

        backpackService.reload();
        petService.reload();

        // 加载装备属性模板
        extractDefault("item/equipment/stat-templates.yml", "equipment/stat-templates.yml");
        extractDefault("item/equipment/weapons/templates.yml", "equipment/weapons/templates.yml");
        extractDefault("item/equipment/armor/templates.yml", "equipment/armor/templates.yml");
        extractDefault("item/equipment/accessories/templates.yml", "equipment/accessories/templates.yml");
        extractDefault("item/equipment/special equipment/templates.yml", "equipment/special equipment/templates.yml");
        extractDefault("item/equipment/titles/templates.yml", "equipment/titles/templates.yml");
        extractDefault("item/equipment/equipment fusion/templates.yml", "equipment/equipment fusion/templates.yml");
        statTemplatesConfig = loadTemplateConfigs();
        aradTemplateCatalog = AradTemplateCatalog.fromTemplates(statTemplatesConfig);

        File armorSystem = new File(plugin.getDataFolder(), "equipment/armor/system.yml");
        statApplier.reloadArmorSystem(YamlConfiguration.loadConfiguration(armorSystem));
    }

    /** 启动2 秒刷INFO 槽位的定时器*/
    public void startInfoUpdater() {
        if (infoTask != null && !infoTask.isCancelled()) infoTask.cancel();
        infoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshInfoSlots, 40L, 40L);
        petService.startTicker();
    }

    /** 关闭服务，保存所有玩家数据*/
    public void shutdown() {
        if (infoTask != null) { infoTask.cancel(); infoTask = null; }
        petService.shutdown();
        backpackService.saveAll();
        for (UUID uuid : equipments.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) savePlayer(player);
        }
        // 解散所有宠
        for (PlayerEquipment eq : equipments.values()) {
            petService.despawnPet(eq);
        }
        equipments.clear();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public SlotManager    slots()      { return slotManager; }
    public BackpackService backpacks() { return backpackService; }
    public PetService     pets()       { return petService; }
    public StatApplier    stats()      { return statApplier; }

    public StatApplier.ArmorDebugSnapshot debugArmor(Player player) {
        PlayerEquipment eq = get(player);
        if (eq == null) return null;
        return statApplier.debugArmor(player, eq);
    }

    public boolean isLoaded(Player player) {
        return equipments.containsKey(player.getUniqueId());
    }

    public PlayerEquipment get(Player player) {
        return equipments.get(player.getUniqueId());
    }

    // ── Player lifecycle ──────────────────────────────────────────────────────

    /** 玩家上线时初始化装备面板（从磁盘加载并与原版护甲同步）*/
    public void initPlayer(Player player) {
        PlayerEquipment eq = loadFromDisk(player);
        equipments.put(player.getUniqueId(), eq);
        syncArmorToEquip(eq, player);
        syncShieldToEquip(eq, player);
        syncHotbarToEquip(eq, player);
        eq.fillHolders(slotManager, fillItem);
        updateInfoSlots(eq, player);
        statApplier.recalculate(player, eq);
    }

    /** 玩家下线时保存并卸载装备面板*/
    public void unloadPlayer(Player player) {
        PlayerEquipment eq = equipments.remove(player.getUniqueId());
        if (eq == null) return;
        petService.despawnPet(eq);
        savePlayer(player, eq);
        statApplier.unload(player);
    }

    /** 打开指定玩家的装备面板界面*/
    public void openEquipment(Player player) {
        PlayerEquipment eq = equipments.computeIfAbsent(player.getUniqueId(), id -> {
            PlayerEquipment fresh = loadFromDisk(player);
            syncArmorToEquip(fresh, player);
            syncShieldToEquip(fresh, player);
            syncHotbarToEquip(fresh, player);
            fresh.fillHolders(slotManager, fillItem);
            return fresh;
        });
        updateInfoSlots(eq, player);
        eq.openFor(player);
    }

    // ── Synchronization ───────────────────────────────────────────────────────

    /** 从玩家原版护装备面板护甲槽*/
    public void syncArmorToEquip(PlayerEquipment eq, Player player) {
        PlayerInventory pi = player.getInventory();
        putArmorSlot(eq, "helmet",     pi.getHelmet());
        putArmorSlot(eq, "chestplate", pi.getChestplate());
        putArmorSlot(eq, "leggings",   pi.getLeggings());
        putArmorSlot(eq, "boots",      pi.getBoots());
    }

    private void putArmorSlot(PlayerEquipment eq, String slotName, ItemStack item) {
        SlotDef slot = slotManager.getByName(slotName);
        if (slot == null) return;
        eq.setItem(slot, (item == null || item.getType() == Material.AIR)
                ? slot.holderItem() : item.clone());
    }

    /** 从装备面板护甲槽 玩家原版护甲*/
    public void applyArmorToPlayer(PlayerEquipment eq, Player player) {
        PlayerInventory pi = player.getInventory();
        pi.setHelmet(takeArmorItem(eq, "helmet"));
        pi.setChestplate(takeArmorItem(eq, "chestplate"));
        pi.setLeggings(takeArmorItem(eq, "leggings"));
        pi.setBoots(takeArmorItem(eq, "boots"));
    }

    private ItemStack takeArmorItem(PlayerEquipment eq, String slotName) {
        SlotDef slot = slotManager.getByName(slotName);
        if (slot == null) return null;
        ItemStack item = eq.getItem(slot);
        return (item == null || slot.isHolder(item)) ? null : item;
    }

    /** 从玩家副盾牌槽*/
    public void syncShieldToEquip(PlayerEquipment eq, Player player) {
        SlotDef shieldSlot = slotManager.getFirst(SlotType.SHIELD);
        if (shieldSlot == null) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == Material.AIR || !shieldSlot.isAllowed(offhand)) {
            eq.setItem(shieldSlot, shieldSlot.holderItem());
        } else {
            eq.setItem(shieldSlot, offhand.clone());
        }
    }

    /** 从盾牌槽 玩家副手*/
    public void applyShieldToPlayer(PlayerEquipment eq, Player player) {
        SlotDef shieldSlot = slotManager.getFirst(SlotType.SHIELD);
        if (shieldSlot == null) return;
        ItemStack item = eq.getItem(shieldSlot);
        player.getInventory().setItemInOffHand(
                (item == null || shieldSlot.isHolder(item)) ? null : item);
    }

    /** 从玩家快捷栏 ACTIVE 槽位*/
    public void syncHotbarToEquip(PlayerEquipment eq, Player player) {
        for (SlotDef slot : slotManager.getByType(SlotType.ACTIVE)) {
            int qs = slot.quickSlot();
            if (qs < 0 || qs > 8) continue;
            ItemStack hotbarItem = player.getInventory().getItem(qs);
            eq.setItem(slot, (hotbarItem == null || hotbarItem.getType() == Material.AIR)
                    ? slot.holderItem() : hotbarItem.clone());
        }
    }

    /** ACTIVE 槽位 玩家快捷栏*/
    public void applyHotbarToPlayer(PlayerEquipment eq, Player player) {
        for (SlotDef slot : slotManager.getByType(SlotType.ACTIVE)) {
            int qs = slot.quickSlot();
            if (qs < 0 || qs > 8) continue;
            ItemStack item = eq.getItem(slot);
            player.getInventory().setItem(qs,
                    (item == null || slot.isHolder(item)) ? null : item.clone());
        }
    }

    // ── INFO slots ────────────────────────────────────────────────────────────

    /** 刷新 INFO 槽位lore（显示实时属性）*/
    public void updateInfoSlots(PlayerEquipment eq, Player player) {
        for (SlotDef slot : slotManager.getByType(SlotType.INFO)) {
            ItemStack holder = slot.holderItem();
            ItemMeta meta = holder.getItemMeta();
            if (meta == null) { eq.setItem(slot, holder); continue; }

            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();

            double maxHp    = plugin.hp().max(player);
            double curHp    = player.getHealth();
            int    maxMp    = plugin.mana().max(player);
            int    curMp    = plugin.mana().current(player);
            double maxSt    = plugin.stamina().max(player);
            double curSt    = plugin.stamina().current(player);
            int    level    = plugin.profiles().profile(player).level();

            List<String> resolved = new ArrayList<>(lore.size());
            for (String line : lore) {
                line = line.replace("%hp_current%", String.format("%.1f", curHp))
                           .replace("%hp_max%",     String.format("%.1f", maxHp))
                           .replace("%mp_current%", String.valueOf(curMp))
                           .replace("%mp_max%",     String.valueOf(maxMp))
                           .replace("%st_current%", String.format("%.0f", curSt))
                           .replace("%st_max%",     String.format("%.0f", maxSt))
                           .replace("%level%",      String.valueOf(level));
                resolved.add(line);
            }
            meta.setLore(resolved);
            holder.setItemMeta(meta);
            eq.setItem(slot, holder);
        }
    }

    private void refreshInfoSlots() {
        for (Map.Entry<UUID, PlayerEquipment> entry : equipments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            PlayerEquipment eq = entry.getValue();
            if (eq.isOpenFor(player)) updateInfoSlots(eq, player);
        }
    }

    // ── Elytra ────────────────────────────────────────────────────────────────

    /** 玩家开始滑翔时：将鞘翅槽中的鞘翅装备到胸甲槽，并保存原胸甲*/
    public void onStartGliding(Player player) {
        PlayerEquipment eq = get(player);
        if (eq == null || eq.isGliding()) return;
        SlotDef elytraSlot = slotManager.getFirst(SlotType.ELYTRA);
        if (elytraSlot == null) return;
        ItemStack elytra = eq.getItem(elytraSlot);
        if (elytra == null || elytraSlot.isHolder(elytra) || elytra.getType() != Material.ELYTRA) return;
        eq.setSavedChestplate(player.getInventory().getChestplate());
        player.getInventory().setChestplate(elytra.clone());
        eq.setGliding(true);
    }

    /** 玩家停止滑翔时：恢复原胸甲*/
    public void onStopGliding(Player player) {
        PlayerEquipment eq = get(player);
        if (eq == null || !eq.isGliding()) return;
        player.getInventory().setChestplate(eq.getSavedChestplate());
        eq.setSavedChestplate(null);
        eq.setGliding(false);
    }

    // ── Save / Load ───────────────────────────────────────────────────────────

    public void savePlayer(Player player) {
        PlayerEquipment eq = get(player);
        if (eq != null) savePlayer(player, eq);
    }

    private void savePlayer(Player player, PlayerEquipment eq) {
        File file = playerFile(player.getUniqueId());
        file.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        for (SlotDef slot : slotManager.getAll()) {
            if (slot.type() == SlotType.INFO || slot.type() == SlotType.ACTION) continue;
            ItemStack item = eq.getItem(slot);
            if (item != null && !slot.isHolder(item) && item.getType() != Material.AIR) {
                config.set("slots." + slot.name(), item);
            }
        }
        try { config.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning(
                    "Failed to save equipment for " + player.getName() + ": " + e.getMessage());
        }
    }

    private PlayerEquipment loadFromDisk(Player player) {
        PlayerEquipment eq = new PlayerEquipment(player.getUniqueId());
        File file = playerFile(player.getUniqueId());
        if (!file.exists()) return eq;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection slots = config.getConfigurationSection("slots");
        if (slots == null) return eq;
        for (String key : slots.getKeys(false)) {
            SlotDef slot = slotManager.getByName(key);
            if (slot == null) continue;
            ItemStack item = slots.getItemStack(key);
            if (item != null && item.getType() != Material.AIR) {
                eq.setItem(slot, item);
            }
        }
        return eq;
    }

    private File playerFile(UUID uuid) {
        return new File(plugin.getDataFolder(), "equipment/players/" + uuid + ".yml");
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private void extractDefault(String resourcePath, String outputPath) {
        File file = new File(plugin.getDataFolder(), outputPath);
        if (file.exists()) return;
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("[Equipment] Missing bundled resource: " + resourcePath);
                return;
            }
            file.getParentFile().mkdirs();
            Files.copy(in, file.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("[Equipment] Failed to extract " + outputPath + ": " + e.getMessage());
        }
    }

    private YamlConfiguration loadTemplateConfigs() {
        YamlConfiguration merged = new YamlConfiguration();
        File root = new File(plugin.getDataFolder(), "equipment");
        List<File> allYaml = listYamlFiles(root);

        // 优先按子目录分类文件，若不存在则回退 legacy 单文件。
        List<File> folderFiles = allYaml.stream()
                .filter(this::isTemplateYaml)
                .filter(file -> !file.getName().equalsIgnoreCase("stat-templates.yml"))
                .toList();

        if (!folderFiles.isEmpty()) {
            for (File file : folderFiles) {
                mergeTemplateFile(merged, file);
            }
            plugin.getLogger().info("[Equipment] Loaded template files by folders: " + folderFiles.size());
            return merged;
        }

        File legacy = new File(root, "stat-templates.yml");
        if (legacy.exists()) {
            mergeTemplateFile(merged, legacy);
            plugin.getLogger().warning("[Equipment] Using legacy stat-templates.yml, please migrate templates into equipment subfolders.");
        }
        return merged;
    }

    private void mergeTemplateFile(YamlConfiguration target, File source) {
        YamlConfiguration from = YamlConfiguration.loadConfiguration(source);
        for (String key : from.getKeys(false)) {
            if (!target.contains(key)) {
                target.set(key, from.get(key));
            } else {
                plugin.getLogger().warning("[Equipment] Duplicate template id '" + key + "' ignored from " + source.getPath());
            }
        }
    }

    private boolean isTemplateYaml(File file) {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".yml")) return false;
        if (name.equals("slots.yml") || name.equals("backpacks.yml")) return false;
        String path = file.getPath().replace('\\', '/').toLowerCase();
        return !path.contains("/players/");
    }

    private List<File> listYamlFiles(File dir) {
        if (dir == null || !dir.exists()) return List.of();
        List<File> files = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return files;

        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(listYamlFiles(child));
                continue;
            }
            if (child.getName().toLowerCase().endsWith(".yml")) {
                files.add(child);
            }
        }

        files.sort(Comparator.comparing(File::getPath, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    /**
     * 根据模板 ID 构建装备物品
     * 如果模板不存在则返回 null
     */
    public ItemStack buildTemplate(String id) {
        if (statTemplatesConfig == null) return null;
        ConfigurationSection sec = statTemplatesConfig.getConfigurationSection(id);
        if (sec == null) return null;
        return StatItemBuilder.fromConfig(sec).build();
    }

    /**
     * 返回所有已加载的模ID 列表（用Tab 补全）
     */
    public List<String> templateIds() {
        if (statTemplatesConfig == null) return List.of();
        return new ArrayList<>(statTemplatesConfig.getKeys(false));
    }

    /** Returns Arad classification for a template id, or null if template is missing. */
    public AradItemClass templateClass(String templateId) {
        if (templateId == null || templateId.isBlank()) return null;
        return aradTemplateCatalog.classify(templateId);
    }

    /** Returns template ids by Arad top-level group. */
    public List<String> templateIds(AradItemGroup group) {
        if (group == null) return List.of();
        return aradTemplateCatalog.templateIds(group);
    }

    /** Returns template ids by Arad equipment category. */
    public List<String> templateIds(AradEquipCategory category) {
        if (category == null) return List.of();
        return aradTemplateCatalog.templateIds(category);
    }

    /** Returns template ids by Arad equipment part. */
    public List<String> templateIds(AradEquipPart part) {
        if (part == null) return List.of();
        return aradTemplateCatalog.templateIds(part);
    }

    /** Returns template ids by resources/item/equipment source directory semantics. */
    public List<String> templateIds(AradEquipmentSource source) {
        if (source == null) return List.of();
        return aradTemplateCatalog.templateIds(source);
    }
}

