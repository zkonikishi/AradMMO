package cn.aradmmo.core.text;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

/**
 * RGB and gradient text utility for Arad MMO.
 */
public final class TextColorService {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern SIMPLE_GRADIENT_PATTERN = Pattern.compile("<g:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</g>", Pattern.DOTALL);
    private static final Random RANDOM = new Random();

    private TextColorService() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String normalized = raw;

        Matcher m = HEX_PATTERN.matcher(normalized);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "<#" + m.group(1).toLowerCase(Locale.ROOT) + ">");
        }
        m.appendTail(sb);
        normalized = sb.toString();

        Matcher g = SIMPLE_GRADIENT_PATTERN.matcher(normalized);
        StringBuffer gb = new StringBuffer();
        while (g.find()) {
            String replacement = "<gradient:" + g.group(1) + ":" + g.group(2) + ">" + g.group(3) + "</gradient>";
            g.appendReplacement(gb, Matcher.quoteReplacement(replacement));
        }
        g.appendTail(gb);
        return gb.toString();
    }

    public static Component component(String raw) {
        if (raw == null || raw.isBlank()) return Component.empty();
        String normalized = normalize(raw);
        if (normalized.contains("<") && normalized.contains(">")) {
            try {
                return MM.deserialize(normalized);
            } catch (Throwable ignored) {
            }
        }
        String legacy = ChatColor.translateAlternateColorCodes('&', normalized);
        return LEGACY.deserialize(legacy);
    }

    public static Component gradientText(String text, String gradientSpec) {
        if (text == null || text.isEmpty()) return Component.empty();
        String[] colors = parseGradientSpec(gradientSpec);
        if (colors == null) return Component.text(text);

        TextColor start = TextColor.fromHexString(colors[0]);
        TextColor end = TextColor.fromHexString(colors[1]);
        if (start == null || end == null) return Component.text(text);

        char[] chars = text.toCharArray();
        int len = chars.length;
        if (len == 1) return Component.text(String.valueOf(chars[0])).color(start);

        TextComponent.Builder out = Component.text();
        for (int i = 0; i < len; i++) {
            float ratio = (float) i / (len - 1);
            int r = lerp(start.red(), end.red(), ratio);
            int g = lerp(start.green(), end.green(), ratio);
            int b = lerp(start.blue(), end.blue(), ratio);
            out.append(Component.text(String.valueOf(chars[i])).color(TextColor.color(r, g, b)));
        }
        return out.build();
    }

    public static boolean isValidGradientSpec(String spec) {
        return parseGradientSpec(spec) != null;
    }

    public static String randomGradientSpec() {
        int h1 = RANDOM.nextInt(360);
        int h2 = (h1 + 90 + RANDOM.nextInt(180)) % 360;
        int[] c1 = hslToRgb(h1 / 360f, 0.85f, 0.58f);
        int[] c2 = hslToRgb(h2 / 360f, 0.85f, 0.58f);
        return rgbHex(c1[0], c1[1], c1[2]) + ":" + rgbHex(c2[0], c2[1], c2[2]);
    }

    public static String[] parseGradientSpec(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String[] arr = spec.trim().split(":");
        if (arr.length != 2) return null;
        String c1 = withSharp(arr[0]);
        String c2 = withSharp(arr[1]);
        if (!c1.matches("#[A-Fa-f0-9]{6}") || !c2.matches("#[A-Fa-f0-9]{6}")) return null;
        return new String[]{c1.toLowerCase(Locale.ROOT), c2.toLowerCase(Locale.ROOT)};
    }

    private static String withSharp(String s) {
        return s.startsWith("#") ? s : "#" + s;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int[] hslToRgb(float h, float s, float l) {
        float r, g, b;

        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h + 1f / 3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f / 3f);
        }

        return new int[]{Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)};
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private static String rgbHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
