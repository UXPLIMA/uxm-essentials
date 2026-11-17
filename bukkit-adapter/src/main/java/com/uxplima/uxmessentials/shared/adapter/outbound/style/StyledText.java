package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderShield;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.domain.message.TypedText;
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
        // A trusted-markup token is never drawn, whatever text carried it this far.
        String clean = TrustedMarkup.strip(source);
        Component drawn = Text.mini(StyleTokens.expand(clean, StyleTags.theme(), false), all);
        return PlaceholderShield.marked(clean) ? exactActions(drawn) : drawn;
    }

    /**
     * {@code drawn} with the placeholder marks taken off what its clicks and insertions carry. A value is marked where it
     * goes into a line (see {@link PlaceholderShield}); on screen the mark has no width, but a command, an address or the
     * text a click copies has to stay byte for byte what was written.
     */
    private static Component exactActions(Component drawn) {
        Style style = drawn.style();
        ClickEvent<?> click = style.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            style = style.clickEvent(exact(click, PlaceholderShield.unshield(text.value())));
        }
        String insertion = style.insertion();
        if (insertion != null) {
            style = style.insertion(PlaceholderShield.unshield(insertion));
        }
        List<Component> children =
                drawn.children().stream().map(StyledText::exactActions).toList();
        Component walked = drawn.style(style).children(children);
        // A <lang:...> line carries its arguments as components of their own, and a click can sit in one of them.
        if (walked instanceof TranslatableComponent translatable
                && !translatable.arguments().isEmpty()) {
            return translatable.arguments(translatable.arguments().stream()
                    .map(StyledText::exactArgument)
                    .toList());
        }
        return walked;
    }

    private static TranslationArgument exactArgument(TranslationArgument argument) {
        return argument.value() instanceof Component component
                ? TranslationArgument.component(exactActions(component))
                : argument;
    }

    private static ClickEvent<?> exact(ClickEvent<?> click, String value) {
        ClickEvent.Action<?> action = click.action();
        if (action.equals(ClickEvent.Action.OPEN_URL)) {
            return ClickEvent.openUrl(value);
        }
        if (action.equals(ClickEvent.Action.OPEN_FILE)) {
            return ClickEvent.openFile(value);
        }
        if (action.equals(ClickEvent.Action.RUN_COMMAND)) {
            return ClickEvent.runCommand(value);
        }
        if (action.equals(ClickEvent.Action.SUGGEST_COMMAND)) {
            return ClickEvent.suggestCommand(value);
        }
        if (action.equals(ClickEvent.Action.COPY_TO_CLIPBOARD)) {
            return ClickEvent.copyToClipboard(value);
        }
        return click;
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
        return TypedText.asWords(text);
    }
}
