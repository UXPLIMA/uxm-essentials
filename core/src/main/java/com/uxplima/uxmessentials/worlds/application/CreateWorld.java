package com.uxplima.uxmessentials.worlds.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldCreated;

/**
 * Creates a new world: rejects a name already in the registry or already present on disk, asks the
 * engine to build the world, persists the aggregate, and publishes {@link WorldCreated}. The engine
 * call is synchronous from the use case's perspective; the adapter runs the heavy work off-tick and
 * on the global thread.
 */
public final class CreateWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CreateWorld(
            WorldRepository repository,
            WorldEngine engine,
            WorldNotifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result<Unit, WorldError> create(PlayerRef creator, WorldName name, WorldSpec spec, boolean autoLoad) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(spec, "spec");
        if (repository.exists(name) || engine.exists(name)) {
            notifier.send(creator, WorldError.ALREADY_EXISTS.messageKey(), Map.of("world", name.value()));
            return Result.err(WorldError.ALREADY_EXISTS);
        }
        ManagedWorld world = ManagedWorld.created(name, spec, autoLoad, Optional.of(creator.uuid()), clock.instant());
        Result<Unit, WorldError> created = engine.create(world);
        if (created.isErr()) {
            notifier.send(creator, created.errorOrThrow().messageKey(), Map.of("world", name.value()));
            return created;
        }
        ManagedWorld persisted = engine.uidOf(name).map(world::withKnownUid).orElse(world);
        repository.save(persisted);
        events.publish(new WorldCreated(name));
        notifier.send(creator, WorldsMessageKey.WORLD_CREATED, Map.of("world", name.value()));
        return Result.ok();
    }
}
