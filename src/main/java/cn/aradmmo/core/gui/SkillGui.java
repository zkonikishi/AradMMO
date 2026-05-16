package cn.aradmmo.core.gui;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * 6-row chest showing all skills with current level and a [+1] button.
 *
 * Each skill occupies two adjacent slots:
 *   Left  slot = info card (level / cap)
 *   Right slot = [+1] button (green if affordable, red if not / capped)
 *
 * Pairs start at slot 10, spaced by 2.
 * Row 5: slot 45 = back to Profile.
 *
 * Click handling is done in GuiListener.
 */
final class SkillGui {

    private SkillGui() {}

    static Inventory build(AradMmoPlugin plugin, Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        List<String> skills = plugin.profiles().availableSkills();

        Inventory inv = org.bukkit.Bukkit.createInventory(null, 54,
            MiniMessage.miniMessage().deserialize("<yellow><bold>技能分配</bold></yellow>"));

        for (int i = 0; i < 54; i++) inv.setItem(i, GuiService.filler());

        int startSlot = 10;
        for (int i = 0; i < skills.size(); i++) {
            String sk = skills.get(i);
            int lvl = profile.skillLevel(sk);
            int cap = plugin.profiles().skillCap(sk);
            boolean capped = lvl >= cap;
            boolean canAfford = profile.skillPoints() > 0 && !capped;

            int infoSlot = startSlot + i * 3;
            int btnSlot = infoSlot + 1;
            if (infoSlot >= 44) break; // safety guard

            inv.setItem(infoSlot, GuiService.item(Material.BOOK,
                    "<yellow>" + sk,
                    "<gray>等级: <white>" + lvl + " / " + cap,
                    capped ? "<red>已达上限" : "<gray>剩余技能点: <white>" + profile.skillPoints()));

            inv.setItem(btnSlot, GuiService.item(
                    canAfford ? Material.LIME_DYE : Material.RED_DYE,
                    canAfford ? "<green>[+1]" : (capped ? "<red>已满级" : "<red>点数不足"),
                    canAfford ? "<dark_gray>点击消耗技能点" : ""));
        }

        inv.setItem(45, GuiService.item(Material.ARROW, "<gray>返回档案", "<dark_gray>点击返回"));

        return inv;
    }
}

