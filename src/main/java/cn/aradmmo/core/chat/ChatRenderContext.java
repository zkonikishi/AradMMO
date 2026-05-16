package cn.aradmmo.core.chat;

import cn.aradmmo.rpg.profile.PlayerProfile;
import org.bukkit.entity.Player;

public record ChatRenderContext(Player player, PlayerProfile profile, String message) {
}
