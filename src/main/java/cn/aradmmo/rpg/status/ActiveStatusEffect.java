package cn.aradmmo.rpg.status;

import java.util.UUID;

/**
 * Represents a single active instance of a status effect on a player.
 *
 * <p>This is a mutable value object: the service decrements
 * {@link #remainingTicks} and {@link #ticksSinceLastDamage} each tick.
 */
public final class ActiveStatusEffect {

    private final String effectId;
    private final UUID sourceUuid;   // null if applied by environment/command
    private int remainingTicks;
    private int stacks;
    private int ticksSinceLastDamage;

    public ActiveStatusEffect(String effectId, UUID sourceUuid, int durationTicks, int stacks) {
        this.effectId            = effectId;
        this.sourceUuid          = sourceUuid;
        this.remainingTicks      = durationTicks;
        this.stacks              = Math.max(1, stacks);
        this.ticksSinceLastDamage = 0;
    }

    public String effectId()        { return effectId; }
    public UUID sourceUuid()        { return sourceUuid; }
    public int remainingTicks()     { return remainingTicks; }
    public int stacks()             { return stacks; }
    public int ticksSinceLastDamage() { return ticksSinceLastDamage; }

    public boolean isExpired()      { return remainingTicks <= 0; }

    /** Called every tick by the service. Returns true if the effect expired this tick. */
    public boolean tick() {
        remainingTicks--;
        ticksSinceLastDamage++;
        return remainingTicks <= 0;
    }

    /** Resets the DOT damage timer (called after damage is dealt). */
    public void resetDamageTick() {
        ticksSinceLastDamage = 0;
    }

    /** Refreshes duration (re-application of the same effect). */
    public void refresh(int newDurationTicks) {
        this.remainingTicks = newDurationTicks;
    }

    /** Adds a stack (capped by the definition's max-stacks). */
    public void addStack(int maxStacks) {
        this.stacks = Math.min(maxStacks, this.stacks + 1);
    }
}

