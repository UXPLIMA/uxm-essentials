package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnregistered;

/** Removes a world from the registry while leaving its files on disk and any live world loaded. */
public final class UnregisterWorld {

    private final WorldRepository repository;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;

    public UnregisterWorld(WorldRepository repository, WorldNotifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    public Result<Unit, WorldError> unregister(PlayerRef who, WorldName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(who, WorldError.NOT_FOUND.messageKey(), Map.of("world", name.value()));
            return Result.err(WorldError.NOT_FOUND);
        }
        repository.delete(name);
        events.publish(new WorldUnregistered(name));
        notifier.send(who, WorldsMessageKey.WORLD_UNREGISTERED, Map.of("world", name.value()));
        return Result.ok();
    }
}
