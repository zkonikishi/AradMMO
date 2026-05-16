package cn.aradmmo.core.skin;

import cn.aradmmo.core.AradMmoPlugin;
import cn.aradmmo.rpg.classes.ClassDefinition;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player skins for Arad class display.
 *
 * <p>Supports:
 * <ul>
 *   <li>Built-in skins: {@code steve} and {@code alex}</li>
 *   <li>Custom skin station API (LittleSkin / self-hosted)</li>
 *   <li>Per-class default skins configured in class YAML files</li>
 *   <li>Memory cache for fetched skin textures</li>
 * </ul>
 *
 * <p>Config keys in {@code skins.yml}:
 * <pre>
 * station:
 *   enabled: true
 *   url: "https://littleskin.cn"
 *   api-type: littleskin          # littleskin | yggdrasil
 * defaults:
 *   adventurer-male: steve
 *   adventurer-female: alex
 * </pre>
 */
public final class SkinService {

    // Mojang base64 texture values for default skins
    private static final String STEVE_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFhNGFmNzE4NzVjZDRiNjJjN2I4NDZjOWY4NGE5OTQ4MjZkMjU1YzcxNTliM2UyMzc4YmEwNGQyMTM4YjgwZjEifX19";
    private static final String ALEX_TEXTURE  =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzgzY2VlNWNhNmFmY2RiMTcxNTg1MjE4NzI0YjJlOGRkNjZlMTM3M2Y1YmY0MGI0ZWM5MjNiYTdiZjZjMGQiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==";

    private final AradMmoPlugin plugin;
    private boolean stationEnabled;
    private String stationUrl;
    private String apiType;
    private String defaultMaleSkin;
    private String defaultFemaleSkin;

    /** Cache: skin name base64 texture string */
    private final Map<String, String> textureCache = new ConcurrentHashMap<>();

