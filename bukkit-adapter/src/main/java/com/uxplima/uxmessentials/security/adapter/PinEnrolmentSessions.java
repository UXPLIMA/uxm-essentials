package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * Who is at the create-a-PIN pad, and what they typed the first time.
 *
 * <p>This is the state a two-step PIN creation needs and nothing else: a player who is being made to set a PIN is
 * present in the map, and their entry is either {@link #NONE} (they have not submitted anything yet) or the PIN they
 * submitted first, waiting for a second submission to match it. Requiring the same PIN twice is what stops a mistyped
 * PIN becoming a PIN nobody knows: the mistake is caught here, in a window the player can still leave, rather than the
 * next time they log in and cannot get past the keypad.
 *
 * <p>The first entry is a plaintext PIN in flight. It lives here for the few seconds between the two submissions,
 * server-side only, is never logged and never rendered, and is dropped the moment the pair is compared, whether they
 * matched or not. Everything is dropped on {@link #clearAll()} at module stop.
 */
@NullMarked
public final class PinEnrolmentSessions {

    /** The placeholder for "at the pad, nothing submitted yet"; the map cannot hold a null value. */
    private static final String NONE = "";

    private final Map<UUID, String> entries = new ConcurrentHashMap<>();

    /** Put {@code playerId} at the create pad with nothing submitted yet. */
    public void begin(UUID playerId) {
        entries.put(Objects.requireNonNull(playerId, "playerId"), NONE);
    }

    /** Whether {@code playerId} is being made to create a PIN. */
    public boolean isPending(UUID playerId) {
        return entries.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /** The PIN {@code playerId} submitted first, empty when they are at the first step (or not at the pad at all). */
    public Optional<String> firstEntry(UUID playerId) {
        String entry = entries.get(Objects.requireNonNull(playerId, "playerId"));
        return entry == null || entry.equals(NONE) ? Optional.empty() : Optional.of(entry);
    }

    /** Remember {@code pin} as the first of the two submissions, so the next one can be compared against it. */
    public void rememberFirst(UUID playerId, String pin) {
        Objects.requireNonNull(pin, "pin");
        entries.computeIfPresent(Objects.requireNonNull(playerId, "playerId"), (id, previous) -> pin);
    }

    /** Forget the first submission but keep the player at the pad, so a mismatch restarts rather than ends. */
    public void restart(UUID playerId) {
        entries.computeIfPresent(Objects.requireNonNull(playerId, "playerId"), (id, previous) -> NONE);
    }

    /** Take {@code playerId} off the create pad and drop whatever they had entered. */
    public void clear(UUID playerId) {
        entries.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every create-pad session, called on module stop so no half-entered PIN survives a disable. */
    public void clearAll() {
        entries.clear();
    }
}
