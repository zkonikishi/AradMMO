package cn.aradmmo.rpg.listener;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Applies death penalties and handles respawn HP restoration.
 *
 * <p>On death:
 * <ul>
 *   <li>Deducts {@code death.exp-loss-percent}% of current EXP (capped so no level-down)</li>
 *   <li>Deducts {@code death.gold-loss-percent}% of current gold (floor 0)</li>
 *   <li>Sends a death message with losses shown</li>
 * </ul>
 *
 * <p>On respawn:
 * <ul>
 *   <li>Re-applies max HP (so the health bar isn't stuck at 0)</li>
 *   <li>Restores HP to {@code death.respawn-hp-percent}% of max (default full)</li>
 * </ul>
 */
public final class DeathListener implements Listener {

    private final AradMmoPlugin plugin;

    public DeathListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerProfile profile = plugin.profiles().profile(player);

        // ── EXP loss ──────────────────────────────────────────────────────
        double expLossPct = plugin.getConfig().getDouble("death.exp-loss-percent", 0.05);
        double currentExp = profile.experience();
        double expLost    = currentExp * expLossPct;

        // Clamp: can't go below 0 (no level-down)
        double newExp = Math.max(0, currentExp - expLost);
        profile.experience(newExp);

        // ── Gold loss ─────────────────────────────────────────────────────
        double goldLossPct = plugin.getConfig().getDouble("death.gold-loss-percent", 0.02);
        double currentGold = profile.balance();
        double goldLost    = currentGold * goldLossPct;
        double newGold     = Math.max(0, currentGold - goldLost);
        profile.balance(newGold);

        // Persist changes
        plugin.profiles().save(player, profile);

        // ── Notify player ─────────────────────────────────────────────────
        plugin.messages().send(player, "event.death.penalty",
                "%exp%",  formatDouble(expLost),
                "%gold%", formatDouble(goldLost));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Re-apply VIT-derived max HP after Minecraft resets health on respawn
        plugin.hp().applyMaxHealth(player);

        double respawnPct = plugin.getConfig().getDouble("death.respawn-hp-percent", 1.0);
        double maxHp = plugin.hp().max(player);
        double healTo = maxHp * Math.min(1.0, Math.max(0.0, respawnPct));
        // Delay by 1 tick so Minecraft finishes setting up the respawned player
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                plugin.hp().applyMaxHealth(player);
                player.setHealth(Math.min(maxHp, healTo));
            }
        });
    }

    private String formatDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}

