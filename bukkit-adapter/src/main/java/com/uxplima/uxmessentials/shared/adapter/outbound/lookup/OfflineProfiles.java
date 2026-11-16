package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The server's record of accounts that are not connected. Reading it can touch the disk, so it is asked off the tick;
 * who is connected is the {@link OnlinePlayerIndex}'s to answer.
 */
@NullMarked
public interface OfflineProfiles {

    /** The account that has played here under this name, from what the server already holds, never a web lookup. */
    Optional<PlayerRef> byName(String name);

    /** The account with this id, named as the server last saw it. */
    Optional<PlayerRef> byUuid(UUID uuid);
}
