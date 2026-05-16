package cn.aradmmo.rpg.stamina;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Runtime Stamina (ST) tracking service.
 *
 * <p>Stamina is scaled by the independent <b>STA (耐力)</b> attribute it does NOT
 * depend on VIT (体力). Both are separate allocatable stats.
 *
 * <p>Draining activities (ST / second, all configurable):
 * <ul>
 *   <li><b>Sprinting</b> {@code stamina.drain.sprint}</li>
 *   <li><b>Swimming</b>  {@code stamina.drain.swim}</li>
 *   <li><b>Gliding</b>   {@code stamina.drain.glide}</li>
 * </ul>
 *
 * <p>Inventory weight affects movement speed independently of the ST bar.
 * Each filled inventory slot counts as 1 weight unit. When carried weight
 * exceeds the player's carry capacity (STA-scaled), a speed penalty modifier
 * is applied. The modifier is keyed {@code aradmmo:weight_penalty} so it can
 * be cleanly removed on logout or when weight drops back in range.
 *
 * <p>Config keys (under the {@code stamina} section):
 * <pre>
 * stamina:
 *   base: 150
 *   per-level: 5
 *   per-stamina: 8
 *   regen-base: 10
 *   regen-per-stamina: 1
 *   sprint-resume-threshold: 20
 *   drain:
 *     sprint: 10
 *     swim:   6
 *     glide:  15
 *   weight:
 *     base: 18
 *     per-stamina: 2
 *     speed-reduction-per-slot: 0.02
 *     max-speed-reduction: 0.50
 * </pre>
 */
public final class StaminaService {

    /** NamespacedKey for the inventory-weight speed modifier. */
    private static final NamespacedKey WEIGHT_KEY = new NamespacedKey("aradmmo", "weight_penalty");

    private final AradMmoPlugin plugin;
    /** Current ST per player. */
    private final Map<UUID, Integer> current = new HashMap<>();
    /** Players who are exhausted and cannot sprint until threshold is met. */
    private final Map<UUID, Boolean> exhausted = new HashMap<>();
    private ScheduledTask tickTask;

