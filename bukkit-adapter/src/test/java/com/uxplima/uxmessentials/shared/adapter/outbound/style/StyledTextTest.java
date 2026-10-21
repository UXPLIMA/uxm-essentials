package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class StyledTextTest {

    @Test
    void rendersTokensFromCatalogString() {
        Component c = StyledText.render("<accent>Base</accent>");
        assertThat(c.color()).isEqualTo(StyleTags.accent());
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("Base");
    }

    @Test
    void rendersHeaderToken() {
        assertThat(PlainTextComponentSerializer.plainText().serialize(StyledText.render("<h:'Home Panel'>")))
                .isEqualTo("ʜᴏᴍᴇ ᴘᴀɴᴇʟ");
    }

    /** A player who types a style token sees the token, whether it is the theme's, a label's or the plugin's own. */
    @Test
    void anEscapedLineReadsAsTyped() {
        for (String typed : List.of("<accent>hi", "<tag:'SHOP'> hi", "<helpop> hi", "<red>hi", "<premium>hi")) {
            assertThat(PlainTextComponentSerializer.plainText().serialize(StyledText.render(StyledText.escape(typed))))
                    .as(typed)
                    .isEqualTo(typed);
        }
    }

    /** The same render takes a line's own placeholders, the way a sink adds a prefix. */
    @Test
    void aCallersTagIsResolvedBesideThePluginsOwn() {
        Component c = StyledText.render("<who> <helpop>", Placeholder.unparsed("who", "Ana"));
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("Ana ʜᴇʟᴘᴏᴘ ▶");
    }

    /**
     * Typed text never becomes markup, however it is spelled. The escape and the token expansion read a line two
     * different ways, so a token the escape did not recognise as a tag, an unterminated quote or a quote inside an
     * unquoted label, could still be expanded into one; a typed backslash must not undo the escape after it either.
     */
    @Test
    void typedTextNeverBecomesMarkup() {
        List<String> typed = List.of(
                "<tag:STAFF'>",
                "<tag:'a'b'>",
                "<etag:x'><click:run_command:'/op me'>here",
                "<h:'x>",
                "\\<accent>hi",
                "\\\\<red>hi",
                "<g:accent:x'>y",
                "a\\");
        for (String line : typed) {
            Component drawn = StyledText.render(StyledText.escape(line));
            assertThat(PlainTextComponentSerializer.plainText().serialize(drawn))
                    .as(line)
                    .isEqualTo(line);
            assertThat(styled(drawn)).as(line).isFalse();
        }
    }

    private static boolean styled(Component component) {
        if (!component.style().isEmpty()) {
            return true;
        }
        return component.children().stream().anyMatch(StyledTextTest::styled);
    }
}
