package cn.aradmmo.eco.integration;

import java.math.BigDecimal;
import org.bukkit.entity.Player;

/**
 * Bridge for mainstream economy plugins (Vault, PlayerPoints, CoinsEngine, etc.).
 */
public interface EconomyPluginBridge {

    String id();

    boolean isAvailable();

    BigDecimal balance(Player player);

    boolean deposit(Player player, BigDecimal amount);

    boolean withdraw(Player player, BigDecimal amount);
}

