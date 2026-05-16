package cn.aradmmo.item.equipment;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;

/**
 * 处理鞘翅滑翔状态变化，实现 ELYTRA 槽与胸甲槽的自动交换
 */
public final class ElytraListener implements Listener {

    private final AradMmoPlugin plugin;

    public ElytraListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        EquipmentService svc = plugin.equipment();
        if (!svc.isLoaded(player)) return;

        if (event.isGliding()) {
            svc.onStartGliding(player);
        } else {
            svc.onStopGliding(player);
        }
    }
}

