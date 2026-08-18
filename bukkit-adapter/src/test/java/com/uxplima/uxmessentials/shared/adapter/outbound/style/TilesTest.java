package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/**
 * Pins where a menu tile's title lives. The canon puts it on the first lore line under a blank display name, so
 * the title reads inside the tooltip with a line of air above it, and a bare button (no lore) keeps its one-line
 * name. Both halves matter: a title left on the display name is the mistake this replaced, and a title forced
 * onto a button would put a diamond on a back arrow.
 */
class TilesTest {

    private static final Component TITLE = Component.text("Accept teleport requests");
    private static final Component CRUMB = Component.text("your preference");

    @Test
    void aTileWithLoreTakesItsTitleOntoTheFirstLoreLine() {
        List<Component> titled = Tiles.titled(TITLE, List.of(CRUMB));

        assertThat(titled).hasSize(2);
        assertThat(plain(titled.get(0))).isEqualTo(" ◆ Accept teleport requests ");
        assertThat(titled.get(1)).isEqualTo(CRUMB);
    }

    @Test
    void theTitleLineIsBoldAndOpensWithTheIconGreyDiamond() {
        Component head = Tiles.titled(TITLE, List.of(CRUMB)).get(0);

        Component diamond = head.children().get(0);
        assertThat(diamond.color()).isEqualTo(StyleTags.ICON);
        assertThat(head.children().get(1).decoration(TextDecoration.BOLD)).isEqualTo(TextDecoration.State.TRUE);
    }

    @Test
    void theNameATitledTileCarriesIsBlankRatherThanEmpty() {
        // Empty would make the client draw the material's own name where the blank line belongs.
        assertThat(plain(Tiles.blankName())).isEqualTo(" ");
        assertThat(Tiles.isBlank(Tiles.blankName())).isTrue();
    }

    @Test
    void aButtonWithNoLoreKeepsItsName() {
        assertThat(Tiles.titled(TITLE, List.of())).isEmpty();
    }

    @Test
    void anUnnamedTileIsLeftAlone() {
        List<Component> lore = List.of(CRUMB);

        assertThat(Tiles.titled(Component.empty(), lore)).isSameAs(lore);
        assertThat(Tiles.titled(Component.empty(), CRUMB)).isSameAs(CRUMB);
    }

    @Test
    void aSingleComponentLoreGetsTheTitleJoinedOnTopWithANewline() {
        Component titled = Tiles.titled(TITLE, CRUMB);

        assertThat(plain(titled)).isEqualTo(" ◆ Accept teleport requests \nyour preference");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
