package com.uxplima.uxmessentials.worlds.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldCreateOptions;
import com.uxplima.uxmessentials.worlds.domain.WorldDefaults;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.domain.event.WorldCreated;

/**
 * Creates a new world: rejects a name already in the registry or already present on disk, fills every
 * option the operator left open from the configured {@link WorldDefaults}, asks the engine to build the
 * world (the Bukkit handle op, on the calling global thread), then persists the aggregate, with the
 * default per-world settings, and publishes {@link WorldCreated} on the {@code Scheduler}'s async
 * executor, hopping back to the creator only to notify. The synchronous {@link Result} carries the
 * gate/engine outcome; the persistence write is off-tick.
 */
public final class CreateWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Supplier<WorldDefaults> defaults;

    public CreateWorld(
            WorldRepository repository,
            WorldEngine engine,
            Notifier notifier,
            DomainEventPublisher events,
            Scheduler scheduler,
            Clock clock,
            Supplier<WorldDefaults> defaults) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    public Result<Unit, WorldError> create(
            PlayerRef creator, WorldName name, WorldCreateOptions options, boolean autoLoad) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        if (repository.exists(name) || engine.exists(name)) {
            notifier.send(creator, WorldError.ALREADY_EXISTS.messageKey(), Map.of("world", name.value()));
            return Result.err(WorldError.ALREADY_EXISTS);
        }
        // One read, so a reload landing mid-create cannot mix the old spec defaults with the new settings.
        WorldDefaults current = defaults.get();
        ManagedWorld world = ManagedWorld.created(
                        name, current.specFor(options), autoLoad, Optional.of(creator.uuid()), clock.instant())
                .withSettings(current.settings());
        Result<Unit, WorldError> created = engine.create(world);
        if (created.isErr()) {
            notifier.send(creator, created.errorOrThrow().messageKey(), Map.of("world", name.value()));
            return created;
        }
        // The uid is read here, on the global thread that just built the world; the server's world list is not the
        // async lane's to read.
        ManagedWorld known = engine.uidOf(name).map(world::withKnownUid).orElse(world);
        scheduler.async(() -> persistOffTick(creator, name, known));
        return Result.ok();
    }

    private void persistOffTick(PlayerRef creator, WorldName name, ManagedWorld world) {
        repository.save(world);
        events.publish(new WorldCreated(name));
        scheduler.onEntity(
                creator, () -> notifier.send(creator, WorldsMessageKey.WORLD_CREATED, Map.of("world", name.value())));
    }
}
