package cn.aradmmo.item.creatures.pet;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 处理宠物相关的游戏事件
 */
public final class PetListener implements Listener {

    private final AradMmoPlugin plugin;

    public PetListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    /** 阻止其他玩家伤害不属于自己的宠物*/
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity pet)) return;
        PetService svc = plugin.equipment().pets();
        if (svc.getOwner(pet) == null) return; // 不是我们的宠

        if (event.getDamager() instanceof Player attacker) {
            // 只有宠物主人可以造成伤害（防grief
            java.util.UUID owner = svc.getOwner(pet);
            if (!attacker.getUniqueId().equals(owner)) {
                event.setCancelled(true);
            }
        }
    }

    /** 宠物死亡时清PlayerEquipment 中的引用*/
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPetDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        PetService svc = plugin.equipment().pets();
        java.util.UUID ownerUuid = svc.getOwner(entity);
        if (ownerUuid == null) return;

        Player owner = org.bukkit.Bukkit.getPlayer(ownerUuid);
        if (owner != null) {
            cn.aradmmo.item.equipment.PlayerEquipment eq = plugin.equipment().get(owner);
            if (eq != null) eq.setPet(null);
        }
    }

    /** 宠物跟随玩家传送（防止卡住）*/
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        cn.aradmmo.item.equipment.PlayerEquipment eq =
                plugin.equipment().get(event.getPlayer());
        if (eq == null) return;
        LivingEntity pet = eq.getPet();
        if (pet == null || pet.isDead()) return;
        // 延迟一 tick 等玩家完成传送后再移动宠
        Player player = event.getPlayer();
        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && !pet.isDead()) {
                pet.teleport(player.getLocation());
            }
        }, 2L);
    }
}

