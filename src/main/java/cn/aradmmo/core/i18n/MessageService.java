package cn.aradmmo.core.i18n;

import cn.aradmmo.core.AradMmoPlugin;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class MessageService {
    private static final String DEFAULT_LOCALE = "zh_cn";

    private final AradMmoPlugin plugin;
    private final MiniMessage miniMessage;
    private final Map<String, YamlConfiguration> bundles;

    public MessageService(AradMmoPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.bundles = new HashMap<>();
    }

    public void reload() {
        bundles.clear();
        for (String locale : plugin.availableConfigLocales()) {
            loadBundle(locale);
        }
    }

    public void send(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(component(sender, path, replacements));
    }

    public Component component(CommandSender sender, String path, String... replacements) {
        String locale = resolveLocale(sender);
        String message = lookup(locale, path);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return miniMessage.deserialize(message);
    }

    /** Returns the resolved MiniMessage string for {@code path} without parsing it into a Component. */
    public String raw(CommandSender sender, String path, String... replacements) {
        String locale = resolveLocale(sender);
        String message = lookup(locale, path);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    public String resolveLocale(CommandSender sender) {
        String configured = normalizeLocale(plugin.getConfig().getString("default-locale", DEFAULT_LOCALE));
        if (!(sender instanceof Player player)) {
            return configured;
        }

        if (!plugin.getConfig().getBoolean("auto-player-locale", true)) {
            return configured;
        }

        String normalized = normalizeLocale(player.locale().toString());
        if (bundles.containsKey(normalized)) {
            return normalized;
        }

        String underscored = normalizeLocale(normalized.replace('-', '_'));
        if (bundles.containsKey(underscored)) {
            return underscored;
        }

        return configured;
    }

    private void loadBundle(String locale) {
        File langFile = plugin.localizedConfigFile(locale, "lang.yml");
        if (!langFile.exists()) {
            return;
        }
        bundles.put(normalizeLocale(locale), YamlConfiguration.loadConfiguration(langFile));
    }

    private String lookup(String locale, String path) {
        YamlConfiguration primary = bundles.get(locale);
        if (primary != null && primary.contains(path)) {
            return Objects.requireNonNull(primary.getString(path));
        }

        YamlConfiguration fallback = bundles.get(DEFAULT_LOCALE);
        if (fallback != null && fallback.contains(path)) {
            return Objects.requireNonNull(fallback.getString(path));
        }

        return "<red>Missing message: " + path + "</red>";
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return locale.toLowerCase(Locale.ROOT);
    }
}
