package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.NullMarked;

/**
 * Parses an already-resolved catalog/MiniMessage source string into a {@code Component} with the project
 * {@link StyleTags} applied. GUI views use this instead of calling MiniMessage directly, so item names and
 * lore pick up the same {@code <accent>}/{@code <h:'…'>}/etc. tokens the chat sink uses. Lore line splitting
 * on embedded newlines is handled downstream by uxmLib {@code ItemBuilder}.
 */
@NullMarked
public final class StyledText {

    private StyledText() {}

    public static Component render(String source) {
        Objects.requireNonNull(source, "source");
        return Text.mini(source, StyleTags.resolver());
    }

    public static Component render(String source, TagResolver... extra) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(extra, "extra");
        TagResolver[] all = new TagResolver[extra.length + 1];
        all[0] = StyleTags.resolver();
        System.arraycopy(extra, 0, all, 1, extra.length);
        return Text.mini(source, all);
    }
}
