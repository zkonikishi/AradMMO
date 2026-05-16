package cn.aradmmo.item.creatures.pet.integration;

import org.bukkit.entity.Player;

/**
 * Bridge for mainstream pet plugins (MyPet, MythicMobs pet usage, etc.).
 */
public interface PetPluginBridge {

    String id();

    boolean isAvailable();

    boolean summon(Player player, String petId);

    boolean dismiss(Player player);
}

