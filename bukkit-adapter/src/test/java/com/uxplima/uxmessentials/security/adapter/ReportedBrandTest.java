package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import org.junit.jupiter.api.Test;

/** A client brand is what the client wrote, so staff read it as words and nothing in it runs. */
class ReportedBrandTest {

    @Test
    void aClickTagInABrandIsShownAsWordsAndRunsNothing() {
        String hostile = "<click:run_command:'/lp user x parent set admin'>vanilla</click>";

        Component line = StyledText.render(
                CatalogMessages.fill("Client: {brand}", Map.of("brand", ReportedBrand.shown(hostile))));

        // Escaped once, by the line: the words as the client wrote them, with no backslash in sight.
        assertThat(PlainTextComponentSerializer.plainText().serialize(line)).isEqualTo("Client: " + hostile);
        List<Component> parts = new ArrayList<>();
        line.iterator(ComponentIteratorType.DEPTH_FIRST).forEachRemaining(parts::add);
        assertThat(parts).allSatisfy(part -> assertThat(part.clickEvent()).isNull());
    }

    @Test
    void aPlaceholderInABrandIsNeverFilled() {
        String shown = ReportedBrand.shown("%luckperms_meta_secret% fabric");

        // PlaceholderAPI fills only text between two percent signs.
        assertThat(shown).doesNotContain("%");
        assertThat(shown).contains("luckperms_meta_secret").endsWith(" fabric");
    }

    @Test
    void aLongBrandIsCutAndControlCharactersAreDropped() {
        String shown = ReportedBrand.shown("vanilla\n\u0007" + "x".repeat(10_000));

        assertThat(shown).startsWith("vanillax").hasSize(ReportedBrand.MAX_LENGTH);
        assertThat(ReportedBrand.shown("vanilla")).isEqualTo("vanilla");
    }
}
