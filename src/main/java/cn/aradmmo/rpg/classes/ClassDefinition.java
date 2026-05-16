package cn.aradmmo.rpg.classes;

import java.util.Map;

/**
 * Simple concrete implementation of AradClass.
 * Used internally by ClassRegistry when loading class folder configs.
 */
public final class ClassDefinition extends AradClass {

    private final String id;
    private final String display;
    private final String gender;
    private final int stage;
    private final int requiresLevel;
    private final String parent;
    private final String combatStyle;
    private final String armorMastery;
    private final Map<String, Integer> baseAttributes;
    private final Map<String, Integer> baseElementAttack;
    private final Map<String, Integer> baseElementResist;
    private final String defaultSkin;
    private final boolean external;

    ClassDefinition(
            String id,
            String display,
            String gender,
            int stage,
            int requiresLevel,
            String parent,
            String combatStyle,
            String armorMastery,
            Map<String, Integer> baseAttributes,
            Map<String, Integer> baseElementAttack,
            Map<String, Integer> baseElementResist,
            String defaultSkin,
            boolean external) {
        this.id = id;
        this.display = display;
        this.gender = gender;
        this.stage = stage;
        this.requiresLevel = requiresLevel;
        this.parent = parent;
        this.combatStyle = combatStyle;
        this.armorMastery = armorMastery;
        this.baseAttributes = Map.copyOf(baseAttributes);
        this.baseElementAttack = Map.copyOf(baseElementAttack);
        this.baseElementResist = Map.copyOf(baseElementResist);
        this.defaultSkin = defaultSkin;
        this.external = external;
    }

    @Override public String id()                               { return id; }
    @Override public String display()                          { return display; }
    @Override public String gender()                           { return gender; }
    @Override public int stage()                               { return stage; }
    @Override public int requiresLevel()                       { return requiresLevel; }
    @Override public String parent()                           { return parent; }
    @Override public String combatStyle()                      { return combatStyle; }
    @Override public String armorMastery()                     { return armorMastery; }
    @Override public Map<String, Integer> baseAttributes()     { return baseAttributes; }
    @Override public Map<String, Integer> baseElementAttack()  { return baseElementAttack; }
    @Override public Map<String, Integer> baseElementResist()  { return baseElementResist; }
    @Override public String defaultSkin()                      { return defaultSkin; }
    @Override public boolean isExternal()                      { return external; }
}

