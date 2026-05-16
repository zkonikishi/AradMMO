package cn.aradmmo.rpg.profile;

import java.util.Arrays;
import java.util.Optional;

public enum AttributeType {
    STRENGTH,
    SPIRIT,
    INTELLECT,
    VITALITY;

    public String key() {
        return name().toLowerCase();
    }

    public static Optional<AttributeType> from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
