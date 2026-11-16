package com.uxplima.uxmessentials.customcommands.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The re-entry guard: how many times one player may be inside a custom command chain at once. A command whose
 * chain runs another command that runs the first would otherwise recurse until something gave way; here the second
 * entry past the limit is simply refused, and the refusal is a normal outcome rather than a crash.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The counter is a {@link ConcurrentHashMap} keyed by player uuid,
 * mutated only through {@code compute}, so a command dispatched from two threads resolves to one loser rather than
 * a lost update. An entry is removed the moment its count reaches zero, so an idle player holds nothing.
 */
public final class ChainDepth {

    private final ConcurrentHashMap<UUID, Integer> depth = new ConcurrentHashMap<>();
    private final int max;

    public ChainDepth(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max chain depth must be at least 1: " + max);
        }
        this.max = max;
    }

    /**
     * Claim one level of depth for {@code who}, or answer false when they are already at the limit. A refused
     * claim consumes nothing, so the caller must not call {@link #exit} for it.
     */
    public boolean enter(UUID who) {
        return enter(who, max);
    }

    /**
     * Claim one level for a run that ends another run of {@code who}'s, one that still holds a level, before it needs
     * its own. One level past the ceiling is allowed for that moment, so the replacement is never refused for the
     * level of the run it ends; once that run releases, the count is back within the ceiling. A refused claim consumes
     * nothing, as with {@link #enter}.
     */
    public boolean enterReplacing(UUID who) {
        return enter(who, max + 1);
    }

    private boolean enter(UUID who, int ceiling) {
        Objects.requireNonNull(who, "who");
        // compute reports the value after the update, not whether it changed, so the decision is carried out of
        // the remapping function. The flag is written inside the map's own lock, so two threads cannot both win.
        AtomicBoolean granted = new AtomicBoolean();
        depth.compute(who, (key, current) -> {
            int now = current == null ? 0 : current;
            if (now >= ceiling) {
                return now;
            }
            granted.set(true);
            return now + 1;
        });
        return granted.get();
    }

    /** Release one level of depth for {@code who}; the entry disappears once nothing is held. */
    public void exit(UUID who) {
        Objects.requireNonNull(who, "who");
        depth.compute(who, (key, current) -> current == null || current <= 1 ? null : current - 1);
    }

    /** Drop every held level, for a module stop. */
    public void clear() {
        depth.clear();
    }

    /** How many players currently hold any depth, for tests and diagnostics. */
    public int tracked() {
        return depth.size();
    }

    /** The configured ceiling. */
    public int max() {
        return max;
    }
}
