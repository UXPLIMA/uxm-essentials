package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class StyledTextTest {

    @Test
    void rendersTokensFromCatalogString() {
        Component c = StyledText.render("<accent>Base</accent>");
        assertThat(c.color()).isEqualTo(StyleTags.ACCENT);
        assertThat(PlainTextComponentSerializer.plainText().serialize(c)).isEqualTo("Base");
    }

    @Test
    void rendersHeaderToken() {
        assertThat(PlainTextComponentSerializer.plainText().serialize(StyledText.render("<h:'Home Panel'>")))
                .isEqualTo("ʜᴏᴍᴇ ᴘᴀɴᴇʟ");
    }
}
