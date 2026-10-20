package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.application.message.CatalogLines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Every operator surface that renders through here (the scoreboard, the tablist, nametags, the announcer, the click
 * actions) reads an {@code @key} line in the viewer's language. The surface hands over its line as written, and the
 * catalogue entry stands in for it before any token or placeholder runs.
 */
class HudTextTest {

    private static final UUID TURKISH = UUID.randomUUID();
    private static final UUID ENGLISH = UUID.randomUUID();

    @BeforeEach
    void startTheServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void forgetTheCatalogue() {
        HudText.useCatalogLines(CatalogLines.none());
        MockBukkit.unmock();
    }

    @Test
    void aCatalogLineRendersInTheViewersLanguage() {
        HudText.useCatalogLines(new CatalogLines(
                (viewer, key, arguments) ->
                        (viewer.uuid().equals(TURKISH) ? "<green>Katıl " : "<green>Join ") + arguments.get("server"),
                Set.of("custom.join")::contains));

        assertThat(plain(HudText.render(TURKISH, "@custom.join server=skyblock")))
                .isEqualTo("Katıl skyblock");
        assertThat(plain(HudText.render(ENGLISH, "@custom.join server=skyblock")))
                .isEqualTo("Join skyblock");
    }

    @Test
    void aSurfaceExpandsTheLineBeforeItsOwnTokens() {
        HudText.useCatalogLines(
                new CatalogLines((viewer, key, arguments) -> "Hello {player}", Set.of("custom.hello")::contains));

        assertThat(HudText.lineFor(TURKISH, "@custom.hello")).isEqualTo("Hello {player}");
        assertThat(HudText.lineFor(TURKISH, "<gold>own words")).isEqualTo("<gold>own words");
    }

    @Test
    void withNoCatalogueTheLineIsTheOperatorsWords() {
        assertThat(plain(HudText.render(TURKISH, "@custom.join"))).isEqualTo("@custom.join");
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
