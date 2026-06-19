package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.worlds.application.port.PendingRestoreRegistry;
import com.uxplima.uxmessentials.worlds.domain.PendingRestore;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/** In-memory restore-confirm staging, keyed by requester. One pending restore per operator. */
@NullMarked
public final class InMemoryPendingRestoreRegistry implements PendingRestoreRegistry {

    private final ConcurrentHashMap<UUID, PendingRestore> staged = new ConcurrentHashMap<>();

    @Override
    public void stage(PendingRestore pending) {
        staged.put(pending.requester(), pending);
    }

    @Override
    public Optional<PendingRestore> take(WorldName world, UUID requester) {
        PendingRestore current = staged.get(requester);
        if (current == null || !current.world().equals(world)) {
            return Optional.empty();
        }
        staged.remove(requester, current);
        return Optional.of(current);
    }

    @Override
    public Optional<PendingRestore> peek(UUID requester) {
        return Optional.ofNullable(staged.get(requester));
    }
}
