package cn.aradmmo.item.equipment.reinforce;

/** 一次强化尝试的结果。 */
public enum ReinforceResult {
    /** 强化成功，等级 +1。 */
    SUCCESS,
    /** 强化失败，等级可能下降。 */
    FAIL,
    /** 强化失败且物品被销毁（仅 +12 失败时触发）。 */
    BREAK,
    /** 物品已达最大强化等级。 */
    MAX_LEVEL,
    /** 玩家背包材料不足。 */
    NO_MATERIAL
}
