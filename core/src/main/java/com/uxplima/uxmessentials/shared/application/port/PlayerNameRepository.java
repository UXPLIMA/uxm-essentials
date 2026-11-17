package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerName;

/**
 * Durable storage for the plugin's name index.
 *
 * <p>Every method hits the database, so every call runs off the tick thread; the in-memory
 * {@link PlayerNameIndex} is what the command path reads.
 */
public interface PlayerNameRepository {

    /** The {@code limit} most recently seen rows, newest first. An empty list when {@code limit} is not positive. */
    List<PlayerName> loadRecent(int limit);

    /**
     * Every account whose name is {@code name} in any case, each with the name it last joined under. Two accounts
     * share a name in different cases on an offline-mode server.
     */
    List<PlayerName> underName(String name);

    /** Insert or update the row for this account. */
    void upsert(PlayerName record);

    /** How many rows the index holds, used to decide whether a first-run backfill is needed. */
    int count();
}
