package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;

/**
 * Centres an inventory title in the chest window. The style canon writes a menu title centred and bare: no colour,
 * no bold, no gradient and no dashes around it, so the window reads as part of the client's own chrome rather than
 * competing with the icons under it. The vanilla client draws the title left-aligned from a fixed origin, so the
 * only way to centre it is to prepend spaces.
 *
 * <p>The maths is the client's own layout: a chest window is 176 pixels wide and its label is drawn from an
 * origin eight pixels in from the left edge, so the padding that centres a title is the free width either side of
 * it less that origin. Each character's width is looked up in {@link FontWidths} and the padding is converted into
 * four-pixel spaces, rounded to the nearest one, which leaves any title within two pixels of the middle. A title
 * already wider than the window gets no padding rather than a negative one.
 *
 * <p>Forgetting the origin is what makes a whole menu look subtly wrong: it pushes every title eight pixels (two
 * spaces) to the right, and because the rounding then lands differently for each length, some titles read as
 * centred and others do not.
 *
 * <p>The styling is dropped here rather than trusted to every catalog entry: a title is flattened to its plain
 * text and handed back as one unstyled component, so a key that still carries a colour tag (or a value tag around
 * a name inside it) cannot paint a two-tone title. Centring runs after the title is resolved, so a title carrying
 * a placeholder centres on the text a player actually sees rather than on the template.
 */
@NullMarked
public final class MenuTitles {

    /** The width of a chest window, in pixels of the default font. */
    private static final int WINDOW_WIDTH = 176;

    /** How far in from the left edge of the window the client starts drawing the label. */
    private static final int TITLE_ORIGIN = 8;

    private static final int SPACE_WIDTH = 4;

    private static final String SPACE = " ";

    private MenuTitles() {}

    /**
     * {@code title} stripped of every colour and decoration and padded so it sits in the middle of the window.
     * Returns the title unchanged when it is empty, since padding a blank title would show a window titled with
     * spaces.
     */
    public static Component centre(Component title) {
        Objects.requireNonNull(title, "title");
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (plain.isBlank()) {
            return title;
        }
        int free = WINDOW_WIDTH - 2 * TITLE_ORIGIN - FontWidths.of(plain);
        int pad = Math.round(free / (2f * SPACE_WIDTH));
        return Component.text(pad <= 0 ? plain : SPACE.repeat(pad) + plain);
    }

    /** The plain-text form of {@code title}, for a renderer that needs the string rather than the component. */
    public static String plain(Component title) {
        Objects.requireNonNull(title, "title");
        return PlainTextComponentSerializer.plainText().serialize(centre(title));
    }
}
