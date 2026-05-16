package cn.aradmmo.rpg.stamina;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;

/**
 * Listens for sprint toggle events and cancels them when the player is exhausted.
 */
public final class StaminaListener implements Listener {

    private final AradMmoPlugin plugin;

    public StaminaListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return; // allow stopping sprint
        Player player = event.getPlayer();
        if (plugin.stamina().isExhausted(player)) {
            event.setCancelled(true);
        }
    }
}

