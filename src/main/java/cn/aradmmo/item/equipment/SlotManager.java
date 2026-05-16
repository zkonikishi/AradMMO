package cn.aradmmo.item.equipment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 管理slots.yml 加载的所{@link SlotDef}
 * 支持按名称、槽ID 和类型查询
 */
public final class SlotManager {

    private final Map<String, SlotDef>  byName   = new HashMap<>();
    private final Map<Integer, SlotDef> bySlotId = new HashMap<>();
    private final List<SlotDef>         all      = new ArrayList<>();

    /** YAML 配置重新加载所有槽位定义*/
    public void load(YamlConfiguration config) {
        byName.clear();
        bySlotId.clear();
        all.clear();

        ConfigurationSection slots = config.getConfigurationSection("slots");
        if (slots == null) return;

        for (String key : slots.getKeys(false)) {
            ConfigurationSection sec = slots.getConfigurationSection(key);
            if (sec == null) continue;
            SlotDef def = SlotDef.load(key, sec);
            byName.put(key, def);
            bySlotId.put(def.slotId(), def);
            all.add(def);
        }
    }

    /** 按名称查找槽位定义，找不到返{@code null}*/
    public SlotDef getByName(String name) { return byName.get(name); }

    /** 按箱子槽ID (0-53) 查找槽位定义，找不到返回 {@code null}*/
    public SlotDef getBySlotId(int id)    { return bySlotId.get(id); }

    /** 返回所有槽位定义的不可变视图*/
    public List<SlotDef> getAll()         { return Collections.unmodifiableList(all); }

    /** 返回指定类型的所有槽位定义*/
    public List<SlotDef> getByType(SlotType type) {
        return all.stream().filter(s -> s.type() == type).toList();
    }

    /** 返回指定类型中第一个槽位定义，找不到返{@code null}*/
    public SlotDef getFirst(SlotType type) {
        return all.stream().filter(s -> s.type() == type).findFirst().orElse(null);
    }

    /** 返回与指定快捷栏下标绑定ACTIVE 槽位，找不到返回 {@code null}*/
    public SlotDef getActiveByQuickSlot(int quickSlot) {
        return all.stream()
                .filter(s -> s.type() == SlotType.ACTIVE && s.quickSlot() == quickSlot)
                .findFirst().orElse(null);
    }
}

