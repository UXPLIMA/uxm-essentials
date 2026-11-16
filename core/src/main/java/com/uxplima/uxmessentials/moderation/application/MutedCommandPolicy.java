package com.uxplima.uxmessentials.moderation.application;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.moderation.application.port.CommandNames;

/**
 * The pure rule that closes the mute-bypass via chat-adjacent commands: a muted player can still type
 * {@code /me}, {@code /msg}, {@code /mail} and friends to broadcast text the mute was meant to silence, so an
 * operator lists those command names in {@code moderation.muted-blocked-commands} and this policy answers
 * "may a muted player run this command label?" against that list. The messaging mute gate already blocks the
 * use cases this plugin owns ({@code /msg}, {@code /mail}, {@code /helpop}); this policy is the wider net that
 * also catches third-party and vanilla commands an operator names.
 *
 * <p>The match ignores case, a leading slash and a {@code plugin:} namespace, so the entry {@code msg} also stops
 * {@code /minecraft:msg} and {@code /essentials:msg}: a list that had to name every namespaced form would leave a
 * gap for each plugin that registers one. An entry also stops every other name of the same command, as far as the
 * server's command list tells them apart. On Paper an alias can be its own entry that knows nothing of the command
 * it points at while the command lists its aliases, so names are compared both ways. The cheap checks run first:
 * the typed word against the list, then the typed command's names, and only then the names of each listed entry,
 * a handful of in-memory lookups for a command that passed the first two.
 *
 * <p>The domain never reads the live mute state or the wall clock; the adapter consults
 * {@link com.uxplima.uxmessentials.moderation.domain.MuteState} after this policy says the label is blocked.
 */
public final class MutedCommandPolicy {

    private final Set<String> blocked;
    private final CommandNames names;

    /**
     * @param blockedCommands the configured command words a muted player may not run, in any case; a leading
     *     slash or a namespace on an entry is tolerated and stripped, so {@code "/me"}, {@code "minecraft:me"} and
     *     {@code "me"} are equivalent
     * @param names how the server knows a command word
     */
    public MutedCommandPolicy(Set<String> blockedCommands, CommandNames names) {
        Objects.requireNonNull(blockedCommands, "blockedCommands");
        this.names = Objects.requireNonNull(names, "names");
        this.blocked = blockedCommands.stream()
                .filter(Objects::nonNull)
                .map(entry -> bare(word(entry)))
                .filter(label -> !label.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True when {@code label}, the command word as typed, is a command a muted player is forbidden to run. */
    public boolean isBlocked(String label) {
        Objects.requireNonNull(label, "label");
        String typed = word(label);
        String bare = bare(typed);
        if (bare.isEmpty()) {
            return false;
        }
        if (blocked.contains(bare) || !Collections.disjoint(names.namesOf(typed), blocked)) {
            return true;
        }
        for (String entry : blocked) {
            if (names.namesOf(entry).contains(bare)) {
                return true;
            }
        }
        return false;
    }

    /** True when no command is blocked, so the adapter can skip the mute lookup entirely. */
    public boolean isEmpty() {
        return blocked.isEmpty();
    }

    /** The command word without a leading slash, in lower case. */
    private static String word(String label) {
        String trimmed = label.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** A command word without its {@code plugin:} namespace. */
    private static String bare(String word) {
        int colon = word.indexOf(':');
        return colon < 0 ? word : word.substring(colon + 1);
    }
}
