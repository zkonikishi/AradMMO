package cn.aradmmo.item.equipment;

/**
 * 装备槽位类型，决定槽位与原版 Minecraft 的同步行为
 */
public enum SlotType {
    /** 与玩家护甲槽同步 (头盔/上衣/下装/*/
    ARMOR,
    /** 与快捷栏指定位置同步*/
    ACTIVE,
    /** 纯被动饰品槽(戒指/项链/耳环，无原版同步*/
    PASSIVE,
    /** 通用槽位，允许任意物品*/
    GENERIC,
    /** 与副手槽同步*/
    SHIELD,
    /** 鞘翅槽：滑翔时自动装备到胸甲槽*/
    ELYTRA,
    /** 背包槽：点击打开二级背包箱子*/
    BACKPACK,
    /** 宠物槽：放入宠物物品时召唤实体，取出时使宠物消失*/
    PET,
    /** 只读信息槽，显示玩家实时属性*/
    INFO,
    /** 点击执行动作(如打开工作*/
    ACTION
}

