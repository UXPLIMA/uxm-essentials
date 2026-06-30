package com.uxplima.uxmessentials.shared.application.port;

import java.util.Map;
import java.util.UUID;

/**
 * The persistence half of the {@link PlayerDataStore}: the durable database operations a {@code PlayerDataStore}
 * implementation drives behind its in-memory cache. The store loads a player's whole row set on join, then upserts
 * or deletes individual keys as they change; this port names exactly those three operations so the cache layer can
 * be wired against it without ever naming a jOOQ type (jOOQ stays an implementation detail of the persistence
 * adapter).
 *
 * <p>Every method here touches the database and must be called off the tick thread.
 */
public interface PlayerDataRepository {

    /** Every stored {@code key→value} for {@code player} (empty when the player has no rows). */
    Map<String, String> loadAll(UUID player);

    /** Insert or overwrite the value stored under {@code (player, key)}. */
    void upsert(UUID player, String key, String value);

    /** Remove the row stored under {@code (player, key)}; a no-op when it is absent. */
    void delete(UUID player, String key);
}
