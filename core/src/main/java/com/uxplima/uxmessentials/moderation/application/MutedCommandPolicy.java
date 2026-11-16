package com.uxplima.uxmessentials.moderation.application;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.moderation.application.port.CommandNames;
import org.jspecify.annotations.Nullable;

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
 * gap for each plugin that registers one. An entry also stops every name of the command that owns the word, as far
 * as the server's command list tells them apart. On Paper an alias can be its own entry that knows nothing of the
 * command it points at while the command lists its aliases, so names are compared both ways.
 *
 * <p>That reach can catch a command the operator meant to leave alone (a ticket plugin's {@code /ticketreply} that
 * lists {@code reply} as an alias), so {@code muted-allowed-commands} is checked first: a typed word on it always
 * runs. That list matches the typed word alone, without the namespace and without looking up other names, so it
 * frees exactly the word written and nothing else.
 *
 * <p>This runs for every command any player types, before the mute is read. The names of the listed entries do not
 * depend on what was typed, so they are resolved once and kept until {@link #forgetCommandNames} (a plugin was
 * enabled or disabled, the server finished loading, or the plugin reloaded); a check is then string set lookups
 * and one lookup of the typed command. The domain never reads the live mute state or the wall clock; the adapter
 * consults {@link com.uxplima.uxmessentials.moderation.domain.MuteState} after this policy says the label is blocked.
 */
public final class MutedCommandPolicy {

    private final Set<String> blocked;
    private final Set<String> allowed;
    private final CommandNames names;
    private final AtomicLong generation = new AtomicLong();
    private volatile @Nullable Resolved listedNames;

    /**
     * @param blockedCommands the configured command words a muted player may not run, in any case; a leading
     *     slash or a namespace on an entry is tolerated and stripped, so {@code "/me"}, {@code "minecraft:me"} and
     *     {@code "me"} are equivalent
     * @param allowedCommands the configured words a muted player may always run, written and stripped the same way
     * @param names how the server knows a command word
     */
    public MutedCommandPolicy(Set<String> blockedCommands, Set<String> allowedCommands, CommandNames names) {
        this.blocked = words(Objects.requireNonNull(blockedCommands, "blockedCommands"));
        this.allowed = words(Objects.requireNonNull(allowedCommands, "allowedCommands"));
        this.names = Objects.requireNonNull(names, "names");
    }

    /** True when {@code label}, the command word as typed, is a command a muted player is forbidden to run. */
    public boolean isBlocked(String label) {
        Objects.requireNonNull(label, "label");
        String typed = word(label);
        String bare = bare(typed);
        if (bare.isEmpty() || blocked.isEmpty() || allowed.contains(bare)) {
            return false;
        }
        if (blocked.contains(bare) || listedNames().contains(bare)) {
            return true;
        }
        return !Collections.disjoint(names.namesOf(typed), blocked);
    }

    /** True when no command is blocked, so the adapter can skip the mute lookup entirely. */
    public boolean isEmpty() {
        return blocked.isEmpty();
    }

    /** Drop the resolved names of the listed entries, so the next check reads them from the server again. */
    public void forgetCommandNames() {
        generation.incrementAndGet();
    }

    /**
     * Every name of every listed entry's command; resolved on first use and kept until forgotten. A set is tagged with
     * the generation it was resolved in, and one from before the latest forget is never used, even when a slow
     * resolve stores it after the forget.
     */
    private Set<String> listedNames() {
        long current = generation.get();
        Resolved known = listedNames;
        if (known != null && known.generation() == current) {
            return known.names();
        }
        Set<String> resolved = new HashSet<>();
        for (String entry : blocked) {
            resolved.addAll(names.namesOf(entry));
        }
        Set<String> fresh = Set.copyOf(resolved);
        listedNames = new Resolved(current, fresh);
        return fresh;
    }

    /** The listed entries' names as resolved in one generation. */
    private record Resolved(long generation, Set<String> names) {}

    private static Set<String> words(Set<String> entries) {
        return entries.stream()
                .filter(Objects::nonNull)
                .map(entry -> bare(word(entry)))
                .filter(label -> !label.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
