package cn.aradmmo.rpg.listener;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.ProfileMutationResult;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Grants XP and gold to the killing player when they slay a mob.
 *
 * <p>Reward values are looked up from {@code config.yml} under
 * {@code mob-rewards.<EntityType>}; the {@code mob-rewards.default} entry
 * is used as a fallback for entity types not explicitly listed.
 *
 * <p>Vanilla EXP drops are cancelled when {@code mob-rewards.cancel-vanilla-exp}
 * is {@code true} (default), so vanilla XP orbs do not interfere with the
 * plugin's own levelling system.
 */
public final class MobKillListener implements Listener {

    private final AradMmoPlugin plugin;

    public MobKillListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Cancel vanilla EXP drops if configured
        if (plugin.getConfig().getBoolean("mob-rewards.cancel-vanilla-exp", true)) {
            event.setDroppedExp(0);
        }

        // Only reward when the killer is a player
        Player killer = entity.getKiller();
        if (killer == null) return;

        // Don't reward for killing other players here (handled by DeathListener)
        if (entity instanceof Player) return;

        // Active companion gains experience from owner's kills.
        if (plugin.equipment() != null) {
            plugin.equipment().pets().handleOwnerKill(killer, entity);
        }

        String typeName = entity.getType().name();
        double xp   = reward(typeName, "xp",   10.0);
        double gold = reward(typeName, "gold",   1.0);

        // Grant XP and handle level-ups
        ProfileMutationResult result = plugin.profiles().addExperience(killer, xp);
        plugin.profiles().addBalance(killer, gold);

        // Sync HP when VIT-related level-up may have changed max HP
        if (result.leveledUp()) {
            plugin.hp().applyMaxHealth(killer);
            plugin.messages().send(killer, "event.kill.level-up",
                    "%level%", Integer.toString(result.profile().level()),
                    "%levels%", Integer.toString(result.levelsGained()),
                    "%stat%",   Integer.toString(result.statPointsGained()),
                    "%skill%",  Integer.toString(result.skillPointsGained()));
        }

        // Optional kill feedback (only shows XP since gold is minor noise)
        plugin.messages().send(killer, "event.kill.reward",
                "%mob%",  mobDisplayName(entity),
                "%xp%",   formatDouble(xp),
                "%gold%", formatDouble(gold));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private double reward(String typeName, String key, double fallback) {
        String specific = "mob-rewards." + typeName + "." + key;
        if (plugin.getConfig().contains(specific)) {
            return plugin.getConfig().getDouble(specific, fallback);
        }
        return plugin.getConfig().getDouble("mob-rewards.default." + key, fallback);
    }

    private String mobDisplayName(LivingEntity entity) {
        if (entity.customName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(entity.customName());
        }
        // e.g. ZOMBIE_VILLAGER -> Zombie Villager
        String raw = entity.getType().name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }

    private String formatDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}

