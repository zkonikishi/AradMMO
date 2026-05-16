# Arad MMO 模块化架构规范

本文档定义项目目录重构目标：`core` / `item` / `rpg` / `eco` / `pet` / `dungeon`。

## 1. 模块职责

### core
放置插件主要框架功能、总指令、所有管理命令系统。

- 插件生命周期与服务装配
- 全局配置加载与重载
- 总命令 `/am` 与管理子命令
- 事件总线入口、通用工具、权限常量

### item
放置装备和槽位系统，以及对标 MMOItems + RPGInventory 的能力。

- 装备面板、槽位定义、槽位校验
- 装备到原版栏位同步
- 物品属性系统（NBT + AttributeModifier）
- 模板化物品构建与发放
- 与 MMOItems/RPGInventory 等主流物品插件扩展

### rpg
放置对标 MMOCore 的 RPG 核心能力。

- 职业、等级、属性成长
- 技能、冷却、施法、增益
- 战斗快照与战斗事件
- RPG 规则引擎

### eco
放置 VIP 系统与经济系统。

- 钱包/货币服务
- 交易、扣费、奖励发放
- VIP 权益与经济加成
- 与 Vault/PlayerPoints/CoinsEngine 等主流经济插件扩展

### pet
放置宠物系统。

- 宠物召唤、跟随、加成
- 宠物槽位行为
- 宠物状态同步
- 与 MyPet/MythicMobs 等主流宠物插件扩展

### dungeon
放置地下城系统。

- 副本创建、队伍匹配、进度状态
- 地下城掉落、结算、重置
- 关卡事件与条件触发
- 与 MythicDungeons/DeluxeDungeons 等主流地下城插件扩展

## 2. 现有代码迁移映射

### 迁移到 core
- `cn.aradmmo.AradMmoPlugin` -> `cn.aradmmo.core.AradMmoPlugin`
- `cn.aradmmo.command.*` -> `cn.aradmmo.core.command.*`
- `cn.aradmmo.listener.PlayerSessionListener` -> `cn.aradmmo.core.listener.PlayerSessionListener`
- 全局初始化/重载相关类 -> `cn.aradmmo.core.bootstrap.*`

### 迁移到 item
- `cn.aradmmo.equipment.*` -> `cn.aradmmo.item.equipment.*`
- `cn.aradmmo.equipment.stat.*` -> `cn.aradmmo.item.stat.*`
- `cn.aradmmo.equipment.backpack.*` -> `cn.aradmmo.item.backpack.*`
- `cn.aradmmo.equipment.pet.*` 中与槽位联动逻辑 -> `cn.aradmmo.item.slot.petbridge.*`

### 迁移到 rpg
- `cn.aradmmo.profile.*` -> `cn.aradmmo.rpg.profile.*`
- `cn.aradmmo.classes.*` -> `cn.aradmmo.rpg.classes.*`
- `cn.aradmmo.combat.*` -> `cn.aradmmo.rpg.combat.*`
- `cn.aradmmo.skill.*` -> `cn.aradmmo.rpg.skill.*`
- `cn.aradmmo.hp|mana|stamina|status.*` -> `cn.aradmmo.rpg.stat.*`

### 迁移到 eco
- `VipSystemRecode` 相关能力整合 -> `cn.aradmmo.eco.vip.*`
- 经济服务 -> `cn.aradmmo.eco.economy.*`

### 迁移到 pet
- 纯宠物逻辑（非装备槽桥接） -> `cn.aradmmo.pet.*`

### 迁移到 dungeon
- 新增副本系统 -> `cn.aradmmo.dungeon.*`

## 3. 资源目录映射

- `src/main/resources/equipment/*` -> `src/main/resources/item/*`
- `paper-plugin.yml` 中命令、权限按新模块前缀整理
- 后续新增：
  - `src/main/resources/eco/*`
  - `src/main/resources/pet/*`
  - `src/main/resources/dungeon/*`

## 4. 插件扩展接口（建议）

### item 扩展
- MMOItems 物品类型识别器
- RPGInventory 槽位映射适配器
- NBT 统一桥接层（MythicLib/ItemNBTAPI）

### rpg 扩展
- MMOCore 职业与等级同步桥接
- MMOCore 技能触发桥接

### eco 扩展
- Vault 经济桥接
- PlayerPoints 点券桥接
- CoinsEngine 代币桥接

### pet 扩展
- MyPet 宠物实体桥接
- MythicMobs 宠物召唤桥接

### dungeon 扩展
- MythicDungeons 副本事件桥接
- DeluxeDungeons 进度桥接

## 5. 实施顺序（推荐）

1. 仅改包名与目录，不改业务逻辑（保证可编译）
2. 抽离扩展接口层（Bridge/Adapter）
3. 接入主流插件实现类（每个插件单独实现）
4. 加入模块自检与缺失依赖降级策略
5. 最后统一命令与权限前缀

## 6. 命名规范

- 模块包名统一：`cn.aradmmo.<module>.*`
- 扩展实现命名：`<PluginName><Domain>Bridge`
- 兼容开关：`integrations.<plugin>.enabled`
- 所有桥接必须支持「插件不存在时安全降级」
