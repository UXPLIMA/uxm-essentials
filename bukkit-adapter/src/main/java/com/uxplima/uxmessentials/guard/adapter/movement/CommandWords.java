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
 * <p>Two words name the same command when they belong to the same owner (the plugin that registered them, or the
 * server for its own commands) and share a name. On Paper an alias is its own command entry that knows nothing of
 * the command it points at, while the command lists its aliases, so names are compared both ways. The owner is
 * what keeps a third plugin out: its {@code /other:home}, or an alias it declares called {@code guard}, shares a
 * name but not the owner, so it neither passes an allow list nor slips under the {@code /guard} exemption.
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
     * True when one entry of {@code list} names the typed command and, when the entry has arguments, the typed
     * arguments begin with them.
     *
     * @param list the comma separated entries, as the behaviour stores them
     * @param typed the command word as typed, lower case, with or without a namespace
     * @param args what was typed after the command word, lower case; empty when nothing was
     * @param lookup how the server knows a command word
     */
    public static boolean matches(String list, String typed, String args, Function<String, Named> lookup) {
        Named command = lookup.apply(typed);
        for (String raw : list.split(",", -1)) {
            String entry = raw.strip().replace('_', ' ').toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            int space = entry.indexOf(' ');
            String word = space < 0 ? entry : entry.substring(0, space);
            String wantedArgs = space < 0 ? "" : entry.substring(space + 1).strip();
            if (!same(command, lookup.apply(word))) {
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
