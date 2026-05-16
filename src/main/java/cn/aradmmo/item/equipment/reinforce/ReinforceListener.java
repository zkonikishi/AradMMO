package cn.aradmmo.item.equipment.reinforce;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 监听强化 GUI 的交互事件。
 */
public class ReinforceListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ReinforceGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // 只允许与装备槽（slot 13）交互——玩家放置或取走物品
        if (slot == ReinforceGui.SLOT_ITEM) {
            event.setCancelled(false);
            // 延迟一 tick 后刷新信息显示
            org.bukkit.Bukkit.getGlobalRegionScheduler().execute(
                    event.getHandlers().getRegisteredListeners()[0].getPlugin(),
                    () -> {
                        ItemStack placed = gui.getInventory().getItem(ReinforceGui.SLOT_ITEM);
                        gui.updateInfo(placed);
                    });
            return;
        }

        if (slot == ReinforceGui.SLOT_CONFIRM) {
            ItemStack target = gui.getInventory().getItem(ReinforceGui.SLOT_ITEM);
            if (target == null || target.getType().isAir()) {
                player.sendMessage("§c请先将装备放入强化槽！");
                return;
            }
            ItemStack[] holder = { target.clone() };
            ReinforceResult result = gui.getService().tryReinforce(player, holder);

            switch (result) {
                case SUCCESS -> {
                    gui.getInventory().setItem(ReinforceGui.SLOT_ITEM, holder[0]);
                    gui.updateInfo(holder[0]);
                    player.sendMessage("§a强化成功！");
                }
                case FAIL -> {
                    gui.getInventory().setItem(ReinforceGui.SLOT_ITEM, holder[0]);
                    gui.updateInfo(holder[0]);
                    player.sendMessage("§c强化失败...");
                }
                case BREAK -> {
                    gui.getInventory().setItem(ReinforceGui.SLOT_ITEM, null);
                    gui.updateInfo(null);
                    player.sendMessage("§4§l强化失败，装备已损毁！");
                }
                case MAX_LEVEL ->
                    player.sendMessage("§e该装备已达最高强化等级！");
                case NO_MATERIAL ->
                    player.sendMessage("§c金锭不足，无法强化！");
            }
            return;
        }

        if (slot == ReinforceGui.SLOT_CANCEL) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ReinforceGui gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // 将装备槽中的物品退还给玩家
        ItemStack item = gui.getInventory().getItem(ReinforceGui.SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld()
                            .dropItemNaturally(player.getLocation(), leftover));
            gui.getInventory().setItem(ReinforceGui.SLOT_ITEM, null);
        }
    }
}
