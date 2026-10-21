package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.gui.style.MenuTitles;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.StyleTokens;
import org.jspecify.annotations.NullMarked;

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
     * {@code source} drawn as a window title: rendered as any line is, then placed where the theme puts titles, by the
     * library's {@link MenuTitles}, so a window of this plugin lines up with a window of every other.
     */
    public static Component title(String source) {
        return title(render(source));
    }

    /** {@code drawn} placed where the theme puts window titles. */
    public static Component title(Component drawn) {
        Objects.requireNonNull(drawn, "drawn");
        return MenuTitles.lay(drawn, StyleTags.theme().titleAlignment());
    }

    /**
     * {@code text} as typed, safe to put into a line: a name or a message somebody wrote reads as written, and
     * {@code <accent>} or {@code <click:...>} in it is the words, not the style.
     *
     * <p>Every {@code <} and every backslash is escaped, whatever follows it. Escaping only what parses as a tag is not
     * enough, because a line is read twice, by the token expansion and then by the parser, and the two disagree about
     * a malformed tag: an unterminated quote the parser passed over as text was expanded into markup, and a click
     * written after it ran.
     */
    public static String escape(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '<' || character == '\\') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
