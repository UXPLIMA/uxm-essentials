package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderShield;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Messages} implementation: resolve the viewer's locale, fetch the catalog template, and do
 * literal {@code {name}} placeholder substitution. The return is a plain MiniMessage source string
 * no Adventure type crosses this boundary, which is what keeps the kernel free of {@code net.kyori};
 * the tag parsing into a {@code Component} happens once downstream in {@link BukkitMessageSink}.
 *
 * <p>The viewer's locale is resolved per viewer by the {@link LocaleResolver} through the
 * override → request-scope → server-default → {@code en} chain, so two players on one server each see
 * their own language from the same call site, and a deferred message renders in the requester's locale
 * via the request-scope binding. The per-key {@code en} fallback chain itself lives behind the
 * {@link LocaleCatalog}; this class only chooses which locale to ask for.
 */
@NullMarked
public final class CatalogMessages implements Messages {

    private final LocaleCatalog catalog;
    private final LocaleResolver locales;

    public CatalogMessages(LocaleCatalog catalog, LocaleResolver locales) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.locales = Objects.requireNonNull(locales, "locales");
    }

    @Override
    public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return fill(template(viewer, key), Objects.requireNonNull(placeholders, "placeholders"), true);
    }

    @Override
    public String resolveAsWritten(PlayerRef viewer, MessageKey key, Map<String, String> arguments) {
        return fill(template(viewer, key), Objects.requireNonNull(arguments, "arguments"), false);
    }

    private String template(PlayerRef viewer, MessageKey key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Locale locale = locales.resolve(viewer);
        return catalog.template(locale, key);
    }

    /** A {@code {token}} argument in a catalog template; {@code group(1)} is the bare token name. */
    private static final Pattern ARGUMENT = Pattern.compile("\\{([A-Za-z0-9_.-]+)\\}");

    /**
     * Fill the {@code {token}} arguments a template spells, asking {@code placeholders} for each one by name.
     *
     * <p>Walking the map and replacing every key it holds gives the same answer for a map that is already a set of
     * values, and the wrong one for a map that resolves a token when it is asked for: uxmLib's menu renderer hands
     * over the placeholders a line names rather than every registered handler's output, so iterating it yields
     * nothing for an {@code @key} line, whose words live in the catalog rather than in the line. Asking by name is
     * what lets the catalog entry request the arguments it actually spells.
     *
     * <p>A token the map does not answer is left as written, so an operator still sees the token they typed rather
     * than a blank. A value is inserted once: a value that itself contains a brace token is never substituted again, so
     * the result does not depend on map order and one value cannot rewrite another.
     *
     * <p>The line is parsed as MiniMessage next, so a value is escaped ({@link StyledText#escape}) and reads as written:
     * a name or a message a player typed cannot open a tag. A value that is markup on purpose, such as a catalogue line
     * used inside this one, is passed in through {@link TrustedMarkup#of} and goes in as it is. Inside the quoted
     * argument of a click or an insertion, which MiniMessage keeps as text, only the quote and the backslash are
     * escaped, the way MiniMessage reads them there, so the command or the text to copy is exactly the value.
     *
     * <p>The line is handed to PlaceholderAPI for its reader after it is filled, so a value is shielded first (see
     * {@link PlaceholderShield}): a placeholder the operator wrote in the template is filled, and one inside a value,
     * which a player may have typed, reads as written. The marks come off again in a click or an insertion when the line
     * is drawn, since those carry a command, an address or text to copy that has to stay exact.
     */
    public static String fill(String template, Map<String, String> placeholders) {
        return fill(template, placeholders, true);
    }

    private static String fill(String template, Map<String, String> placeholders, boolean shield) {
        if (template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = ARGUMENT.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = placeholders.get(matcher.group(1));
            String inserted = value == null
                    ? matcher.group()
                    : shield ? asValue(value, quoteAround(template, matcher.start())) : TrustedMarkup.strip(value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(inserted));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * {@code value} as it goes into a line: escaped unless it is {@link TrustedMarkup trusted markup}, and shielded from
     * PlaceholderAPI either way. A character a token is built from is taken off a value first, so no value can pass
     * itself off as markup. {@code quote} is the quote of the action argument the value sits in, if it sits in one.
     */
    private static String asValue(String value, Optional<Character> quote) {
        String written = TrustedMarkup.markup(value).orElseGet(() -> {
            String text = TrustedMarkup.strip(value);
            return quote.map(mark -> asArgument(text, mark)).orElseGet(() -> StyledText.escape(text));
        });
        return PlaceholderShield.shield(written);
    }

    /** {@code text} inside an argument quoted with {@code quote}: MiniMessage reads a backslash before either back. */
    private static String asArgument(String text, char quote) {
        return text.replace("\\", "\\\\").replace(String.valueOf(quote), "\\" + quote);
    }

    /** The tags whose argument MiniMessage keeps as text rather than reading it as more MiniMessage. */
    private static final Pattern ACTION = Pattern.compile("<(?:click|insert|insertion):", Pattern.CASE_INSENSITIVE);

    /**
     * The quote of the click or insertion argument that position {@code at} of {@code template} sits in, or empty when
     * it sits anywhere else, an unquoted argument included, where a value is escaped like text.
     */
    private static Optional<Character> quoteAround(String template, int at) {
        int open = template.lastIndexOf('<', at);
        if (open < 0
                || !ACTION.matcher(template).region(open, template.length()).lookingAt()) {
            return Optional.empty();
        }
        char quote = 0;
        for (int i = open; i < at; i++) {
            char c = template.charAt(i);
            if (quote == 0 && c == '>') {
                return Optional.empty();
            }
            if (c == '\\' && quote != 0) {
                i++;
            } else if (quote == 0 && (c == '\'' || c == '"')) {
                quote = c;
            } else if (c == quote) {
                quote = 0;
            }
        }
        return quote == 0 ? Optional.empty() : Optional.of(quote);
    }
}
