package cn.aradmmo.item.creatures.mount;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Mount event bridge — handles summoning via item use and vehicle enter/exit feedback.
 */
public final class MountListener implements Listener {

    private final AradMmoPlugin plugin;

    public MountListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    /** Right-click a mount token to summon the mount. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseMountItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!plugin.mounts().isMountItem(item)) return;

        plugin.mounts().summon(player, item);
        event.setCancelled(true);
    }

    /** Notify the rider when they mount their companion. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player rider)) return;
        if (!(event.getVehicle() instanceof LivingEntity mount)) return;
        java.util.UUID owner = plugin.mounts().getOwner(mount);
        if (!rider.getUniqueId().equals(owner)) return;
        // Brief mount-up message. Delayed one tick so the ride is registered.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (rider.isOnline()) rider.sendMessage("§6[Mount] §bYou are riding your mount!");
        }, 2L);
    }

    /** Notify the rider when they dismount. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player rider)) return;
        if (!(event.getVehicle() instanceof LivingEntity mount)) return;
        java.util.UUID owner = plugin.mounts().getOwner(mount);
        if (!rider.getUniqueId().equals(owner)) return;
        rider.sendMessage("§6[Mount] §7You dismounted.");
    }

    /** Dismiss mount on logout. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.mounts().despawn(event.getPlayer());
    }

    /** Clear tracking when the mount entity dies. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMountDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        java.util.UUID owner = plugin.mounts().getOwner(living);
        if (owner == null) return;
        Player player = org.bukkit.Bukkit.getPlayer(owner);
        if (player != null) plugin.mounts().despawn(player);
    }
}