    public StaminaService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /** Starts the stamina tick (every 4 server ticks = 0.2 s). */
    public void startTicker() {
        if (tickTask != null) tickTask.cancel();
        tickTask = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> tick(), 4L, 4L);
    }

    /** Stops the ticker and clears runtime data. */
    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        current.clear();
        exhausted.clear();
    }

    // ── Session ────────────────────────────────────────────────────────────

    /** Initialises ST to max. Call on login. */
    public void initialize(Player player) {
        current.put(player.getUniqueId(), max(player));
        exhausted.put(player.getUniqueId(), false);
    }

    /**
     * Removes runtime data and cleans up the speed modifier.
     * Resolves the player object while it is still available during the quit event.
     */
    public void evict(UUID uuid) {
        current.remove(uuid);
        exhausted.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) removeWeightModifier(player);
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Current ST. Auto-initialises to max if not yet tracked. */
    public int current(Player player) {
        return current.computeIfAbsent(player.getUniqueId(), k -> max(player));
    }

    /** Maximum ST derived from player profile and config. */
    public int max(Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int base     = plugin.getConfig().getInt("stamina.base",         150);
        int perLevel = plugin.getConfig().getInt("stamina.per-level",      5);
        int perSta   = plugin.getConfig().getInt("stamina.per-stamina",    8);
        int sta = plugin.profiles().effectiveAttribute(player, "stamina");
        return base + profile.level() * perLevel + sta * perSta;
    }

    /** {@code true} if the player is exhausted (ST too low to sprint). */
    public boolean isExhausted(Player player) {
        return exhausted.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Calculates the player's total inventory weight by summing
     * {@code itemWeight(material) × stackSize} for every slot.
     * Returns a rounded integer (weight units × 10 internally, displayed as tenths).
     * Callers can use {@link #currentWeightExact} for the raw double.
     */
    public int currentWeight(Player player) {
        return (int) currentWeightExact(player);
    }

    /** Returns the raw (un-rounded) total weight of the player's inventory. */
    public double currentWeightExact(Player player) {
        FileConfiguration wCfg  = plugin.itemWeightsConfig();
        double defaultW = wCfg != null ? wCfg.getDouble("default-weight", 1.0) : 1.0;
        double total = 0.0;
        if (wCfg == null) return total;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            String key = "items." + item.getType().name();
            double w = wCfg.contains(key) ? wCfg.getDouble(key) : defaultW;
            total += w * item.getAmount();
        }
        return total;
    }

    /** Returns the maximum carry weight (in weight units) before speed penalties apply.
     * Scaled by player level; equip/title bonuses can be added here in the future.
     */
    public double maxCarry(Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int base     = plugin.getConfig().getInt("stamina.weight.base",     300);
        int perLevel = plugin.getConfig().getInt("stamina.weight.per-level", 10);
        return base + profile.level() * perLevel;
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private void tick() {
        final double tickFraction = 4.0 / 20.0; // 0.2 s

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            int cur   = current(player);
            int maxSt = max(player);

            ActivityType activity = detectActivity(player);

            if (activity != ActivityType.NONE) {
                int drainNow = (int) Math.ceil(drainRate(activity) * tickFraction);
                int next     = Math.max(0, cur - drainNow);
                current.put(uuid, next);
                if (next == 0) setExhausted(player, true);
            } else {
                int regenNow = (int) Math.ceil(regenRate(player) * tickFraction);
                int next     = Math.min(maxSt, cur + regenNow);
                current.put(uuid, next);
                if (isExhausted(player)) {
                    int threshold = plugin.getConfig().getInt("stamina.sprint-resume-threshold", 20);
                    if (next >= threshold) setExhausted(player, false);
                }
            }

            updateWeightModifier(player);
        }
    }

    private int regenRate(Player player) {
        int base   = plugin.getConfig().getInt("stamina.regen-base",         10);
        int perSta = plugin.getConfig().getInt("stamina.regen-per-stamina",   1);
        int sta = plugin.profiles().effectiveAttribute(player, "stamina");
        return base + sta * perSta;
    }

    private int drainRate(ActivityType activity) {
        return switch (activity) {
            case SPRINT -> plugin.getConfig().getInt("stamina.drain.sprint", 10);
            case SWIM   -> plugin.getConfig().getInt("stamina.drain.swim",    6);
            case GLIDE  -> plugin.getConfig().getInt("stamina.drain.glide",  15);
            case NONE   -> 0;
        };
    }

    private void setExhausted(Player player, boolean state) {
        exhausted.put(player.getUniqueId(), state);
        if (state && player.isSprinting()) player.setSprinting(false);
    }

    // ── Weight modifier ────────────────────────────────────────────────────

    private void updateWeightModifier(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        removeWeightModifier(player);

        double total     = currentWeightExact(player);
        double maxWeight = maxCarry(player);
        if (total <= maxWeight) return;

        double over             = total - maxWeight;
        double reductionPerUnit = plugin.getConfig().getDouble("stamina.weight.speed-reduction-per-unit", 0.001);
        double maxReduction     = plugin.getConfig().getDouble("stamina.weight.max-speed-reduction", 0.50);
        double reduction        = Math.min(over * reductionPerUnit, maxReduction);

        speedAttr.addModifier(new AttributeModifier(
                WEIGHT_KEY, -reduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeWeightModifier(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        for (AttributeModifier m : new ArrayList<>(speedAttr.getModifiers())) {
            if (WEIGHT_KEY.equals(m.getKey())) speedAttr.removeModifier(m);
        }
    }

    // ── Activity detection ─────────────────────────────────────────────────

    private ActivityType detectActivity(Player player) {
        if (player.isGliding()) return ActivityType.GLIDE;
        if (player.isSwimming() || isInWater(player)) return ActivityType.SWIM;
        if (player.isSprinting() && isMoving(player)) return ActivityType.SPRINT;
        return ActivityType.NONE;
    }

    private boolean isInWater(Player player) {
        Block block = player.getLocation().getBlock();
        return block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN;
    }

    private boolean isMoving(Player player) {
        org.bukkit.util.Vector v = player.getVelocity();
        return (v.getX() * v.getX() + v.getZ() * v.getZ()) > 0.0001;
    }

    /** Stamina-draining activity types. */
    public enum ActivityType { SPRINT, SWIM, GLIDE, NONE }
}

