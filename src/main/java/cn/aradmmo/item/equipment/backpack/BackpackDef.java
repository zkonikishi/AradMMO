package cn.aradmmo.item.equipment.backpack;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * backpacks.yml 加载的背包类型定义（不可变）
 */
public final class BackpackDef {

    private final String    id;
    private final String    name;
    private final Material  material;
    private final int       size;
    private final List<String> lore;

    private BackpackDef(String id, String name, Material material, int size, List<String> lore) {
        this.id       = id;
        this.name     = name;
        this.material = material;
        this.size     = size;
        this.lore     = List.copyOf(lore);
    }

    public String   id()       { return id; }
    public String   name()     { return name; }
    public Material material() { return material; }
    public int      size()     { return size; }

    /**
     * 根据此类型定义创建一个背包物品（写入 id 标签{@link BackpackService} 负责）
     */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** YAML 配置节加载背包类型定义*/
    public static BackpackDef load(String id, ConfigurationSection section) {
        String   name = ChatColor.translateAlternateColorCodes('&',
                section.getString("name", "&6背包"));
        String   matName = section.getString("material", "CHEST");
        Material mat  = Material.matchMaterial(matName.toUpperCase());
        if (mat == null) mat = Material.CHEST;
        int size = Math.max(9, Math.min(54, section.getInt("size", 27)));
        // Round size to nearest multiple of 9
        size = ((size + 8) / 9) * 9;

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        return new BackpackDef(id, name, mat, size, lore);
    }
}

