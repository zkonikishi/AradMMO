package cn.aradmmo.rpg.integration;

import org.bukkit.entity.Player;

/**
 * Bridge for MMOCore-like RPG integrations.
 */
public interface RpgPluginBridge {

    String id();

    boolean isAvailable();

    int getLevel(Player player);

    String getArchetype(Player player);
}

