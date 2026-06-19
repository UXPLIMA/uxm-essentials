package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.jspecify.annotations.NullMarked;

/**
 * The project palette as MiniMessage tags. Solid-colour tags ({@code <accent>}, {@code <body>}, …) wrap
 * their content; the composite tags build a styled component from an argument: {@code <tag:'HOME'>} renders
 * the bracketed gold-gradient chat prefix, {@code <etag:'…'>} its red error variant, and {@code <h:'…'>} a
 * gold-gradient bold header. The resolver is immutable and cached; callers add it at every MiniMessage parse.
 */
@NullMarked
public final class StyleTags {

    public static final TextColor ACCENT = TextColor.color(0x45cdf9);
    public static final TextColor BODY = TextColor.color(0xfcfce3);
    public static final TextColor CTA = TextColor.color(0xff5733);
    public static final TextColor MONEY = TextColor.color(0x2ecc71);
    public static final TextColor BAD = TextColor.color(0xe63946);
    public static final TextColor LEVEL = TextColor.color(0xffe66d);
    public static final TextColor MUTED = TextColor.color(0xa9a9a9);
    public static final TextColor TITLE_FROM = TextColor.color(0xfc7a00);
    public static final TextColor TITLE_TO = TextColor.color(0xfcc600);
    public static final TextColor ERR_FROM = TextColor.color(0xe62314);
    public static final TextColor ERR_TO = TextColor.color(0xec6116);

    private static final String BRACKET_OPEN = "「 ";
    private static final String BRACKET_CLOSE = " 」";

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
                        prefixTag("tag", TITLE_FROM, TITLE_TO),
                        prefixTag("etag", ERR_FROM, ERR_TO),
                        header("h", TITLE_FROM, TITLE_TO))
                .build();
    }

    private static TagResolver prefixTag(String name, TextColor from, TextColor to) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String label = args.popOr(name + " requires a label").value();
            Component component = Component.text(BRACKET_OPEN, MUTED)
                    .append(gradient(label, from, to).decorate(TextDecoration.BOLD))
                    .append(Component.text(BRACKET_CLOSE, MUTED));
            return Tag.selfClosingInserting(component);
        });
    }

    private static TagResolver header(String name, TextColor from, TextColor to) {
        return TagResolver.resolver(name, (ArgumentQueue args, Context ctx) -> {
            String text = args.popOr(name + " requires text").value();
            return Tag.selfClosingInserting(gradient(text, from, to).decorate(TextDecoration.BOLD));
        });
    }

    private static Component gradient(String text, TextColor from, TextColor to) {
        int length = text.length();
        if (length == 0) {
            return Component.empty();
        }
        Component result = Component.empty();
        for (int i = 0; i < length; i++) {
            float t = length == 1 ? 0f : (float) i / (length - 1);
            result = result.append(Component.text(text.charAt(i), TextColor.lerp(t, from, to)));
        }
        return result;
    }
}
