package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Puts a menu tile's title where the style canon keeps it: on the first line of the lore, under a blank display
 * name, drawn by the library's {@link com.uxplima.uxmlib.gui.style.Tiles} so a tile here looks like a tile in every
 * other plugin.
 *
 * <p>A vanilla tooltip draws the display name hard against the top edge of the box, and the client will not put a
 * line above it. Leaving the name blank buys that line of air, and the title then reads as the first thing inside
 * the tooltip rather than as its lid: the theme's title glyph in the icon grey, then the title itself, bold, in a
 * ramp. A blank line closes the tooltip the same way.
 *
 * <p>What this plugin adds is the choice of ramp. A title is painted in the family of the colour its catalogue line
 * gave it: the bad colour reads as danger, the good colour as money, the warn colour as attention, and anything else,
 * a reading grey or no colour at all, in the brand sky. The incoming colour picks the family and is then dropped, so
 * two tiles titled the same way look the same however their catalogue keys were written. The four ramps are theme
 * gradients that {@link ThemeFile} fills from the server's own colours.
 *
 * <p>Only a tile that has something to say is treated this way. A bare button carries no lore at all: a back arrow,
 * a page arrow or a filler pane keeps its one-line name, and {@link #titled} hands such an item straight back.
 */
@NullMarked
public final class Tiles {

    private Tiles() {}

    /** The name a titled tile is given, so callers do not have to know what a blank name is made of. */
    public static Component blankName() {
        return com.uxplima.uxmlib.gui.style.Tiles.blankName();
    }

    /**
     * The lore for a tile titled {@code name}: the title line, the lore as written, then the closing blank. Returns
     * {@code lore} itself when there is nothing to title (a button with no lore) or no title to move (an unnamed
     * tile), so a caller may route every item through it.
     */
    public static List<Component> titled(Component name, List<Component> lore) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        if (com.uxplima.uxmlib.gui.style.Tiles.isUntitled(name, lore)) {
            return lore;
        }
        return com.uxplima.uxmlib.gui.style.Tiles.titled(StyleTags.theme(), bare(name), lore, rampOf(name));
    }

    /**
     * The same for a tile whose lore is one component the item builder splits on its newlines. Returns {@code lore}
     * unchanged when there is no title to move.
     */
    public static Component titled(Component name, Component lore) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        if (isBlank(name)) {
            return lore;
        }
        return com.uxplima.uxmlib.gui.style.Tiles.titled(StyleTags.theme(), bare(name), lore, rampOf(name));
    }

    /** Whether {@code name} would put a title on the tile, or is the blank a titled tile already carries. */
    public static boolean isBlank(Component name) {
        return com.uxplima.uxmlib.gui.style.Tiles.isBlank(name);
    }

    /** The words of {@code name} with none of its style, which the ramp replaces. */
    private static Component bare(Component name) {
        return Component.text(PlainTextComponentSerializer.plainText().serialize(name));
    }

    /** The ramp the family of {@code name}'s first colour is painted in. */
    private static String rampOf(Component name) {
        @Nullable TextColor colour = firstColour(name);
        if (StyleTags.bad().equals(colour)) {
            return ThemeFile.TILE_DANGER;
        }
        if (StyleTags.good().equals(colour) || StyleTags.emerald().equals(colour)) {
            return ThemeFile.TILE_MONEY;
        }
        if (StyleTags.gold().equals(colour)) {
            return ThemeFile.TILE_ATTENTION;
        }
        return ThemeFile.TILE;
    }

    /** The first colour set anywhere in {@code component}, depth first, or {@code null} when it carries none. */
    private static @Nullable TextColor firstColour(Component component) {
        @Nullable TextColor own = component.style().color();
        if (own != null) {
            return own;
        }
        for (Component child : component.children()) {
            @Nullable TextColor colour = firstColour(child);
            if (colour != null) {
                return colour;
            }
        }
        return null;
    }
}
