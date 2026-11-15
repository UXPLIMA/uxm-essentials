package com.uxplima.uxmessentials.worlds.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.worlds.application.port.PendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.domain.PendingDeletion;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Delete confirmations staged in memory, one per operator. */
final class InMemoryPendingDeletions implements PendingDeletionRegistry {

    private final Map<UUID, PendingDeletion> staged = new HashMap<>();

    @Override
    public void stage(PendingDeletion pending) {
        staged.put(pending.requester(), pending);
    }

    @Override
    public Optional<PendingDeletion> take(WorldName name, UUID requester) {
        PendingDeletion current = staged.get(requester);
        if (current == null || !current.name().equals(name)) {
            return Optional.empty();
        }
        staged.remove(requester);
        return Optional.of(current);
    }

    @Override
    public Optional<PendingDeletion> peek(UUID requester) {
        return Optional.ofNullable(staged.get(requester));
    }

    @Override
    public void clear(WorldName name) {
        staged.values().removeIf(pending -> pending.name().equals(name));
    }
}
