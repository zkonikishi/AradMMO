package cn.aradmmo.rpg.combat;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements Listener {
    private final AradMmoPlugin plugin;
    private final CombatService combatService;

    public CombatListener(AradMmoPlugin plugin, CombatService combatService) {
        this.plugin = plugin;
        this.combatService = combatService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {

        Player attacker = attacker(event.getDamager());
        if (attacker != null) {
            boolean projectileAttack = event.getDamager() instanceof Projectile;
            PlayerProfile attackerProfile = plugin.profiles().profile(attacker);
            CombatSnapshot snapshot = combatService.snapshot(attacker.getUniqueId(), attackerProfile, projectileAttack);

            // 元素倍率：选取攻击者最高元素强化对应属
            double elementMultiplier = 1.0;
            if (event.getEntity() instanceof Player defender) {
                PlayerProfile defenderProfile = plugin.profiles().profile(defender);
                String dominantElement = plugin.elementKeys().stream()
                        .max(java.util.Comparator.comparingInt(attackerProfile::elementAttack))
                        .orElse("fire");
                elementMultiplier += combatService.elementBonus(attackerProfile, defenderProfile, dominantElement);
            }

            double damage = event.getDamage() * snapshot.outgoingMultiplier();
            if (ThreadLocalRandom.current().nextDouble() < snapshot.critChance()) {
                damage *= snapshot.critMultiplier();
            }

            // Consume slash buff on melee hit
            if (!projectileAttack && plugin.skillBuffs().hasBuff(attacker.getUniqueId(), "slash")) {
                int slashLevel = attackerProfile.skillLevel("slash");
                double bonus = slashLevel * plugin.getConfig().getDouble("combat.skills.slash.active-bonus", 0.30D);
                damage *= (1D + bonus);
                plugin.skillBuffs().consumeBuff(attacker.getUniqueId(), "slash");
            }

            // 应用元素倍率
            damage *= elementMultiplier;

            event.setDamage(damage);

            // ActionBar: show outgoing damage to attacker
            final double displayDamage = damage;
            attacker.sendActionBar(MiniMessage.miniMessage()
                    .deserialize("<red>\u2694 <white>" + String.format(Locale.US, "%.1f", displayDamage)));
        }

        if (event.getEntity() instanceof Player defender) {
            PlayerProfile profile = plugin.profiles().profile(defender);
            CombatSnapshot snapshot = combatService.snapshot(defender.getUniqueId(), profile, false);
            double reduced = event.getDamage() * snapshot.incomingMultiplier();
            event.setDamage(reduced);

            // ActionBar: show incoming damage to defender
            defender.sendActionBar(MiniMessage.miniMessage()
                    .deserialize("<dark_red>\u2764 -" + String.format(Locale.US, "%.1f", reduced)));
        }
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Arrow arrow) {
            ProjectileSource shooter = arrow.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
            return null;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
