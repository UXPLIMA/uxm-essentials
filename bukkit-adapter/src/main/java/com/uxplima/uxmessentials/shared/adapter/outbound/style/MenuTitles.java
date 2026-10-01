package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.TitleAlignment;
import org.jspecify.annotations.NullMarked;

/**
 * Lays an inventory title in the chest window: centred, unless the theme says left. The vanilla client draws the title
 * left-aligned from a fixed origin, so the only way to centre it is to prepend spaces.
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
 * <p>The title is drawn as it was written. It was once flattened to plain text here, to keep window titles bare
 * (docs/14-ui-style), and that broke every title an operator styled: a translated key came out as its raw name and a
 * resource-pack font was dropped. The shipped titles are bare where they are written instead. A title whose width the
 * plain letters do not give, a translated key, a pack font or a keybind, is never padded, because the pack lines it up.
 */
@NullMarked
public final class MenuTitles {

    /** The width of a chest window, in pixels of the default font. */
    private static final int WINDOW_WIDTH = 176;

    /** How far in from the left edge of the window the client starts drawing the label. */
    private static final int TITLE_ORIGIN = 8;

    private static final int SPACE_WIDTH = 4;

    private static final String SPACE = " ";

    /** Where a title sits until a theme is read, which is the shipped theme's answer. */
    private static volatile TitleAlignment alignment = TitleAlignment.CENTRE;

    private MenuTitles() {}

    /** Where every window title sits from now on, as the theme says: set at enable and on every reload. */
    public static void useAlignment(TitleAlignment loaded) {
        alignment = Objects.requireNonNull(loaded, "loaded");
    }

    /**
     * {@code title} where the theme puts it: padded into the middle of the window, or as written when the theme says
     * left. Returns the title unchanged when it is empty, since padding a blank title would show a window titled with
     * spaces, and when its width cannot be measured.
     */
    public static Component centre(Component title) {
        Objects.requireNonNull(title, "title");
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (alignment == TitleAlignment.LEFT || plain.isBlank() || !measurable(title)) {
            return title;
        }
        int free = WINDOW_WIDTH - 2 * TITLE_ORIGIN - FontWidths.of(plain);
        int pad = Math.round(free / (2f * SPACE_WIDTH));
        return pad <= 0 ? title : Component.text(SPACE.repeat(pad)).append(title);
    }

    /** Whether every part of {@code component} is literal text in the default font, the only width known here. */
    private static boolean measurable(Component component) {
        if (!(component instanceof TextComponent) || component.style().font() != null) {
            return false;
        }
        for (Component child : component.children()) {
            if (!measurable(child)) {
                return false;
            }
        }
        return true;
    }

    /** The plain-text form of {@code title}, for a renderer that needs the string rather than the component. */
    public static String plain(Component title) {
        Objects.requireNonNull(title, "title");
        return PlainTextComponentSerializer.plainText().serialize(centre(title));
    }
}
