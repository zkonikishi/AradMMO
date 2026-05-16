package cn.aradmmo.core.gui.framework;

import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable definition of one item entry loaded from a GUI YAML {@code items} section.
 */
public final class ItemDef {
    private final String key;
    private final List<Integer> slots;
    private final String function;
    private final String item;
    private final String name;
    private final List<String> lore;
    private final ConfigurationSection raw;

    public ItemDef(String key, ConfigurationSection section) {
        this.key      = key;
        this.slots    = Collections.unmodifiableList(section.getIntegerList("slots"));
        this.function = section.getString("function", key);
        this.item     = section.getString("item");
        this.name     = section.getString("name", " ");
        this.lore     = Collections.unmodifiableList(section.getStringList("lore"));
        this.raw      = section;
    }

    /** YAML key for this item. */
    public String key()               { return key; }

    /** Slot indices this item occupies. */
    public List<Integer> slots()      { return slots; }

    /** Logical function identifier; defaults to the YAML key if not specified. */
    public String function()          { return function; }

    /** Material name from config may be {@code null} for fully dynamic items. */
    public String item()              { return item; }

    /** MiniMessage display name. */
    public String name()              { return name; }

    /** MiniMessage lore lines. */
    public List<String> lore()        { return lore; }

    /** Raw YAML section for reading plugin-specific extra keys. */
    public ConfigurationSection raw() { return raw; }
}

