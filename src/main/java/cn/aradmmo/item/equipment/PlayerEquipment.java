package cn.aradmmo.item.equipment;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * 每个玩家的装备面板状态持有者
 * 实现 {@link InventoryHolder} 以便{@link org.bukkit.event.inventory.InventoryClickEvent}
 * 中通过 {@code getHolder()} 快速识别该箱子界面属于本系统
 */
public final class PlayerEquipment implements InventoryHolder {

    /** 装备面板箱子界面标题。 */
    static final String TITLE = "§6§lArad 装备栏";

    private final UUID      ownerUuid;
    private final Inventory inventory;

    /** 当前召唤的宠物实体，null 时表示未召唤*/
    private LivingEntity pet;

    /** 装备鞘翅飞行前保存的原始胸甲，落地后恢复*/
    private ItemStack savedChestplate;

    /** 是否正在滑翔（用于避免重复触发鞘翅交换逻辑）*/
    private boolean gliding;

    public PlayerEquipment(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
    }

    // ── InventoryHolder ───────────────────────────────────────────────────────

    @Override
    public Inventory getInventory() { return inventory; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID ownerUuid()                    { return ownerUuid; }
    public LivingEntity getPet()               { return pet; }
    public void setPet(LivingEntity pet)       { this.pet = pet; }
    public ItemStack getSavedChestplate()      { return savedChestplate; }
    public void setSavedChestplate(ItemStack i) { this.savedChestplate = i; }
    public boolean isGliding()                 { return gliding; }
    public void setGliding(boolean gliding)    { this.gliding = gliding; }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    /** 返回装备面板是否对给定玩家处于打开状态*/
    public boolean isOpenFor(Player player) {
        return player.getOpenInventory().getTopInventory().equals(inventory);
    }

    /** 向玩家打开装备面板*/
    public void openFor(Player player) {
        player.openInventory(inventory);
    }

    /** 获取指定槽位中的物品*/
    public ItemStack getItem(SlotDef slot) {
        return inventory.getItem(slot.slotId());
    }

    /** 设置指定槽位中的物品*/
    public void setItem(SlotDef slot, ItemStack item) {
        inventory.setItem(slot.slotId(), item);
    }

    /**
     * 用填充物品和占位符填充整个面板
     * 已有实际物品的槽位不会被覆盖
     *
     * @param slots    所有槽位定
     * @param fillItem 填充空白格子的物品（如灰色玻璃板
     */
    public void fillHolders(SlotManager slots, ItemStack fillItem) {
        // 先用填充物品填满所54 
        ItemStack fill = (fillItem != null) ? fillItem : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            ItemStack current = inventory.getItem(i);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(i, fill.clone());
            }
        }
        // 再在装备槽位放占位符（如果该槽为空）
        for (SlotDef slot : slots.getAll()) {
            ItemStack current = inventory.getItem(slot.slotId());
            boolean isEmpty  = current == null || current.getType() == Material.AIR;
            boolean isFill   = current != null && current.equals(fill);
            boolean isHolder = slot.isHolder(current);
            if (isEmpty || isFill || isHolder) {
                inventory.setItem(slot.slotId(), slot.holderItem());
            }
        }
    }
}

