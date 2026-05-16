package cn.aradmmo.core.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatRule {
    private static final Pattern PART_PATTERN = Pattern.compile("\\[([^\\[\\]]+)]");

    private final String id;
    private final int index;
    private final String permission;
    private final String format;
    private final int range;
    private final boolean item;
    private final String itemFormat;
    private final List<String> segments;

    public ChatRule(String id, int index, String permission, String format, int range, boolean item, String itemFormat) {
        this.id = id;
        this.index = index;
        this.permission = permission;
        this.format = format;
        this.range = range;
        this.item = item;
        this.itemFormat = itemFormat;
        this.segments = splitSegments(format);
    }

    public String id() {
        return id;
    }

    public int index() {
        return index;
    }

    public String permission() {
        return permission;
    }

    public String format() {
        return format;
    }

    public int range() {
        return range;
    }

    public boolean item() {
        return item;
    }

    public String itemFormat() {
        return itemFormat;
    }

    public List<String> segments() {
        return Collections.unmodifiableList(segments);
    }

    private List<String> splitSegments(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();
        Matcher matcher = PART_PATTERN.matcher(source);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                out.add(source.substring(cursor, matcher.start()));
            }
            out.add(matcher.group(1));
            cursor = matcher.end();
        }
        if (cursor < source.length()) {
            out.add(source.substring(cursor));
        }
        return out;
    }
}
