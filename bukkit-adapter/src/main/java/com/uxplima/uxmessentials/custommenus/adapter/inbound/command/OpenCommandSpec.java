package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.NullMarked;

/**
 * The parsed {@code command {}} block of a custom menu: the operator-declared open command a menu registers for
 * itself. {@code name} is the primary literal ({@code /shop}), {@code aliases} the extra literals it answers to
 * ({@code /store}), {@code permission} an optional node gating who may open it, {@code denyMessage} the optional
 * MiniMessage line shown when that gate fails (operator content, rendered verbatim — not a catalog key), and
 * {@code consoleAllowed} whether a non-player sender may invoke it at all.
 *
 * <p>The record is the immutable value the {@link MenuOpenCommand} reads; parsing HOCON into one lives in the
 * loader. It validates at construction: {@code name} must be a single lowercase command word (no spaces), aliases
 * are normalised the same way and de-duplicated (dropping any blank, malformed, or self-referential entry), and a
 * blank {@code permission}/{@code deny-message} collapses to absent so an empty config value never gates or shows
 * an empty line.
 */
@NullMarked
public record OpenCommandSpec(
        String name,
        List<String> aliases,
        Optional<String> permission,
        Optional<String> denyMessage,
        boolean consoleAllowed) {

    /** A Brigadier command literal: lowercase letters, digits, underscores or hyphens, with no whitespace. */
    private static final Pattern COMMAND_WORD = Pattern.compile("[a-z0-9_-]+");

    public OpenCommandSpec {
        name = requireCommandWord(name);
        aliases = normaliseAliases(aliases, name);
        permission = Objects.requireNonNull(permission, "permission")
                .map(String::strip)
                .filter(node -> !node.isBlank());
        denyMessage = Objects.requireNonNull(denyMessage, "denyMessage").filter(line -> !line.isBlank());
    }

    /** Normalise a raw name to a single lowercase command word, rejecting a blank or whitespace-bearing value. */
    private static String requireCommandWord(String raw) {
        String normalised = Objects.requireNonNull(raw, "name").strip().toLowerCase(Locale.ROOT);
        if (!COMMAND_WORD.matcher(normalised).matches()) {
            throw new IllegalArgumentException("menu command name must be a single lowercase word: '" + raw + "'");
        }
        return normalised;
    }

    /**
     * Normalise the declared aliases: each is lowercased and trimmed, blanks and malformed tokens are dropped, an
     * alias equal to the primary name is dropped (the literal is already registered), and duplicates collapse — so
     * a typo in one alias never aborts the command, it is simply skipped.
     */
    private static List<String> normaliseAliases(List<String> raw, String name) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String alias : Objects.requireNonNull(raw, "aliases")) {
            if (alias == null) {
                continue;
            }
            String normalised = alias.strip().toLowerCase(Locale.ROOT);
            if (COMMAND_WORD.matcher(normalised).matches() && !normalised.equals(name)) {
                unique.add(normalised);
            }
        }
        return List.copyOf(unique);
    }
}
