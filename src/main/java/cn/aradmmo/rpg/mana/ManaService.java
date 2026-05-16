package cn.aradmmo.rpg.mana;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Runtime MP tracking service.
 *
 * <p>MP is not persisted to disk it resets to maximum on login.
 * Max and regen values are derived from the player's profile and {@code config.yml}.
 *
 * <p>Config keys (under the {@code mana} section):
 * <pre>
 * mana:
 *   base: 100
 *   per-level: 10
 *   per-spirit: 5          # SPI控制最大MP（每250点翻倍）
 *   regen-base: 5
 *   regen-per-spirit: 1    # SPI控制MP回复（每250点翻倍）
 * </pre>
 *
 * <p>Per-skill costs are read from {@code skills.<id>.mana-cost}.
 */
public final class ManaService {

    private final AradMmoPlugin plugin;
    private final Map<UUID, Integer> current = new HashMap<>();
    private ScheduledTask regenTask;

    public ManaService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Starts the 1-second regen ticker. Call once after plugin enable. */
    public void startTicker() {
        if (regenTask != null) regenTask.cancel();
        regenTask = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> tick(), 20L, 20L);
    }

    /** Stops the ticker and clears all runtime data. */
    public void shutdown() {
        if (regenTask != null) { regenTask.cancel(); regenTask = null; }
        current.clear();
    }

    // ── Session ────────────────────────────────────────────────────────────

    /** Initialises a player's MP to maximum. Call on login. */
    public void initialize(Player player) {
        current.put(player.getUniqueId(), max(player));
    }

    /** Removes the player's MP entry. Call on logout. */
    public void evict(UUID uuid) {
        current.remove(uuid);
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Current MP. Initialises to max if not yet tracked. */
    public int current(Player player) {
        return current.computeIfAbsent(player.getUniqueId(), k -> max(player));
    }

    /**
     * Maximum MP calculated from profile.
     * Arad原设定：50点SPI，最大MP翻倍（+100%）
     * 公式base + level×perLevel) × max(1, spi/250)
     */
    public int max(Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int base     = plugin.getConfig().getInt("mana.base",      100);
        int perLevel = plugin.getConfig().getInt("mana.per-level",  10);
        int perSpi   = plugin.getConfig().getInt("mana.per-spirit",  5);
        int spi      = plugin.profiles().effectiveAttribute(player, "spirit");
        double spiFactor = Math.max(1.0, spi / 250.0);
        int flat = base + profile.level() * perLevel + spi * perSpi;
        return (int) (flat * spiFactor);
    }

    /**
     * Returns regen amount per second.
     * Arad原设定：50点SPI，MP回复翻倍（+100%）
     */
    public int regenPerSec(Player player) {
        int baseRegen   = plugin.getConfig().getInt("mana.regen-base",        5);
        int perSpiRegen = plugin.getConfig().getInt("mana.regen-per-spirit",  1);
        int spi         = plugin.profiles().effectiveAttribute(player, "spirit");
        double spiFactor = Math.max(1.0, spi / 250.0);
        return (int) ((baseRegen + spi * perSpiRegen) * spiFactor);
    }

    /** Returns the MP cost for a skill (0 if not configured). */
    public int skillCost(String skillId) {
        return plugin.getConfig().getInt("skills." + skillId + ".mana-cost", 0);
    }

    /** Returns {@code true} if the player can afford {@code amount} MP. */
    public boolean canConsume(Player player, int amount) {
        return amount <= 0 || current(player) >= amount;
    }

    /**
     * Deducts {@code amount} MP. Returns {@code true} on success;
     * returns {@code false} (without modifying) if insufficient.
     */
    public boolean consume(Player player, int amount) {
        if (amount <= 0) return true;
        int c = current(player);
        if (c < amount) return false;
        current.put(player.getUniqueId(), c - amount);
        return true;
    }

    /** Restores up to {@code amount} MP, capped at maximum. */
    public void restore(Player player, int amount) {
        if (amount <= 0) return;
        int c = current(player);
        current.put(player.getUniqueId(), Math.min(max(player), c + amount));
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            int regen = regenPerSec(player);
            if (regen > 0) restore(player, regen);
        }
    }
}

