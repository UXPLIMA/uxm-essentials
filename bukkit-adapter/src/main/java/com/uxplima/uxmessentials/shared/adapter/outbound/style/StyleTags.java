package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.jspecify.annotations.NullMarked;

/**
 * The project palette as MiniMessage tags. Solid-colour tags ({@code <accent>}, {@code <body>}, …) wrap
 * their content; {@code <tag:'…'>} and {@code <etag:'…'>} both render the single {@code uxmEssentials »}
 * brand chat prefix (blue brand, dark-gray separator) — the label argument is accepted but ignored, so
 * catalog keys may keep their {@code <tag:'MODULE'>} form while every message shows the one brand prefix.
 * {@code <h:'…'>} is a solid blue header. The resolver is immutable and cached; callers add it at every
 * MiniMessage parse.
 */
@NullMarked
public final class StyleTags {

    public static final TextColor HEADER = TextColor.color(0x4aa3ff);
    public static final TextColor ACCENT = TextColor.color(0x45cdf9);
    public static final TextColor BODY = TextColor.color(0xfcfce3);
    public static final TextColor CTA = TextColor.color(0x7cc7ff);
    public static final TextColor MONEY = TextColor.color(0x2ecc71);
    public static final TextColor BAD = TextColor.color(0xe63946);
    public static final TextColor LEVEL = TextColor.color(0xffe66d);
    public static final TextColor MUTED = TextColor.color(0xa9a9a9);
    public static final TextColor TITLE = TextColor.color(0x555555);

    private static final String BRAND = "uxmEssentials";
    private static final String SEPARATOR = " »";

    private static final TagResolver RESOLVER = build();

    private StyleTags() {}

    public static TagResolver resolver() {
        return RESOLVER;
    }

    private static TagResolver build() {
        return TagResolver.builder()
                .resolvers(
                        Placeholder.styling("accent", ACCENT),
                        Placeholder.styling("value", ACCENT),
                        Placeholder.styling("body", BODY),
                        Placeholder.styling("cta", CTA),
                        Placeholder.styling("money", MONEY),
                        Placeholder.styling("good", MONEY),
                        Placeholder.styling("bad", BAD),
                        Placeholder.styling("level", LEVEL),
                        Placeholder.styling("muted", MUTED),
                        Placeholder.styling("title", TITLE),
                        prefixTag("tag"),
                        prefixTag("etag"),
                        header("h"))
                .build();
    }

    /**
     * Both {@code <tag:'…'>} and {@code <etag:'…'>} render the one {@code uxmEssentials »} brand prefix. The
     * label argument is popped and ignored so existing catalog keys (e.g. {@code <tag:'HOME'>}) keep working
     * while every message shows the same prefix. The separator carries a leading space and no trailing space;
     * the single gap before the message body comes from the catalog key, so the prefix is never double-spaced.
     */
    private static TagResolver prefixTag(String name) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            args.popOr("");
            Component component = Component.text(BRAND, HEADER).append(Component.text(SEPARATOR, TITLE));
            return Tag.selfClosingInserting(component);
        });
    }

    private static TagResolver header(String name) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String text = args.popOr(name + " requires text").value();
            return Tag.selfClosingInserting(Component.text(text, HEADER));
        });
    }
}
