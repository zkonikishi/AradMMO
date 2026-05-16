package cn.aradmmo.core.gui;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.gui.framework.GuiPage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class GuiListener implements Listener {
    private final AradMmoPlugin plugin;

    public GuiListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiService gs = plugin.gui();
        if (!gs.isOpen(player.getUniqueId())) return;

        event.setCancelled(true); // block item movement in all our GUIs

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) return;

        GuiPage page = gs.pageFor(player.getUniqueId());
        if (page != null) page.handleClick(event.getSlot(), event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.gui().close(player.getUniqueId());
        }
    }
}

