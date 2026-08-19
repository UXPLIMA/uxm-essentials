package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Locale;
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
 * <p>Colour carries meaning, never decoration: sky is the brand accent, ice is a value, white and subtext are
 * the reading colours, and green/red/gold are reserved for outcome, denial and attention. The hexes are the
 * palette canon and nothing outside this class may name one.
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

    public static final TextColor ACCENT = TextColor.color(0x38b6ff);
    public static final TextColor VALUE = TextColor.color(0x8fd9ff);
    public static final TextColor BODY = TextColor.color(0xffffff);
    public static final TextColor SUBTEXT = TextColor.color(0xdde8f0);
    public static final TextColor MUTED = TextColor.color(0x93a4b3);
    public static final TextColor DIM = TextColor.color(0x6b7886);
    public static final TextColor ICON = TextColor.color(0x8a93a1);
    public static final TextColor CRUMB = TextColor.color(0x565f6b);
    public static final TextColor GOOD = TextColor.color(0x5be38c);
    public static final TextColor BAD = TextColor.color(0xff6b6b);
    public static final TextColor GOLD = TextColor.color(0xffc93c);
    public static final TextColor INFO = TextColor.color(0x4fd6e8);
    public static final TextColor RANK = TextColor.color(0xb68cff);
    public static final TextColor EVENT = TextColor.color(0xff8fd0);

    /**
     * The second half of each title gradient. A gradient runs from a palette colour to its lighter neighbour in
     * the same family, which is what keeps a title reading as one colour rather than two: sky lightens into ice
     * (that pair is {@link #ACCENT} and {@link #VALUE} above), green into emerald, red into rose, gold into
     * yellow.
     */
    public static final TextColor EMERALD = TextColor.color(0x45d9a6);

    public static final TextColor ROSE = TextColor.color(0xff7aa8);
    public static final TextColor YELLOW = TextColor.color(0xffe15c);

    /**
     * Role names for the three colours the palette reuses. An amount, a highlighted number and the click word
     * all read gold; a header and a plain title read in the brand accent and the body colour respectively. They
     * are separate constants because a call site names the role it means, not the hex behind it.
     */
    public static final TextColor MONEY = GOLD;

    public static final TextColor LEVEL = GOLD;
    public static final TextColor CTA = GOLD;
    public static final TextColor HEADER = ACCENT;
    public static final TextColor TITLE = BODY;

    private static final String SEPARATOR = "▶";
    private static final String GAP = " ";
    private static final String ERROR_LABEL = "error";

    /**
     * The categories whose prefix is green rather than sky. Money is the one subject the palette gives its own
     * prefix colour to, so a line that moves a balance is recognisable before it is read.
     */
    private static final Set<String> MONEY_CATEGORIES = Set.of("economy", "bank", "loan", "trade", "vault");

    private static final TagResolver RESOLVER = build();

    private StyleTags() {}

    public static TagResolver resolver() {
        return RESOLVER;
    }

    private static TagResolver build() {
        return TagResolver.builder()
                .resolvers(
                        Placeholder.styling("accent", ACCENT),
                        Placeholder.styling("value", VALUE),
                        Placeholder.styling("body", BODY),
                        Placeholder.styling("subtext", SUBTEXT),
                        Placeholder.styling("muted", MUTED),
                        Placeholder.styling("dim", DIM),
                        Placeholder.styling("icon", ICON),
                        Placeholder.styling("crumb", CRUMB),
                        Placeholder.styling("good", GOOD),
                        Placeholder.styling("bad", BAD),
                        Placeholder.styling("money", GOLD),
                        Placeholder.styling("level", GOLD),
                        Placeholder.styling("cta", GOLD),
                        Placeholder.styling("info", INFO),
                        Placeholder.styling("rank", RANK),
                        Placeholder.styling("event", EVENT),
                        Placeholder.styling("title", BODY),
                        // Carries no style of its own: it marks the text a catalog line wants left in
                        // ordinary letters, which the typography pass reads before MiniMessage sees it.
                        Placeholder.styling("plain"),
                        categoryPrefix("tag"),
                        errorPrefix("etag"),
                        labelledPrefix("helpop", "helpop"),
                        labelledPrefix("staffchat", "staffchat"),
                        header("h"))
                .build();
    }

    /**
     * The category prefix: the label the catalog key carries, in small capitals and bold, then the dim triangle.
     * The label colour comes from what the category is about rather than from the module that raised the line,
     * so every money movement reads green and every other feature reads in the brand sky. The separator carries
     * no trailing space; the single gap before the body comes from the catalog key.
     */
    private static TagResolver categoryPrefix(String name) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String label = args.popOr("").value();
            TextColor colour = MONEY_CATEGORIES.contains(label.toLowerCase(Locale.ROOT)) ? GOOD : ACCENT;
            return Tag.selfClosingInserting(prefix(label, colour));
        });
    }

    /**
     * The denial prefix. The label argument is popped and ignored: a refusal reads as an error whichever module
     * raised it, and the red word plus the dim triangle is the whole signal, which is why an error line carries
     * no trailing cross either.
     */
    private static TagResolver errorPrefix(String name) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            args.popOr("");
            return Tag.selfClosingInserting(prefix(ERROR_LABEL, BAD));
        });
    }

    /**
     * A channel prefix that keeps its own constant label instead of taking one from the catalog. The two staff
     * channels need to be told apart at a glance, so each names itself rather than sharing a category word.
     */
    private static TagResolver labelledPrefix(String name, String label) {
        return TagResolver.resolver(
                name, (ArgumentQueue args, Context ctx) -> Tag.selfClosingInserting(prefix(label, ACCENT)));
    }

    /** The bold sky header a lore block opens with, and the heading a banner chat line uses. */
    private static TagResolver header(String name) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String text = args.popOr(name + " requires text").value();
            return Tag.selfClosingInserting(Component.text(SmallCaps.of(text), ACCENT)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        });
    }

    private static Component prefix(String label, TextColor colour) {
        return Component.text(SmallCaps.of(label), colour)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(GAP, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text(SEPARATOR, DIM).decoration(TextDecoration.BOLD, false));
    }
}
