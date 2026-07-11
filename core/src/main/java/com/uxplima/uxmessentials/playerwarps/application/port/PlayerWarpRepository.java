package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for durable player-warp storage. Every warp fact (owner, name, world, coordinates, access,
 * status, economy, and the rating/visit rollups) is a first-class column — there is no opaque JSON blob (the
 * architecture persistence invariant) — so a {@link PlayerWarp} loaded from a row is rebuilt from queryable
 * fields, and the list queries read them in stored creation order.
 *
 * <p>Warp names are now server-wide unique, so lookups key on the {@link PlayerWarpName} or the surrogate
 * {@link PlayerWarpId}, never on {@code (owner, name)}. A {@link #save} upserts on the surrogate id — assigning a
 * fresh one on insert and returning it — and a delete is by id. A cache decorator may sit in front of this port;
 * the contract here is the durable source of truth.
 */
public interface PlayerWarpRepository {

    /** The warp under {@code name}, if one exists anywhere on the server (names are globally unique). */
    Optional<PlayerWarp> findByName(PlayerWarpName name);

    /** The warp with the surrogate key {@code id}, if it still exists. */
    Optional<PlayerWarp> findById(PlayerWarpId id);

    /** Every warp {@code owner} owns, in stored creation order, for the {@code /pwarps} list. */
    List<PlayerWarp> ownedBy(PlayerRef owner);

    /**
     * The {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#ACTIVE active},
     * {@link com.uxplima.uxmessentials.playerwarps.domain.WarpAccess#PUBLIC public} warps {@code owner} owns, in
     * stored creation order, for a cross-owner {@code /pwarps} — a private, suspended, or archived warp never
     * leaks to another player through this query.
     */
    List<PlayerWarp> publicOwnedBy(PlayerRef owner);

    /**
     * Every warp across every owner, in stored creation order, for the management GUI's admin view (an operator
     * holding {@code uxmessentials.pwarp.gui} manages all players' warps, not just their own). A bounded scan run
     * off the tick thread; an owner-scoped caller uses {@link #ownedBy} instead. The default returns an empty
     * list so a store that does not implement it simply shows nothing rather than failing — the jOOQ adapter
     * overrides it with the real query.
     */
    default List<PlayerWarp> all() {
        return List.of();
    }

    /** How many warps {@code owner} owns (the {@code /setpwarp} limit check). */
    int count(PlayerRef owner);

    /** True when a warp already exists under {@code name} anywhere on the server (the global name-collision check). */
    boolean existsByName(PlayerWarpName name);

    /**
     * Insert {@code warp} or overwrite the row under its surrogate {@link PlayerWarpId}, returning the id. A warp
     * with no id is a new row: the store assigns a fresh key and returns it, so the caller can re-read the saved
     * aggregate through {@link PlayerWarp#withId}. A warp that already carries an id is updated in place and that
     * same id is returned.
     */
    PlayerWarpId save(PlayerWarp warp);

    /** Remove the warp with surrogate key {@code id}; a no-op when no such row exists. */
    void deleteById(PlayerWarpId id);

    /**
     * Atomically bump the visit counter on the warp with surrogate key {@code id} by one. This is a
     * high-frequency, eventually-consistent write — it runs on every teleport to the warp — so it does the
     * increment in the database rather than reading, mutating, and saving the whole row (which would lose
     * concurrent visits to a last-writer-wins race). It deliberately stays off the cross-server sync path:
     * a visitor count drifting by a few on peers until the next real change is fine, and is not worth a
     * cluster-wide cache invalidation per visit.
     */
    void recordVisit(PlayerWarpId id);

    /**
     * Overwrite the denormalised rating rollup on the warp with surrogate key {@code id} with {@code summary} — the
     * sum, count, average, and the Bayesian score the "top rated" browse sorts on. A guarded single-row UPDATE the
     * rate use case runs after recomputing the rollup from the {@code WarpRatingStore}, so the sort column stays in
     * step with the vote rows without the browse ever touching the per-vote table.
     *
     * <p>The default is a no-op so a test double that does not maintain the rollup need not implement it; the durable
     * jOOQ store and both the cache and cross-server decorators override it — production never hits this default.
     */
    default void updateRating(PlayerWarpId id, RatingSummary summary) {}

    /**
     * Recompute {@code favourite_count} on the warp with surrogate key {@code id} from the live favourite rows —
     * {@code SET favourite_count = (SELECT COUNT(*) FROM player_warp_favourites WHERE warp_id = id)} — rather than a
     * {@code +1}/{@code -1} bump, so a double-click that races the favourite membership check can never drift the
     * stored count away from the true row count. The favourite use case calls it after a star or un-star commits.
     *
     * <p>The default is a no-op, overridden by the durable jOOQ store and both decorators, exactly as
     * {@link #updateRating} is.
     */
    default void refreshFavouriteCount(PlayerWarpId id) {}

    /**
     * The warps {@code owner} owns if they are already in memory, without touching the database. A cache
     * decorator returns its cached set on a hit and an empty {@link Optional} on a miss; an undecorated store
     * has nothing in memory and so returns empty. This exists for tick-thread callers that must never block
     * on I/O — chiefly the {@code /pwarp}/{@code /pwarp del} name-argument suggesters, which complete only the
     * warps a join-warmed cache already holds and suggest nothing on a cold miss rather than reaching the
     * disk while the player types. Never loads; never blocks.
     */
    default Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
        return Optional.empty();
    }
}
