package cn.aradmmo.core.gui.page;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.gui.framework.GuiDef;
import cn.aradmmo.core.gui.framework.GuiPage;
import cn.aradmmo.core.gui.framework.ItemDef;
import cn.aradmmo.core.text.TextColorService;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * VIP cosmetic color menu: random gradient choices for name and chat.
 */
public final class VipCosmeticsPage extends GuiPage {
    private final List<String> nameOptions = new ArrayList<>();
    private final List<String> chatOptions = new ArrayList<>();

    public VipCosmeticsPage(AradMmoPlugin plugin, Player player, GuiDef def) {
        super(plugin, player, def);
        generateOptions(nameOptions, def.raw().getInt("dynamic.name-options-count", 3));
        generateOptions(chatOptions, def.raw().getInt("dynamic.chat-options-count", 3));
    }

    @Override
    protected String resolveTitlePlaceholders(String title) {
        return title.replace("%player_name%", player.getName());
    }

    @Override
    protected ItemStack buildItem(ItemDef itemDef) {
        PlayerProfile profile = plugin.profiles().profile(player);
        return switch (itemDef.function()) {
            case "vip-current-name" -> buildFromDef(itemDef,
                    "value", profile.nameGradient().isBlank() ? "未设置" : profile.nameGradient());
            case "vip-current-chat" -> buildFromDef(itemDef,
                    "value", profile.chatGradient().isBlank() ? "未设置" : profile.chatGradient());
            default -> buildFromDef(itemDef);
        };
    }

    @Override
    protected void buildDynamic(Inventory inv) {
        PlayerProfile profile = plugin.profiles().profile(player);
        boolean allowName = plugin.cosmeticColors().canUseNameGradient(player, profile);
        boolean allowChat = plugin.cosmeticColors().canUseChatGradient(player, profile);

        List<Integer> nameSlots = def.raw().getIntegerList("dynamic.name-option-slots");
        List<Integer> chatSlots = def.raw().getIntegerList("dynamic.chat-option-slots");

        for (int i = 0; i < nameSlots.size() && i < nameOptions.size(); i++) {
            int slot = nameSlots.get(i);
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, gradientItem(allowName, nameOptions.get(i), "名字", player.getName()));
        }

        String chatPreviewText = def.raw().getString("dynamic.chat-preview-text", "这是一条渐变聊天文本");
        for (int i = 0; i < chatSlots.size() && i < chatOptions.size(); i++) {
            int slot = chatSlots.get(i);
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, gradientItem(allowChat, chatOptions.get(i), "聊天", chatPreviewText));
        }
    }

    @Override
    public void handleClick(int slot, InventoryClickEvent event) {
        ItemDef clicked = def.defForSlot(slot);
        if (clicked != null) {
            switch (clicked.function()) {
                case "back-profile" -> {
                    plugin.gui().openProfile(player);
                    return;
                }
                case "clear-name-gradient" -> {
                    if (!plugin.cosmeticColors().canUseNameGradient(player, plugin.profiles().profile(player))) {
                        player.sendMessage(Component.text("你没有名字渐变权限。"));
                        return;
                    }
                    plugin.profiles().setNameGradient(player, "");
                    plugin.cosmeticColors().applyNameStyle(player);
                    player.sendMessage(Component.text("已清除渐变名字。"));
                    plugin.gui().openVipCosmetics(player);
                    return;
                }
                case "clear-chat-gradient" -> {
                    if (!plugin.cosmeticColors().canUseChatGradient(player, plugin.profiles().profile(player))) {
                        player.sendMessage(Component.text("你没有聊天渐变权限。"));
                        return;
                    }
                    plugin.profiles().setChatGradient(player, "");
                    player.sendMessage(Component.text("已清除渐变聊天文本。"));
                    plugin.gui().openVipCosmetics(player);
                    return;
                }
            }
        }

        PlayerProfile profile = plugin.profiles().profile(player);
        boolean allowName = plugin.cosmeticColors().canUseNameGradient(player, profile);
        boolean allowChat = plugin.cosmeticColors().canUseChatGradient(player, profile);
        if (!allowName && !allowChat) {
            player.sendMessage(Component.text("该功能为 VIP 特权。"));
            return;
        }

        List<Integer> nameSlots = def.raw().getIntegerList("dynamic.name-option-slots");
        for (int i = 0; i < nameSlots.size() && i < nameOptions.size(); i++) {
            if (nameSlots.get(i) == slot) {
                if (!allowName) {
                    player.sendMessage(Component.text("你没有名字渐变权限。"));
                    return;
                }
                plugin.profiles().setNameGradient(player, nameOptions.get(i));
                plugin.cosmeticColors().applyNameStyle(player);
                player.sendMessage(Component.text("已应用渐变名字: " + nameOptions.get(i)));
                plugin.gui().openVipCosmetics(player);
                return;
            }
        }

        List<Integer> chatSlots = def.raw().getIntegerList("dynamic.chat-option-slots");
        for (int i = 0; i < chatSlots.size() && i < chatOptions.size(); i++) {
            if (chatSlots.get(i) == slot) {
                if (!allowChat) {
                    player.sendMessage(Component.text("你没有聊天渐变权限。"));
                    return;
                }
                plugin.profiles().setChatGradient(player, chatOptions.get(i));
                player.sendMessage(Component.text("已应用渐变聊天文本: " + chatOptions.get(i)));
                plugin.gui().openVipCosmetics(player);
                return;
            }
        }
    }

    private ItemStack gradientItem(boolean enabled, String spec, String category, String previewText) {
        ItemStack item = new ItemStack(enabled ? Material.NAME_TAG : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (!enabled) {
            meta.displayName(Component.text("VIP 专属功能"));
            meta.lore(List.of(Component.text("当前 VIP 等级不足，无法设置渐变")));
            item.setItemMeta(meta);
            return item;
        }

        Component preview = TextColorService.gradientText(previewText, spec);
        meta.displayName(TextColorService.gradientText(category + "渐变预览", spec));
        meta.lore(List.of(
                preview,
                Component.text("色值: " + spec),
                Component.text("点击应用")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void generateOptions(List<String> out, int count) {
        Set<String> seen = new HashSet<>();
        while (out.size() < Math.max(1, count)) {
            String spec = plugin.cosmeticColors().randomGradientSpec();
            if (seen.add(spec)) out.add(spec);
        }
    }
}
