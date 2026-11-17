package com.uxplima.uxmessentials.guard.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jspecify.annotations.Nullable;

/**
 * Durable regions, their change history and the optional denial log. Every call blocks and runs on the async
 * lane. A write stores the region change and its history entry in one transaction, so history never disagrees
 * with the regions.
 */
public interface GuardStore {

    /** Every region in the given scopes; throws {@link GuardStoreException} when they cannot be read. */
    List<Region> load(Set<ServerScope> scopes);

    /** One region, or empty. */
    Optional<Region> find(ServerScope scope, RegionKey key);

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
    long stamp(Set<ServerScope> scopes);

    /** Append denials to the log. */
    void logDenials(List<Denial> denials);

    /** Logged denials, newest first, filtered by player or region id when given. */
    List<Denial> denials(@Nullable DenialPlayer player, @Nullable String region, int offset, int limit);

    /** Drop logged denials older than {@code before}. */
    void pruneDenials(Instant before);

    /**
     * One logged denial.
     *
     * @param at when
     * @param playerId the player's account; empty on a denial logged before accounts were kept
     * @param player the player's name
     * @param world the world
     * @param x block x
     * @param y block y
     * @param z block z
     * @param region the deciding region id, or {@code -}
     * @param action the action id
     */
    record Denial(
            Instant at,
            Optional<UUID> playerId,
            String player,
            String world,
            int x,
            int y,
            int z,
            String region,
            String action) {

        public Denial {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    /**
     * Whose denials to read. A denial logged with its account is matched by the account alone, so two accounts whose
     * names differ only in case stay apart; one logged before accounts were kept is matched by its name.
     *
     * @param account the player's account, empty when the name named no single account
     * @param name the name as asked for
     */
    record DenialPlayer(Optional<UUID> account, String name) {

        public DenialPlayer {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(name, "name");
        }
    }
}
