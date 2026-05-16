package cn.aradmmo.item.equipment.stat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 玩家当前所有装备带来的属性汇总缓存
 * <p>
 * 包含两类数据
 * <ul>
 *   <li>有原版映射（{@link ItemStat#vanillaKey()} null）的属—已通过
 *       {@link StatApplier} 写入原版 AttributeModifier，此处也保留总量供其他系统读取/li>
 *   <li>无原版映射的自定义属性（PHYS_ATK、MAGIC_ATK 等）—仅存于此缓存
 *       由战技能系统读取/li>
 * </ul>
 */
public final class EquipmentStats {

    private final Map<ItemStat, Double> totals = new EnumMap<>(ItemStat.class);

    /** 获取属性总量；未设置时返0*/
    public double get(ItemStat stat) {
        return totals.getOrDefault(stat, 0.0);
    }

    /** 累加属性值*/
    public void add(ItemStat stat, double value) {
        totals.merge(stat, value, Double::sum);
    }

    /** 清除所有缓存*/
    public void clear() {
        totals.clear();
    }

    /**
     * 返回只读快照，供战斗/技能等系统读取当前装备属性
     */
    public Map<ItemStat, Double> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(totals));
    }
}

