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
 * <p>The maths is the client's own font metrics: the title is drawn in the default font, where a space is four
 * pixels wide and the usable label area of a generic chest is 176 pixels. Each character's width is looked up in
 * {@link FontWidths}, the remaining space is halved, and that many spaces are prepended. A title that is already
 * wider than the window gets no padding rather than a negative one.
 *
 * <p>The styling is dropped here rather than trusted to every catalog entry: a title is flattened to its plain
 * text and handed back as one unstyled component, so a key that still carries a colour tag (or a value tag around
 * a name inside it) cannot paint a two-tone title. Centring runs after the title is resolved, so a title carrying
 * a placeholder centres on the text a player actually sees rather than on the template.
 */
@NullMarked
public final class MenuTitles {

    /** The inner width of a generic chest's label area, in pixels of the default font. */
    private static final int WINDOW_WIDTH = 176;

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
        int pad = (WINDOW_WIDTH - FontWidths.of(plain)) / (2 * SPACE_WIDTH);
        return Component.text(pad <= 0 ? plain : SPACE.repeat(pad) + plain);
    }

    /** The plain-text form of {@code title}, for a renderer that needs the string rather than the component. */
    public static String plain(Component title) {
        Objects.requireNonNull(title, "title");
        return PlainTextComponentSerializer.plainText().serialize(centre(title));
    }
}
