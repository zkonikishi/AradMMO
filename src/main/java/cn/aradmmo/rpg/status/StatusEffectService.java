package cn.aradmmo.rpg.status;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.status.StatusEffectDefinition.EffectType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages all active status effects for players.
 *
 * <h3>Design principles:</h3>
 * <ul>
 *   <li>All effect definitions are read from {@code status.yml} adding, removing,
 *       or modifying effects requires only a config change and {@code /am reload}.</li>
 *   <li>Java handles four generic behavior types (crowd-control / dot / debuff / buff);
 *       the specifics (duration, damage, potion level) come entirely from config.</li>
 *   <li>The tick task runs every Bukkit tick; potion re-application and DOT damage
 *       respect each effect's {@code tick-interval}.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * plugin.statusEffects().apply(player, "burn", null, 100);   // 5 s burn
 * plugin.statusEffects().remove(player, "burn");
 * plugin.statusEffects().has(player, "stun");
 * plugin.statusEffects().notifyDamage(player);               // for break-on-damage
 * }</pre>
 */
public final class StatusEffectService {

    private final AradMmoPlugin plugin;

    /** All loaded definitions keyed by effect ID. */
    private final Map<String, StatusEffectDefinition> definitions = new LinkedHashMap<>();

    /** Per-player active effects: UUID (effectId instance). */
    private final Map<UUID, Map<String, ActiveStatusEffect>> active = new HashMap<>();

    private BukkitTask tickTask;
    private BukkitTask actionBarTask;

