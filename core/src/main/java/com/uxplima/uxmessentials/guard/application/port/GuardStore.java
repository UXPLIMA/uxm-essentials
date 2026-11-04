package com.uxplima.uxmessentials.guard.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import org.jspecify.annotations.Nullable;

/**
 * Durable regions, their change history and the optional denial log. Every call blocks and runs on the async
 * lane. A write stores the region change and its history entry in one transaction, so history never disagrees
 * with the regions.
 */
public interface GuardStore {

    /** Every region in the given scopes; throws {@link GuardStoreException} when they cannot be read. */
    List<Region> load(Set<GuardScope> scopes);

    /** One region, or empty. */
    Optional<Region> find(GuardScope scope, RegionKey key);

    /**
     * Store a change: {@code after} replaces {@code before} (a create when {@code before} is null, a removal when
     * {@code after} is null). Returns the stored history entry.
     */
    HistoryEntry write(@Nullable Region before, @Nullable Region after, String actor, String change);

    /** History entries, newest first; all regions when {@code key} is null. */
    List<HistoryEntry> history(@Nullable RegionKey key, int offset, int limit);

    /** One history entry by id. */
    Optional<HistoryEntry> historyEntry(long id);

    /** A number that changes whenever a region in the scopes changes, for polling peers without Redis. */
    long stamp(Set<GuardScope> scopes);

    /** Append denials to the log. */
    void logDenials(List<Denial> denials);

    /** Logged denials, newest first, filtered by player name or region id when given. */
    List<Denial> denials(@Nullable String player, @Nullable String region, int offset, int limit);

    /** Drop logged denials older than {@code before}. */
    void pruneDenials(Instant before);

    /**
     * One logged denial.
     *
     * @param at when
     * @param player the player's name
     * @param world the world
     * @param x block x
     * @param y block y
     * @param z block z
     * @param region the deciding region id, or {@code -}
     * @param action the action id
     */
    record Denial(Instant at, String player, String world, int x, int y, int z, String region, String action) {}
}
