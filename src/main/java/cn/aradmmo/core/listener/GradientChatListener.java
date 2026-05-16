package cn.aradmmo.core.listener;

import cn.aradmmo.core.AradMmoPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Chat renderer with VIP gradient support and name-style sync.
 */
public final class GradientChatListener implements Listener {
    private final AradMmoPlugin plugin;

    public GradientChatListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component message = plugin.cosmeticColors().formatChatMessage(player, event.message());

        event.renderer((source, sourceDisplayName, msg, viewer) -> Component.empty()
                .append(plugin.cosmeticColors().formatDisplayName(player, plugin.profiles().profile(player)))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.cosmeticColors().applyNameStyle(player), 1L);
    }
}
