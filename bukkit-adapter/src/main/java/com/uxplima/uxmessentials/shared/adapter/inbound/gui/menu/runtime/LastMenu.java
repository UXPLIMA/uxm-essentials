package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * Per-player memory of the last custom menu each viewer had open, so {@code /menu last} can reopen it with the same
 * page and typed command arguments. Only a subject-less menu — a disk-loaded custom menu — is ever recorded; a
 * feature menu carries a live domain subject that must not be reopened blind, and it has its own command, so the
 * engine deliberately never remembers one here.
 *
 * <p>There is at most one entry per player: recording a new open overwrites the previous one, so the map is
 * naturally bounded by the online roster while players stay online. {@link #clear} closes the one remaining gap — a
 * player who logs off and never returns — so a quit listener can drop the entry and keep the map tracking only the
 * online set.
 *
 * <p>Bukkit-free (only {@code java.util}) and thread-safe: the backing map is a {@link ConcurrentHashMap}, so the
 * record on the viewer's entity thread and a clear on the quit thread never corrupt each other.
 */
@NullMarked
public final class LastMenu {

    /**
     * One remembered open: the menu id, the page it was shown on, and the typed command arguments it carried.
     * Immutable — the arguments are defensively copied so a later mutation of the caller's map cannot rewrite a
     * player's remembered open.
     */
    public record LastOpen(String menuId, int page, Map<String, String> arguments) {
        public LastOpen {
            Objects.requireNonNull(menuId, "menuId");
            arguments = Map.copyOf(arguments);
        }
    }

    private final Map<UUID, LastOpen> byPlayer = new ConcurrentHashMap<>();

    /** Remember {@code open} as {@code player}'s reopen target, overwriting any previous one. */
    public void record(UUID player, LastOpen open) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(open, "open");
        byPlayer.put(player, open);
    }

    /** The menu {@code player} last had open, or empty if none has been recorded (or it was cleared). */
    public Optional<LastOpen> get(UUID player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(byPlayer.get(player));
    }

    /** Forget {@code player}'s remembered open — called on quit so the map never retains an offline player. */
    public void clear(UUID player) {
        Objects.requireNonNull(player, "player");
        byPlayer.remove(player);
    }
}
