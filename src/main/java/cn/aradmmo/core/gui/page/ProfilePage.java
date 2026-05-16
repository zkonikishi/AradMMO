package cn.aradmmo.core.gui.page;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.gui.framework.GuiDef;
import cn.aradmmo.core.gui.framework.GuiPage;
import cn.aradmmo.core.gui.framework.ItemDef;
import cn.aradmmo.core.text.TextColorService;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class ProfilePage extends GuiPage {

    public ProfilePage(AradMmoPlugin plugin, Player player, GuiDef def) {
        super(plugin, player, def);
    }

    @Override
    protected String resolveTitlePlaceholders(String title) {
        return title.replace("%player_name%", player.getName());
    }

    @Override
    protected ItemStack buildItem(ItemDef itemDef) {
        PlayerProfile profile = plugin.profiles().profile(player);
        double needed = plugin.profiles().threshold(profile.level());
        String classDisplay = profile.archetype().isEmpty()
            ? "无" : plugin.profiles().classDisplay(profile.archetype());

        return switch (itemDef.function()) {
            case "player-head" -> {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                sm.setOwningPlayer(player);
                sm.displayName(TextColorService.component("<yellow>" + player.getName()));
                head.setItemMeta(sm);
                yield head;
            }
            case "level" -> buildFromDef(itemDef,
                    "level",   profile.level(),
                    "exp",     fmt(profile.experience()),
                    "max_exp", fmt(needed));
            case "balance"        -> buildFromDef(itemDef, "balance",      fmt(profile.balance()));
            case "vip"            -> buildFromDef(itemDef, "vip",          profile.vipTier());
            case "nav-class"      -> buildFromDef(itemDef, "class",        classDisplay,
                                                           "stat_points",  profile.statPoints(),
                                                           "skill_points", profile.skillPoints());
            case "nav-attributes" -> buildFromDef(itemDef, "stat_points",  profile.statPoints());
            case "nav-skills"     -> buildFromDef(itemDef, "skill_points", profile.skillPoints());
            default               -> buildFromDef(itemDef); // fillers, static nav buttons
        };
    }

    @Override
    protected void buildDynamic(Inventory inv) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int attrStart  = def.raw().getInt("dynamic.attributes-start", 19);
        int elemStart  = def.raw().getInt("dynamic.elements-start",   28);
        int skillStart = def.raw().getInt("dynamic.skills-start",     37);
        int skillMax   = def.raw().getInt("dynamic.skills-max",        8);

        // Attributes
        List<String> attrKeys = plugin.attributeKeys();
        for (int i = 0; i < attrKeys.size(); i++) {
            String key    = attrKeys.get(i);
            Material mat  = parseMat(plugin.attributesConfig().getString(key + ".icon", "PAPER"));
            String display = plugin.attributesConfig().getString(key + ".display", key.toUpperCase(Locale.ROOT));
            int slot = attrStart + i;
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, buildSimple(mat, display,
                    List.of(plugin.messages().raw(player, "gui.profile.attr-value",
                            "%value%", String.valueOf(profile.attribute(key))))));
        }

        // Elements
        List<String> elemKeys = plugin.elementKeys();
        for (int i = 0; i < elemKeys.size(); i++) {
            String eKey    = elemKeys.get(i);
            Material mat   = parseMat(plugin.attributesConfig().getString("elements." + eKey + ".icon", "PAPER"));
            String display = plugin.attributesConfig().getString("elements." + eKey + ".display", eKey.toUpperCase(Locale.ROOT));
            int atk = profile.elementAttack(eKey);
            int res = profile.elementResist(eKey);
            List<String> loreTpl = plugin.attributesConfig().getStringList("elements." + eKey + ".lore");
            List<String> lore = loreTpl.stream()
                    .map(l -> l.replace("{attack}", String.valueOf(atk))
                               .replace("{resist}", String.valueOf(res)))
                    .toList();
            int slot = elemStart + i;
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, buildSimple(mat, display, lore));
        }

        // Skills
        List<String> skills = plugin.profiles().availableSkills();
        for (int i = 0; i < skills.size() && i < skillMax; i++) {
            String sk  = skills.get(i);
            int lvl    = profile.skillLevel(sk);
            int cap    = plugin.profiles().skillCap(sk);
            int slot   = skillStart + i;
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, buildSimple(Material.BOOK, "<yellow>" + sk,
                    List.of(plugin.messages().raw(player, "gui.profile.skill-level",
                            "%level%", String.valueOf(lvl), "%cap%", String.valueOf(cap)))));
        }
    }

    @Override
    public void handleClick(int slot, InventoryClickEvent event) {
        ItemDef clicked = def.defForSlot(slot);
        if (clicked == null) return;
        switch (clicked.function()) {
            case "nav-class"      -> plugin.gui().openClass(player);
            case "nav-attributes" -> plugin.gui().openAttributes(player);
            case "nav-skills"     -> plugin.gui().openSkills(player);
            case "vip"            -> plugin.gui().openVipCosmetics(player);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return Material.PAPER; }
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}

