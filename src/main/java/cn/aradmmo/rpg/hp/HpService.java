package cn.aradmmo.rpg.hp;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runtime HP service syncs Minecraft's {@code MAX_HEALTH} attribute to the
 * player's VIT-based formula and handles passive HP regeneration.
 *
 * <p>Unlike MP and ST, health is persisted natively by Minecraft, so there is
 * no runtime map to manage.  This service only needs to recalculate and apply
 * the max-health ceiling whenever the player's stats change.
 *
 * <p>Formula (same doubling pattern as mana/SPI):
 * <pre>
 *   flat    = base + level × perLevel + vit × perVit
 *   maxHp   = flat × max(1, vit / 250)
 * </pre>
 *
 * <p>Config keys (under the {@code hp} section of {@code config.yml}):
 * <pre>
 * hp:
 *   base: 20
 *   per-level: 5
 *   per-vitality: 2
 *   regen-base: 0.5
 *   regen-per-vitality: 0.01
 * </pre>
 */
public final class HpService {

    private final AradMmoPlugin plugin;
    private BukkitTask regenTask;

    public HpService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Starts the 1-second HP regen ticker. */
    public void startTicker() {
        if (regenTask != null && !regenTask.isCancelled()) regenTask.cancel();
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Stops the ticker. */
    public void shutdown() {
        if (regenTask != null) { regenTask.cancel(); regenTask = null; }
    }

    // ── Session ────────────────────────────────────────────────────────────

    /**
     * Applies the player's VIT-derived max HP and resets current HP to that
     * maximum. Call on player login.
     */
    public void initialize(Player player) {
        applyMaxHealth(player);
        double maxHp = max(player);
        player.setHealth(maxHp);
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Recalculates and applies max HP from the player's current profile.
     * Current HP is clamped down if it exceeds the new maximum, but is never
     * raised (use {@link #initialize} for a full heal).
     */
    public void applyMaxHealth(Player player) {
        double maxHp = max(player);
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(maxHp);
        if (player.getHealth() > maxHp) {
            player.setHealth(maxHp);
        }
    }

    /**
     * Maximum HP for this player.
     * Formula: {@code (base + level×perLevel + vit×perVit) × max(1, vit/250)}
     */
    public double max(Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int base     = plugin.getConfig().getInt("hp.base",         20);
        int perLevel = plugin.getConfig().getInt("hp.per-level",     5);
        int perVit   = plugin.getConfig().getInt("hp.per-vitality",  2);
        int vit      = plugin.profiles().effectiveAttribute(player, "vitality");
        double vitFactor = Math.max(1.0, vit / 250.0);
        double flat = base + (double) profile.level() * perLevel + (double) vit * perVit;
        return flat * vitFactor;
    }

    /** HP regen amount per second for this player. */
    public double regenPerSec(Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        double baseRegen = plugin.getConfig().getDouble("hp.regen-base",          0.5);
        double perVit    = plugin.getConfig().getDouble("hp.regen-per-vitality",  0.01);
        int vit          = plugin.profiles().effectiveAttribute(player, "vitality");
        return baseRegen + vit * perVit;
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead()) continue;
            double current = player.getHealth();
            double maxHp   = max(player);
            if (current >= maxHp) continue;
            double regen = regenPerSec(player);
            if (regen > 0) {
                player.setHealth(Math.min(maxHp, current + regen));
            }
        }
    }
}

