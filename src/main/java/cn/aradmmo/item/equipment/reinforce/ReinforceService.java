package cn.aradmmo.item.equipment.reinforce;

import cn.aradmmo.item.equipment.stat.StatItemBuilder;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 强化业务逻辑服务。
 *
 * <h3>成功率（固定值）：</h3>
 * +1~3: 100%  | +4: 90%  | +5: 85%  | +6: 80%
 * +7: 70%    | +8: 65%  | +9: 60%  | +10: 50%
 * +11: 35%   | +12: 20%
 *
 * <h3>失败惩罚：</h3>
 * +1~5 失败：无惩罚（保持原级别）
 * +6~8 失败：等级 -1
 * +9~11 失败：等级 -3（最低 0）
 * +12 失败：物品销毁
 *
 * <h3>费用（金锭）：</h3>
 * +0→+3: 5  | +3→+6: 15  | +6→+9: 50  | +9→+11: 100  | +11→+12: 200
 */
public class ReinforceService {

    public static final int MAX_LEVEL = 12;

    private static final double[] SUCCESS_RATE = {
            1.00, // +0 → +1 (unused but keeps index aligned)
            1.00, // +1
            1.00, // +2
            1.00, // +3
            0.90, // +4
            0.85, // +5
            0.80, // +6
            0.70, // +7
            0.65, // +8
            0.60, // +9
            0.50, // +10
            0.35, // +11
            0.20  // +12
    };

    private static final int[] COST_GOLD = {
            5,   // +0 → +1
            5,   // +1 → +2
            5,   // +2 → +3
            15,  // +3 → +4
            15,  // +4 → +5
            15,  // +5 → +6
            50,  // +6 → +7
            50,  // +7 → +8
            50,  // +8 → +9
            100, // +9 → +10
            100, // +10 → +11
            200  // +11 → +12
    };

    private final Random random = new Random();
    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    public ReinforceService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取当前等级的强化成功率（0.0~1.0）。
     * 如果已达最大等级则返回 -1。
     */
    public double successRate(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return -1;
        int idx = currentLevel + 1;
        return idx < SUCCESS_RATE.length ? SUCCESS_RATE[idx] : 0;
    }

    /**
     * 获取将等级从 currentLevel 提升一级所需的金锭数量。
     */
    public int goldCost(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= COST_GOLD.length) return 0;
        return COST_GOLD[currentLevel];
    }

    /**
     * 尝试对 slot 中的物品进行一次强化。
     * 该方法会直接修改传入的 ItemStack 数组中的元素，并从玩家背包扣除金锭。
     *
     * @param player     强化玩家
     * @param itemHolder 长度 1 的数组，[0] 为待强化物品（可能被替换或置 null）
     * @return 强化结果枚举
     */
    public ReinforceResult tryReinforce(Player player, ItemStack[] itemHolder) {
        ItemStack item = itemHolder[0];
        if (item == null || item.getType().isAir()) return ReinforceResult.NO_MATERIAL;

        int currentLevel;
        try {
            NBTItem nbt = NBTItem.get(item);
            currentLevel = nbt.hasTag("ARAD_REINFORCE") ? nbt.getInteger("ARAD_REINFORCE") : 0;
        } catch (Throwable t) {
            return ReinforceResult.NO_MATERIAL;
        }

        if (currentLevel >= MAX_LEVEL) return ReinforceResult.MAX_LEVEL;

        int cost = goldCost(currentLevel);
        if (!hasGold(player, cost)) return ReinforceResult.NO_MATERIAL;

        double rate = successRate(currentLevel);
        boolean success = random.nextDouble() < rate;

        if (success) {
            removeGold(player, cost);
            itemHolder[0] = setReinforceLevel(item, currentLevel + 1);
            return ReinforceResult.SUCCESS;
        } else {
            // 失败惩罚
            removeGold(player, cost);
            if (currentLevel >= 12) {
                itemHolder[0] = null;
                return ReinforceResult.BREAK;
            } else if (currentLevel >= 9) {
                int newLevel = Math.max(0, currentLevel - 3);
                itemHolder[0] = setReinforceLevel(item, newLevel);
            } else if (currentLevel >= 6) {
                int newLevel = Math.max(0, currentLevel - 1);
                itemHolder[0] = setReinforceLevel(item, newLevel);
            }
            // +1~5 失败无惩罚
            return ReinforceResult.FAIL;
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private boolean hasGold(Player player, int amount) {
        if (amount <= 0) return true;
        int count = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.GOLD_INGOT) count += s.getAmount();
        }
        return count >= amount;
    }

    private void removeGold(Player player, int amount) {
        if (amount <= 0) return;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != Material.GOLD_INGOT) continue;
            if (s.getAmount() <= remaining) {
                remaining -= s.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.updateInventory();
    }

    private ItemStack setReinforceLevel(ItemStack item, int level) {
        try {
            NBTItem nbt = NBTItem.get(item);
            if (level <= 0) {
                nbt.removeTag("ARAD_REINFORCE");
            } else {
                nbt.setInteger("ARAD_REINFORCE", level);
            }
            item = nbt.toItem();
        } catch (Throwable ignored) {}
        return StatItemBuilder.refreshLore(item);
    }
}
