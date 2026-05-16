package cn.aradmmo.dungeon.integration;

import org.bukkit.entity.Player;

/**
 * Bridge for mainstream dungeon plugins (MythicDungeons, DeluxeDungeons, etc.).
 */
public interface DungeonPluginBridge {

    String id();

    boolean isAvailable();

    boolean joinDungeon(Player player, String dungeonId);

    boolean leaveDungeon(Player player);
}

