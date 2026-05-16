package cn.aradmmo.core.chat;

public enum ChatClickType {
    NONE,
    COMMAND,
    SUGGEST,
    OPENURL;

    public static ChatClickType from(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        try {
            return ChatClickType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
