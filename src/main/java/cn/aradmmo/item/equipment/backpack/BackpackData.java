package cn.aradmmo.item.equipment.backpack;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * 玩家背包数据：持有背包内容和所属玩UUID
 * 作为 {@link InventoryHolder} 注册以便在事件中识别
 */
public final class BackpackData implements InventoryHolder {

    private final UUID       ownerUuid;
    private final BackpackDef def;
    private final Inventory  inventory;

    public BackpackData(UUID ownerUuid, BackpackDef def) {
        this.ownerUuid = ownerUuid;
        this.def       = def;
        this.inventory = Bukkit.createInventory(this, def.size(),
                "§6背包: §f" + def.name());
    }

    /** 从已有内容恢复背包数据*/
    public BackpackData(UUID ownerUuid, BackpackDef def, ItemStack[] contents) {
        this(ownerUuid, def);
        ItemStack[] copy = contents.clone();
        for (int i = 0; i < Math.min(copy.length, inventory.getSize()); i++) {
            inventory.setItem(i, copy[i]);
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public UUID       ownerUuid() { return ownerUuid; }
    public BackpackDef def()      { return def; }
    public ItemStack[] contents() { return inventory.getContents(); }

    /** 向玩家打开背包界面*/
    public void openFor(Player player) {
        player.openInventory(inventory);
    }
}

