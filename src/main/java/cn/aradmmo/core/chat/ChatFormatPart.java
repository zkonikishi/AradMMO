package cn.aradmmo.core.chat;

import cn.aradmmo.core.text.TextColorService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public final class ChatFormatPart {
    private final String text;
    private final List<String> tip;
    private final ChatClickType clickType;
    private final String clickValue;

    public ChatFormatPart(String text, List<String> tip, ChatClickType clickType, String clickValue) {
        this.text = text == null ? "" : text;
        this.tip = tip == null ? List.of() : List.copyOf(tip);
        this.clickType = clickType == null ? ChatClickType.NONE : clickType;
        this.clickValue = clickValue == null ? "" : clickValue;
    }

    public Component render(ChatService service, ChatRenderContext context) {
        Component part = service.renderText(service.applyPlaceholders(context, text));

        if (!tip.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : tip) {
                lines.add(service.renderText(service.applyPlaceholders(context, line)));
            }
            Component hover = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
            part = part.hoverEvent(HoverEvent.showText(hover));
        }

        if (!clickValue.isBlank()) {
            String value = service.applyPlaceholders(context, clickValue);
            switch (clickType) {
                case COMMAND -> part = part.clickEvent(ClickEvent.runCommand(value));
                case SUGGEST -> part = part.clickEvent(ClickEvent.suggestCommand(value));
                case OPENURL -> part = part.clickEvent(ClickEvent.openUrl(value));
                default -> {
                }
            }
        }

        return part;
    }
}
