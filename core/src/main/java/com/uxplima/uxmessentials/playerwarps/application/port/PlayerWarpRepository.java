package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for durable, per-owner player-warp storage. Every warp fact (owner, name, world, coordinates,
 * visibility, creation time) is a first-class column — there is no opaque JSON blob (the architecture
 * persistence invariant) — so a {@link PlayerWarp} loaded from a row is rebuilt from queryable fields, and the
 * list queries read them in stored creation order.
 *
 * <p>Player-warps are keyed by {@code (owner, name)}, so a {@code save} upserts on that composite key — a
 * re-anchor or a visibility flip overwrites the same row — and a delete is by owner and name. A cache
 * decorator may sit in front of this port; the contract here is the durable source of truth.
 */
public interface PlayerWarpRepository {

    /** The warp {@code owner} owns under {@code name}, if one exists. */
    Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name);

    /** Every warp {@code owner} owns, in stored creation order, for the {@code /pwarps} list. */
    List<PlayerWarp> ownedBy(PlayerRef owner);

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

    /** The public warps {@code owner} owns, in stored creation order, for a cross-owner {@code /pwarps}. */
    List<PlayerWarp> publicOf(PlayerRef owner);

    /** How many warps {@code owner} owns (the {@code /setpwarp} limit check). */
    int count(PlayerRef owner);

    /** True when {@code owner} already owns a warp under {@code name} (the {@code /setpwarp} name check). */
    boolean exists(PlayerRef owner, PlayerWarpName name);

    /** Insert {@code warp} or overwrite the row under its {@code (owner, name)} key (a set, move, or flip). */
    void save(PlayerWarp warp);

    /** Remove {@code owner}'s warp under {@code name}; a no-op when no such row exists. */
    void delete(PlayerRef owner, PlayerWarpName name);

    /**
     * The warps {@code owner} owns if they are already in memory, without touching the database. A cache
     * decorator returns its cached set on a hit and an empty {@link Optional} on a miss; an undecorated store
     * has nothing in memory and so returns empty. This exists for tick-thread callers that must never block
     * on I/O — chiefly the {@code /pwarp}/{@code /delpwarp} name-argument suggesters, which complete only the
     * warps a join-warmed cache already holds and suggest nothing on a cold miss rather than reaching the
     * disk while the player types. Never loads; never blocks.
     */
    default Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
        return Optional.empty();
    }

    /** Save/update a player's rating for a player warp. */
    void rate(PlayerRef owner, PlayerWarpName name, java.util.UUID player, double rating);

    /** Get the average rating for a player warp, or 0.0 if not rated. */
    double averageRating(PlayerRef owner, PlayerWarpName name);
}
