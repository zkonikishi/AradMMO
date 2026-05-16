package cn.aradmmo.core.gui;

import cn.aradmmo.core.AradMmoPlugin;
import java.util.List;
import java.util.Locale;
import cn.aradmmo.rpg.profile.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 6-row chest showing the player's current stats.
 *
 * Layout (slot indices, 0-based):
 *   Row 0: filler bar
 *   Slot  4: player head (summary)
 *   Slot 10: Level / XP
 *   Slot 11: Balance
 *   Slot 12: VIP
 *   Slot 13: Class
 *   Slot 14: Stat points
 *   Slot 15: Skill points
 *   Slot 19: STRENGTH
 *   Slot 20: AGILITY
 *   Slot 21: INTELLECT
 *   Slot 22: VITALITY
 *   Slot 28..31: Skills
 *   Row 5: nav bar - slot 45 = Class, slot 46 = Skills
 */
final class ProfileGui {

    private ProfileGui() {}

    static Inventory build(AradMmoPlugin plugin, Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        double needed = plugin.profiles().threshold(profile.level());

        Inventory inv = org.bukkit.Bukkit.createInventory(null, 54,
                MiniMessage.miniMessage().deserialize("<gold><bold>Arad MMO</bold></gold> <gray>- " + profile.name() + "</gray>"));

        // Fill border
        for (int i = 0; i < 9; i++) inv.setItem(i, GuiService.filler());
        for (int i = 45; i < 54; i++) inv.setItem(i, GuiService.filler());
        for (int i = 9; i < 45; i += 9) inv.setItem(i, GuiService.filler());
        for (int i = 17; i < 45; i += 9) inv.setItem(i, GuiService.filler());

        // Summary head
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(player);
        sm.displayName(MiniMessage.miniMessage().deserialize("<yellow>" + profile.name()));
        head.setItemMeta(sm);
        inv.setItem(4, head);

        // Core stats
        inv.setItem(10, GuiService.item(Material.EXPERIENCE_BOTTLE,
                "<aqua>等级 / 经验",
                "<gray>等级: <white>" + profile.level(),
                "<gray>经验: <white>" + fmt(profile.experience()) + " / " + fmt(needed)));

        inv.setItem(11, GuiService.item(Material.GOLD_INGOT,
                "<gold>金币",
                "<gray>余额: <white>" + fmt(profile.balance())));

        inv.setItem(12, GuiService.item(Material.NETHER_STAR,
                "<light_purple>VIP 等级",
                "<gray>当前: <white>" + profile.vipTier()));

        inv.setItem(13, GuiService.item(Material.DIAMOND_SWORD,
                "<green>职业",
                "<gray>当前: <white>" + profile.archetype(),
                "<dark_gray>点击 -> 职业菜单"));

        inv.setItem(14, GuiService.item(Material.REDSTONE,
                "<red>属性点",
                "<gray>可用: <white>" + profile.statPoints(),
                "<dark_gray>点击 -> 属性菜单"));

        inv.setItem(15, GuiService.item(Material.BLAZE_POWDER,
                "<yellow>技能点",
                "<gray>可用: <white>" + profile.skillPoints(),
                "<dark_gray>点击 -> 技能菜单"));

        // Attributes: icon and display name from attributes.yml
        List<String> attrKeys = plugin.attributeKeys();
        for (int i = 0; i < attrKeys.size(); i++) {
            String key = attrKeys.get(i);
            String iconName = plugin.attributesConfig().getString(key + ".icon", "PAPER");
            Material mat;
            try { mat = Material.valueOf(iconName.toUpperCase()); }
            catch (IllegalArgumentException e) { mat = Material.PAPER; }
            String display = plugin.attributesConfig().getString(key + ".display", key.toUpperCase());
            inv.setItem(19 + i, GuiService.item(mat,
                    display,
                                        "<gray>数值: <white>" + profile.attribute(key)));
        }

                // Elements - row 3 (slots 28-31): combined attack+resist per element
        List<String> elemKeys = plugin.elementKeys();
        for (int i = 0; i < elemKeys.size(); i++) {
            String eKey = elemKeys.get(i);
            String iconName = plugin.attributesConfig().getString("elements." + eKey + ".icon", "PAPER");
            Material eMat;
            try { eMat = Material.valueOf(iconName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { eMat = Material.PAPER; }
            String eDisplay = plugin.attributesConfig().getString("elements." + eKey + ".display", eKey.toUpperCase());
            int atk = profile.elementAttack(eKey);
            int res = profile.elementResist(eKey);
            // Build lore from config template, replacing {attack} and {resist}
            java.util.List<String> loreTpl = plugin.attributesConfig().getStringList("elements." + eKey + ".lore");
            String[] loreLines = loreTpl.stream()
                    .map(l -> l.replace("{attack}", String.valueOf(atk)).replace("{resist}", String.valueOf(res)))
                    .toArray(String[]::new);
            inv.setItem(28 + i, GuiService.item(eMat, eDisplay, loreLines));
        }

        // Skills - row 4 (slots 37-44)
        var skills = plugin.profiles().availableSkills();
        for (int i = 0; i < skills.size() && i < 8; i++) {
            String sk = skills.get(i);
            int lvl = profile.skillLevel(sk);
            int cap = plugin.profiles().skillCap(sk);
            inv.setItem(37 + i, GuiService.item(Material.BOOK,
                    "<yellow>" + sk,
                    "<gray>等级: <white>" + lvl + " / " + cap));
        }

        // Nav
        inv.setItem(46, GuiService.item(Material.COMPASS, "<aqua>职业菜单", "<dark_gray>点击切换"));
        inv.setItem(47, GuiService.item(Material.ENCHANTING_TABLE, "<aqua>技能菜单", "<dark_gray>点击切换"));
        inv.setItem(48, GuiService.item(Material.REDSTONE, "<red>属性菜单", "<dark_gray>点击分配属性"));

        return inv;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}

