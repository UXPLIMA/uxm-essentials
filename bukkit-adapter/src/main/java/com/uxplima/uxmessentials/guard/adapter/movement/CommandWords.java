package com.uxplima.uxmessentials.guard.adapter.movement;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Matches a typed command against a region's {@code commands.deny} or {@code commands.allow} list.
 *
 * <p>An entry is a command word, optionally followed by leading arguments written with {@code _} ({@code warp_vip}),
 * since a list value splits on spaces. The word matches every name the command answers to, so an entry {@code home}
 * also catches {@code /h} and {@code /essentials:home}: a list that only knew the typed word would let every alias
 * through. Names come from a lookup, because on Paper an alias is its own command entry that knows nothing of the
 * command it points at, while the command itself lists its aliases; the two sides are therefore compared by the
 * names each one knows.
 */
public final class CommandWords {

    private CommandWords() {}

    /**
     * True when one entry of {@code list} names the typed command and, when the entry has arguments, the typed
     * arguments begin with them.
     *
     * @param list the comma separated entries, as the behaviour stores them
     * @param typed the command word as typed, lower case, with or without a namespace
     * @param args what was typed after the command word, lower case; empty when nothing was
     * @param namesOf every name a command word answers to, lower case and without namespaces; at least the bare word
     */
    public static boolean matches(String list, String typed, String args, Function<String, Set<String>> namesOf) {
        Set<String> typedNames = namesOf.apply(typed);
        for (String raw : list.split(",", -1)) {
            String entry = raw.strip().replace('_', ' ').toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            int space = entry.indexOf(' ');
            String word = space < 0 ? entry : entry.substring(0, space);
            String wantedArgs = space < 0 ? "" : entry.substring(space + 1).strip();
            if (Collections.disjoint(typedNames, namesOf.apply(word))) {
                continue;
            }
            if (wantedArgs.isEmpty() || args.equals(wantedArgs) || args.startsWith(wantedArgs + " ")) {
                return true;
            }
        }
        return false;
    }

    /** True when the typed word and {@code command} are names of one command. */
    public static boolean same(String typed, String command, Function<String, Set<String>> namesOf) {
        return !Collections.disjoint(namesOf.apply(typed), namesOf.apply(command));
    }

    /** A command word without its {@code plugin:} namespace. */
    public static String bare(String word) {
        int colon = word.indexOf(':');
        return colon < 0 ? word : word.substring(colon + 1);
    }
}
