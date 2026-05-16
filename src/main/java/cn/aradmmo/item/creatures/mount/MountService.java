package cn.aradmmo.item.creatures.mount;

import cn.aradmmo.core.AradMmoPlugin;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Mount subsystem service: vanilla mount summon and despawn only.
 */
public final class MountService {

    public static final String ITEM_KEY  = "aradmmo_mount_id";
    public static final String OWNER_KEY = "aradmmo_mount_owner";

    private final AradMmoPlugin plugin;
    private final Map<String, MountDef> defs = new HashMap<>();
    private final Map<UUID, LivingEntity> active = new HashMap<>();

    public MountService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        defs.clear();
        active.clear();

        extractDefault("item/creatures/mount/mounts.yml", "item/creatures/mounts.yml");
        File file = plugin.itemFile("creatures/mounts.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("[Mount] item/creatures/mounts.yml not found in data folder");
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection mounts = yml.getConfigurationSection("mounts");
        if (mounts == null) return;

        for (String key : mounts.getKeys(false)) {
            ConfigurationSection sec = mounts.getConfigurationSection(key);
            if (sec != null) defs.put(key, MountDef.load(key, sec));
        }
    }

    public void shutdown() {
        for (LivingEntity mount : active.values()) {
            if (mount != null && !mount.isDead()) mount.remove();
        }
        active.clear();
    }

    // ── Mount item helpers ─────────────────────────────────────────────────

    public List<String> mountIds() {
        return defs.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public ItemStack createMountItem(String defId) {
        MountDef def = defs.get(defId);
        if (def == null) return null;
        ItemStack item = def.createItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING, defId);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isMountItem(ItemStack item) {
        return getMountId(item) != null;
    }

    public String getMountId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING);
    }

    // Summon / despawn

    public void summon(Player owner, ItemStack mountItem) {
        String id = getMountId(mountItem);
        if (id == null) return;
        MountDef def = defs.get(id);
        if (def == null) return;

        despawn(owner);

        LivingEntity entity = (LivingEntity) owner.getWorld().spawnEntity(
                owner.getLocation(), def.entityType());

        if (entity instanceof Ageable ageable) {
            if (def.baby()) ageable.setBaby();
            else ageable.setAdult();
        }
        if (entity instanceof Tameable tameable) {
            tameable.setOwner(owner);
        }

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, OWNER_KEY), PersistentDataType.STRING,
                owner.getUniqueId().toString());
        pdc.set(new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING, id);

        entity.setCustomName("§b" + def.name() + " §8[" + owner.getName() + "]");
        entity.setCustomNameVisible(true);

        active.put(owner.getUniqueId(), entity);
    }

    public void despawn(Player owner) {
        LivingEntity old = active.remove(owner.getUniqueId());
        if (old == null) return;
        if (!old.isDead()) old.remove();
    }

    public LivingEntity activeMount(Player owner) {
        return active.get(owner.getUniqueId());
    }

    public UUID getOwner(LivingEntity entity) {
        String uuid = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, OWNER_KEY), PersistentDataType.STRING);
        if (uuid == null) return null;
        try { return UUID.fromString(uuid); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private void extractDefault(String resourcePath, String outputPath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("[Mount] Missing bundled resource: " + resourcePath);
                return;
            }
            File out = new File(plugin.getDataFolder(), outputPath);
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!out.exists()) Files.copy(in, out.toPath());
        } catch (Exception ex) {
            plugin.getLogger().warning("[Mount] Failed to extract "
                    + resourcePath + ": " + ex.getMessage());
        }
    }
}
