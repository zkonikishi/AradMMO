package cn.aradmmo.core.gui;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.gui.framework.GuiPage;
import cn.aradmmo.core.gui.page.AttributePage;
import cn.aradmmo.core.gui.page.ClassPage;
import cn.aradmmo.core.gui.page.ProfilePage;
import cn.aradmmo.core.gui.page.SkillPage;
import cn.aradmmo.core.gui.page.VipCosmeticsPage;
import cn.aradmmo.core.text.TextColorService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Opens and tracks inventory-based GUI sessions.
 * GUI layouts are defined in {@code gui/*.yml} and rendered by {@link GuiPage} subclasses.
 */
public final class GuiService {

    private final AradMmoPlugin plugin;
    private final Map<UUID, GuiPage> open = new HashMap<>();

    public GuiService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ open
    public void openProfile(Player player) {
        GuiPage page = new ProfilePage(plugin, player, plugin.guiDef("profile"));
        page.open(); // may fire InventoryCloseEvent for previous GUI, removing stale entry
        open.put(player.getUniqueId(), page);
    }

    public void openClass(Player player) {
        GuiPage page = new ClassPage(plugin, player, plugin.guiDef("class"));
        page.open();
        open.put(player.getUniqueId(), page);
    }

    public void openSkills(Player player) {
        GuiPage page = new SkillPage(plugin, player, plugin.guiDef("skills"));
        page.open();
        open.put(player.getUniqueId(), page);
    }

    public void openAttributes(Player player) {
        GuiPage page = new AttributePage(plugin, player, plugin.guiDef("attributes"));
        page.open();
        open.put(player.getUniqueId(), page);
    }

    public void openVipCosmetics(Player player) {
        GuiPage page = new VipCosmeticsPage(plugin, player, plugin.guiDef("vip-cosmetics"));
        page.open();
        open.put(player.getUniqueId(), page);
    }

    // ------------------------------------------------------------------ state
    public boolean isOpen(UUID uuid) {
        return open.containsKey(uuid);
    }

    public GuiPage pageFor(UUID uuid) {
        return open.get(uuid);
    }

    public void close(UUID uuid) {
        open.remove(uuid);
    }

    // ------------------------------------------------------------------ helpers (package-private)
    static ItemStack item(Material material, String miniMessageTitle, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextColorService.component(miniMessageTitle));
        if (loreLines.length > 0) {
            java.util.List<Component> lore = new java.util.ArrayList<>();
            for (String line : loreLines) {
                lore.add(TextColorService.component(line));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.empty());
        stack.setItemMeta(meta);
        return stack;
    }
}

