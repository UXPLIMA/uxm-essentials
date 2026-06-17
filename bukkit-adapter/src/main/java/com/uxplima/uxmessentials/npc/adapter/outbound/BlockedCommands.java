package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NullMarked;

/**
 * The set of command labels an NPC's bound command or click action may not run, matched case-insensitively on the
 * command's first word (its label) with any leading slash stripped. An empty set blocks nothing — the default, so
 * a server that configures no blocklist behaves exactly as before. Pure of any Bukkit reference so the matching is
 * unit-testable; {@link FilteredNpcCommandRunner} consults it before every NPC-driven dispatch, and the npc module
 * config supplies the labels. The blocklist guards a player-owned NPC's command actions from invoking dangerous
 * operator commands (the {@code uxmessentials.npc.admin} surface can be granted more broadly once NPCs are owned).
 */
@NullMarked
public final class BlockedCommands {

    private final Set<String> blocked;

    private BlockedCommands(Set<String> blocked) {
        this.blocked = blocked;
    }

    /** Build a blocklist from the configured labels; blank entries are dropped and each is normalised to its label. */
    public static BlockedCommands of(List<String> labels) {
        Objects.requireNonNull(labels, "labels");
        Set<String> normalized = labels.stream()
                .map(BlockedCommands::label)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new BlockedCommands(normalized);
    }

    /** True when {@code command}'s first word (slash-stripped, lower-cased) is on the blocklist. */
    public boolean isBlocked(String command) {
        Objects.requireNonNull(command, "command");
        return blocked.contains(label(command));
    }

    /** True when nothing is blocked, so the decorator can skip wrapping entirely. */
    public boolean isEmpty() {
        return blocked.isEmpty();
    }

    /** The command's label: trimmed, leading slash removed, first whitespace-delimited word, lower-cased. */
    private static String label(String command) {
        String trimmed = command.strip();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        String word = space < 0 ? trimmed : trimmed.substring(0, space);
        return word.toLowerCase(Locale.ROOT);
    }
}
