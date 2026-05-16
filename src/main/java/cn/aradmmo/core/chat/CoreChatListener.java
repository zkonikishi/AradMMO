package cn.aradmmo.core.chat;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class CoreChatListener implements Listener {
    private final AradMmoPlugin plugin;

    public CoreChatListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = plugin.profiles().profile(player);
        ChatService chatService = plugin.chat();
        if (chatService == null || !chatService.enabled()) {
            return;
        }

        ChatRule rule = chatService.selectRule(player);
        if (rule != null && rule.range() > 0) {
            filterByRange(event, player, rule.range());
        }
        filterByReceiverToggle(event, player, chatService);

        Component prefix = chatService.renderPrefix(player, profile, rule,
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()));
        Component name = plugin.cosmeticColors().formatDisplayName(player, profile);
        Component message = chatService.renderMessage(player, profile, rule, event.message());
        Component fullLine = chatService.composeChatLine(prefix, name, message);

        if (rule == null || rule.range() <= 0) {
            plugin.chatBridge().broadcast(fullLine);
        }

        event.renderer((source, sourceDisplayName, msg, viewer) -> fullLine);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.cosmeticColors().applyNameStyle(player), 1L);
    }

    private void filterByRange(AsyncChatEvent event, Player sender, int range) {
        double maxDistanceSquared = (double) range * range;
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player target)) {
                return false;
            }
            if (target.getUniqueId().equals(sender.getUniqueId())) {
                return false;
            }
            if (!target.getWorld().equals(sender.getWorld())) {
                return true;
            }
            return target.getLocation().distanceSquared(sender.getLocation()) > maxDistanceSquared;
        });
    }

    private void filterByReceiverToggle(AsyncChatEvent event, Player sender, ChatService chatService) {
        event.viewers().removeIf(audience -> {
            if (!(audience instanceof Player target)) {
                return false;
            }
            if (target.getUniqueId().equals(sender.getUniqueId())) {
                return false;
            }
            return !chatService.isChatEnabled(target);
        });
    }
}
