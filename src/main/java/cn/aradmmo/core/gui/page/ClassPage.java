package cn.aradmmo.core.gui.page;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.classes.ClassDefinition;
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

public final class ClassPage extends GuiPage {

    /** Set by {@link #resolveTitlePlaceholders} before {@link #buildDynamic} runs. */
    private String currentState = "gender-select";

    public ClassPage(AradMmoPlugin plugin, Player player, GuiDef def) {
        super(plugin, player, def);
    }

    @Override
    protected String resolveTitlePlaceholders(String ignored) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int stage = plugin.profiles().classStage(profile.archetype());

        if (profile.gender().isEmpty()) {
            currentState = "gender-select";
        } else if (stage == 0 && profile.level() < 10) {
            currentState = "locked-0";
        } else if (stage == 0) {
            currentState = "class-select-0";
        } else if (stage == 1 && profile.level() < 20) {
            currentState = "locked-1";
        } else if (stage == 1) {
            currentState = "class-select-1";
        } else {
            currentState = "maxed";
        }

        String title = def.raw().getString("states." + currentState);
        return title != null ? title : "<white>职业";
    }

    @Override
    protected ItemStack buildItem(ItemDef itemDef) {
        // Gender items are handled entirely in buildDynamic; all other defs render from YAML
        return switch (itemDef.function()) {
            case "gender-male", "gender-female" -> null;
            default -> buildFromDef(itemDef);
        };
    }

    @Override
    protected void buildDynamic(Inventory inv) {
        PlayerProfile profile = plugin.profiles().profile(player);

        switch (currentState) {
            case "gender-select"  -> buildGenderSelect(inv);
            case "locked-0"       -> inv.setItem(22, buildSimple(Material.BARRIER,
                    plugin.messages().raw(player, "gui.class.locked-0-title"),
                    List.of(plugin.messages().raw(player, "gui.class.current-level",
                                    "%value%", String.valueOf(profile.level())),
                            plugin.messages().raw(player, "gui.class.locked-0-hint"))));
            case "class-select-0",
                 "class-select-1" -> buildClassCards(inv, profile);
            case "locked-1"       -> inv.setItem(22, buildSimple(Material.DIAMOND_SWORD,
                    "<green>" + plugin.profiles().classDisplay(profile.archetype()),
                    List.of(plugin.messages().raw(player, "gui.class.current-level",
                                    "%value%", String.valueOf(profile.level())),
                            plugin.messages().raw(player, "gui.class.locked-1-hint"),
                            plugin.messages().raw(player, "gui.class.advance-reset"))));
            case "maxed"          -> inv.setItem(22, buildSimple(Material.NETHER_STAR,
                    "<aqua>" + plugin.profiles().classDisplay(profile.archetype()),
                    List.of(plugin.messages().raw(player, "gui.class.maxed-stage"),
                            plugin.messages().raw(player, "gui.class.maxed-note"))));
        }
    }

    private void buildGenderSelect(Inventory inv) {
        ItemDef male   = def.defForFunction("gender-male");
        ItemDef female = def.defForFunction("gender-female");
        if (male   != null) for (int s : male.slots())   inv.setItem(s, buildFromDef(male));
        if (female != null) for (int s : female.slots()) inv.setItem(s, buildFromDef(female));
    }

    private void buildClassCards(Inventory inv, PlayerProfile profile) {
        List<Integer> cardSlots = def.raw().getIntegerList("card-slots");
        if (cardSlots.isEmpty()) cardSlots = List.of(10, 12, 14, 16, 19, 21, 23, 25, 28, 30);

        List<String> classes = plugin.profiles().availableClassesFor(profile);
        int stage = plugin.profiles().classStage(profile.archetype());

        for (int i = 0; i < classes.size() && i < cardSlots.size(); i++) {
            String id  = classes.get(i);
            ClassDefinition cls = plugin.classes().get(id);
            String gt  = cls != null ? cls.gender() : "any";
            Material icon = stage == 0
                    ? (gt.equals("male") ? Material.IRON_SWORD : Material.GOLDEN_SWORD)
                    : Material.DIAMOND;
            String attrLine  = cls != null ? formatBaseAttr(cls) : "";
            String actionHint = stage == 0 ? plugin.messages().raw(player, "gui.class.select-hint")
                                           : plugin.messages().raw(player, "gui.class.advance-hint");
            inv.setItem(cardSlots.get(i), buildSimple(icon,
                    (stage == 0 ? "<yellow>" : "<gold>") + plugin.profiles().classDisplay(id),
                    List.of(attrLine, actionHint)));
        }
    }

    @Override
    public void handleClick(int slot, InventoryClickEvent event) {
        PlayerProfile profile = plugin.profiles().profile(player);
        int stage = plugin.profiles().classStage(profile.archetype());

        // Back button
        ItemDef clicked = def.defForSlot(slot);
        if (clicked != null && "back".equals(clicked.function())) {
            plugin.gui().openProfile(player);
            return;
        }

        // Gender selection
        if (profile.gender().isEmpty()) {
            if (clicked == null) return;
            String gender = switch (clicked.function()) {
                case "gender-male"   -> "male";
                case "gender-female" -> "female";
                default -> null;
            };
            if (gender != null) {
                plugin.profiles().setGender(player, gender);
                String display = plugin.messages().raw(player,
                        gender.equals("male") ? "gui.gender.male" : "gui.gender.female");
                plugin.messages().send(player, "command.gender.done", "%value%", display);
                plugin.gui().openClass(player);
            }
            return;
        }

        // Class card selection
        if (stage == 0 || stage == 1) {
            List<Integer> cardSlots = def.raw().getIntegerList("card-slots");
            if (cardSlots.isEmpty()) cardSlots = List.of(10, 12, 14, 16, 19, 21, 23, 25, 28, 30);
            List<String> classes = plugin.profiles().availableClassesFor(profile);
            for (int i = 0; i < cardSlots.size() && i < classes.size(); i++) {
                if (slot == cardSlots.get(i)) {
                    String classId = classes.get(i);
                    try {
                        plugin.profiles().advanceClass(player, classId);
                        plugin.messages().send(player, "command.class.done",
                                "%player%", player.getName(),
                                "%value%",  plugin.profiles().classDisplay(classId));
                    } catch (IllegalArgumentException ignored) {
                        plugin.messages().send(player, "error.invalid-class", "%value%", classId);
                    }
                    plugin.gui().openClass(player);
                    return;
                }
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String formatBaseAttr(ClassDefinition cls) {
        int str = cls.baseAttributes().getOrDefault("strength",  5);
        int spi = cls.baseAttributes().getOrDefault("spirit",    5);
        int itl = cls.baseAttributes().getOrDefault("intellect", 5);
        int vit = cls.baseAttributes().getOrDefault("vitality",  5);
        return plugin.messages().raw(player, "gui.class.attr-line",
                "%str%", String.valueOf(str),
                "%spi%", String.valueOf(spi),
                "%itl%", String.valueOf(itl),
                "%vit%", String.valueOf(vit));
    }
}

