package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.StyleTokens;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;

/**
 * The look every line is drawn in, and the few tags this plugin adds to the library's.
 *
 * <p>The tokens a line is written with ({@code <accent>}, {@code <tag:'HOME'>}, {@code <h:'…'>}) are the library's:
 * {@link StyleTokens} turns each into what a client is sent, with the {@link Theme} held here. A catalogue line is
 * styled once when its language is read; a line an operator wrote in a menu, a hologram or a kit is styled when it is
 * drawn, through {@link StyledText}. What this class adds is what only this plugin speaks: {@code <helpop>} and
 * {@code <staffchat>}, the two staff channels, each with a label of its own so they read apart, and
 * {@code <plain>}, the marker a line keeps ordinary letters with, which carries no style here.
 *
 * <p>The look of a server is one value for the whole process, read when the plugin enables and again when an operator
 * reloads it, so it is held here rather than threaded through every class that writes a line. It is volatile, so a
 * reload on one thread is seen by a line drawn on any other.
 */
@NullMarked
public final class StyleTags {

    private static volatile Theme theme = ThemeFile.shippedTheme();
    private static volatile TagResolver resolver = build(theme);

    private StyleTags() {}

    /** The look in use. */
    public static Theme theme() {
        return theme;
    }

    /** Draw every line with {@code loaded} from now on. */
    public static void use(Theme loaded) {
        Objects.requireNonNull(loaded, "loaded");
        theme = loaded;
        resolver = build(loaded);
    }

    /** The glyph a tile title opens with. */
    public static String titleGlyph() {
        return theme.glyph("title");
    }

    /** The tags this plugin adds, for a parse of a line the library's tokens have already been expanded in. */
    public static TagResolver resolver() {
        return resolver;
    }

    public static TextColor accent() {
        return theme.colour("accent");
    }

    public static TextColor value() {
        return theme.colour("value");
    }

    public static TextColor body() {
        return theme.colour("body");
    }

    public static TextColor subtext() {
        return theme.colour("subtext");
    }

    public static TextColor muted() {
        return theme.colour("muted");
    }

    public static TextColor dim() {
        return theme.colour("dim");
    }

    public static TextColor icon() {
        return theme.colour("icon");
    }

    public static TextColor crumb() {
        return theme.colour("crumb");
    }

    public static TextColor good() {
        return theme.colour("good");
    }

    public static TextColor bad() {
        return theme.colour("bad");
    }

    public static TextColor gold() {
        return theme.colour("warn");
    }

    public static TextColor money() {
        return theme.colour("money");
    }

    public static TextColor level() {
        return theme.colour("level");
    }

    public static TextColor cta() {
        return theme.colour("cta");
    }

    public static TextColor info() {
        return theme.colour("info");
    }

    public static TextColor rank() {
        return theme.colour("rank");
    }

    public static TextColor event() {
        return theme.colour("event");
    }

    /** The lighter half of the money ramp. */
    public static TextColor emerald() {
        return next(good(), "emerald");
    }

    /** The lighter half of the denial ramp. */
    public static TextColor rose() {
        return next(bad(), "rose");
    }

    /** The lighter half of the attention ramp. */
    public static TextColor yellow() {
        return next(gold(), "yellow");
    }

    /**
     * The colour after {@code colour} on the theme's wheel, so a ramp runs inside one family of the server's own
     * colours; the {@code fallback} role when the wheel does not hold the colour.
     */
    private static TextColor next(TextColor colour, String fallback) {
        java.util.List<TextColor> wheel = theme.wheel();
        int at = wheel.indexOf(colour);
        return at < 0 ? theme.colour(fallback) : wheel.get((at + 1) % wheel.size());
    }

    /**
     * The plugin's own tags. Each staff channel is a category prefix with a label of its own, drawn by the library
     * exactly as a {@code <tag:'…'>} is, so the two channels look like every other prefix and read apart.
     */
    private static TagResolver build(Theme look) {
        return TagResolver.resolver(
                // Carries no style of its own: it marks text the letters pass leaves in ordinary letters.
                Placeholder.styling("plain"), channel("helpop", look), channel("staffchat", look));
    }

    private static TagResolver channel(String name, Theme look) {
        return TagResolver.resolver(
                name, Tag.selfClosingInserting(Text.mini(StyleTokens.expand("<tag:'" + name + "'>", look, false))));
    }
}
