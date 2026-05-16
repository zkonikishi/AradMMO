package cn.aradmmo.rpg.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SkillBuffService {
    private final Map<UUID, Map<String, Long>> buffs = new HashMap<>();

    public boolean hasBuff(UUID uuid, String buffId) {
        Map<String, Long> map = buffs.get(uuid);
        if (map == null) return false;
        Long expiry = map.get(buffId);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public long remainingMillis(UUID uuid, String buffId) {
        Map<String, Long> map = buffs.get(uuid);
        if (map == null) return 0L;
        Long expiry = map.get(buffId);
        if (expiry == null) return 0L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    public void applyBuff(UUID uuid, String buffId, long durationMillis) {
        buffs.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(buffId, System.currentTimeMillis() + durationMillis);
    }

    public void consumeBuff(UUID uuid, String buffId) {
        Map<String, Long> map = buffs.get(uuid);
        if (map != null) {
            map.remove(buffId);
        }
    }

    public void clear(UUID uuid) {
        buffs.remove(uuid);
    }
}

