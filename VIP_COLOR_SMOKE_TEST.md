# Arad MMO 渐变颜色系统烟测清单

## 0. 前置准备
- 服务端版本: Paper/Folia 26.1.x
- 插件产物: `target/arad-mmo-0.1.0-SNAPSHOT.jar`
- 测试账号:
  - `AdminA` (OP)
  - `VipNameOnly` (仅名字渐变权限)
  - `VipChatOnly` (仅聊天渐变权限)
  - `VipBoth` (名字+聊天渐变权限)
  - `NormalPlayer` (无 VIP 特权)

## 1. 权限配置
为对应玩家分配权限:
- 名字渐变: `aradmmo.vip.gradient.name`
- 聊天渐变: `aradmmo.vip.gradient.chat`

示例（按你的权限插件语法执行）:
- `VipNameOnly` -> `aradmmo.vip.gradient.name`
- `VipChatOnly` -> `aradmmo.vip.gradient.chat`
- `VipBoth` -> `aradmmo.vip.gradient.name` + `aradmmo.vip.gradient.chat`

## 2. VIP 档位设置
在后台或管理员账号执行:
- `/am vip VipNameOnly custom`
- `/am vip VipChatOnly custom`
- `/am vip VipBoth custom`
- `/am vip NormalPlayer standard`

预期:
- 前三个玩家可进入 VIP 特权逻辑。
- `NormalPlayer` 不应获得渐变特权。

## 3. 命令功能验证（/am vipcolor）
### 3.1 名字渐变手动设置
使用 `VipBoth`:
- `/am vipcolor name #ff6a00:#ffd000`

预期:
- 聊天中发送消息时，玩家名字为渐变色。
- TAB 名单中的玩家名字显示渐变（若客户端/主题允许）。
- 世界中玩家名字（头顶）显示渐变或至少显示被系统样式接管。

### 3.2 聊天渐变手动设置
使用 `VipBoth`:
- `/am vipcolor chat #00d4ff:#6a5cff`

预期:
- 聊天消息正文为渐变色。
- 玩家名字保持名字渐变。

### 3.3 随机设置
使用 `VipBoth`:
- `/am vipcolor random both`

预期:
- 名字和聊天都更新为同一组随机渐变。

## 4. 权限隔离验证
### 4.1 仅名字权限
使用 `VipNameOnly`:
- `/am vipcolor name #ff0000:#00ffff` -> 应成功
- `/am vipcolor chat #ff0000:#00ffff` -> 应提示无权限

### 4.2 仅聊天权限
使用 `VipChatOnly`:
- `/am vipcolor chat #ff0000:#00ffff` -> 应成功
- `/am vipcolor name #ff0000:#00ffff` -> 应提示无权限

### 4.3 无 VIP 或无权限
使用 `NormalPlayer`:
- `/am vipcolor random both`

预期:
- 被拒绝（无权限或无 VIP 特权）。

## 5. GUI 菜单验证（个人资料 -> VIP）
进入:
- `/am menu`
- 点击 `VIP 等级` 卡片进入渐变菜单

验证点:
- 菜单存在随机的名字渐变选项和聊天渐变选项。
- 点击可应用对应渐变。
- 清除按钮可恢复默认。
- 无权限项应显示受限提示（或点击提示无权限）。

## 6. 物品文本 RGB/渐变验证
### 6.1 发放带颜色配置的模板物品
- `/am item give VipBoth <templateId>`

在 `stat-templates.yml` 里准备如下示例:
- 名字含 RGB: `&#ff6a00烈焰之刃`
- Lore 含渐变: `<g:#ff6a00:#ffd000>传说中的炽焰力量</g>`

预期:
- 物品名称显示 RGB 色。
- Lore 显示渐变。

## 7. 重载与稳定性验证
### 7.1 重载
- `/am reload`

预期:
- 在线玩家名字样式自动恢复。
- 聊天渐变继续生效。
- VIP 菜单可正常打开。

### 7.2 跨场景稳定性
在有其他聊天/称号插件的环境下执行:
- 反复切换世界、重连、重载

预期:
- 名字样式会被系统定时刷新回渐变状态。
- 不应出现永久丢失样式。

## 8. 回归检查
- 装备系统 `/am equip` 正常打开。
- 强化 GUI `/am reinforce` 正常。
- 宠物/坐骑命令不受影响。

## 9. 常见问题排查
### 问题: `/am vipcolor` 提示无权限
检查:
- 是否有 `aradmmo.command.vip`（管理员设定 VIP）
- 玩家是否有 `aradmmo.vip.gradient.name` / `aradmmo.vip.gradient.chat`
- 玩家 vip-tier 是否不是 `standard` / `none`

### 问题: 名字在某些场景不渐变
检查:
- 是否有其他插件在持续覆盖 displayName / tabListName
- 执行 `/am reload` 看样式是否恢复
- 观察 5 秒内是否被系统定时刷新回渐变

### 问题: 渐变格式无效
正确格式:
- `#RRGGBB:#RRGGBB`
示例:
- `#ff6a00:#ffd000`
- `#00d4ff:#6a5cff`
