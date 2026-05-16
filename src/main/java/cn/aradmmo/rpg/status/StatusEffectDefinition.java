package cn.aradmmo.rpg.status;

import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

/**
 * Immutable definition of a status effect, loaded from {@code status.yml}.
 *
 * <p>Server admins can add, modify, or remove effects entirely in config
 * without touching Java code. The {@link EffectType} determines which
 * generic behavior the service applies at runtime.
 */
public final class StatusEffectDefinition {

    /** High-level behavior category. */
    public enum EffectType {
        CROWD_CONTROL,
        DOT,
        DEBUFF,
        BUFF,
        UNKNOWN
    }

    /** Damage type for DOT effects. */
    public enum DamageType {
        FIRE, PHYSICAL, MAGIC, TRUE
    }

    private final String id;
    private final String display;
    private final String icon;
    private final EffectType effectType;
    private final int defaultDuration;
    private final boolean breakOnDamage;
    private final boolean stackable;
    private final int maxStacks;

    // crowd-control
    private final boolean restrictMovement;
    private final boolean restrictActions;

    // dot
    private final DamageType damageType;
    private final double baseDamage;
    private final int tickInterval;

    // debuff / buff
    private final PotionEffectType potionEffect;
    private final int potionAmplifier;

    // visual
    private final Particle particle;
    private final int particleCount;

    private StatusEffectDefinition(Builder b) {
        this.id               = b.id;
        this.display          = b.display;
        this.icon             = b.icon;
        this.effectType       = b.effectType;
        this.defaultDuration  = b.defaultDuration;
        this.breakOnDamage    = b.breakOnDamage;
        this.stackable        = b.stackable;
        this.maxStacks        = b.maxStacks;
        this.restrictMovement = b.restrictMovement;
        this.restrictActions  = b.restrictActions;
        this.damageType       = b.damageType;
        this.baseDamage       = b.baseDamage;
        this.tickInterval     = b.tickInterval;
        this.potionEffect     = b.potionEffect;
        this.potionAmplifier  = b.potionAmplifier;
        this.particle         = b.particle;
        this.particleCount    = b.particleCount;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public String id()              { return id; }
    public String display()         { return display; }
    public String icon()            { return icon; }
    public EffectType effectType()  { return effectType; }
    public int defaultDuration()    { return defaultDuration; }
    public boolean breakOnDamage()  { return breakOnDamage; }
    public boolean stackable()      { return stackable; }
    public int maxStacks()          { return maxStacks; }

    public boolean restrictMovement() { return restrictMovement; }
    public boolean restrictActions()  { return restrictActions; }

    public DamageType damageType()  { return damageType; }
    public double baseDamage()      { return baseDamage; }
    public int tickInterval()       { return tickInterval; }

    public PotionEffectType potionEffect()  { return potionEffect; }
    public int potionAmplifier()            { return potionAmplifier; }

    public Particle particle()      { return particle; }
    public int particleCount()      { return particleCount; }

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Loads a {@link StatusEffectDefinition} from a YAML config section.
     * Returns {@code null} if the section is null or missing required fields.
     *
     * @param id      the effect ID (the config key)
     * @param section the {@link ConfigurationSection} for this effect
     */
    public static StatusEffectDefinition fromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        Builder b = new Builder(id);
        b.display         = section.getString("display", id);
        b.icon            = section.getString("icon", "PAPER");
        b.defaultDuration = section.getInt("default-duration", 60);
        b.breakOnDamage   = section.getBoolean("break-on-damage", false);
        b.stackable       = section.getBoolean("stackable", false);
        b.maxStacks       = section.getInt("max-stacks", 1);

        // effect-type
        String typeStr = section.getString("effect-type", "unknown").toLowerCase();
        b.effectType = switch (typeStr) {
            case "crowd-control" -> EffectType.CROWD_CONTROL;
            case "dot"           -> EffectType.DOT;
            case "debuff"        -> EffectType.DEBUFF;
            case "buff"          -> EffectType.BUFF;
            default              -> EffectType.UNKNOWN;
        };

        // crowd-control fields
        b.restrictMovement = section.getBoolean("movement", false);
        b.restrictActions  = section.getBoolean("actions", false);

        // dot fields
        String dmgTypeStr = section.getString("damage-type", "true").toLowerCase();
        b.damageType = switch (dmgTypeStr) {
            case "fire"     -> DamageType.FIRE;
            case "physical" -> DamageType.PHYSICAL;
            case "magic"    -> DamageType.MAGIC;
            default         -> DamageType.TRUE;
        };
        b.baseDamage    = section.getDouble("base-damage", 1.0);
        b.tickInterval  = Math.max(1, section.getInt("tick-interval", 20));

        // debuff / buff fields
        String potionName = section.getString("potion-effect", "");
        if (!potionName.isEmpty()) {
            try {
                NamespacedKey key = NamespacedKey.minecraft(potionName.toLowerCase());
                b.potionEffect = Registry.EFFECT.get(key);
            } catch (Exception ignored) {
                b.potionEffect = null;
            }
        }
        b.potionAmplifier = section.getInt("potion-amplifier", 0);

        // particle
        String particleName = section.getString("particle", "");
        if (!particleName.isEmpty()) {
            try {
                b.particle = Particle.valueOf(particleName.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                b.particle = null;
            }
        }
        b.particleCount = section.getInt("particle-count", 3);

        return new StatusEffectDefinition(b);
    }

    // ── Builder ───────────────────────────────────────────────────────────

    private static final class Builder {
        final String id;
        String display = "";
        String icon = "PAPER";
        EffectType effectType = EffectType.UNKNOWN;
        int defaultDuration = 60;
        boolean breakOnDamage = false;
        boolean stackable = false;
        int maxStacks = 1;
        boolean restrictMovement = false;
        boolean restrictActions = false;
        DamageType damageType = DamageType.TRUE;
        double baseDamage = 1.0;
        int tickInterval = 20;
        PotionEffectType potionEffect = null;
        int potionAmplifier = 0;
        Particle particle = null;
        int particleCount = 3;

        Builder(String id) { this.id = id; }
    }
}

