package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;
import java.util.Optional;
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
     * Like {@link #byName}, but complete and current: the memory holds only the most recently seen names and hears
     * only of some joins, so this reads the durable store when there is one, and a remembered holder the store has
     * seen under another name since is not answered. It reads storage, so it is called off the tick. The default
     * answers from memory, for an index that has no store.
     */
    default List<PlayerRef> storedUnder(String name) {
        return byName(name);
    }

    /**
     * The account {@code uuid} under the name it was last seen with, from memory; empty when the memory does not hold
     * it. Safe on the tick. The default holds nothing.
     */
    default Optional<PlayerRef> byUuid(UUID uuid) {
        return Optional.empty();
    }

    /**
     * Like {@link #byUuid}, but complete: it also reads the durable store, which every backend writes, so an account
     * seen only on another server, or let go from memory, is named too. It reads storage, so it is called off the
     * tick. The default answers from memory.
     */
    default Optional<PlayerRef> storedByUuid(UUID uuid) {
        return byUuid(uuid);
    }

    /**
     * Whether more than one account may hold {@code name} in some case, so a lone account found under it in another
     * case is not known to be the one meant. Answers from memory, so it is safe on the tick. The default says yes,
     * for an index that cannot tell.
     */
    default boolean shared(String name) {
        return true;
    }

    /**
     * Note that {@code uuid} is about to join under {@code name}, before it is recorded, so whether another account
     * holds the name is known by the time the player can be named on the tick. It may read storage, so it is called
     * off the tick. The default does nothing.
     */
    default void joining(UUID uuid, String name) {}

    /** Note that {@code uuid} is joining under {@code name}. */
    void record(UUID uuid, String name);

    /**
     * Note that {@code uuid} is online under {@code name} on another backend. Memory only: the backend the player
     * joined records the name itself, so persisting it here again would only repeat that write once per backend.
     */
    void learn(UUID uuid, String name);
}
