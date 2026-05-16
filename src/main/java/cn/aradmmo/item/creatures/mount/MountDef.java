package cn.aradmmo.item.creatures.mount;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Mount type definition loaded from mounts.yml.
 */
public final class MountDef {

    private final String id;
    private final String name;
    private final Material material;
    private final EntityType entityType;
    private final boolean baby;
    private final List<String> lore;

    private MountDef(String id, String name, Material material, EntityType entityType,
                     boolean baby, List<String> lore) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.entityType = entityType;
        this.baby = baby;
        this.lore = List.copyOf(lore);
    }

    public String id()            { return id; }
    public String name()          { return name; }
    public Material material()    { return material; }
    public EntityType entityType(){ return entityType; }
    public boolean baby()         { return baby; }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (!lore.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static MountDef load(String id, ConfigurationSection section) {
        String name = section.getString("name", "&f坐骑");
        String matName = section.getString("material", "SADDLE");
        Material mat = Material.matchMaterial(matName.toUpperCase());
        if (mat == null) mat = Material.SADDLE;

        String entName = section.getString("entity", "HORSE").toUpperCase();
        EntityType type;
        try {
            type = EntityType.valueOf(entName);
        } catch (IllegalArgumentException ex) {
            type = EntityType.HORSE;
        }

        boolean baby = section.getBoolean("baby", false);

        List<String> lore = section.getStringList("lore");
        return new MountDef(id, name, mat, type, baby, lore);
    }
}
