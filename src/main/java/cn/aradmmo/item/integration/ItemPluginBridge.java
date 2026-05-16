package cn.aradmmo.item.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Bridge for mainstream item plugins (MMOItems, RPGInventory, etc.).
 */
public interface ItemPluginBridge {

    /** @return unique plugin id, e.g. "mmoitems" */
    String id();

    /** @return true when dependency is loaded and bridge can be used */
    boolean isAvailable();

    /**
     * Read item type id from external plugin metadata.
     * Return null when item does not belong to this plugin.
     */
    String readItemType(ItemStack item);

    /**
     * Validate external plugin requirements for this item.
     */
    boolean canEquip(Player player, ItemStack item);
}

