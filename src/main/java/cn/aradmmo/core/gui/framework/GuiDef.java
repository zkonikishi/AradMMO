package cn.aradmmo.core.gui.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable definition of a full GUI page loaded from a YAML file.
 *
 * <pre>
 * title: '&lt;gold&gt;My GUI'
 * rows: 6
 * items:
 *   filler:
 *     slots: [0, 1, 2]
 *     item: GRAY_STAINED_GLASS_PANE
 *     name: ' '
 *   my-btn:
 *     slots: [22]
 *     function: do-something
 *     item: EMERALD
 *     name: '&lt;green&gt;Click me'
 * </pre>
 */
public final class GuiDef {
    private final String title;
    private final int rows;
    private final List<ItemDef> itemDefs;
    private final Map<Integer, ItemDef> slotIndex;
    private final Map<String, ItemDef>  functionIndex;
    private final ConfigurationSection  raw;

    public GuiDef(ConfigurationSection section) {
        this.raw   = section;
        this.title = section.getString("title", "<white>Menu");
        this.rows  = section.getInt("rows", 6);

        List<ItemDef>         defs   = new ArrayList<>();
        Map<Integer, ItemDef> bySlot = new HashMap<>();
        Map<String,  ItemDef> byFunc = new HashMap<>();

        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection cs = items.getConfigurationSection(key);
                if (cs == null) continue;
                ItemDef def = new ItemDef(key, cs);
                defs.add(def);
                for (int slot : def.slots()) bySlot.put(slot, def);
                byFunc.putIfAbsent(def.function(), def);
            }
        }

        this.itemDefs      = Collections.unmodifiableList(defs);
        this.slotIndex     = Collections.unmodifiableMap(bySlot);
        this.functionIndex = Collections.unmodifiableMap(byFunc);
    }

    /** MiniMessage title string (may contain {@code %placeholder%} tokens). */
    public String title()               { return title; }

    /** Number of rows; inventory size = {@code rows × 9}. */
    public int rows()                   { return rows; }

    /** All item definitions in YAML declaration order. */
    public List<ItemDef> itemDefs()     { return itemDefs; }

    /** Returns the {@link ItemDef} whose slot list contains {@code slot}, or {@code null}. */
    public ItemDef defForSlot(int slot) { return slotIndex.get(slot); }

    /** Returns the first {@link ItemDef} whose function equals {@code function}, or {@code null}. */
    public ItemDef defForFunction(String function) { return functionIndex.get(function); }

    /** Raw root section for reading plugin-specific extra keys (e.g. {@code dynamic.*}). */
    public ConfigurationSection raw()   { return raw; }
}

