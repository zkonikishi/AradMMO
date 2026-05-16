package cn.aradmmo.rpg.classes;

import java.util.Map;

/**
 * Base class for all Arad class definitions.
 * Each class defines attributes, combat style, progression stage, and skin info.
 */
public abstract class AradClass {

    /**
     * Unique identifier (e.g., "slayer", "gunner-m", "adventurer").
     */
    public abstract String id();

    /**
     * Display name in game (e.g., "鬼剑, "Slayer").
     */
    public abstract String display();

    /**
     * Gender requirement: "male", "female", or "any".
     */
    public abstract String gender();

    /**
     * Class progression stage: 0 (adventurer), 1 (first job), 2 (second job).
     */
    public abstract int stage();

    /**
     * Minimum level required to select this class.
     */
    public abstract int requiresLevel();

    /**
     * Parent class ID (prerequisite for selection).
     * Empty string ("") if this is a root class (like adventurer).
     */
    public abstract String parent();

    /**
     * Combat style (e.g., "melee", "ranged", "magic", "balanced").
     */
    public abstract String combatStyle();

    /**
     * Optional armor mastery declared by class config.
     * Supported values: CLOTH, LEATHER, LIGHT, HEAVY, PLATE.
     * Empty string means use armor-system mapping/fallback rules.
     */
    public String armorMastery() { return ""; }

    /**
     * Base attribute values: "strength", "spirit", "intellect", "vitality".
     */
    public abstract Map<String, Integer> baseAttributes();

    /**
     * Base element attack values per element key ("fire", "ice", "light", "dark").
     * Represents initial % bonus when dealing that element type.
     */
    public Map<String, Integer> baseElementAttack() { return Map.of(); }

    /**
     * Base element resistance values per element key.
     * Represents initial % reduction when receiving that element type.
     */
    public Map<String, Integer> baseElementResist() { return Map.of(); }

    /**
     * Default skin for this class. Can be overridden by player skin selection.
     * Examples: "steve", "alex", or custom skin name from skin station.
     */
    public abstract String defaultSkin();

    /**
     * Whether this is an external class (like 黑暗武士, 缔造.
     * External classes unlock separately, not through normal progression.
     */
    public abstract boolean isExternal();

    @Override
    public String toString() {
        return "AradClass{" +
                "id='" + id() + '\'' +
                ", display='" + display() + '\'' +
                ", stage=" + stage() +
                ", gender='" + gender() + '\'' +
                '}';
    }
}

