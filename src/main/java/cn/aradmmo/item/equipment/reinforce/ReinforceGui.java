package cn.aradmmo.item.equipment.reinforce;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

/**
 * 强化工坊 GUI（27 格箱子）。
 * <pre>
 *  0  1  2  3  4  5  6  7  8
 *  9 10 [11:成功率] [12] [13:装备槽] [14] [15:费用] 16 17
 * 18 19 [20:确认] 21 22 23 [24:取消] 25 26
 * </pre>
 */
public class ReinforceGui implements InventoryHolder {

    public static final int SLOT_ITEM    = 13;
    public static final int SLOT_RATE    = 11;
    public static final int SLOT_COST    = 15;
    public static final int SLOT_CONFIRM = 20;
    public static final int SLOT_CANCEL  = 24;

    private final Inventory inventory;
    private final ReinforceService service;
    private final Player player;

    public ReinforceGui(ReinforceService service, Player player) {
        this.service   = service;
        this.player    = player;
        this.inventory = Bukkit.createInventory(this, 27, "§6§l强化工坊");
        fillBg();
        updateInfo(null);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public ReinforceService getService() { return service; }
    public Player getPlayer()           { return player; }

    /** 向玩家打开此 GUI。 */
    public void open() { player.openInventory(inventory); }

    /** 在装备栏位发生变化后刷新成功率/费用提示。可传入 null 以显示默认文字。 */
    public void updateInfo(ItemStack targetItem) {
        int level = 0;
        if (targetItem != null && !targetItem.getType().isAir()) {
            try {
                io.lumine.mythic.lib.api.item.NBTItem nbt = io.lumine.mythic.lib.api.item.NBTItem.get(targetItem);
                if (nbt.hasTag("ARAD_REINFORCE")) level = nbt.getInteger("ARAD_REINFORCE");
            } catch (Throwable ignored) {}
        }

        // 成功率信息
        ItemStack rateInfo = makeInfo(Material.PAPER, "§b成功率信息",
                targetItem == null || targetItem.getType().isAir()
                        ? List.of("§7将装备放入中央槽位")
                        : List.of(
                                "§7当前等级: §e+" + level,
                                "§7成功率: §a" + (int)(service.successRate(level) * 100) + "%",
                                (level >= ReinforceService.MAX_LEVEL ? "§c已达最高强化等级" : "§7下一级: §e+" + (level + 1))
                          ));
        inventory.setItem(SLOT_RATE, rateInfo);

        // 费用信息
        ItemStack costInfo = makeInfo(Material.GOLD_INGOT, "§6强化费用",
                targetItem == null || targetItem.getType().isAir()
                        ? List.of("§7将装备放入中央槽位")
                        : List.of(
                                "§7消耗金锭: §e" + service.goldCost(level),
                                "§7（从背包自动扣除）"
                          ));
        inventory.setItem(SLOT_COST, costInfo);

        // 确认/取消按钮
        inventory.setItem(SLOT_CONFIRM, makeInfo(Material.LIME_DYE, "§a§l确认强化",
                List.of("§7点击开始强化")));
        inventory.setItem(SLOT_CANCEL,  makeInfo(Material.RED_DYE, "§c§l关闭",
                List.of("§7取回装备并关闭")));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void fillBg() {
        ItemStack pane = makeInfo(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            if (i != SLOT_ITEM && i != SLOT_RATE && i != SLOT_COST
                    && i != SLOT_CONFIRM && i != SLOT_CANCEL) {
                inventory.setItem(i, pane);
            }
        }
    }

    private static ItemStack makeInfo(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
