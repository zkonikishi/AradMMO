package cn.aradmmo.core.listener;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSessionListener implements Listener {
    private final AradMmoPlugin plugin;

    public PlayerSessionListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        // Pre-load profile so first combat hit has no I/O delay
        plugin.profiles().preload(event.getPlayer());
        // Sync max HP from VIT and restore HP to full
        plugin.hp().initialize(event.getPlayer());
        // Initialise MP and SP to maximum on login
        plugin.mana().initialize(event.getPlayer());
        plugin.stamina().initialize(event.getPlayer());
        // 初始化装备面板（加载存档、同步原版护快捷栏）
        plugin.equipment().initPlayer(event.getPlayer());
        // Apply class-based skin after a short delay to ensure client is fully connected
        Player joinedPlayer = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joinedPlayer.isOnline()) plugin.skins().applyClassSkin(joinedPlayer);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cosmeticColors().clearNameStyle(event.getPlayer());
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        // 卸载装备面板（保存数据、解散宠物）
        plugin.equipment().unloadPlayer(event.getPlayer());
        plugin.profiles().saveAndEvict(uuid);
        plugin.skillCooldowns().clear(uuid);
        plugin.skillBuffs().clear(uuid);
        plugin.statusEffects().handleQuit(uuid);
        plugin.mana().evict(uuid);
        plugin.stamina().evict(uuid);
    }
}

