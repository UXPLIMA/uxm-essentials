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
}
