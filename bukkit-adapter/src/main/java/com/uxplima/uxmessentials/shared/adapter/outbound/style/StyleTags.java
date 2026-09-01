package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.jspecify.annotations.NullMarked;

/**
 * The project palette as MiniMessage tags. Solid-colour tags ({@code <accent>}, {@code <body>}, …) wrap their
 * content; the prefix and header tags take a quoted argument and are self-closing.
 *
 * <p>Colour carries meaning, never decoration: the brand accent, a value, the reading colours, and the three
 * reflexes for outcome, denial and attention. The colours themselves live in {@link Palette}, which reads
 * them from the server's theme file, so nothing outside that class names a hex.
 *
 * <p>{@code <tag:'HOME'>} renders the category prefix a chat line opens with: the label in small capitals, bold,
 * coloured by what the category is about (money categories green, everything else sky), then the dim triangle.
 * {@code <etag:'…'>} ignores its label and renders the red error word, because a denial reads as an error
 * whichever module raised it. {@code <helpop>} and {@code <staffchat>} are the two channels that keep their own
 * label so they read apart from each other. {@code <h:'…'>} is the bold sky header a lore block opens with.
 * The resolver is immutable and cached; callers add it at every MiniMessage parse.
 */
@NullMarked
public final class StyleTags {

    /**
     * The colours in use, and the resolver built from them.
     *
     * <p>The look of a server is one value for the whole process, read once when the plugin enables and
     * again when an operator reloads it, so it is held here rather than threaded through every class that
     * writes a line. Both fields are replaced together in {@link #use(Palette)}, and both are volatile, so a
     * reload on the main thread is seen by a message rendered on any other.
     */
    private static volatile Palette palette = Palette.shipped();

    private static volatile TagResolver resolver = build(palette);

    private static final String SEPARATOR = "▶";

    private static final String GAP = " ";

    private static final String ERROR_LABEL = "error";

    /**
     * The categories whose prefix is green rather than the brand colour. Money is the one subject the palette
     * gives its own prefix colour to, so a line that moves a balance is recognisable before it is read.
     */
    private static final Set<String> MONEY_CATEGORIES = Set.of("economy", "bank", "loan", "trade", "vault");

    private StyleTags() {}

    /** The colours every line is painted with. */
    public static Palette palette() {
        return palette;
    }

    /**
     * Take the colours a server wrote down. Called once when the plugin enables, and again on a reload.
     *
     * <p>The resolver is rebuilt here rather than per parse, because a server's own role is a tag of its own
     * and the resolver is the only place that list exists.
     */
    public static void use(Palette loaded) {
        Objects.requireNonNull(loaded, "loaded");
        palette = loaded;
        resolver = build(loaded);
    }

    public static TagResolver resolver() {
        return resolver;
    }

    public static TextColor accent() {
        return palette.accent();
    }

    public static TextColor value() {
        return palette.value();
    }

    public static TextColor body() {
        return palette.body();
    }

    public static TextColor subtext() {
        return palette.subtext();
    }

    public static TextColor muted() {
        return palette.muted();
    }

    public static TextColor dim() {
        return palette.dim();
    }

    public static TextColor icon() {
        return palette.icon();
    }

    public static TextColor crumb() {
        return palette.crumb();
    }

    public static TextColor good() {
        return palette.good();
    }

    public static TextColor bad() {
        return palette.bad();
    }

    public static TextColor gold() {
        return palette.gold();
    }

    public static TextColor money() {
        return palette.money();
    }

    public static TextColor level() {
        return palette.level();
    }

    public static TextColor cta() {
        return palette.cta();
    }

    public static TextColor info() {
        return palette.info();
    }

    public static TextColor rank() {
        return palette.rank();
    }

    public static TextColor event() {
        return palette.event();
    }

    /** The lighter half of the positive ramp. */
    public static TextColor emerald() {
        return palette.emerald();
    }

    /** The lighter half of the denial ramp. */
    public static TextColor rose() {
        return palette.rose();
    }

    /** The lighter half of the attention ramp. */
    public static TextColor yellow() {
        return palette.yellow();
    }

