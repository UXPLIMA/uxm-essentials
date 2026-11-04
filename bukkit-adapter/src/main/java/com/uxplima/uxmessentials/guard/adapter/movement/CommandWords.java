package com.uxplima.uxmessentials.guard.adapter.movement;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Matches a typed command against a region's {@code commands.deny} or {@code commands.allow} list.
 *
 * <p>An entry is a command word, optionally followed by leading arguments written with {@code _} ({@code warp_vip}),
 * since a list value splits on spaces. The word matches every name of the same command, so an entry {@code home}
 * also catches {@code /h} and {@code /uxmessentials:home}: a list that only knew the typed word would let every
 * alias through.
 *
 * <p>On Paper an alias is its own command entry that knows nothing of the command it points at, while the command
 * lists its aliases, so names are compared both ways. How strict the comparison is depends on which way a mistake
 * would fail. A deny list blocks any command sharing a name with an entry, whoever registered it, so
 * {@code /other:home} is blocked by {@code home} too. An allow list and the {@code /guard} exemption also need the
 * same owner (the plugin that registered the command, or the server for its own), so a third plugin's command
 * that merely declares a matching alias neither passes an allow list nor slips under the exemption.
 */
public final class CommandWords {

    private CommandWords() {}

    /**
     * A command word as the server knows it.
     *
     * @param owner the plugin that registered it, or a fixed name for the server's own commands; {@code null} when
     *     no command answers to the word
     * @param names every name of the command, lower case without namespaces; at least the bare word
     */
    public record Named(@Nullable String owner, Set<String> names) {
        public Named {
            names = Set.copyOf(names);
        }
    }

    /**
     * True when one entry of a deny list names the typed command: any shared name, whoever owns it.
     *
     * @param list the comma separated entries, as the behaviour stores them
     * @param typed the command word as typed, lower case, with or without a namespace
     * @param args what was typed after the command word, lower case; empty when nothing was
     * @param lookup how the server knows a command word
     */
    public static boolean denies(String list, String typed, String args, Function<String, Named> lookup) {
        return matches(list, typed, args, lookup, false);
    }

    /** True when one entry of an allow list names the typed command: a shared name and the same owner. */
    public static boolean allows(String list, String typed, String args, Function<String, Named> lookup) {
        return matches(list, typed, args, lookup, true);
    }

    private static boolean matches(
            String list, String typed, String args, Function<String, Named> lookup, boolean sameOwner) {
        Named command = lookup.apply(typed);
        for (String raw : list.split(",", -1)) {
            String entry = raw.strip().replace('_', ' ').toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            int space = entry.indexOf(' ');
            String word = space < 0 ? entry : entry.substring(0, space);
            String wantedArgs = space < 0 ? "" : entry.substring(space + 1).strip();
            Named listed = lookup.apply(word);
            boolean named = sameOwner ? same(command, listed) : !Collections.disjoint(command.names(), listed.names());
            if (!named) {
                continue;
            }
            if (wantedArgs.isEmpty() || args.equals(wantedArgs) || args.startsWith(wantedArgs + " ")) {
                return true;
            }
        }
        return false;
    }

    /** True when the two words name one command: the same owner and a shared name. */
    public static boolean same(Named a, Named b) {
        return Objects.equals(a.owner(), b.owner()) && !Collections.disjoint(a.names(), b.names());
    }

    /** A command word without its {@code plugin:} namespace. */
    public static String bare(String word) {
        int colon = word.indexOf(':');
        return colon < 0 ? word : word.substring(colon + 1);
    }
}
