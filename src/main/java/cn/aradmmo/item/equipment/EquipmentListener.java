package cn.aradmmo.item.equipment;

import cn.aradmmo.core.AradMmoPlugin;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 处理装备面板 ({@link PlayerEquipment}) 的所有交互事件
 * <p>
 * 逻辑摘要
 * <ul>
 *   <li>填充始终阻止</li>
 *   <li>INFO 始终阻止</li>
 *   <li>ACTION 阻止并执行动/li>
 *   <li>PET / BACKPACK 正常存取 + 召唤/解散宠物 / 打开背包</li>
 *   <li>ARMOR / ACTIVE / SHIELD / PASSIVE / GENERIC / ELYTRA 
 *       验证材质后允许存取，然后同步到原版玩家栏</li>
 *   <li>从玩家背Shift 点击 自动找到匹配的装备槽放入</li>
 *   <li>关闭界面 保存数据并回写护副手/快捷/li>
 * </ul>
 */
public final class EquipmentListener implements Listener {

    private final AradMmoPlugin plugin;

    public EquipmentListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // 识别装备面板
        Inventory topInv = event.getInventory();
        if (!(topInv.getHolder() instanceof PlayerEquipment eq)) return;

        int     rawSlot   = event.getRawSlot();
        int     invSize   = topInv.getSize(); // 54
        boolean isTopSlot = rawSlot >= 0 && rawSlot < invSize;

        InventoryAction action = event.getAction();
        EquipmentService svc = plugin.equipment();

        // ── 快捷数字键切───────────────────────────────────────
        if (action == InventoryAction.HOTBAR_SWAP && isTopSlot) {
            event.setCancelled(true);
            SlotDef slot = svc.slots().getBySlotId(rawSlot);
            if (slot == null || slot.type() == SlotType.INFO || slot.type() == SlotType.ACTION) return;
            int hotbarBtn = event.getHotbarButton();
            if (hotbarBtn < 0) return;
            ItemStack hotbarItem = player.getInventory().getItem(hotbarBtn);
            ItemStack equipItem  = eq.getInventory().getItem(rawSlot);
            if (hotbarItem != null && !hotbarItem.isEmpty() && slot.isAllowed(hotbarItem)) {
                ItemStack toHotbar = (equipItem != null && !slot.isHolder(equipItem)) ? equipItem : null;
                eq.getInventory().setItem(rawSlot, hotbarItem);
                player.getInventory().setItem(hotbarBtn, toHotbar);
                onSlotChanged(svc, eq, player, slot);
            }
            return;
        }

        if (isTopSlot) {
            // ── 点击装备面板上半部分 ─────────────────────────────
            SlotDef slot = svc.slots().getBySlotId(rawSlot);

            if (slot == null) {
                // 填充格：始终阻止
                event.setCancelled(true);
                return;
            }

            if (slot.type() == SlotType.INFO) {
                event.setCancelled(true);
                return;
            }

            if (slot.type() == SlotType.ACTION) {
                event.setCancelled(true);
                performAction(slot, player);
                return;
            }

            // 验证放入物品是否允许
            ItemStack cursor  = player.getItemOnCursor();
            ItemStack current = eq.getInventory().getItem(rawSlot);

            boolean takingItem   = action == InventoryAction.PICKUP_ALL
                                || action == InventoryAction.PICKUP_HALF
                                || action == InventoryAction.PICKUP_SOME
                                || action == InventoryAction.PICKUP_ONE;
            boolean placingItem  = action == InventoryAction.PLACE_ALL
                                || action == InventoryAction.PLACE_ONE
                                || action == InventoryAction.PLACE_SOME;
            boolean swapCursor   = action == InventoryAction.SWAP_WITH_CURSOR;
            boolean shiftTake    = event.getClick().isShiftClick();

            if (takingItem || shiftTake) {
                // 取出物品 只要该格不是占位符就允许
                boolean hasReal = current != null && !slot.isHolder(current)
                                && current.getType() != Material.AIR;
                if (!hasReal) {
                    event.setCancelled(true);
                    return;
                }
                // 允许取出；延迟处理后置同
                Bukkit.getScheduler().runTask(plugin,
                        () -> onSlotChanged(svc, eq, player, slot));
                return;
            }

            if (placingItem) {
                if (cursor == null || cursor.isEmpty() || !slot.isAllowed(cursor)) {
                    event.setCancelled(true);
                    return;
                }
                if (!checkItemRequirements(player, cursor)) {
                    event.setCancelled(true);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin,
                        () -> onSlotChanged(svc, eq, player, slot));
                return;
            }

            if (swapCursor) {
                if (cursor == null || cursor.isEmpty() || !slot.isAllowed(cursor)) {
                    event.setCancelled(true);
                    return;
                }
                if (!checkItemRequirements(player, cursor)) {
                    event.setCancelled(true);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin,
                        () -> onSlotChanged(svc, eq, player, slot));
                return;
            }

            // 其他动作 (DROP  阻止
            event.setCancelled(true);
            return;
        }

