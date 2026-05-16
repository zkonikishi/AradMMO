package cn.aradmmo.rpg.skill;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class SkillActivationService {
    private final AradMmoPlugin plugin;
    private final SkillCooldownService cooldownService;
    private final SkillBuffService buffService;

    public SkillActivationService(AradMmoPlugin plugin,
                                  SkillCooldownService cooldownService,
                                  SkillBuffService buffService) {
        this.plugin = plugin;
        this.cooldownService = cooldownService;
        this.buffService = buffService;
    }

    public SkillCastResult cast(Player player, String skillId) {
        String normalized = skillId.toLowerCase(Locale.ROOT);

        if (!plugin.profiles().availableSkills().contains(normalized)) {
            return SkillCastResult.UNKNOWN;
        }

        PlayerProfile profile = plugin.profiles().profile(player);
        int level = profile.skillLevel(normalized);
        if (level <= 0) {
            return SkillCastResult.NOT_LEARNED;
        }

        if (cooldownService.onCooldown(player, normalized)) {
            return SkillCastResult.ON_COOLDOWN;
        }

        int manaCost = plugin.mana().skillCost(normalized);
        if (!plugin.mana().canConsume(player, manaCost)) {
            return SkillCastResult.NO_MANA;
        }

        boolean success = switch (normalized) {
            case "slash" -> castSlash(player, level);
            case "burst-shot" -> castBurstShot(player, level);
            case "mana-weave" -> castManaWeave(player, level);
            case "iron-will" -> castIronWill(player, level);
            default -> false;
        };

        if (!success) {
            return SkillCastResult.UNKNOWN;
        }

        // Deduct mana after confirming the cast succeeded
        plugin.mana().consume(player, manaCost);

        long cooldownMs = (long) (plugin.getConfig().getDouble(
                "skills." + normalized + ".cooldown-seconds", 10D) * 1000L);
        cooldownService.setCooldown(player, normalized, cooldownMs);

        // ActionBar notification (locale-aware)
        player.sendActionBar(plugin.messages().component(player, "command.cast.done", "%skill%", normalized));

        return SkillCastResult.SUCCESS;
    }

    /** Charges a one-hit melee burst; consumed on next melee attack. */
    private boolean castSlash(Player player, int level) {
        long durationMs = (long) (plugin.getConfig().getDouble(
                "skills.slash.buff-duration-seconds", 5D) * 1000L);
        buffService.applyBuff(player.getUniqueId(), "slash", durationMs);
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 6, 0.3, 0.3, 0.3, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        return true;
    }

    /** Fires a spread of arrows scaled by skill level. */
    private boolean castBurstShot(Player player, int level) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        int arrowCount = 1 + level;
        double spread = plugin.getConfig().getDouble("skills.burst-shot.spread", 0.12D);

        player.getWorld().spawnParticle(Particle.CRIT, eye.toLocation(eye.getWorld()), 10, 0.2, 0.2, 0.2, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f);

        for (int i = 0; i < arrowCount; i++) {
            double ox = (Math.random() - 0.5D) * spread;
            double oy = (Math.random() - 0.5D) * spread;
            double oz = (Math.random() - 0.5D) * spread;
            Vector adjusted = direction.clone().add(new Vector(ox, oy, oz)).normalize();

            if (eye.getWorld().spawnEntity(eye, EntityType.ARROW) instanceof Arrow arrow) {
                arrow.setShooter(player);
                arrow.setVelocity(adjusted.multiply(3D));
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            }
        }
        return true;
    }

    /** Grants a temporary crit boost, scales with mana-weave level. */
    private boolean castManaWeave(Player player, int level) {
        long durationMs = (long) (plugin.getConfig().getDouble(
                "skills.mana-weave.buff-duration-seconds", 6D) * 1000L);
        buffService.applyBuff(player.getUniqueId(), "mana-weave", durationMs);
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.WITCH, loc, 25, 0.3, 0.6, 0.3, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        return true;
    }

    /** Grants a temporary damage-reduction shield, scales with iron-will level. */
    private boolean castIronWill(Player player, int level) {
        long durationMs = (long) (plugin.getConfig().getDouble(
                "skills.iron-will.buff-duration-seconds", 8D) * 1000L);
        buffService.applyBuff(player.getUniqueId(), "iron-will", durationMs);
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 0.3, 0.6, 0.3, 0);
        player.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.7f);
        return true;
    }
}

