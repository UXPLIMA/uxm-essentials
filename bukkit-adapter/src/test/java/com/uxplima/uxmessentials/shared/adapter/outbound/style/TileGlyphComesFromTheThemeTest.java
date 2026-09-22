package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The glyph in front of a tile title is the one the theme names.
 *
 * <p>`ThemeFile` writes `glyphs.title` into the theme the library's renderers read, and its own javadoc
 * calls it "the one glyph a tile title is drawn with". `Tiles` did not ask: it held the diamond as a
 * constant of its own.
 *
 * <p>So an operator who changed `glyphs.title` changed it for every tile uxmLib draws and for none of
 * the tiles this plugin draws itself. **One setting, two glyphs, on one server**, and nothing anywhere
 * said which was which.
 */
class TileGlyphComesFromTheThemeTest {

    @AfterEach
    void restore() {
        StyleTags.useTitleGlyph(ThemeFile.titleGlyph());
    }

    @Test
    @DisplayName("a titled tile draws the glyph the theme names")
    void atitledTileUsesTheThemeGlyph() {
        StyleTags.useTitleGlyph("✦");

        List<Component> lore = Tiles.titled(Component.text("Warps"), List.of(Component.text("Go somewhere")));

        assertThat(plain(lore.get(0)))
                .describedAs("the theme owns this glyph and the tile has to ask for it")
                .contains("✦")
                .doesNotContain("◆");
    }

    @Test
    @DisplayName("the one line form draws it too, because both are one tile")
    void theoneLineFormUsesItToo() {
        StyleTags.useTitleGlyph("✦");

        Component lore = Tiles.titled(Component.text("Warps"), Component.text("Go somewhere"));

        assertThat(plain(lore)).contains("✦").doesNotContain("◆");
    }

    @Test
    @DisplayName("a server that names no glyph gets the shipped diamond")
    void theshippedGlyphIsTheDiamond() {
        StyleTags.useTitleGlyph(ThemeFile.titleGlyph());

        List<Component> lore = Tiles.titled(Component.text("Warps"), List.of(Component.text("Go somewhere")));

        assertThat(plain(lore.get(0))).contains("◆");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
