package cn.aradmmo.core.text;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Applies VIP cosmetic gradient styles to player name and chat.
 */
public final class PlayerCosmeticColorService {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final AradMmoPlugin plugin;

    public PlayerCosmeticColorService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasVipPrivilege(PlayerProfile profile) {
        if (profile == null) return false;
        String tier = profile.vipTier();
        return tier != null && !tier.isBlank() && !tier.equalsIgnoreCase("standard") && !tier.equalsIgnoreCase("none");
    }

    public boolean canUseNameGradient(Player player, PlayerProfile profile) {
        return player.hasPermission("aradmmo.vip.gradient.name") && hasVipPrivilege(profile);
    }

    public boolean canUseChatGradient(Player player, PlayerProfile profile) {
        return player.hasPermission("aradmmo.vip.gradient.chat") && hasVipPrivilege(profile);
    }

    public void applyNameStyle(Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        Component name = formatDisplayName(player, profile);
        player.displayName(name);
        player.playerListName(name);
        try {
            player.customName(name);
            player.setCustomNameVisible(true);
        } catch (Throwable ignored) {
        }
    }

    public void clearNameStyle(Player player) {
        Component vanilla = Component.text(player.getName());
        player.displayName(vanilla);
        player.playerListName(vanilla);
        try {
            player.customName(null);
            player.setCustomNameVisible(false);
        } catch (Throwable ignored) {
        }
    }

    public Component formatDisplayName(Player player, PlayerProfile profile) {
        String gradient = profile.nameGradient();
        if (canUseNameGradient(player, profile) && TextColorService.isValidGradientSpec(gradient)) {
            return TextColorService.gradientText(player.getName(), gradient);
        }
        return Component.text(player.getName());
    }

    public Component formatChatMessage(Player player, Component original) {
        PlayerProfile profile = plugin.profiles().profile(player);
        String gradient = profile.chatGradient();
        if (!canUseChatGradient(player, profile) || !TextColorService.isValidGradientSpec(gradient)) {
            return original;
        }
        String plain = PLAIN.serialize(original);
        return TextColorService.gradientText(plain, gradient);
    }

    public String randomGradientSpec() {
        return TextColorService.randomGradientSpec();
    }

    public String normalizeGradientSpec(String spec) {
        String[] parsed = TextColorService.parseGradientSpec(spec);
        if (parsed == null) return "";
        return parsed[0].toLowerCase(Locale.ROOT) + ":" + parsed[1].toLowerCase(Locale.ROOT);
    }
}
