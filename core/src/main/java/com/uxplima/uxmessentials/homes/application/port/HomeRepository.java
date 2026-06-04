package com.uxplima.uxmessentials.homes.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for durable, per-owner home storage. Every home fact (owner, name, world, coordinates,
 * creation time) is a first-class column — there is no opaque JSON blob (the architecture persistence
 * invariant) — so the {@link HomeSet} an owner loads is rebuilt from queryable rows and the count is read
 * with a {@code COUNT(*)} rather than by deserialising a document.
 *
 * <p>The jOOQ adapter behind this port loads an owner's set, upserts a single home on a {@code /sethome}
 * or move (overwriting the {@code (owner, name)} primary key in place), renames by key, and deletes by
 * key. A cache decorator may sit in front of it; the contract here is the durable source of truth.
 */
public interface HomeRepository {

    /** The owner's full set of homes, in stored creation order. Empty set when the owner has none. */
    HomeSet load(PlayerRef owner);

    /** How many homes the owner holds, read without materialising the whole set (for the quota check). */
    int count(PlayerRef owner);

    /** Insert {@code home} or overwrite the row under its {@code (owner, name)} key (a set or a move). */
    void save(Home home);

    /** Atomically replace the row under {@code from} with one under {@code to}, keeping the location. */
    void rename(PlayerRef owner, HomeName from, HomeName to);

    /** Remove the owner's home under {@code name}; a no-op when no such row exists. */
    void delete(PlayerRef owner, HomeName name);

    /**
     * The owner's homes if they are already in memory, without touching the database. A cache decorator
     * returns its cached set on a hit and an empty {@link Optional} on a miss; an undecorated store has
     * nothing in memory and so returns empty. This exists for tick-thread callers that must never block on
     * I/O — chiefly the {@code /home}/{@code /delhome}/… name-argument suggesters, which complete only the
     * homes a join-warmed cache already holds and suggest nothing on a cold miss rather than reaching the
     * disk while the player types. Never loads; never blocks.
     */
    default Optional<List<Home>> peek(PlayerRef owner) {
        return Optional.empty();
    }
}