    /** The colour a header reads in. */
    public static TextColor header() {
        return palette.accent();
    }

    /** The colour a plain title reads in. */
    public static TextColor title() {
        return palette.role("title");
    }

    /**
     * The resolver for {@code colours}: one tag per role, then the tags that insert text of their own.
     *
     * <p>The role list comes from the palette rather than from this class, so a role a server invented is a
     * tag the same day it is written, and a role dropped from a later version of this file keeps working for
     * the server that still writes it.
     */
    private static TagResolver build(Palette colours) {
        TagResolver.Builder builder = TagResolver.builder();
        colours.roles().forEach((role, colour) -> builder.resolver(Placeholder.styling(role, colour)));
        return builder.resolvers(
                        // Carries no style of its own: it marks the text a catalog line wants left in
                        // ordinary letters, which the typography pass reads before MiniMessage sees it.
                        Placeholder.styling("plain"),
                        categoryPrefix("tag", colours),
                        errorPrefix("etag", colours),
                        labelledPrefix("helpop", "helpop", colours),
                        labelledPrefix("staffchat", "staffchat", colours),
                        header("h", colours))
                .build();
    }

    /**
     * The category prefix: the label the catalog key carries, in small capitals and bold, then the dim
     * triangle. The label colour comes from what the category is about rather than from the module that
     * raised the line, so every money movement reads green and every other feature reads in the brand
     * colour. The separator carries no trailing space; the single gap before the body comes from the catalog
     * key.
     */
    private static TagResolver categoryPrefix(String name, Palette colours) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String label = args.popOr("").value();
            TextColor colour =
                    MONEY_CATEGORIES.contains(label.toLowerCase(Locale.ROOT)) ? colours.good() : colours.accent();
            return Tag.selfClosingInserting(prefix(label, colour, colours));
        });
    }

    /**
     * The denial prefix. The label argument is popped and ignored: a refusal reads as an error whichever
     * module raised it, and the red word plus the dim triangle is the whole signal, which is why an error
     * line carries no trailing cross either.
     */
    private static TagResolver errorPrefix(String name, Palette colours) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            args.popOr("");
            return Tag.selfClosingInserting(prefix(ERROR_LABEL, colours.bad(), colours));
        });
    }

    /**
     * A channel prefix that keeps its own constant label instead of taking one from the catalog. The two
     * staff channels need to be told apart at a glance, so each names itself rather than sharing a category
     * word.
     */
    private static TagResolver labelledPrefix(String name, String label, Palette colours) {
        return TagResolver.resolver(
                name,
                (ArgumentQueue args, Context ctx) ->
                        Tag.selfClosingInserting(prefix(label, colours.accent(), colours)));
    }

    /**
     * The bold header a lore block opens with, and the heading a banner chat line uses: the label in small
     * capitals and bold.
     *
     * <p>It reads in the brand colour unless the line names a position on the theme's wheel,
     * {@code <h:'Warps':3>}, and then it is painted across that arc. A file with several headings on one
     * screen gives each of them a position, so they differ from each other without any of them naming a
     * colour. A position on a theme whose wheel is shorter than two colours falls back to the brand colour,
     * which is what a server that never asked for the effect should see.
     */
    private static TagResolver header(String name, Palette colours) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String text = args.popOr(name + " requires text").value();
            Component flat = Component.text(SmallCaps.of(text), colours.accent())
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
            List<TextColor> arc =
                    args.hasNext() ? colours.arc(args.pop().asInt().orElse(0)) : List.of();
            return Tag.selfClosingInserting(
                    arc.isEmpty()
                            ? flat
                            : Gradients.across(Component.text(SmallCaps.of(text)), arc.get(0), arc.get(1))
                                    .decoration(TextDecoration.ITALIC, false));
        });
    }

    private static Component prefix(String label, TextColor colour, Palette colours) {
        return Component.text(SmallCaps.of(label), colour)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(GAP, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text(SEPARATOR, colours.dim()).decoration(TextDecoration.BOLD, false));
    }
}
