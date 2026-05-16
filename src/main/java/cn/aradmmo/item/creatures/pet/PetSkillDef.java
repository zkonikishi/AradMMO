package cn.aradmmo.item.creatures.pet;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable pet skill definition loaded from config.
 */
public final class PetSkillDef {

    private final String id;
    private final String display;
    private final PetSkillType type;
    private final long cooldownTicks;
    private final double power;

    public PetSkillDef(String id, String display, PetSkillType type, long cooldownTicks, double power) {
        this.id = id;
        this.display = display;
        this.type = type;
        this.cooldownTicks = Math.max(1L, cooldownTicks);
        this.power = power;
    }

    public String id() { return id; }
    public String display() { return display; }
    public PetSkillType type() { return type; }
    public long cooldownTicks() { return cooldownTicks; }
    public double power() { return power; }

    public static PetSkillDef load(String id, ConfigurationSection section, PetSkillType fallbackType) {
        String display = section.getString("display", id);
        String rawType = section.getString("type", fallbackType.name());
        PetSkillType type;
        try {
            type = PetSkillType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            type = fallbackType;
        }
        long cooldown = section.getLong("cooldown-ticks", 100L);
        double power = section.getDouble("power", 1.0);
        return new PetSkillDef(id.toLowerCase(Locale.ROOT), display, type, cooldown, power);
    }
}
