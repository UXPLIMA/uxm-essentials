package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of the recent command timestamps one player has sent, the per-player state the command-spam
 * guard folds forward. It is a plain value: {@link #record(long, long)} returns a new window with the entries older than
 * the sliding interval dropped and the new timestamp appended, so the adapter's {@code ConcurrentHashMap<UUID,
 * CommandWindow>} is mutated only through a {@code compute} that swaps one immutable value for the next, never a shared
 * mutable buffer.
 *
 * <p>Timestamps are epoch milliseconds; the window keeps them in send order so the oldest is pruned first. The count is
 * simply the list size, which the {@link CommandRateLimiter} compares against the configured maximum.
 *
 * @param timestamps the retained command send-times (epoch millis), oldest first; always defensively copied
 */
public record CommandWindow(List<Long> timestamps) {

    public CommandWindow {
        Objects.requireNonNull(timestamps, "timestamps");
        timestamps = List.copyOf(timestamps);
    }

    /** An empty window - the state for a player who has not sent a command inside the interval yet. */
    public static CommandWindow empty() {
        return new CommandWindow(List.of());
    }

    /**
     * Fold {@code nowMillis} into the window: drop every timestamp older than {@code nowMillis - windowMillis} (outside
     * the sliding interval), then append {@code nowMillis}. Returns a new window; this one is unchanged.
     *
     * @param nowMillis the send-time of the command being recorded (epoch millis)
     * @param windowMillis the sliding-interval width in millis; a non-positive width keeps only the new entry
     */
    public CommandWindow record(long nowMillis, long windowMillis) {
        long cutoff = nowMillis - windowMillis;
        List<Long> kept = new ArrayList<>(timestamps.size() + 1);
        for (long stamp : timestamps) {
            if (stamp > cutoff) {
                kept.add(stamp);
            }
        }
        kept.add(nowMillis);
        return new CommandWindow(kept);
    }

    /** How many commands are retained in the current interval - the value compared against the configured maximum. */
    public int size() {
        return timestamps.size();
    }
}