    public StatusEffectService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void reload() {
        definitions.clear();
        var config = plugin.statusConfig();
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            var section = config.getConfigurationSection(key);
            StatusEffectDefinition def = StatusEffectDefinition.fromConfig(key, section);
            if (def != null) {
                definitions.put(key.toLowerCase(), def);
            }
        }
        plugin.getLogger().info("[StatusEffects] Loaded " + definitions.size() + " effect definitions.");
    }

    /** Starts the per-tick processing task. Call once after reload. */
    public void startTicker() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        if (actionBarTask != null && !actionBarTask.isCancelled()) {
            actionBarTask.cancel();
        }
        // Update ActionBar every 20 ticks (1 second)
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::showActionBar, 20L, 20L);
    }

    /** Stops the ticker and clears all active effects (called on plugin disable). */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        // Remove all visual effects from online players
        for (UUID uuid : active.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) clearPotionEffects(p, active.get(uuid).values());
        }
        active.clear();
    }
    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns the definition for the given effect ID, or {@code null} if not found.
     * Use this to check if an effect is configured before applying it.
     */
    public StatusEffectDefinition definition(String effectId) {
        return definitions.get(effectId.toLowerCase());
    }

    /** Returns an unmodifiable view of all loaded definitions. */
    public Collection<StatusEffectDefinition> allDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    /**
     * Applies a status effect to a player.
     *
     * @param player      target player
     * @param effectId    effect ID (must exist in status.yml)
     * @param sourceUuid  attacker UUID, or {@code null} for environment/command
     * @param durationTicks override duration in ticks; pass {@code -1} to use default
     */
    public void apply(Player player, String effectId, UUID sourceUuid, int durationTicks) {
        String id = effectId.toLowerCase();
        StatusEffectDefinition def = definitions.get(id);
        if (def == null) return;

        int duration = durationTicks < 0 ? def.defaultDuration() : durationTicks;
        Map<String, ActiveStatusEffect> playerEffects = active.computeIfAbsent(
                player.getUniqueId(), k -> new LinkedHashMap<>());

        ActiveStatusEffect existing = playerEffects.get(id);
        if (existing != null) {
            // Re-application: refresh duration and possibly add a stack
            existing.refresh(Math.max(existing.remainingTicks(), duration));
            if (def.stackable()) {
                existing.addStack(def.maxStacks());
            }
        } else {
            playerEffects.put(id, new ActiveStatusEffect(id, sourceUuid, duration, 1));
        }

        // Immediately apply crowd-control or potion effect
        applyVisual(player, def, playerEffects.get(id));
    }

    /**
     * Applies a status effect using the default duration defined in config.
     */
    public void apply(Player player, String effectId, UUID sourceUuid) {
        apply(player, effectId, sourceUuid, -1);
    }

    /**
     * Removes a specific status effect from a player.
     */
    public void remove(Player player, String effectId) {
        String id = effectId.toLowerCase();
        Map<String, ActiveStatusEffect> playerEffects = active.get(player.getUniqueId());
        if (playerEffects == null) return;
        playerEffects.remove(id);
        // Remove potion effects associated with this definition
        StatusEffectDefinition def = definitions.get(id);
        if (def != null && def.potionEffect() != null) {
            player.removePotionEffect(def.potionEffect());
        }
        if (def != null && def.effectType() == EffectType.CROWD_CONTROL) {
            removeMovementRestriction(player);
        }
    }

    /**
     * Removes all active status effects from a player.
     */
    public void removeAll(Player player) {
        Map<String, ActiveStatusEffect> playerEffects = active.remove(player.getUniqueId());
        if (playerEffects == null) return;
        clearPotionEffects(player, playerEffects.values());
        removeMovementRestriction(player);
    }

    /**
     * Returns whether the player currently has the given status effect.
     */
    public boolean has(Player player, String effectId) {
        Map<String, ActiveStatusEffect> playerEffects = active.get(player.getUniqueId());
        if (playerEffects == null) return false;
        ActiveStatusEffect eff = playerEffects.get(effectId.toLowerCase());
        return eff != null && !eff.isExpired();
    }

    /**
     * Returns an unmodifiable view of all active effects on a player.
     */
    public Map<String, ActiveStatusEffect> getActive(Player player) {
        Map<String, ActiveStatusEffect> playerEffects = active.get(player.getUniqueId());
        return playerEffects == null ? Map.of() : Collections.unmodifiableMap(playerEffects);
    }

    /**
     * Called when a player takes damage. Removes effects with {@code break-on-damage: true}.
     */
    public void notifyDamage(Player player) {
        Map<String, ActiveStatusEffect> playerEffects = active.get(player.getUniqueId());
        if (playerEffects == null) return;
        playerEffects.entrySet().removeIf(entry -> {
            StatusEffectDefinition def = definitions.get(entry.getKey());
            if (def != null && def.breakOnDamage()) {
                if (def.potionEffect() != null) player.removePotionEffect(def.potionEffect());
                return true;
            }
            return false;
        });
    }

    /**
     * Cleans up player data when they log out.
     */
    public void handleQuit(UUID uuid) {
        active.remove(uuid);
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    private void tick() {
        var iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            Map<String, ActiveStatusEffect> effects = entry.getValue();
            effects.entrySet().removeIf(effectEntry -> {
                String id = effectEntry.getKey();
                ActiveStatusEffect inst = effectEntry.getValue();
                StatusEffectDefinition def = definitions.get(id);

                if (def == null) return true; // definition removed from config

                boolean expired = inst.tick();

                if (!expired) {
                    processTick(player, def, inst);
                } else {
                    // Expire cleanup
                    if (def.potionEffect() != null) player.removePotionEffect(def.potionEffect());
                    if (def.effectType() == EffectType.CROWD_CONTROL) removeMovementRestriction(player);
                }
                return expired;
            });

            if (effects.isEmpty()) iterator.remove();
        }
    }

    private void processTick(Player player, StatusEffectDefinition def, ActiveStatusEffect inst) {
        switch (def.effectType()) {
            case CROWD_CONTROL -> refreshCrowdControl(player, def);
            case DOT           -> tickDot(player, def, inst);
            case DEBUFF, BUFF  -> refreshPotion(player, def);
            default            -> {}
        }
        spawnParticle(player, def);
    }

    // ── Effect Behavior ───────────────────────────────────────────────────

    private void applyVisual(Player player, StatusEffectDefinition def, ActiveStatusEffect inst) {
        switch (def.effectType()) {
            case CROWD_CONTROL -> refreshCrowdControl(player, def);
            case DEBUFF, BUFF  -> refreshPotion(player, def);
            default            -> {}
        }
        spawnParticle(player, def);
    }

    private void refreshCrowdControl(Player player, StatusEffectDefinition def) {
        int dur = 40; // Re-apply every 2 s worth of ticks to keep it active
        if (def.restrictMovement()) {
            player.addPotionEffect(new PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, dur, 255, false, false, false));
        }
        if (def.restrictActions()) {
            player.addPotionEffect(new PotionEffect(
                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE, dur, 255, false, false, false));
        }
    }

    private void removeMovementRestriction(Player player) {
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE);
    }

    private void refreshPotion(Player player, StatusEffectDefinition def) {
        if (def.potionEffect() == null) return;
        // Apply with ambient=false, showParticles=false to avoid default particle spam
        player.addPotionEffect(new PotionEffect(
                def.potionEffect(), 60, def.potionAmplifier(), false, false, true));
    }

    private void tickDot(Player player, StatusEffectDefinition def, ActiveStatusEffect inst) {
        if (inst.ticksSinceLastDamage() < def.tickInterval()) return;

        double damage = def.baseDamage() * inst.stacks();
        switch (def.damageType()) {
            case FIRE -> {
                player.setFireTicks(30);
                player.damage(damage);
            }
            case TRUE -> player.damage(damage);
            // PHYSICAL and MAGIC are handled as generic damage for now;
            // future work: apply CombatService reduction formulas
            default -> player.damage(damage);
        }
        inst.resetDamageTick();
    }

    private void spawnParticle(Player player, StatusEffectDefinition def) {
        if (def.particle() == null) return;
        try {
            player.getWorld().spawnParticle(def.particle(),
                    player.getLocation().add(0, 1, 0),
                    def.particleCount(), 0.3, 0.5, 0.3, 0);
        } catch (Exception ignored) {
            // Some particles require extra data; silently skip if incompatible
        }
    }

    private void clearPotionEffects(Player player, Collection<ActiveStatusEffect> effects) {
        for (ActiveStatusEffect inst : effects) {
            StatusEffectDefinition def = definitions.get(inst.effectId());
            if (def != null && def.potionEffect() != null) {
                player.removePotionEffect(def.potionEffect());
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    /** Returns the set of effect IDs currently active for this player. */
    public Set<String> activeIds(Player player) {
        Map<String, ActiveStatusEffect> effects = active.get(player.getUniqueId());
        return effects == null ? Set.of() : Collections.unmodifiableSet(effects.keySet());
    }
    // ── ActionBar Display ──────────────────────────────────────────────

    private void showActionBar() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // ── HP / MP / SP bar ─────────────────────────────────────────
            double hp    = player.getHealth();
            double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                    ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0;
            int mp    = plugin.mana().current(player);
            int maxMp = plugin.mana().max(player);
            int sp    = plugin.stamina().current(player);
            int maxSp = plugin.stamina().max(player);

            // Sprint indicator: dim when exhausted
            String spColor     = plugin.stamina().isExhausted(player) ? "<yellow>" : "<green>";
            String spDarkColor = plugin.stamina().isExhausted(player) ? "<gold>"   : "<dark_green>";

            StringBuilder sb = new StringBuilder();
            sb.append("<red>").append((int) hp).append("<dark_red>/").append((int) maxHp)
              .append("  <aqua>").append(mp).append("<dark_aqua>/").append(maxMp).append(" MP")
              .append("  ").append(spColor).append("").append(sp)
              .append(spDarkColor).append("/").append(maxSp).append(" ST");

            // ── Status effects (players who have them) ───────────────────
            Map<String, ActiveStatusEffect> effects = active.get(player.getUniqueId());
            if (effects != null && !effects.isEmpty()) {
                StringJoiner joiner = new StringJoiner("  ");
                for (Map.Entry<String, ActiveStatusEffect> e : effects.entrySet()) {
                    StatusEffectDefinition def = definitions.get(e.getKey());
                    if (def == null) continue;
                    ActiveStatusEffect inst = e.getValue();
                    int secs = (inst.remainingTicks() + 19) / 20;
                    String color = switch (def.effectType()) {
                        case CROWD_CONTROL -> "<red>";
                        case DOT           -> "<gold>";
                        case DEBUFF        -> "<yellow>";
                        case BUFF          -> "<green>";
                        default            -> "<gray>";
                    };
                    String stackStr = (def.stackable() && inst.stacks() > 1) ? "x" + inst.stacks() : "";
                    joiner.add(color + def.display() + "</" + color.substring(1)
                            + " <dark_gray>(" + secs + "s" + stackStr + ")");
                }
                sb.append("  <dark_gray>|  ").append(joiner);
            }

            player.sendActionBar(MiniMessage.miniMessage().deserialize(sb.toString()));
        }
    }}

