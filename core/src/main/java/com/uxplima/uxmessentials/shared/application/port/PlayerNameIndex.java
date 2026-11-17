package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Case-insensitive name resolution for accounts that have joined before, served from memory.
 *
 * <p>Paper only consults the server's own name cache on an online-mode server, so on an offline-mode server
 * a name typed in a different case resolves to a uuid nobody owns. This index closes that gap and gives both
 * modes one lookup path.
 *
 * <p>{@link #byName} is memory-only and safe to call on a tick thread. {@link #record} mutates memory
 * synchronously and persists off-thread. {@link #learn} is memory-only.
 */
public interface PlayerNameIndex {

    /**
     * Every account known under this name in any case, most recently seen first, each under the name it was last
     * seen with; empty when the index has never seen it. Two accounts share a name in different cases on an
     * offline-mode server, and an online-mode account can still hold a name another account took since.
     */
    List<PlayerRef> byName(String name);

    /**
     * Like {@link #byName}, but complete: the memory holds only the most recently seen names, so this also reads the
     * durable store when there is one. It reads storage, so it is called off the tick. The default answers from
     * memory, for an index that has no store.
     */
    default List<PlayerRef> storedUnder(String name) {
        return byName(name);
    }

    /** Note that {@code uuid} is joining under {@code name}. */
    void record(UUID uuid, String name);

    /**
     * Note that {@code uuid} is online under {@code name} on another backend. Memory only: the backend the player
     * joined records the name itself, so persisting it here again would only repeat that write once per backend.
     */
    void learn(UUID uuid, String name);
}
