package cn.aradmmo.rpg.profile;

/**
 * The four elements used in the Arad combat system.
 *
 * <ul>
 *   <li>{@link #FIRE}  火焰</li>
 *   <li>{@link #ICE}   冰霜</li>
 *   <li>{@link #LIGHT} 光明</li>
 *   <li>{@link #DARK}  暗黑</li>
 * </ul>
 *
 * Each player profile stores two values per element:
 * <ol>
 *   <li><b>Element Attack</b> (属性强 bonus damage % when dealing this element.</li>
 *   <li><b>Element Resist</b> (属性抵 damage reduction % when receiving this element.</li>
 * </ol>
 *
 * Net element effect on a hit = attacker's attack - defender's resist (clamped by config).
 */
public enum ElementType {
    FIRE,
    ICE,
    LIGHT,
    DARK;

    /** Lower-case config / YAML key (e.g. "fire", "ice"). */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Looks up an ElementType by key (case-insensitive), or {@code null} if not found. */
    public static ElementType from(String s) {
        if (s == null) return null;
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        for (ElementType t : values()) {
            if (t.key().equals(lower)) return t;
        }
        return null;
    }
}

