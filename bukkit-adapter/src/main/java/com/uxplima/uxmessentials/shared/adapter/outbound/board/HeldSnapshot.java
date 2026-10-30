package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.uxmlib.board.Ranked;
import com.uxplima.uxmlib.board.Snapshot;

/**
 * A snapshot board read whole from a module's own data at every refresh and held in memory, so a player's own score
 * answers without the database. Fit for a value only some players hold above zero, such as a prestige, or one the
 * module keeps a row per player for, such as the playtime ledger.
 *
 * <p>{@link #top} reads the source and {@link #countByScore} counts what that read held, which is the order
 * {@code Leaderboard.refresh()} calls them in. Of equal scores, the lower id ranks first, so the order is stable.
 */
final class HeldSnapshot implements Snapshot {

    private final Supplier<Map<UUID, Long>> source;
    private volatile Map<UUID, Long> held = Map.of();

    /** @param source every player's score above zero; called on the async lane, may block */
    HeldSnapshot(Supplier<Map<UUID, Long>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public List<Ranked> top(int limit) {
        held = Map.copyOf(source.get());
        return held.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new Ranked(entry.getKey(), null, entry.getValue()))
                .toList();
    }

    @Override
    public Map<Long, Integer> countByScore() {
        Map<Long, Integer> counted = new HashMap<>();
        held.values().stream().filter(score -> score > 0).forEach(score -> counted.merge(score, 1, Integer::sum));
        return Map.copyOf(counted);
    }

    @Override
    public OptionalLong scoreOf(UUID player) {
        Long score = held.get(player);
        return score == null ? OptionalLong.empty() : OptionalLong.of(score);
    }

    /** Move one player's score before the next read, so a prestige shows at once. */
    void set(UUID player, long score) {
        Map<UUID, Long> next = new HashMap<>(held);
        next.put(player, score);
        held = Map.copyOf(next);
    }
}
