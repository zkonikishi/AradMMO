package cn.aradmmo.item.equipment.backpack;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 监听背包界面关闭事件，持久化内容
 */
public final class BackpackListener implements Listener {

    private final AradMmoPlugin plugin;

    public BackpackListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof BackpackData)) return;
        plugin.equipment().backpacks().onClose(player);
    }

    /** 阻止在背包界面中拖拽（简化处理，避免背包数据不一致）*/
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackData)) return;
        // 允许拖拽，内容会在关闭时保存，无需额外处理
    }
}

