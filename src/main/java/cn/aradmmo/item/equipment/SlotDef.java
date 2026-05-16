package cn.aradmmo.item.equipment;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 不可变的槽位定义，从 slots.yml 中的一YAML 节加载
 */
public final class SlotDef {

    private final String name;
    private final SlotType type;
    private final int slotId;
    /** 与此槽位绑定的快捷栏下标 (ACTIVE 类型有效，其余为 -1)*/
    private final int quickSlot;
    /** 允许放入此槽的材质集合。为空时不限制材质*/
    private final Set<Material> allowedMaterials;
    /** 允许放入此槽MMOItems 类型 ID 集合（大写，SWORD、ARMOR）。为空时不限制类型*/
    private final List<String> allowedTypes;
    /** 槽位为空时显示的占位物品*/
    private final ItemStack holderItem;
    /** 死亡时是否保留此槽内物品（drop=false 时保留）*/
    private final boolean dropOnDeath;
    /**
     * ARMOR 类型槽位对应的原版护甲子类型
     * 取 "helmet" | "chestplate" | "leggings" | "boots" | ""
     */
    private final String armorSlot;
    /**
     * ACTION 类型槽位触发的动作
     * 取 "WORKBENCH" | "ENDERCHEST" | ""
     */
    private final String action;

    private SlotDef(Builder b) {
        this.name             = b.name;
        this.type             = b.type;
        this.slotId           = b.slotId;
        this.quickSlot        = b.quickSlot;
        this.allowedMaterials = b.allowedMaterials.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(b.allowedMaterials));
        this.allowedTypes     = Collections.unmodifiableList(new ArrayList<>(b.allowedTypes));
        this.holderItem       = b.holderItem;
        this.dropOnDeath      = b.dropOnDeath;
        this.armorSlot        = b.armorSlot;
        this.action           = b.action;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String name()                    { return name; }
    public SlotType type()                  { return type; }
    public int slotId()                     { return slotId; }
    public int quickSlot()                  { return quickSlot; }
    public Set<Material> allowedMaterials() { return allowedMaterials; }
    public List<String> allowedTypes()      { return allowedTypes; }
    public ItemStack holderItem()           { return holderItem.clone(); }
    public boolean dropOnDeath()            { return dropOnDeath; }
    public String armorSlot()              { return armorSlot; }
    public String action()                 { return action; }

    // ── Validation helpers ───────────────────────────────────────────────────

    /**
     * 返回给定物品是否是该槽的占位物品（根据材质和显示名判断）
     */
    public boolean isHolder(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType() != holderItem.getType()) return false;
        ItemMeta hm = holderItem.getItemMeta();
        ItemMeta im = item.getItemMeta();
        if (hm == null || im == null) return false;
        return hm.hasDisplayName() && hm.getDisplayName().equals(im.getDisplayName());
    }

    /**
     * 返回给定物品是否可以放入该槽
     * <p>
     * 优先通过 MMOItems 物品类型判断（如 SWORD、ARMOR）；
     * 如无 MMOItems 类型则回退到原版材质匹配
     * INFO ACTION 槽永远不允许放物品
     */
    public boolean isAllowed(ItemStack item) {
        if (type == SlotType.INFO || type == SlotType.ACTION) return false;
        if (item == null || item.getType() == Material.AIR) return false;

        // ── MMOItems 类型检────────────────────────────────────────────────
        try {
            NBTItem nbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(item);
            if (nbt.hasTag("MMOITEMS_ITEM_TYPE")) {
                String miType = nbt.getString("MMOITEMS_ITEM_TYPE").toUpperCase();
                if (!allowedTypes.isEmpty()) {
                    return allowedTypes.contains(miType);
                }
                // 没有配置类型限制 允许所MMOItems 物品（材质无关）
                return allowedMaterials.isEmpty() || allowedMaterials.contains(item.getType());
            }
        } catch (Throwable ignored) {
            // MythicLib 未加载时静默忽略
        }

        // ── 原版材质检─────────────────────────────────────────────────────
        if (allowedMaterials.isEmpty()) return true;
        return allowedMaterials.contains(item.getType());
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * YAML ConfigurationSection 解析并构SlotDef
     */
    public static SlotDef load(String name, ConfigurationSection section) {
        Builder b = new Builder(name);

        b.type      = parseType(section.getString("type", "GENERIC"));
        b.slotId    = section.getInt("slot", 0);
        b.quickSlot = section.getInt("quickbar", -1);
        b.dropOnDeath = section.getBoolean("drop", true);
        b.action    = section.getString("action", "").toUpperCase();

        // 推断 ARMOR 子类
        b.armorSlot = switch (name.toLowerCase()) {
            case "helmet"     -> "helmet";
            case "chestplate" -> "chestplate";
            case "leggings"   -> "leggings";
            case "boots"      -> "boots";
            default           -> "";
        };

        // 允许的材
        for (String matName : section.getStringList("items")) {
            Material mat = Material.matchMaterial(matName.toUpperCase());
            if (mat != null) b.allowedMaterials.add(mat);
        }

        // 允许MMOItems 类型
        for (String typeName : section.getStringList("allowed-types")) {
            b.allowedTypes.add(typeName.toUpperCase());
        }

        // 占位物品
        ConfigurationSection holder = section.getConfigurationSection("holder");
        if (holder != null) {
            String matName = holder.getString("item", "GRAY_STAINED_GLASS_PANE");
            Material mat = Material.matchMaterial(matName.toUpperCase());
            if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack holderItem = new ItemStack(mat);
            ItemMeta meta = holderItem.getItemMeta();
            if (meta != null) {
                String displayName = holder.getString("name", "&7" + name);
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                List<String> lore = new ArrayList<>();
                for (String line : holder.getStringList("lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                if (!lore.isEmpty()) meta.setLore(lore);
                holderItem.setItemMeta(meta);
            }
            b.holderItem = holderItem;
        }

        return new SlotDef(b);
    }

    private static SlotType parseType(String s) {
        try {
            return SlotType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SlotType.GENERIC;
        }
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    private static final class Builder {
        final String name;
        SlotType type = SlotType.GENERIC;
        int slotId    = 0;
        int quickSlot = -1;
        Set<Material> allowedMaterials = EnumSet.noneOf(Material.class);
        List<String> allowedTypes      = new ArrayList<>();
        ItemStack holderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        boolean dropOnDeath  = true;
        String armorSlot     = "";
        String action        = "";

        Builder(String name) { this.name = name; }
    }
}

