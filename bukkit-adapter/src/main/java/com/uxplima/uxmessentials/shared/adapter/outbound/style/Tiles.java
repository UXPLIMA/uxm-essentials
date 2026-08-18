package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;

/**
 * Puts a menu tile's title where the style canon keeps it: on the first line of the lore, under a blank display
 * name.
 *
 * <p>A vanilla tooltip draws the display name hard against the top edge of the box, and the client will not put a
 * line above it. Leaving the name blank buys that line of air, and the title then reads as the first thing inside
 * the tooltip rather than as its lid: {@code ◆} in the icon grey, then the title itself, bold, in whatever colour
 * the catalog gave it. Everything below (the breadcrumb, the description, the facts, the click line) already
 * follows in the shipped lore blocks, so this only adds the head they were written to sit under.
 *
 * <p>Only a tile that has something to say is treated this way. A bare button carries no lore at all: a back
 * arrow, a page arrow or a filler pane keeps its one-line name, which is exactly how the canon writes them, and
 * {@link #titled} hands such an item straight back.
 */
@NullMarked
public final class Tiles {

    /** The diamond that opens a tile's title, in the icon grey the rest of the lore icons use. */
    private static final String DIAMOND_GLYPH = "◆ ";

    /** Every lore line is padded a space either side, so the text never touches the edge of the tooltip. */
    private static final String PADDING = " ";

    private static final Component DIAMOND = Component.text(DIAMOND_GLYPH, StyleTags.ICON);

    private static final Component PAD = Component.text(PADDING);

    /**
     * The display name a titled tile carries. A single space rather than an empty component: an empty name makes
     * the client fall back to the material's own name, which would put "Ender Eye" where the blank line belongs.
     */
    private static final Component BLANK_NAME = Component.text(PADDING);

    private Tiles() {}

    /** The name a titled tile is given, so callers do not have to know what a blank name is made of. */
    public static Component blankName() {
        return BLANK_NAME;
    }

    /**
     * The lore for a tile titled {@code name}: the title line first, then the lore as written. Returns
     * {@code lore} unchanged when there is nothing to title (a button with no lore) or no title to move (an
     * unnamed tile), so a caller may route every item through it.
     */
    public static List<Component> titled(Component name, List<Component> lore) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        if (lore.isEmpty() || isBlank(name)) {
            return lore;
        }
        List<Component> titled = new ArrayList<>(lore.size() + 1);
        titled.add(PAD.append(DIAMOND)
                .append(name.decoration(TextDecoration.BOLD, true))
                .append(PAD));
        titled.addAll(lore);
        return titled;
    }

    /**
     * The same for a tile whose lore is one component the item builder splits on its newlines: the title line is
     * joined on top with a newline of its own. Returns {@code lore} unchanged when there is no title to move.
     */
    public static Component titled(Component name, Component lore) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        if (isBlank(name)) {
            return lore;
        }
        return PAD.append(DIAMOND)
                .append(name.decoration(TextDecoration.BOLD, true))
                .append(PAD)
                .append(Component.newline())
                .append(lore);
    }

    /** Whether {@code name} would put a title on the tile, or is the blank a titled tile already carries. */
    public static boolean isBlank(Component name) {
        Objects.requireNonNull(name, "name");
        return PlainTextComponentSerializer.plainText().serialize(name).isBlank();
    }
}
