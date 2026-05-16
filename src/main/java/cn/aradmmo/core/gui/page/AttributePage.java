package cn.aradmmo.core.gui.page;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.gui.framework.GuiDef;
import cn.aradmmo.core.gui.framework.GuiPage;
import cn.aradmmo.core.gui.framework.ItemDef;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AttributePage extends GuiPage {

    public AttributePage(AradMmoPlugin plugin, Player player, GuiDef def) {
        super(plugin, player, def);
    }

    @Override
    protected ItemStack buildItem(ItemDef itemDef) {
        return buildFromDef(itemDef); // filler and back button rendered directly from YAML
    }

    @Override
    protected void buildDynamic(Inventory inv) {
        PlayerProfile  profile   = plugin.profiles().profile(player);
        List<String>   attrKeys  = plugin.attributeKeys();
        int startSlot = def.raw().getInt("dynamic.start-slot", 10);
        int stride    = def.raw().getInt("dynamic.stride",      3);

        for (int i = 0; i < attrKeys.size(); i++) {
            String key        = attrKeys.get(i);
            int current       = profile.attribute(key);
            boolean canAfford = profile.statPoints() > 0;

            Material icon   = parseMat(plugin.attributesConfig().getString(key + ".icon",    "PAPER"));
            String display  = plugin.attributesConfig().getString(key + ".display", key.toUpperCase(Locale.ROOT));
            List<String> configLore = plugin.attributesConfig().getStringList(key + ".lore");

            int infoSlot = startSlot + i * stride;
            int btnSlot  = infoSlot + 1;
            if (infoSlot >= inv.getSize()) break;

            inv.setItem(infoSlot, buildSimple(icon, display,
                    buildAttrLore(plugin, player, key, configLore, current, profile.statPoints())));

            if (btnSlot < inv.getSize()) {
                inv.setItem(btnSlot, buildSimple(
                        canAfford ? Material.LIME_DYE : Material.RED_DYE,
                        plugin.messages().raw(player, canAfford ? "gui.attr.add-btn" : "gui.attr.no-points-btn"),
                        canAfford ? List.of(plugin.messages().raw(player, "gui.attr.add-btn-lore")) : List.of()));
            }
        }
    }

    @Override
    public void handleClick(int slot, InventoryClickEvent event) {
        // Back button
        ItemDef clicked = def.defForSlot(slot);
        if (clicked != null && "back".equals(clicked.function())) {
            plugin.gui().openProfile(player);
            return;
        }

        // [+1] button for an attribute
        List<String> attrKeys = plugin.attributeKeys();
        int startSlot = def.raw().getInt("dynamic.start-slot", 10);
        int stride    = def.raw().getInt("dynamic.stride",      3);
        for (int i = 0; i < attrKeys.size(); i++) {
            int btnSlot = startSlot + i * stride + 1;
            if (slot == btnSlot) {
                String attrKey = attrKeys.get(i);
                try {
                    plugin.profiles().allocateAttribute(player, attrKey, 1);
                    PlayerProfile p = plugin.profiles().profile(player);
                    plugin.messages().send(player, "command.stat.done",
                            "%player%",    player.getName(),
                            "%attribute%", attrKey,
                            "%value%",     String.valueOf(p.attribute(attrKey)),
                            "%points%",    String.valueOf(p.statPoints()));
                } catch (IllegalArgumentException e) {
                    plugin.messages().send(player, "error.not-enough-stat-points");
                }
                plugin.gui().openAttributes(player); // refresh
                return;
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    /**
     * Builds the lore for an attribute info item.
     * Prepends current-value and remaining-points lines, then appends config lore
     * with {@code {token}} per-point value substitution.
     */
    private static List<String> buildAttrLore(AradMmoPlugin plugin, Player player, String attrKey,
                                               List<String> configLore, int current, int remaining) {
        List<String> result = new ArrayList<>();
        result.add(plugin.messages().raw(player, "gui.attr.current", "%value%", String.valueOf(current)));
        result.add(plugin.messages().raw(player, "gui.attr.remaining", "%value%", String.valueOf(remaining)));
        result.add("");

        for (String line : configLore) {
            String resolved = line;
            int start = resolved.indexOf('{');
            while (start >= 0) {
                int end = resolved.indexOf('}', start);
                if (end < 0) break;
                String token = resolved.substring(start + 1, end);
                double val = plugin.attributesConfig().getDouble(attrKey + ".per-point." + token, 0.0);
                String formatted = val == Math.floor(val)
                        ? String.valueOf((int) val)
                        : String.format("%.3f", val).replaceAll("0+$", "");
                resolved = resolved.substring(0, start) + formatted + resolved.substring(end + 1);
                start = resolved.indexOf('{', start + formatted.length());
            }
            result.add(resolved);
        }
        return result;
    }
}

