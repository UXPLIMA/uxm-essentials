package com.uxplima.uxmessentials.worlds.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.worlds.domain.PendingRestore;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Short-lived staging of restore confirmations, keyed by requester. Backed by an in-memory map. */
public interface PendingRestoreRegistry {

    void stage(PendingRestore pending);

    /** Consume the staged restore iff it belongs to the requester and matches the world. */
    Optional<PendingRestore> take(WorldName world, UUID requester);

    Optional<PendingRestore> peek(UUID requester);
}
