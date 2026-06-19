package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class StyleTagsTest {

    private final MiniMessage mini = MiniMessage.miniMessage();

    private Component parse(String src) {
        return mini.deserialize(src, StyleTags.resolver());
    }

    private TextColor firstColor(Component root) {
        Deque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component node = queue.removeFirst();
            TextColor color = node.color();
            if (color != null) {
                return color;
            }
            queue.addAll(node.children());
        }
        throw new AssertionError("no coloured node found in " + root);
    }

    private boolean anyBold(Component root) {
        if (root.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
            return true;
        }
        return root.children().stream().anyMatch(this::anyBold);
    }

    @Test
    void accentAppliesCyan() {
        assertThat(firstColor(parse("<accent>hello</accent>"))).isEqualTo(StyleTags.ACCENT);
    }

    @Test
    void badAppliesRed() {
        assertThat(firstColor(parse("<bad>nope</bad>"))).isEqualTo(StyleTags.BAD);
    }

    @Test
    void tagRendersBracketedBoldGradientLabel() {
        Component c = parse("<tag:'HOME'> hi");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertThat(plain).startsWith("「 HOME 」");
        // the gradient label is rendered bold
        assertThat(anyBold(c)).isTrue();
    }

    @Test
    void headerRendersGradientText() {
        Component c = parse("<h:'HOME PANEL'>");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("HOME PANEL");
    }

    @Test
    void valueIsAccentAlias() {
        assertThat(firstColor(parse("<value>x</value>"))).isEqualTo(StyleTags.ACCENT);
    }
}
