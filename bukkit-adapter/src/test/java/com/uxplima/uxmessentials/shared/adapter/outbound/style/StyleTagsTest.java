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

    private TextColor colorOfRun(Component root, String text) {
        Deque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component node = queue.removeFirst();
            if (node instanceof net.kyori.adventure.text.TextComponent textNode
                    && textNode.content().equals(text)) {
                return node.color();
            }
            queue.addAll(node.children());
        }
        throw new AssertionError("no run with text '" + text + "' found in " + root);
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
    void valueIsAccentAlias() {
        assertThat(firstColor(parse("<value>x</value>"))).isEqualTo(StyleTags.ACCENT);
    }

    @Test
    void badAppliesRed() {
        assertThat(firstColor(parse("<bad>nope</bad>"))).isEqualTo(StyleTags.BAD);
    }

    @Test
    void titleAppliesDarkGray() {
        assertThat(firstColor(parse("<title>Your Homes</title>"))).isEqualTo(StyleTags.TITLE);
    }

    @Test
    void tagRendersTheBrandPrefixWithASingleSpace() {
        Component c = parse("<tag:'HOME'> hi");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        // label ('HOME') is ignored — every prefix is the brand — and exactly one space precedes the body
        assertThat(plain).isEqualTo("uxmEssentials » hi");
        assertThat(colorOfRun(c, "uxmEssentials")).isEqualTo(StyleTags.HEADER);
        assertThat(anyBold(c)).isFalse();
    }

    @Test
    void etagRendersTheSameBrandPrefix() {
        Component c = parse("<etag:'ERROR'> oops");
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertThat(plain).isEqualTo("uxmEssentials » oops");
        assertThat(colorOfRun(c, "uxmEssentials")).isEqualTo(StyleTags.HEADER);
        assertThat(anyBold(c)).isFalse();
    }

    @Test
    void helpopAndStaffchatCarryDistinctLabelledPrefixes() {
        Component help = parse("<helpop> hi");
        Component staff = parse("<staffchat> hi");
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        // The two staff channels read apart from each other rather than both showing the brand prefix.
        assertThat(plain.serialize(help)).isEqualTo("HelpOP » hi");
        assertThat(plain.serialize(staff)).isEqualTo("StaffChat » hi");
        assertThat(colorOfRun(help, "HelpOP")).isEqualTo(StyleTags.HEADER);
        assertThat(colorOfRun(staff, "StaffChat")).isEqualTo(StyleTags.HEADER);
        assertThat(anyBold(help)).isFalse();
        assertThat(anyBold(staff)).isFalse();
    }

    @Test
    void headerRendersSolidBlueText() {
        Component c = parse("<h:'HOME PANEL'>");
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("HOME PANEL");
        assertThat(firstColor(c)).isEqualTo(StyleTags.HEADER);
        assertThat(anyBold(c)).isFalse();
    }
}
