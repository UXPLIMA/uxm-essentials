package com.uxplima.uxmessentials.customcommands.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a custom command answers to: its primary word, its plain aliases, and the aliases that belong to one
 * language only. The shape rules match the ones the shared command catalog already applies to a built-in command,
 * so a custom command can be renamed and realiased from {@code commands.conf} by the same code path.
 *
 * <p>Every collection is copied defensively, so a literal handed around after a load can never be edited under a
 * running command.
 */
public record CommandLiteral(String name, List<String> aliases, Map<String, List<String>> localizedAliases) {

    public CommandLiteral {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(localizedAliases, "localizedAliases");
        if (!validWord(name)) {
            throw new IllegalArgumentException("command name must be a single word without a leading slash: " + name);
        }
        aliases = List.copyOf(aliases);
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        localizedAliases.forEach((locale, words) -> snapshot.put(locale, List.copyOf(words)));
        localizedAliases = Map.copyOf(snapshot);
    }

    /** A literal with no aliases at all, which is what a file declaring only a name produces. */
    public static CommandLiteral of(String name) {
        return new CommandLiteral(name, List.of(), Map.of());
    }

    /**
     * Whether {@code candidate} may be registered as a command word: non-blank, no whitespace, no leading slash.
     * The loader uses it to drop one bad alias with a warning rather than losing the whole file.
     */
    public static boolean validWord(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.charAt(0) == '/') {
            return false;
        }
        return candidate.codePoints().noneMatch(Character::isWhitespace);
    }

    /** A copy carrying {@code replacement} in place of the current aliases; used when a collision drops one. */
    public CommandLiteral withAliases(List<String> replacement) {
        return new CommandLiteral(name, replacement, localizedAliases);
    }

    /** A copy carrying {@code replacement} in place of the current per-locale aliases. */
    public CommandLiteral withLocalizedAliases(Map<String, List<String>> replacement) {
        return new CommandLiteral(name, aliases, replacement);
    }

    /** Every word this literal claims, primary first, in a stable order. */
    public List<String> allWords() {
        List<String> words = new java.util.ArrayList<>();
        words.add(name);
        words.addAll(aliases);
        localizedAliases.values().forEach(words::addAll);
        return List.copyOf(words);
    }
}
