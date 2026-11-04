package com.uxplima.uxmessentials.guard.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One change to one region, with the region before and after, so it can be shown and undone. A created region
 * has no {@code before}; a removed one has no {@code after}.
 *
 * @param id the store's sequence number, 0 before it is stored
 * @param at when it happened
 * @param actor who did it: a player name, {@code console}, {@code import} or {@code sync}
 * @param key the region
 * @param change a short word: create, redefine, remove, rule, role, set, priority, parent, family, tag,
 *     template, scope, lock, restore, undo, import
 * @param before the region before the change
 * @param after the region after the change
 */
public record HistoryEntry(
        long id,
        Instant at,
        String actor,
        RegionKey key,
        String change,
        @Nullable Region before,
        @Nullable Region after) {

    public HistoryEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(change, "change");
        if (before == null && after == null) {
            throw new IllegalArgumentException("a history entry needs a before or an after");
        }
    }

    public Optional<Region> beforeRegion() {
        return Optional.ofNullable(before);
    }

    public Optional<Region> afterRegion() {
        return Optional.ofNullable(after);
    }
}
