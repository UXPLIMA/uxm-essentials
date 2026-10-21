package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.StyleTokens;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Turns a line into what a client is shown: the library's style tokens expanded with the theme in use, then parsed
 * with this plugin's own tags and whatever the caller adds. Every place that draws a line goes through here, so a
 * catalogue line, a menu tile an operator wrote and a reason a staff member typed read in the same look. Lore line
 * splitting on embedded newlines is handled downstream by uxmLib {@code ItemBuilder}.
 *
 * <p>A catalogue line arrives already styled, and expanding it again changes nothing, because what styling leaves is
 * no longer a token.
 */
@NullMarked
public final class StyledText {

    /** The tokens a heading or a prefix is written with, which the library expands. */
    private static final Set<String> LABEL_TOKENS = Set.of("tag", "etag", "h", "g");

    /** Every name a line could style with: a role of the theme, a label token, or a tag of this plugin. */
    private static final TagResolver STYLE_NAMES = new TagResolver() {
        @Override
        public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context ctx) {
            return null;
        }

        @Override
        public boolean has(String name) {
            return StyleTags.theme().hasColour(name)
                    || LABEL_TOKENS.contains(name)
                    || StyleTags.resolver().has(name);
        }
    };

    private StyledText() {}

    public static Component render(String source) {
        return render(source, new TagResolver[0]);
    }

    public static Component render(String source, TagResolver... extra) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(extra, "extra");
        TagResolver[] all = new TagResolver[extra.length + 1];
        all[0] = StyleTags.resolver();
        System.arraycopy(extra, 0, all, 1, extra.length);
        return Text.mini(StyleTokens.expand(source, StyleTags.theme(), false), all);
    }

    /**
     * {@code text} with every tag it could style with escaped, so a name or a message somebody typed reads as typed
     * when it is put into a line: {@code <accent>} in a chat message is the word, not the colour.
     */
    public static String escape(String text) {
        Objects.requireNonNull(text, "text");
        return MiniMessage.miniMessage().escapeTags(text, STYLE_NAMES);
    }
}
