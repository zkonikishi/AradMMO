package cn.aradmmo.core.chat;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.core.text.TextColorService;
import cn.aradmmo.rpg.profile.PlayerProfile;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ChatService {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern ITEM_TOKEN_PATTERN = Pattern.compile("%([i0-9])");

    private final AradMmoPlugin plugin;
    private final Map<String, ChatFormatPart> parts = new HashMap<>();
    private final List<ChatRule> rules = new ArrayList<>();
    private final Set<UUID> chatDisabledPlayers = ConcurrentHashMap.newKeySet();
    private Method placeholderMethod;
    private boolean placeholderApiEnabled;

    private boolean enabled;
    private String separator;
    private boolean bridgeEnabled;
    private String bridgeTarget;
    private String bridgeGroup;
    private String bridgeChannel;
    private String bridgeServerName;
    private String bridgeIncomingFormat;
    private final Map<String, List<String>> bridgeGroups = new HashMap<>();
    private List<String> bridgeTargets = List.of();
    private String runtimeBridgeGroupOverride = "";

    public ChatService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.parts.clear();
        this.rules.clear();
        this.bridgeGroups.clear();
        this.placeholderMethod = null;
        this.placeholderApiEnabled = false;

        YamlConfiguration chatConfig = YamlConfiguration.loadConfiguration(
                plugin.localizedConfigFile("chat/chat.yml"));
        YamlConfiguration formatConfig = YamlConfiguration.loadConfiguration(
                plugin.localizedConfigFile("chat/chat-format.yml"));

        this.enabled = chatConfig.getBoolean("enabled", true);
        this.separator = chatConfig.getString("separator", "<dark_gray> » </dark_gray>");
        this.bridgeEnabled = chatConfig.getBoolean("bridge.enabled", false);
        this.bridgeTarget = chatConfig.getString("bridge.target", "ALL");
        this.bridgeGroup = chatConfig.getString("bridge.group", "").trim();
        this.bridgeChannel = normalizeBridgeChannel(chatConfig.getString("bridge.channel", "aradmmo:chat"));
        this.bridgeServerName = chatConfig.getString("bridge.server-name", "").trim();
        this.bridgeIncomingFormat = chatConfig.getString(
            "bridge.incoming-format",
            "<gray>[跨服][%server%]</gray> ");
        this.bridgeTargets = List.copyOf(chatConfig.getStringList("bridge.targets"));
        loadBridgeGroups(chatConfig.getConfigurationSection("bridge.groups"));
        detectPlaceholderApi();

        loadFormatParts(formatConfig);
        loadRules(chatConfig);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isChatEnabled(Player player) {
        return !chatDisabledPlayers.contains(player.getUniqueId());
    }

    public void setChatEnabled(Player player, boolean enabled) {
        if (enabled) {
            chatDisabledPlayers.remove(player.getUniqueId());
            return;
        }
        chatDisabledPlayers.add(player.getUniqueId());
    }

    public Component separator() {
        return renderText(separator);
    }

    public boolean bridgeEnabled() {
        return bridgeEnabled;
    }

    public String bridgeTarget() {
        return bridgeTarget == null || bridgeTarget.isBlank() ? "ALL" : bridgeTarget;
    }

    public String bridgeGroup() {
        return bridgeGroup == null ? "" : bridgeGroup;
    }

    public String activeBridgeGroup() {
        if (runtimeBridgeGroupOverride != null && !runtimeBridgeGroupOverride.isBlank()) {
            return runtimeBridgeGroupOverride;
        }
        return bridgeGroup();
    }

    public String bridgeChannel() {
        return bridgeChannel;
    }

    public String bridgeServerName() {
        return bridgeServerName == null ? "" : bridgeServerName;
    }

    public boolean setRuntimeBridgeGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            this.runtimeBridgeGroupOverride = "";
            return true;
        }

        String normalized = groupName.trim().toLowerCase(Locale.ROOT);
        if (!bridgeGroups.containsKey(normalized)) {
            return false;
        }

        this.runtimeBridgeGroupOverride = normalized;
        return true;
    }

    public List<String> availableBridgeGroups() {
        return bridgeGroups.keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> bridgeGroupTargets(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return List.of();
        }
        List<String> targets = bridgeGroups.get(groupName.trim().toLowerCase(Locale.ROOT));
        return targets == null ? List.of() : List.copyOf(targets);
    }

    public boolean isActiveBridgeGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return activeBridgeGroup().isBlank();
        }
        return groupName.trim().equalsIgnoreCase(activeBridgeGroup());
    }

    public boolean bridgeGroupContainsCurrentServer(String groupName) {
        String self = bridgeServerName();
        if (self.isBlank()) {
            return false;
        }
        return bridgeGroupTargets(groupName).stream().anyMatch(target -> target.equalsIgnoreCase(self));
    }

    public boolean saveActiveBridgeGroupToConfig() {
        String group = activeBridgeGroup();
        if (!group.isBlank() && !availableBridgeGroups().contains(group.toLowerCase(Locale.ROOT))) {
            return false;
        }

        var file = plugin.localizedConfigFile("chat/chat.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("bridge.group", group);
        try {
            config.save(file);
            this.bridgeGroup = group;
            this.runtimeBridgeGroupOverride = "";
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save chat bridge group: " + exception.getMessage());
            return false;
        }
    }

    public List<String> resolveBridgeTargets() {
        if (!activeBridgeGroup().isBlank()) {
            List<String> groupTargets = bridgeGroups.get(activeBridgeGroup().toLowerCase(Locale.ROOT));
            if (groupTargets != null && !groupTargets.isEmpty()) {
                return normalizeTargets(groupTargets);
            }
        }

        if (!bridgeTargets.isEmpty()) {
            return normalizeTargets(bridgeTargets);
        }

        String single = bridgeTarget();
        if (single.equalsIgnoreCase("ALL")) {
            return List.of("ALL");
        }
        return normalizeTargets(List.of(single));
    }

    public Component renderIncomingBridgePrefix(String originServer) {
        String server = originServer == null || originServer.isBlank() ? "unknown" : originServer;
        return renderText(bridgeIncomingFormat.replace("%server%", server));
    }

    public ChatRule selectRule(Player player) {
        for (ChatRule rule : rules) {
            if (player.hasPermission(rule.permission())) {
                return rule;
            }
        }
        return null;
    }

    public Component renderPrefix(Player player, PlayerProfile profile, ChatRule rule, String plainMessage) {
        if (rule == null) {
            return Component.empty();
        }
        ChatRenderContext context = new ChatRenderContext(player, profile, plainMessage);
        TextComponent.Builder out = Component.text();
        for (String segment : rule.segments()) {
            ChatFormatPart part = parts.get(segment);
            if (part != null) {
                out.append(part.render(this, context));
            } else {
                out.append(renderText(applyPlaceholders(context, segment)));
            }
        }
        return out.build();
    }

    public Component renderMessage(Player player, PlayerProfile profile, ChatRule rule, Component original) {
        String plain = PLAIN.serialize(original);
        Component message = applyColorPermission(player, plain, original);
        if (rule == null || !rule.item()) {
            return message;
        }
        return injectItemToken(player, plain, rule, message);
    }

    public Component composeChatLine(Component prefix, Component displayName, Component message) {
        return Component.empty()
                .append(prefix)
                .append(displayName)
                .append(separator())
                .append(message);
    }

    public Component renderText(String raw) {
        return TextColorService.component(raw);
    }

    public String applyPlaceholders(ChatRenderContext context, String value) {
        Player player = context.player();
        PlayerProfile profile = context.profile();
        String output = value == null ? "" : value;

        output = output.replace("%player_name%", player.getName());
        output = output.replace("%player_world%", player.getWorld().getName());
        output = output.replace("%world%", player.getWorld().getName());
        output = output.replace("%message%", context.message());
        output = output.replace("%level%", Integer.toString(profile.level()));
        output = output.replace("%balance%", format(profile.balance()));
        output = output.replace("%vip_tier%", profile.vipTier());
        output = output.replace("%x%", Integer.toString(player.getLocation().getBlockX()));
        output = output.replace("%y%", Integer.toString(player.getLocation().getBlockY()));
        output = output.replace("%z%", Integer.toString(player.getLocation().getBlockZ()));
        output = applyExternalPlaceholders(player, output);
        return output;
    }

    private void detectPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.placeholderMethod = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            this.placeholderApiEnabled = true;
            plugin.getLogger().info("Chat subsystem hooked PlaceholderAPI.");
        } catch (Throwable ex) {
            plugin.getLogger().warning("PlaceholderAPI detected but hook failed: " + ex.getMessage());
            this.placeholderApiEnabled = false;
            this.placeholderMethod = null;
        }
    }

    private String applyExternalPlaceholders(Player player, String value) {
        if (!placeholderApiEnabled || placeholderMethod == null || value == null || value.isEmpty()) {
            return value;
        }
        try {
            Object out = placeholderMethod.invoke(null, player, value);
            if (out instanceof String str) {
                return str;
            }
        } catch (Throwable ignored) {
        }
        return value;
    }

    private Component applyColorPermission(Player player, String plain, Component original) {
        boolean rgb = player.hasPermission("aradmmo.chat.rgb");
        boolean color = player.hasPermission("aradmmo.chat.color") || rgb;
        if (!color) {
            return original;
        }

        if (rgb) {
            return TextColorService.component(plain);
        }

        String legacy = ChatColor.translateAlternateColorCodes('&', plain);
        return LEGACY.deserialize(legacy);
    }

    private Component injectItemToken(Player player, String plain, ChatRule rule, Component fallback) {
        Matcher matcher = ITEM_TOKEN_PATTERN.matcher(plain);
        if (!matcher.find()) {
            return fallback;
        }

        matcher.reset();
        int cursor = 0;
        TextComponent.Builder out = Component.text();
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                String chunk = plain.substring(cursor, matcher.start());
                out.append(applyColorPermission(player, chunk, Component.text(chunk)));
            }

            ItemStack item = resolveItemToken(player, matcher.group(1).charAt(0));
            if (item == null || item.getType() == Material.AIR) {
                out.append(applyColorPermission(player, matcher.group(0), Component.text(matcher.group(0))));
            } else {
                String label = resolveItemLabel(item);
                String text = String.format(Locale.ROOT, rule.itemFormat(), label);
                out.append(renderText(text));
            }

            cursor = matcher.end();
        }

        if (cursor < plain.length()) {
            String suffix = plain.substring(cursor);
            out.append(applyColorPermission(player, suffix, Component.text(suffix)));
        }

        return out.build();
    }

    private ItemStack resolveItemToken(Player player, char token) {
        if (token == 'i') {
            return player.getInventory().getItemInMainHand();
        }

        if (Character.isDigit(token)) {
            int slot = token - '0';
            if (slot >= 0 && slot <= 8) {
                return player.getInventory().getItem(slot);
            }
        }
        return null;
    }

    private String resolveItemLabel(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return PLAIN.serialize(item.getItemMeta().displayName());
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] chunks = name.split(" ");
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(chunk.charAt(0))).append(chunk.substring(1));
        }
        return out.toString();
    }

    private void loadFormatParts(YamlConfiguration formatConfig) {
        for (String key : formatConfig.getKeys(false)) {
            ConfigurationSection section = formatConfig.getConfigurationSection(key);
            if (section == null) continue;

            String text = section.getString("text", "");
            List<String> tip = section.getStringList("tip");
            String clickTypeRaw = section.getString("click.type", "NONE");
            String clickValue = section.getString("click.command", "");
            parts.put(key, new ChatFormatPart(text, tip, ChatClickType.from(clickTypeRaw), clickValue));
        }
    }

    private void loadRules(YamlConfiguration chatConfig) {
        ConfigurationSection section = chatConfig.getConfigurationSection("rules");
        if (section == null) {
            rules.add(new ChatRule("default", 50, "aradmmo.chat.default", "[world][player]: ", 0, true,
                    "<gold>[<aqua>%s</aqua>]</gold>"));
            return;
        }

        for (String ruleId : section.getKeys(false)) {
            ConfigurationSection ruleSec = section.getConfigurationSection(ruleId);
            if (ruleSec == null) continue;

            int index = ruleSec.getInt("index", 50);
            String permission = ruleSec.getString("permission", "aradmmo.chat." + ruleId);
            String format = ruleSec.getString("format", "[world][player]: ");
            int range = ruleSec.getInt("range", 0);
            boolean item = ruleSec.getBoolean("item", true);
            String itemFormat = ruleSec.getString("itemformat", "<gold>[<aqua>%s</aqua>]</gold>");

            rules.add(new ChatRule(ruleId, index, permission, format, range, item, itemFormat));
        }

        rules.sort(Comparator.comparingInt(ChatRule::index));
    }

    private String format(double value) {
        if (Math.floor(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String normalizeBridgeChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "aradmmo:chat";
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private void loadBridgeGroups(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            List<String> values = section.getStringList(key);
            if (!values.isEmpty()) {
                bridgeGroups.put(key.toLowerCase(Locale.ROOT), List.copyOf(values));
            }
        }
    }

    private List<String> normalizeTargets(List<String> rawTargets) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        String self = bridgeServerName();
        for (String raw : rawTargets) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String value = raw.trim();
            if (!self.isBlank() && value.equalsIgnoreCase(self)) {
                continue;
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
}
