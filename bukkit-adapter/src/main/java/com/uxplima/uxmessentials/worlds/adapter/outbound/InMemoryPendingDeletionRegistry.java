package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.worlds.application.port.PendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.domain.PendingDeletion;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/** In-memory delete-confirm staging, keyed by requester. One pending deletion per operator. */
@NullMarked
public final class InMemoryPendingDeletionRegistry implements PendingDeletionRegistry {

    private final ConcurrentHashMap<UUID, PendingDeletion> staged = new ConcurrentHashMap<>();

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
        staged.remove(requester, current);
        return Optional.of(current);
    }

    @Override
    public Optional<PendingDeletion> peek(UUID requester) {
        return Optional.ofNullable(staged.get(requester));
    }

    @Override
    public void clear(WorldName name) {
        staged.values().removeIf(p -> p.name().equals(name));
    }
}
