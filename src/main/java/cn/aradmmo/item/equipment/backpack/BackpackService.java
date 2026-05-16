package cn.aradmmo.item.equipment.backpack;

import cn.aradmmo.core.AradMmoPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 管理背包类型定义的加载与玩家背包内容的保读取
 */
public final class BackpackService {

    /** NBT 键：存储在背包物品上，值为背包类型 id*/
    public static final String NBT_KEY = "aradmmo_backpack_id";

    private final AradMmoPlugin             plugin;
    private final Map<String, BackpackDef>  defs = new HashMap<>();
    /** 每个玩家当前打开中的背包数据（最多同时一个）*/
    private final Map<UUID, BackpackData>   open = new HashMap<>();

    public BackpackService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void reload() {
        defs.clear();
        File file = new File(plugin.getDataFolder(), "equipment/backpacks.yml");
        if (!file.exists()) plugin.saveResource("equipment/backpacks.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection bps = config.getConfigurationSection("backpacks");
        if (bps == null) return;
        for (String key : bps.getKeys(false)) {
            ConfigurationSection sec = bps.getConfigurationSection(key);
            if (sec != null) defs.put(key, BackpackDef.load(key, sec));
        }
    }

    // ── Item API ──────────────────────────────────────────────────────────────

    /** 创建指定类型的背包物品并写入 NBT 标签*/
    public ItemStack createBackpackItem(String defId) {
        BackpackDef def = defs.get(defId);
        if (def == null) return null;
        ItemStack item = def.createItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, NBT_KEY);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, defId);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 返回物品的背包类id，非背包物品返回 {@code null}*/
    public String getBackpackId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, NBT_KEY);
        return pdc.get(key, PersistentDataType.STRING);
    }

    /** 返回此物品是否是一个背包*/
    public boolean isBackpack(ItemStack item) {
        return getBackpackId(item) != null;
    }

    // ── Open/Close ────────────────────────────────────────────────────────────

    /**
     * 向玩家打开背包
     * 若玩家尚未有该背包的数据，从磁盘加载（或创建空的）
     *
     * @param player      要打开背包的玩
     * @param backpackItem 放在装备槽位中的背包物品
     */
    public void open(Player player, ItemStack backpackItem) {
        String defId = getBackpackId(backpackItem);
        if (defId == null) return;
        BackpackDef def = defs.get(defId);
        if (def == null) return;

        BackpackData data = loadBackpack(player.getUniqueId(), def);
        open.put(player.getUniqueId(), data);
        data.openFor(player);
    }

    /** 当玩家关闭背包界面时调用，保存内容到磁盘*/
    public void onClose(Player player) {
        BackpackData data = open.remove(player.getUniqueId());
        if (data != null) saveBackpack(player.getUniqueId(), data);
    }

    /** 返回玩家当前打开的背包数据，未打开则返{@code null}*/
    public BackpackData getOpen(UUID uuid) { return open.get(uuid); }

    // ── Persistence ───────────────────────────────────────────────────────────

    private BackpackData loadBackpack(UUID ownerUuid, BackpackDef def) {
        File file = backpackFile(ownerUuid, def.id());
        if (!file.exists()) return new BackpackData(ownerUuid, def);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> raw = config.getList("contents");
        if (raw == null) return new BackpackData(ownerUuid, def);
        ItemStack[] contents = raw.stream()
                .map(o -> o instanceof ItemStack ? (ItemStack) o : null)
                .toArray(ItemStack[]::new);
        return new BackpackData(ownerUuid, def, contents);
    }

    private void saveBackpack(UUID ownerUuid, BackpackData data) {
        File file = backpackFile(ownerUuid, data.def().id());
        file.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        config.set("contents", Arrays.asList(data.contents()));
        try { config.save(file); }
        catch (IOException e) {
            plugin.getLogger().warning("Failed to save backpack for " + ownerUuid + ": " + e.getMessage());
        }
    }

    /** 保存指定玩家所有已打开的背包（服务器关闭时使用）*/
    public void saveAll() {
        for (Map.Entry<UUID, BackpackData> entry : open.entrySet()) {
            saveBackpack(entry.getKey(), entry.getValue());
        }
    }

    private File backpackFile(UUID ownerUuid, String defId) {
        return new File(plugin.getDataFolder(),
                "equipment/backpacks/" + ownerUuid + "/" + defId + ".yml");
    }
}