    public SkinService(AradMmoPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Config loading ───────────────────────────────────────────────────────

    public void reload() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.skinsConfig();
        stationEnabled   = cfg.getBoolean("station.enabled", false);
        stationUrl       = cfg.getString("station.url", "https://littleskin.cn");
        apiType          = cfg.getString("station.api-type", "littleskin").toLowerCase();
        defaultMaleSkin  = cfg.getString("defaults.adventurer-male", "steve");
        defaultFemaleSkin= cfg.getString("defaults.adventurer-female", "alex");
        textureCache.clear();
        plugin.getLogger().info("[SkinService] Station enabled=" + stationEnabled + " url=" + stationUrl);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Returns the default skin name for a given gender. */
    public String defaultSkin(String gender) {
        return gender.equals("female") ? defaultFemaleSkin : defaultMaleSkin;
    }

    /**
     * Applies the player skin for the given skin name to a skull item asynchronously.
     * The callback is always called on the main thread.
     */
    public void applyToSkull(ItemStack skull, String skinName, Runnable onDone) {
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            resolveTexture(skinName).thenAccept(texture -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyTexture(meta, texture, skinName);
                    skull.setItemMeta(meta);
                    onDone.run();
                });
            });
        }
    }

    /**
     * Resolves the base64 texture string for a given skin value.
     *
     * <p>Supported formats for {@code skinValue}:
     * <ul>
     *   <li>{@code steve} / {@code alex} built-in default skins</li>
     *   <li>{@code base64:<value>} raw Mojang base64 texture JSON, used as-is</li>
     *   <li>{@code url:<https://...>} texture image URL, wrapped into a base64 JSON blob</li>
     *   <li>{@code <playerName>} look up on the configured skin station</li>
     * </ul>
     */
    public CompletableFuture<String> resolveTexture(String skinValue) {
        if (skinValue == null || skinValue.isBlank()) {
            return CompletableFuture.completedFuture(STEVE_TEXTURE);
        }

        // base64: prefix use value directly
        if (skinValue.startsWith("base64:")) {
            return CompletableFuture.completedFuture(skinValue.substring(7).strip());
        }

        // url: prefix wrap URL into base64 texture JSON
        if (skinValue.startsWith("url:")) {
            String textureUrl = skinValue.substring(4).strip();
            return CompletableFuture.completedFuture(wrapTextureUrl(textureUrl));
        }

        String lower = skinValue.toLowerCase();
        String cached = textureCache.get(lower);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return switch (lower) {
            case "steve" -> CompletableFuture.completedFuture(STEVE_TEXTURE);
            case "alex"  -> CompletableFuture.completedFuture(ALEX_TEXTURE);
            default      -> fetchFromStation(skinValue);
        };
    }

    /**
     * Wraps a texture image URL into the base64-encoded JSON string that
     * Minecraft's PlayerProfile "textures" property expects.
     *
     * <p>Resulting JSON: {@code {"textures":{"SKIN":{"url":"<url>"}}}}
     */
    public static String wrapTextureUrl(String textureUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        return java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── Skin station fetching ────────────────────────────────────────────────

    private CompletableFuture<String> fetchFromStation(String skinName) {
        if (!stationEnabled) {
            plugin.getLogger().warning("[SkinService] Skin station disabled, falling back to steve for: " + skinName);
            return CompletableFuture.completedFuture(STEVE_TEXTURE);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String texture = switch (apiType) {
                    case "yggdrasil" -> fetchYggdrasil(skinName);
                    default          -> fetchLittleSkin(skinName);
                };
                if (texture != null) {
                    textureCache.put(skinName.toLowerCase(), texture);
                    return texture;
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[SkinService] Failed to fetch skin '" + skinName + "' from station", ex);
            }
            return STEVE_TEXTURE;
        });
    }

    /**
     * Fetches a player skin texture from a LittleSkin-compatible API.
     * Endpoint: GET {url}/api/yggdrasil/sessionserver/session/minecraft/profile/{uuid}
     * First resolves name UUID via: GET {url}/api/yggdrasil/api/profiles/minecraft (POST)
     */
    private String fetchLittleSkin(String name) throws IOException {
        // Step 1: Resolve name to UUID
        String profileUrl = stationUrl + "/api/yggdrasil/api/profiles/minecraft";
        String body = "[\"" + name + "\"]";
        String profileJson = httpPost(profileUrl, body);
        String uuid = extractUuid(profileJson);
        if (uuid == null) {
            plugin.getLogger().warning("[SkinService] Player '" + name + "' not found on skin station.");
            return null;
        }

        // Step 2: Fetch texture with UUID
        String textureUrl = stationUrl + "/api/yggdrasil/sessionserver/session/minecraft/profile/" + uuid;
        String textureJson = httpGet(textureUrl);
        return extractTexture(textureJson);
    }

    /**
     * Fetches from a standard Yggdrasil auth server.
     * Falls back to LittleSkin format.
     */
    private String fetchYggdrasil(String name) throws IOException {
        return fetchLittleSkin(name);
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private String httpGet(String urlStr) throws IOException {
        var url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Accept", "application/json");
        return readBody(conn);
    }

    private String httpPost(String urlStr, String jsonBody) throws IOException {
        var url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        return readBody(conn);
    }

    private String readBody(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        var is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ── JSON parsing (minimal, avoids adding dependencies) ───────────────────

    /** Extracts first "id" field value from profile list JSON. */
    private String extractUuid(String json) {
        int idx = json.indexOf("\"id\"");
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 4) + 1;
        int end   = json.indexOf('"', start);
        return (start > 0 && end > start) ? json.substring(start, end) : null;
    }

    /** Extracts the "value" field from the properties array (base64 texture). */
    private String extractTexture(String json) {
        int idx = json.indexOf("\"value\"");
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 7) + 1;
        int end   = json.indexOf('"', start);
        return (start > 0 && end > start) ? json.substring(start, end) : null;
    }

    // ── Player skin application ──────────────────────────────────────────────

    /**
     * Resolves {@code skinName} and applies it to {@code player}'s profile,
     * then refreshes the player's appearance for all online players.
     * Safe to call from any thread.
     */
    public void applyToPlayer(Player player, String skinName) {
        resolveTexture(skinName).thenAccept(texture -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerProfile profile = player.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty("textures", texture));
            player.setPlayerProfile(profile);
            // Refresh skin for other online players via hide/show cycle
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(plugin, player);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        online.showPlayer(plugin, player);
                    }
                }
            }, 5L);
        }));
    }

    /**
     * Looks up the player's current archetype and applies the class's default skin.
     * Falls back to the configured adventurer default if the class has no skin set.
     */
    public void applyClassSkin(Player player) {
        cn.aradmmo.rpg.profile.PlayerProfile profile = plugin.profiles().profile(player);
        String archetype = profile.archetype();
        ClassDefinition classDef = plugin.classes().get(archetype);
        String skinName = (classDef != null && classDef.defaultSkin() != null && !classDef.defaultSkin().isBlank())
                ? classDef.defaultSkin()
                : defaultSkin(classDef != null ? classDef.gender() : "male");
        applyToPlayer(player, skinName);
    }

    // ── Skull helper ─────────────────────────────────────────────────────────

    private void applyTexture(SkullMeta meta, String base64Texture, String skinName) {
        UUID fakeUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + skinName).getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(fakeUuid, skinName);
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
    }
}

