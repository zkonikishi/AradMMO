package cn.aradmmo.core.gui;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class ClassGui {

    static final int[] CARD_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30};
    static final int SLOT_MALE   = 20;
    static final int SLOT_FEMALE = 24;

    private ClassGui() {}

    static Inventory build(AradMmoPlugin plugin, Player player) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int stage = plugin.profiles().classStage(profile.archetype());

        if (profile.gender().isEmpty()) {
            Inventory inv = blank(plugin, "<yellow><bold>\u9009\u62e9\u6027\u522b</bold></yellow>");
            inv.setItem(SLOT_MALE,   GuiService.item(Material.LIGHT_BLUE_WOOL, "<aqua><bold>\u2642 \u7537\u6027</bold></aqua>", "<gray>\u9009\u62e9\u7537\u6027\u89d2\u8272", "<dark_gray>\u70b9\u51fb\u786e\u8ba4"));
            inv.setItem(SLOT_FEMALE, GuiService.item(Material.PINK_WOOL, "<light_purple><bold>\u2640 \u5973\u6027</bold></light_purple>", "<gray>\u9009\u62e9\u5973\u6027\u89d2\u8272", "<dark_gray>\u70b9\u51fb\u786e\u8ba4"));
            inv.setItem(45, GuiService.item(Material.ARROW, "<gray>\u8fd4\u56de\u6863\u6848", "<dark_gray>\u70b9\u51fb\u8fd4\u56de"));
            return inv;
        }

        if (stage == 0 && profile.level() < 10) {
            Inventory inv = blank(plugin, "<gray>\u804c\u4e1a\u9009\u62e9 \u2014 10\u7ea7\u89e3\u9501</gray>");
            inv.setItem(22, GuiService.item(Material.BARRIER, "<red>\u5c1a\u672a\u89e3\u9501\u521d\u59cb\u804c\u4e1a", "<gray>\u5f53\u524d\u7b49\u7ea7: <white>" + profile.level(), "<yellow>\u8fbe\u523010\u7ea7\u540e\u53ef\u9009\u62e9\u521d\u59cb\u804c\u4e1a"));
            inv.setItem(45, GuiService.item(Material.ARROW, "<gray>\u8fd4\u56de\u6863\u6848", "<dark_gray>\u70b9\u51fb\u8fd4\u56de"));
            return inv;
        }

        if (stage == 0) {
            Inventory inv = blank(plugin, "<green><bold>\u9009\u62e9\u521d\u59cb\u804c\u4e1a</bold></green>");
            List<String> classes = plugin.profiles().availableClassesFor(profile);
            for (int i = 0; i < classes.size() && i < CARD_SLOTS.length; i++) {
                String id = classes.get(i);
                String gt = plugin.classes().get(id) != null ? plugin.classes().get(id).gender() : "any";
                Material icon = gt.equals("male") ? Material.IRON_SWORD : Material.GOLDEN_SWORD;
                inv.setItem(CARD_SLOTS[i], GuiService.item(icon, "<yellow>" + plugin.profiles().classDisplay(id), formatBaseAttr(plugin, id), "<dark_gray>\u70b9\u51fb\u9009\u62e9\u6b64\u804c\u4e1a"));
            }
            inv.setItem(45, GuiService.item(Material.ARROW, "<gray>\u8fd4\u56de\u6863\u6848", "<dark_gray>\u70b9\u51fb\u8fd4\u56de"));
            return inv;
        }

        if (stage == 1 && profile.level() < 20) {
            Inventory inv = blank(plugin, "<gray>\u804c\u4e1a\u664b\u5347 \u2014 20\u7ea7\u89e3\u9501</gray>");
            inv.setItem(22, GuiService.item(Material.DIAMOND_SWORD, "<green>" + plugin.profiles().classDisplay(profile.archetype()), "<gray>\u5f53\u524d\u7b49\u7ea7: <white>" + profile.level(), "<yellow>\u8fbe\u523020\u7ea7\u540e\u53ef\u8fdb\u884c\u4e00\u8f6c", "<dark_gray>\u4e00\u8f6c\u5c06\u91cd\u7f6e\u5c5e\u6027\u5206\u914d"));
            inv.setItem(45, GuiService.item(Material.ARROW, "<gray>\u8fd4\u56de\u6863\u6848", "<dark_gray>\u70b9\u51fb\u8fd4\u56de"));
            return inv;
        }

        if (stage == 1) {
            Inventory inv = blank(plugin, "<gold><bold>\u9009\u62e9\u8f6c\u804c</bold></gold>");
            List<String> adv = plugin.profiles().availableClassesFor(profile);
            for (int i = 0; i < adv.size() && i < CARD_SLOTS.length; i++) {
                String id = adv.get(i);
                inv.setItem(CARD_SLOTS[i], GuiService.item(Material.DIAMOND, "<gold>" + plugin.profiles().classDisplay(id), formatBaseAttr(plugin, id), "<dark_gray>\u70b9\u51fb\u8fdb\u884c\u4e00\u8f6c (\u91cd\u7f6e\u5c5e\u6027)"));
            }
            inv.setItem(45, GuiService.item(Material.ARROW, "<gray>\u8fd4\u56de\u6863\u6848", "<dark_gray>\u70b9\u51fb\u8fd4\u56de"));
            return inv;
        }

        Inventory inv = blank(plugin, "<aqua>\u804c\u4e1a\u4fe1\u606f</aqua>");
        inv.setItem(22, GuiService.item(Material.NETHER_STAR, "<aqua>" + plugin.profiles().classDisplay(profile.archetype()), "<gray>\u8f6c\u804c\u9636\u6bb5: <white>\u6700\u7ec8\u804c\u4e1a", "<dark_gray>\u5df2\u8fbe\u5230\u6700\u7ec8\u804c\u4e1a\uff0c\u65e0\u6cd5\u7ee7\u7eed\u8f6c\u804c"));
        inv.setItem(45, GuiService.item(Material.ARROW, "<gray>\u8fd4\u56de\u6863\u6848", "<dark_gray>\u70b9\u51fb\u8fd4\u56de"));
        return inv;
    }

    private static Inventory blank(AradMmoPlugin plugin, String title) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(title));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiService.filler());
        return inv;
    }

    private static String formatBaseAttr(AradMmoPlugin plugin, String id) {
        cn.aradmmo.rpg.classes.ClassDefinition cls = plugin.classes().get(id);
        int str = cls == null ? 5 : cls.baseAttributes().getOrDefault("strength",  5);
        int spi = cls == null ? 5 : cls.baseAttributes().getOrDefault("spirit",    5);
        int itl = cls == null ? 5 : cls.baseAttributes().getOrDefault("intellect", 5);
        int vit = cls == null ? 5 : cls.baseAttributes().getOrDefault("vitality",  5);
        return "<gray>力 " + str + "  精 " + spi + "  智 " + itl + "  体 " + vit;
    }
}

