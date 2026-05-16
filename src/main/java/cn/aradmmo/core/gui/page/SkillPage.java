package cn.aradmmo.core.gui.page;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.gui.framework.GuiDef;
import cn.aradmmo.core.gui.framework.GuiPage;
import cn.aradmmo.core.gui.framework.ItemDef;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SkillPage extends GuiPage {

    public SkillPage(AradMmoPlugin plugin, Player player, GuiDef def) {
        super(plugin, player, def);
    }

    @Override
    protected ItemStack buildItem(ItemDef itemDef) {
        return buildFromDef(itemDef); // filler and back button rendered directly from YAML
    }

    @Override
    protected void buildDynamic(Inventory inv) {
        PlayerProfile profile   = plugin.profiles().profile(player);
        List<String>  skills    = plugin.profiles().availableSkills();
        int startSlot = def.raw().getInt("dynamic.start-slot", 10);
        int stride    = def.raw().getInt("dynamic.stride",      3);

        for (int i = 0; i < skills.size(); i++) {
            String  sk        = skills.get(i);
            int     lvl       = profile.skillLevel(sk);
            int     cap       = plugin.profiles().skillCap(sk);
            boolean capped    = lvl >= cap;
            boolean canAfford = profile.skillPoints() > 0 && !capped;

            int infoSlot = startSlot + i * stride;
            int btnSlot  = infoSlot + 1;
            if (infoSlot >= inv.getSize()) break;

            inv.setItem(infoSlot, buildSimple(Material.BOOK, "<yellow>" + sk,
                    List.of(plugin.messages().raw(player, "gui.skill.level",
                                    "%level%", String.valueOf(lvl), "%cap%", String.valueOf(cap)),
                            capped ? plugin.messages().raw(player, "gui.skill.capped-label")
                                   : plugin.messages().raw(player, "gui.skill.remaining",
                                            "%value%", String.valueOf(profile.skillPoints())))));

            if (btnSlot < inv.getSize()) {
                String btnLabel;
                if (canAfford)   btnLabel = plugin.messages().raw(player, "gui.skill.add-btn");
                else if (capped) btnLabel = plugin.messages().raw(player, "gui.skill.maxed-btn");
                else             btnLabel = plugin.messages().raw(player, "gui.skill.no-points-btn");
                inv.setItem(btnSlot, buildSimple(
                        canAfford ? Material.LIME_DYE : Material.RED_DYE,
                        btnLabel,
                        canAfford ? List.of(plugin.messages().raw(player, "gui.skill.add-btn-lore")) : List.of()));
            }
        }
    }

    @Override
    public void handleClick(int slot, InventoryClickEvent event) {
        // Back button
        ItemDef clicked = def.defForSlot(slot);
        if (clicked != null && "back".equals(clicked.function())) {
            plugin.gui().openProfile(player);
            return;
        }

        // [+1] button for a skill
        List<String> skills   = plugin.profiles().availableSkills();
        int startSlot = def.raw().getInt("dynamic.start-slot", 10);
        int stride    = def.raw().getInt("dynamic.stride",      3);
        for (int i = 0; i < skills.size(); i++) {
            int btnSlot = startSlot + i * stride + 1;
            if (slot == btnSlot) {
                String skillId = skills.get(i);
                try {
                    plugin.profiles().allocateSkill(player, skillId, 1);
                    PlayerProfile p = plugin.profiles().profile(player);
                    plugin.messages().send(player, "command.skill.done",
                            "%player%", player.getName(),
                            "%skill%",  skillId,
                            "%value%",  String.valueOf(p.skillLevel(skillId)),
                            "%points%", String.valueOf(p.skillPoints()));
                } catch (IllegalArgumentException e) {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (msg.startsWith("Skill cap")) {
                        plugin.messages().send(player, "error.skill-cap", "%value%", skillId);
                    } else {
                        plugin.messages().send(player, "error.not-enough-skill-points");
                    }
                }
                plugin.gui().openSkills(player); // refresh
                return;
            }
        }
    }
}

