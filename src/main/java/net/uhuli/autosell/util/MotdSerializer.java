package net.uhuli.autosell.util;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class MotdSerializer {

    private MotdSerializer() {}

    public static String toJson(Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(JsonOps.INSTANCE, component)
                .result()
                .map(Object::toString)
                .orElse("{}");
    }

    public static String toMiniMessage(Component component) {
        StringBuilder sb = new StringBuilder();
        component.visit((style, text) -> {
            appendSegment(sb, style, text);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static void appendSegment(StringBuilder sb, Style style, String text) {
        if (text.isEmpty()) return;

        Deque<String> closers = new ArrayDeque<>();

        TextColor color = style.getColor();
        if (color != null) {
            sb.append("<color:").append(color.serialize()).append('>');
            closers.push("</color>");
        }
        if (style.isBold()) {
            sb.append("<b>");
            closers.push("</b>");
        }
        if (style.isItalic()) {
            sb.append("<i>");
            closers.push("</i>");
        }
        if (style.isUnderlined()) {
            sb.append("<u>");
            closers.push("</u>");
        }
        if (style.isStrikethrough()) {
            sb.append("<st>");
            closers.push("</st>");
        }
        if (style.isObfuscated()) {
            sb.append("<obf>");
            closers.push("</obf>");
        }

        sb.append(escape(text));

        while (!closers.isEmpty()) {
            sb.append(closers.pop());
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
