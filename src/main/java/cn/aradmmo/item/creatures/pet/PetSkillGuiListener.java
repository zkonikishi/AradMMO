package cn.aradmmo.item.creatures.pet;

import cn.aradmmo.core.AradMmoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles click actions in pet skill GUI.
 */
public final class PetSkillGuiListener implements Listener {

    private final AradMmoPlugin plugin;

    public PetSkillGuiListener(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetSkillGui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;

        if (rawSlot == PetSkillGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (rawSlot == PetSkillGui.SLOT_REFRESH) {
            gui.refresh();
            return;
        }
        if (rawSlot == PetSkillGui.SLOT_MODE) {
            PetBehaviorMode mode = plugin.equipment().pets().cycleBehaviorMode(player);
            plugin.messages().send(player, "command.pet.mode-done", "%value%", mode.name());
            gui.refresh();
            return;
        }

        String skillId = gui.skillIdAt(rawSlot);
        if (skillId == null) return;

        boolean learned = plugin.equipment().pets().learnSkill(player, skillId);
        if (learned) {
            plugin.messages().send(player, "command.pet.learn-done", "%value%", skillId);
        } else {
            plugin.messages().send(player, "error.pet-learn-failed", "%value%", skillId);
        }
        gui.refresh();
    }
}
