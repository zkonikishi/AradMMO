package cn.aradmmo.rpg.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class SkillCooldownService {
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public boolean onCooldown(Player player, String skillId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Long expiry = map.get(skillId);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public long remainingMillis(Player player, String skillId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return 0L;
        Long expiry = map.get(skillId);
        if (expiry == null) return 0L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    public void setCooldown(Player player, String skillId, long millis) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(skillId, System.currentTimeMillis() + millis);
    }

    public void clear(UUID uuid) {
        cooldowns.remove(uuid);
    }
}

