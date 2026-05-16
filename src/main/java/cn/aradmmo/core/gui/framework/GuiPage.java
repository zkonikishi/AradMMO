package cn.aradmmo.core.gui.framework;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.text.TextColorService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Abstract base for all YAML-driven GUI pages.
 *
 * <p>Subclasses implement {@link #buildItem} to provide an {@link ItemStack} for each
 * YAML-defined {@link ItemDef}, and {@link #handleClick} to react to player clicks.
 * Override {@link #buildDynamic} to insert additional items after the static YAML items
 * have been rendered.
 */
public abstract class GuiPage {
    protected final AradMmoPlugin plugin;
    protected final Player player;
    protected final GuiDef def;

    protected GuiPage(AradMmoPlugin plugin, Player player, GuiDef def) {
        this.plugin = plugin;
        this.player = player;
        this.def    = def;
    }

    /**
     * Builds, populates, and opens this GUI inventory for the player.
     * Renders all YAML-defined items first, then calls {@link #buildDynamic}.
     */
    public final void open() {
        String titleStr = resolveTitlePlaceholders(def.title());
        Inventory inv = Bukkit.createInventory(null, def.rows() * 9, TextColorService.component(titleStr));

        for (ItemDef itemDef : def.itemDefs()) {
            ItemStack stack = buildItem(itemDef);
            if (stack == null) continue;
            for (int slot : itemDef.slots()) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
            }
        }

        buildDynamic(inv);
        player.openInventory(inv);
    }

    // ─── Abstract ────────────────────────────────────────────────────────────

    /**
     * Returns the {@link ItemStack} to place for a YAML-defined item.
     * Return {@code null} to skip (slot handled by {@link #buildDynamic} or left empty).
     */
    protected abstract ItemStack buildItem(ItemDef itemDef);

    /** Handle a raw slot click from an {@link InventoryClickEvent}. */
    public abstract void handleClick(int slot, InventoryClickEvent event);

    // ─── Overrideable hooks ──────────────────────────────────────────────────

    /**
     * Called after all YAML items have been placed.
     * Override to add dynamic per-row items (e.g. per-attribute or per-skill rows).
     */
    protected void buildDynamic(Inventory inv) {}

    /**
     * Apply per-page placeholder substitutions to the GUI title string.
     * Default implementation returns the string unchanged.
     */
    protected String resolveTitlePlaceholders(String title) { return title; }

    // ─── Utilities ───────────────────────────────────────────────────────────

    /** Build a simple item with MiniMessage name and lore list. */
    protected ItemStack buildSimple(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(TextColorService.component(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(TextColorService::component).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Build an item from an {@link ItemDef}, applying optional {@code %key%→value} placeholder pairs.
     * Returns {@code null} if the def has no {@code item} material field.
     */
    protected ItemStack buildFromDef(ItemDef itemDef, Object... placeholders) {
        if (itemDef.item() == null) return null;
        Material mat;
        try { mat = Material.valueOf(itemDef.item().toUpperCase()); }
        catch (IllegalArgumentException e) { mat = Material.PAPER; }

        String       name = itemDef.name();
        List<String> lore = itemDef.lore();
        if (placeholders.length >= 2) {
            name = ph(name, placeholders);
            lore = ph(lore, placeholders);
        }
        return buildSimple(mat, name, lore);
    }

    /** Apply {@code %key%→value} substitutions to a string. */
    protected String ph(String s, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2)
            s = s.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
        return s;
    }

    /** Apply {@code %key%→value} substitutions to every element of a list. */
    protected List<String> ph(List<String> lines, Object... pairs) {
        return lines.stream().map(l -> ph(l, pairs)).toList();
    }
}

