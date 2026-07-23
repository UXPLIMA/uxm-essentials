package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The pure sliding-window decision behind command-spam protection. Built once from config, it holds the enable gate,
 * the maximum number of commands allowed inside the interval, the interval width, and the {@link SpamAction} to apply
 * on a breach. Given a player's previous {@link CommandWindow} and the current time it folds the new command in and
 * reports whether the player has now exceeded the limit.
 *
 * <p>Nothing here is Bukkit-aware: the adapter owns the {@code ConcurrentHashMap<UUID, CommandWindow>} and the bypass /
 * kick / cancel side effects, and calls {@link #evaluate} inside its {@code compute} so the count and the trip decision
 * are one atomic step per command. The rule is a plain function that unit-tests exactly.
 *
 * <p>The breach test is strictly-greater-than: with {@code maxPerWindow = N}, the first {@code N} commands in the
 * interval are fine and the {@code N + 1}-th trips, matching the operator's reading of "at most N commands per window".
 */
public final class CommandRateLimiter {

    private final boolean enabled;
    private final int maxPerWindow;
    private final long windowMillis;
    private final SpamAction action;

    private CommandRateLimiter(boolean enabled, int maxPerWindow, long windowMillis, SpamAction action) {
        this.enabled = enabled;
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowMillis;
        this.action = action;
    }

    /**
     * Build a rate limiter.
     *
     * @param enabled whether the command-spam guard is switched on
     * @param maxPerWindow the greatest number of commands allowed inside the interval (must be positive)
     * @param windowSeconds the sliding-interval width in seconds (must be positive)
     * @param action what to do when a player exceeds the limit
     */
    public static CommandRateLimiter of(boolean enabled, int maxPerWindow, int windowSeconds, SpamAction action) {
        Objects.requireNonNull(action, "action");
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException("maxPerWindow must be positive");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        return new CommandRateLimiter(enabled, maxPerWindow, windowSeconds * 1000L, action);
    }

    /**
     * Fold {@code nowMillis} into {@code previous} and decide. Returns the updated window (for the caller to store) and
     * whether the player has now exceeded the limit. A {@code null} previous is treated as an empty window (the
     * player's first command in the interval).
     */
    public Evaluation evaluate(@Nullable CommandWindow previous, long nowMillis) {
        CommandWindow base = previous == null ? CommandWindow.empty() : previous;
        CommandWindow updated = base.record(nowMillis, windowMillis);
        return new Evaluation(updated, updated.size() > maxPerWindow);
    }

    /** True while the guard is switched on; the adapter's listener does nothing when it is off. */
    public boolean isEnabled() {
        return enabled;
    }

    /** The action to apply to a player who exceeds the limit. */
    public SpamAction action() {
        return action;
    }

    /**
     * The outcome of {@link #evaluate}: the window to store back for the player and whether this command tripped the
     * limit. When {@link #tripped()} is true the adapter applies the configured {@link SpamAction}.
     *
     * @param window the folded-forward window to store back under the player's uuid
     * @param tripped whether the player has now exceeded the configured maximum
     */
    public record Evaluation(CommandWindow window, boolean tripped) {

        public Evaluation {
            Objects.requireNonNull(window, "window");
        }
    }
}
