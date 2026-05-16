package cn.aradmmo.rpg.combat;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.classes.ClassDefinition;
import cn.aradmmo.rpg.profile.PlayerProfile;
import cn.aradmmo.rpg.skill.SkillBuffService;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Calculates combat snapshots using the Arad attribute system.
 *
 * <p>Attribute roles:
 * <ul>
 *   <li><b>STR (Strength)</b> - Physical/Independent attack scaling</li>
 *   <li><b>INT (Intellect)</b> - Magic attack scaling</li>
 *   <li><b>SPI (Spirit)</b>   - Crit chance and magic defense</li>
 *   <li><b>VIT (Vitality)</b> - Physical damage reduction and HP</li>
 * </ul>
 *
 * <p>Which stat scales the outgoing multiplier depends on the class's {@code combat-style}:
 * <ul>
 *   <li>{@code melee / ranged / assassin / tank} - STR only</li>
 *   <li>{@code magic}                            - INT only</li>
 *   <li>{@code balanced}                         - STR x 0.5 + INT x 0.5</li>
 *   <li>{@code independent}                      - STR independent-multiplier</li>
 * </ul>
 *
 * <p>All per-point coefficients are read from {@code attributes.yml}; class style
 * bonuses remain in {@code config.yml -> combat.styles}.
 */
public final class CombatService {
    private final AradMmoPlugin plugin;
    private final SkillBuffService buffService;

    public CombatService(AradMmoPlugin plugin, SkillBuffService buffService) {
        this.plugin = plugin;
        this.buffService = buffService;
    }

    public CombatSnapshot snapshot(UUID uuid, PlayerProfile profile, boolean projectileAttack) {
        String style = classStyle(profile.archetype());

        // -- Outgoing multiplier
        double outgoing = 1.0;

        int str = effectiveAttribute(uuid, profile, "strength");
        int spi = effectiveAttribute(uuid, profile, "spirit");
        int itl = effectiveAttribute(uuid, profile, "intellect");

        double physMult  = attrDouble("strength.per-point.physical-multiplier",   0.018);
        double indepMult = attrDouble("strength.per-point.independent-multiplier", 0.020);
        double magMult   = attrDouble("intellect.per-point.magic-multiplier",      0.018);

        outgoing += switch (style) {
            case "magic"       -> itl * magMult;
            case "balanced"    -> str * physMult * 0.5 + itl * magMult * 0.5;
            case "independent" -> str * indepMult;
            default            -> str * physMult;   // melee/ranged/assassin/tank
        };

        outgoing += classStyleBonus(style, projectileAttack);
        outgoing += skillAttackBonus(profile, projectileAttack);

        // -- Incoming multiplier (damage taken)
        int vit = effectiveAttribute(uuid, profile, "vitality");
        double physReduct = attrDouble("vitality.per-point.damage-reduction", 0.008);
        double magReduct  = attrDouble("spirit.per-point.magic-reduction",    0.005);
        double minIncoming = plugin.getConfig().getDouble("combat.attributes.minimum-incoming-multiplier", 0.35);

        double incoming = 1.0;
        incoming -= vit * physReduct;
        incoming -= spi * magReduct;
        incoming -= profile.skillLevel("iron-will")
                * plugin.getConfig().getDouble("combat.skills.iron-will.reduction", 0.03);
        incoming = Math.max(minIncoming, incoming);

        if (uuid != null && buffService.hasBuff(uuid, "iron-will")) {
            int lvl = profile.skillLevel("iron-will");
            incoming -= lvl * plugin.getConfig().getDouble("combat.skills.iron-will.active-reduction", 0.08);
            incoming = Math.max(minIncoming, incoming);
        }

        // -- Crit
        double maxCrit = plugin.getConfig().getDouble("combat.attributes.maximum-crit-chance", 0.45);
        double critChance = spi * attrDouble("spirit.per-point.crit-chance", 0.003);
        critChance += profile.skillLevel("mana-weave")
                * plugin.getConfig().getDouble("combat.skills.mana-weave.crit-chance", 0.03);

        double critMultiplier = 1.0 + plugin.getConfig().getDouble("combat.base-crit-bonus", 0.5)
                + profile.skillLevel("mana-weave")
                        * plugin.getConfig().getDouble("combat.skills.mana-weave.crit-bonus", 0.08);

        if (uuid != null && buffService.hasBuff(uuid, "mana-weave")) {
            int lvl = profile.skillLevel("mana-weave");
            critChance     += lvl * plugin.getConfig().getDouble("combat.skills.mana-weave.active-crit-chance", 0.05);
            critMultiplier += lvl * plugin.getConfig().getDouble("combat.skills.mana-weave.active-crit-bonus", 0.10);
        }

        critChance = Math.min(maxCrit, critChance);

        return new CombatSnapshot(outgoing, incoming, critChance, critMultiplier);
    }

    // -- Helpers

    /**
     * Calculates the net element damage multiplier for one hit.
     *
     * <p>Formula:
     * <pre>net = attacker.elementAttack(elem) - defender.elementResist(elem)</pre>
     * Clamped to {@code [elements.minimum-bonus, elements.maximum-bonus]} from {@code attributes.yml}.
     *
     * @param attacker the attacking player's profile
     * @param defender the defending player's profile
     * @param element  the element of the attack
     * @return a multiplier addend (e.g. 0.20 means +20% damage)
     */
    public double elementBonus(PlayerProfile attacker, PlayerProfile defender, String elementKey) {
        int net = attacker.elementAttack(elementKey) - defender.elementResist(elementKey);
        int min = plugin.attributesConfig().getInt("elements.minimum-bonus", -50);
        int max = plugin.attributesConfig().getInt("elements.maximum-bonus", 150);
        return Math.max(min, Math.min(max, net)) / 100.0;
    }

    /** Reads a double from attributes.yml with a fallback default. */
    private double attrDouble(String path, double def) {
        return plugin.attributesConfig().getDouble(path, def);
    }

    /** Returns the combat style string for the given archetype. */
    private String classStyle(String archetype) {
        ClassDefinition cls = plugin.classes().get(archetype.toLowerCase(Locale.ROOT));
        return cls == null ? "balanced" : cls.combatStyle();
    }

    /** Returns the style attack bonus from config.yml -> combat.styles. */
    private double classStyleBonus(String style, boolean projectileAttack) {
        double all    = plugin.getConfig().getDouble("combat.styles." + style + ".all-bonus",    0.0);
        double melee  = plugin.getConfig().getDouble("combat.styles." + style + ".melee-bonus",  0.0);
        double ranged = plugin.getConfig().getDouble("combat.styles." + style + ".ranged-bonus", 0.0);
        return projectileAttack ? (all + ranged) : (all + melee);
    }

    /** Returns skill-based attack bonuses. */
    private double skillAttackBonus(PlayerProfile profile, boolean projectileAttack) {
        if (projectileAttack) {
            return profile.skillLevel("burst-shot")
                    * plugin.getConfig().getDouble("combat.skills.burst-shot.damage", 0.05);
        }
        return profile.skillLevel("slash")
                * plugin.getConfig().getDouble("combat.skills.slash.damage", 0.06);
    }

    private int effectiveAttribute(UUID uuid, PlayerProfile fallbackProfile, String key) {
        if (uuid != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return plugin.profiles().effectiveAttribute(player, key);
            }
        }
        return fallbackProfile.attribute(key);
    }
}
