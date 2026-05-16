package cn.aradmmo.core.gui;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 6-row chest for allocating stat (attribute) points.
 *
 * Layout (stride = 3, start = 10):
 *   STRENGTH:  info=10, btn=11
 *   SPIRIT:    info=13, btn=14
 *   INTELLECT: info=16, btn=17
 *   VITALITY:  info=19, btn=20
 *
 * Slot 45 = back to Profile.
 * Display names, icons, and lore come from attributes.yml.
 * Click handling is done in GuiListener.
 */
final class AttributeGui {

    private AttributeGui() {}

    static Inventory build(AradMmoPlugin plugin, Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        java.util.List<String> attrKeys = plugin.attributeKeys();

        Inventory inv = org.bukkit.Bukkit.createInventory(null, 54,
                MiniMessage.miniMessage().deserialize("<red><bold>属性分/bold></red>"));

        for (int i = 0; i < 54; i++) inv.setItem(i, GuiService.filler());

        int startSlot = 10;
        for (int i = 0; i < attrKeys.size(); i++) {
            String key           = attrKeys.get(i);
            int current          = profile.attribute(key);
            boolean canAfford    = profile.statPoints() > 0;

            // Read metadata from attributes.yml
            Material icon   = parseMaterial(plugin.attributesConfig().getString(key + ".icon", "PAPER"));
            String display  = plugin.attributesConfig().getString(key + ".display", key.toUpperCase());
            List<String> loreLines = plugin.attributesConfig().getStringList(key + ".lore");

            // Build lore: replace {per-point-key} tokens
            String[] infoLore = buildLore(plugin, key, loreLines, current, profile.statPoints());

            int infoSlot = startSlot + i * 3;
            int btnSlot  = infoSlot + 1;

            inv.setItem(infoSlot, GuiService.item(icon, display, infoLore));

            inv.setItem(btnSlot, GuiService.item(
                    canAfford ? Material.LIME_DYE : Material.RED_DYE,
                    canAfford ? "<green>[+1]" : "<red>点数不足",
                    canAfford ? "<dark_gray>点击消属性点" : ""));
        }

        inv.setItem(45, GuiService.item(Material.ARROW, "<gray>返回档案", "<dark_gray>点击返回"));

        return inv;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    /**
     * Builds the lore list for an attribute info item.
     * Prepends current value line, then appends config lore (replacing {key} tokens).
     */
    private static String[] buildLore(AradMmoPlugin plugin, String attrKey,
                                       List<String> configLore, int current, int remaining) {
        java.util.List<String> result = new java.util.ArrayList<>();
        result.add("<gray>当前: <white>" + current);
        result.add("<gray>剩余属性点: <white>" + remaining);
        result.add("");

        for (String line : configLore) {
            // Replace {some-key} tokens with per-point values from attributes.yml
            String resolved = line;
            int start = resolved.indexOf('{');
            while (start >= 0) {
                int end = resolved.indexOf('}', start);
                if (end < 0) break;
                String token = resolved.substring(start + 1, end);
                double val = plugin.attributesConfig().getDouble(attrKey + ".per-point." + token, 0.0);
                // Format: remove trailing zeros
                String formatted = val == Math.floor(val) ? String.valueOf((int) val)
                        : String.format("%.3f", val).replaceAll("0+$", "");
                resolved = resolved.substring(0, start) + formatted + resolved.substring(end + 1);
                start = resolved.indexOf('{', start + formatted.length());
            }
            result.add(resolved);
        }
        return result.toArray(new String[0]);
    }
}


/**
 * 6-row chest for allocating stat (attribute) points.
 *
 * Layout (stride = 3, start = 10):
 *   STRENGTH:  info=10, btn=11
 *   AGILITY:   info=13, btn=14
 *   INTELLECT: info=16, btn=17
 *   VITALITY:  info=19, btn=20
 *
 * Slot 45 = back to Profile.
 * Click handling is done in GuiListener.
 */

