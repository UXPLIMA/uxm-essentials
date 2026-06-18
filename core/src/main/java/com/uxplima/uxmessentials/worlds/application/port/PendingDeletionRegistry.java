package com.uxplima.uxmessentials.worlds.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.worlds.domain.PendingDeletion;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Short-lived staging of delete confirmations, keyed by requester. Backed by an in-memory map. */
public interface PendingDeletionRegistry {

    void stage(PendingDeletion pending);

    /** Consume the staged deletion iff it belongs to the requester and matches the world. */
    Optional<PendingDeletion> take(WorldName name, UUID requester);

    Optional<PendingDeletion> peek(UUID requester);

    void clear(WorldName name);
}
