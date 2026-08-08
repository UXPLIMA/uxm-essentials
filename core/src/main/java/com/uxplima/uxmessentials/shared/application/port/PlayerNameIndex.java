package com.uxplima.uxmessentials.shared.application.port;

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
 * synchronously and persists off-thread.
 */
public interface PlayerNameIndex {

    /** The account that last joined under this name, ignoring case, or empty when the index has never seen it. */
    Optional<PlayerRef> byName(String name);

    /** Note that {@code uuid} is joining under {@code name}. */
    void record(UUID uuid, String name);
}
