package cn.aradmmo.item.creatures.pet;

import cn.aradmmo.core.AradMmoPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Pet skill learning GUI.
 */
public final class PetSkillGui implements InventoryHolder {

    public static final int SLOT_PET_INFO = 4;
    public static final int SLOT_MODE = 8;
    public static final int SLOT_POINTS = 22;
    public static final int SLOT_REFRESH = 18;
    public static final int SLOT_CLOSE = 26;

    private static final int[] SKILL_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};

    private final AradMmoPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, String> slotSkillMap = new HashMap<>();

    public PetSkillGui(AradMmoPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, "§6§lPet Skills");
        fillBackground();
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    public String skillIdAt(int slot) {
        return slotSkillMap.get(slot);
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void refresh() {
        slotSkillMap.clear();
        clearSkillSlots();

        PetService.ActivePetView view = plugin.equipment().pets().activePetView(player);
        if (view == null) {
            inventory.setItem(SLOT_PET_INFO, makeItem(Material.BARRIER, "§cNo active pet",
                    List.of("§7Equip a pet in pet slot first.")));
                inventory.setItem(SLOT_MODE, makeItem(Material.LEVER, "§6Behavior: §fFOLLOW",
                    List.of("§7No active pet", "§eClick to cycle mode")));
            inventory.setItem(SLOT_POINTS, makeItem(Material.EXPERIENCE_BOTTLE, "§eSkill Points: 0", List.of()));
            inventory.setItem(SLOT_REFRESH, makeItem(Material.CLOCK, "§bRefresh", List.of("§7Click to refresh")));
            inventory.setItem(SLOT_CLOSE, makeItem(Material.RED_DYE, "§cClose", List.of("§7Click to close")));
            return;
        }

        PetCompanionProfile profile = view.profile();
        inventory.setItem(SLOT_PET_INFO, makeItem(Material.NAME_TAG,
                "§6" + view.def().name(),
                List.of(
                        "§7Pet ID: §f" + view.petId(),
                        "§7Level: §a" + profile.level(),
                        "§7EXP: §b" + format(profile.experience()) + "§7/§b" + format(profile.expToNext())
                )));

        inventory.setItem(SLOT_POINTS, makeItem(Material.EXPERIENCE_BOTTLE,
                "§eSkill Points: " + profile.skillPoints(),
                List.of("§7Learn new skills by clicking unlearned entries.")));

        PetBehaviorMode mode = profile.behaviorMode();
        inventory.setItem(SLOT_MODE, makeItem(Material.LEVER,
            "§6Behavior: §f" + mode.name(),
            List.of(
                "§7FOLLOW: follow owner and auto combat by pet type",
                "§7STAY: stay in place and stop targeting",
                "§7COMBAT: aggressive combat mode",
                "§eClick to cycle"
            )));

        inventory.setItem(SLOT_REFRESH, makeItem(Material.CLOCK, "§bRefresh", List.of("§7Click to refresh")));
        inventory.setItem(SLOT_CLOSE, makeItem(Material.RED_DYE, "§cClose", List.of("§7Click to close")));

        int idx = 0;
        for (PetSkillDef skill : plugin.equipment().pets().skills(player)) {
            if (idx >= SKILL_SLOTS.length) break;
            int slot = SKILL_SLOTS[idx++];
            boolean learned = profile.learnedSkills().contains(skill.id());
            Material icon = iconFor(skill.type());
            String status = learned ? "§aLearned" : "§cUnlearned";
            String hint = learned ? "§7Already learned" : "§eClick to learn (1 point)";
            inventory.setItem(slot, makeItem(icon,
                    "§f" + skill.id() + " §8[" + skill.type().name() + "]",
                    List.of(
                            "§7Status: " + status,
                            "§7Cooldown: §b" + skill.cooldownTicks() + " ticks",
                            "§7Power: §b" + format(skill.power()),
                            "§7Display: §f" + skill.display(),
                            hint
                    )));
            slotSkillMap.put(slot, skill.id());
        }
    }

    private Material iconFor(PetSkillType type) {
        return switch (type) {
            case ACTIVE -> Material.IRON_SWORD;
            case PASSIVE -> Material.TOTEM_OF_UNDYING;
            case SUPPORT -> Material.CHEST;
        };
    }

    private void clearSkillSlots() {
        for (int slot : SKILL_SLOTS) {
            inventory.setItem(slot, filler());
        }
    }

    private void fillBackground() {
        ItemStack filler = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack makeItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private String format(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
