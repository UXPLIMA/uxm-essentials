package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.object.ObjectContents;

import com.uxplima.uxmlib.text.GlyphWidthTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A client only sends a click on an entity it can reach, three blocks for a player in survival or adventure, so a
 * board read from across a square never heard its clicks. The server works the click out itself instead: the text is
 * a panel turned towards the reader, as wide as its widest line and as tall as its lines, and a look that crosses it
 * within the reach is a click on it.
 */
final class HologramAimTest {

    /** A board two blocks wide and four tall, its foot at y 63, standing at x 13.5, z -21.5. */
    private static final HologramAim.Panel BOARD = new HologramAim.Panel(13.5, -21.5, 63.0, 4.0, 1.0);

    @Test
    @DisplayName("a look at the middle of the board from nine blocks is a click on it")
    void aLookAtTheMiddleHits() {
        assertThat(HologramAim.hit(4.5, 64.6, -21.5, 1, 0.05, 0, BOARD, 12)).hasValueCloseTo(9.0, within(0.01));
    }

    @Test
    @DisplayName("a look that passes beside, above or below the text misses")
    void aLookBesideMisses() {
        assertThat(HologramAim.hit(4.5, 64.6, -21.5, 1, 0, 0.3, BOARD, 12)).isEmpty();
        assertThat(HologramAim.hit(4.5, 64.6, -21.5, 1, 0.4, 0, BOARD, 12)).isEmpty();
        assertThat(HologramAim.hit(4.5, 64.6, -21.5, 1, -0.4, 0, BOARD, 12)).isEmpty();
    }

    @Test
    @DisplayName("the panel turns towards the reader, so a look from the side meets it too")
    void thePanelTurnsTowardsTheReader() {
        // From the south east, looking north west straight at the axis.
        double d = Math.sqrt(0.5);
        assertThat(HologramAim.hit(19.5, 64.6, -15.5, -d, 0, -d, BOARD, 12)).isPresent();
    }

    @Test
    @DisplayName("a board out of reach, or behind the reader, is not clicked")
    void outOfReachOrBehindMisses() {
        assertThat(HologramAim.hit(4.5, 64.6, -21.5, 1, 0.05, 0, BOARD, 8)).isEmpty();
        assertThat(HologramAim.hit(4.5, 64.6, -21.5, -1, 0, 0, BOARD, 12)).isEmpty();
    }

    @Test
    @DisplayName("the widest line is measured, a head counting as a glyph of its own")
    void theWidestLineIsMeasured() {
        Component head =
                Component.object(ObjectContents.playerHead().name("Steve").build());
        Component text = Component.join(
                JoinConfiguration.newlines(),
                Component.text("ab"),
                Component.text("abcd").append(head),
                Component.text("a"));

        assertThat(HologramAim.widestLinePixels(text))
                .isEqualTo(GlyphWidthTable.widthOf("abcd", false) + HologramAim.HEAD_PIXELS);
    }

    @Test
    @DisplayName("a board that keeps its own facing is met where the look crosses its face")
    void aFixedBoardKeepsItsFacing() {
        // The board faces west, towards x 4.5. A look from the south west at its middle crosses its face.
        double d = Math.sqrt(0.5);
        assertThat(HologramAim.hitFacing(7.5, 64.6, -27.5, d, 0, d, BOARD, -1, 0, 12))
                .hasValueCloseTo(6 * Math.sqrt(2), within(0.01));
        // A look along its face never crosses it.
        assertThat(HologramAim.hitFacing(13.5, 64.6, -30.5, 0, 0, 1, BOARD, -1, 0, 12))
                .isEmpty();
    }

    @Test
    @DisplayName("the lines are counted as the reader sees them")
    void theLinesAreCounted() {
        Component text = Component.join(
                JoinConfiguration.newlines(), Component.text("a"), Component.text("b\nc"), Component.text("d"));

        assertThat(HologramAim.lineCount(text)).isEqualTo(4);
        assertThat(HologramAim.lineCount(Component.empty())).isEqualTo(1);
    }
}