        // ── 点击玩家背包（下半部分）Shift 点击 自动装备 ─────────────────
        if (event.getClick().isShiftClick()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.isEmpty()) return;

            SlotDef target = findFirstEmptySlot(svc, eq, clickedItem);
            if (target == null) {
                event.setCancelled(true);
                return;
            }

            // MMOItems 需求验
            if (!checkItemRequirements(player, clickedItem)) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            // 手动将物品放入目标槽
            ItemStack inSlot = eq.getInventory().getItem(target.slotId());
            boolean slotEmpty = inSlot == null || target.isHolder(inSlot) || inSlot.getType() == Material.AIR;
            if (slotEmpty) {
                eq.getInventory().setItem(target.slotId(), clickedItem.clone());
                event.setCurrentItem(null);
                onSlotChanged(svc, eq, player, target);
            }
        }
    }

    // ── Drag ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlayerEquipment)) return;
        int invSize = event.getInventory().getSize();
        // 如果拖拽覆盖了顶部格子，全部取消
        for (int slot : event.getRawSlots()) {
            if (slot < invSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Close ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PlayerEquipment eq)) return;

        EquipmentService svc = plugin.equipment();
        // 回写所有原版同步槽
        svc.applyArmorToPlayer(eq, player);
        svc.applyHotbarToPlayer(eq, player);
        svc.applyShieldToPlayer(eq, player);
        svc.savePlayer(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * 根据槽位类型执行后置同步。在 Bukkit 处理事件后通过 scheduler 调用
     */
    private void onSlotChanged(EquipmentService svc, PlayerEquipment eq,
                               Player player, SlotDef slot) {
        // 若槽位变成空/占位放回 holder
        ItemStack newItem = eq.getInventory().getItem(slot.slotId());
        if (newItem == null || newItem.getType() == Material.AIR) {
            eq.setItem(slot, slot.holderItem());
        }

        switch (slot.type()) {
            case ARMOR   -> svc.applyArmorToPlayer(eq, player);
            case ACTIVE  -> svc.applyHotbarToPlayer(eq, player);
            case SHIELD  -> svc.applyShieldToPlayer(eq, player);
            case PET     -> svc.pets().onPetSlotChange(eq, player, slot);
            case BACKPACK -> {
                ItemStack bpItem = eq.getItem(slot);
                if (bpItem != null && !slot.isHolder(bpItem)) {
                    svc.backpacks().open(player, bpItem);
                }
            }
            default -> { /* PASSIVE / GENERIC / ELYTRA 无需实时同步 */ }
        }

        // 装备变化后重算属
        svc.stats().recalculate(player, eq);
    }

    /**
     * 检查物品的装备需求：
     * <ul>
     *   <li>Arad 原生需求：{@code ARAD_REQUIRED_LEVEL} / {@code ARAD_REQUIRED_CLASS}</li>
     *   <li>MMOItems 需求：{@code MMOITEMS_REQUIRED_LEVEL} / {@code MMOITEMS_REQUIRED_CLASS}</li>
     * </ul>
     * MythicLib 未加载则直接放行
     */
    private boolean checkItemRequirements(Player player, ItemStack item) {
        try {
            NBTItem nbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(item);

            // ── Arad 原生需────────────────────────────────────
            if (nbt.hasTag("ARAD_REQUIRED_LEVEL")) {
                int reqLvl = nbt.getInteger("ARAD_REQUIRED_LEVEL");
                if (reqLvl > 0 && !player.hasPermission("aradmmo.bypass.level")) {
                    int playerLevel = plugin.profiles().profile(player).level();
                    if (playerLevel < reqLvl) {
                        player.sendMessage(ChatColor.RED + "» 等级不足！装备该物品需要 "
                                + ChatColor.YELLOW + reqLvl + ChatColor.RED + " 级，你目前为 "
                                + ChatColor.YELLOW + playerLevel + ChatColor.RED + " 级。");
                        return false;
                    }
                }
            }

            if (nbt.hasTag("ARAD_REQUIRED_CLASS")) {
                String reqClass = nbt.getString("ARAD_REQUIRED_CLASS");
                if (reqClass != null && !reqClass.isBlank()
                        && !player.hasPermission("aradmmo.bypass.class")) {
                    String playerClass = plugin.profiles().profile(player).archetype();
                    if (playerClass == null || !reqClass.contains(playerClass)) {
                        player.sendMessage(ChatColor.RED + "» 职业不符！该物品需要职业: "
                                + ChatColor.YELLOW + reqClass);
                        return false;
                    }
                }
            }

            // ── MMOItems 需────────────────────────────────────
            if (!nbt.hasTag("MMOITEMS_ITEM_TYPE")) return true; // MMOItems 物品

            int requiredLevel = nbt.getInteger("MMOITEMS_REQUIRED_LEVEL");
            if (requiredLevel > 0 && !player.hasPermission("mmoitems.bypass.level")) {
                int playerLevel = plugin.profiles().profile(player).level();
                if (playerLevel < requiredLevel) {
                    player.sendMessage(ChatColor.RED + "» 等级不足！装备该物品需要 "
                            + ChatColor.YELLOW + requiredLevel + ChatColor.RED + " 级，你目前为 "
                            + ChatColor.YELLOW + playerLevel + ChatColor.RED + " 级。");
                    return false;
                }
            }

            String requiredClass = nbt.getString("MMOITEMS_REQUIRED_CLASS");
            if (requiredClass != null && !requiredClass.isBlank()
                    && !player.hasPermission("mmoitems.bypass.class")) {
                String playerClass = plugin.profiles().profile(player).archetype();
                if (playerClass == null || !requiredClass.contains(playerClass)) {
                    player.sendMessage(ChatColor.RED + "» 职业不符！该物品需要职 "
                            + ChatColor.YELLOW + requiredClass);
                    return false;
                }
            }

            return true;
        } catch (Throwable ignored) {
            return true; // MythicLib 未加载时放行
        }
    }

    /** 执行 ACTION 槽位绑定的动作*/
    private void performAction(SlotDef slot, Player player) {
        switch (slot.action()) {
            case "WORKBENCH"  -> player.openWorkbench(null, true);
            case "ENDERCHEST" -> player.openInventory(player.getEnderChest());
            default -> {}
        }
    }

    /**
     * 在装备面板中找到第一个可以接收该物品的空槽位
     */
    private SlotDef findFirstEmptySlot(EquipmentService svc, PlayerEquipment eq, ItemStack item) {
        for (SlotDef slot : svc.slots().getAll()) {
            if (!slot.isAllowed(item)) continue;
            ItemStack current = eq.getInventory().getItem(slot.slotId());
            if (current == null || slot.isHolder(current) || current.getType() == Material.AIR) {
                return slot;
            }
        }
        return null;
    }
}

