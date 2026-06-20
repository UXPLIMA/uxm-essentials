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
 * their content; the composite tags build a styled component from an argument: {@code <tag:'HOME'>} renders
 * the {@code HOME »} chat prefix (blue label, dark-gray separator), {@code <etag:'…'>} its red error variant,
 * and {@code <h:'…'>} a solid blue header. The resolver is immutable and cached; callers add it at every
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

    private static final String SEPARATOR = " » ";

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
                        prefixTag("tag", HEADER),
                        prefixTag("etag", BAD),
                        header("h"))
                .build();
    }

    private static TagResolver prefixTag(String name, TextColor labelColor) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String label = args.popOr(name + " requires a label").value();
            Component component = Component.text(label, labelColor).append(Component.text(SEPARATOR, TITLE));
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
