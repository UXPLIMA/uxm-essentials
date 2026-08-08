package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;

/**
 * Loads a registered world that is not currently loaded (the Bukkit handle op, on the calling global
 * thread), then refreshes its known uid and publishes {@link WorldLoaded} on the {@code Scheduler}'s
 * async executor, hopping back to the requester only to notify.
 */
public final class LoadWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;

    public LoadWorld(
            WorldRepository repository,
            WorldEngine engine,
            Notifier notifier,
            DomainEventPublisher events,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> load(PlayerRef who, WorldName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Optional<ManagedWorld> known = repository.find(name);
        if (known.isEmpty()) {
            return fail(who, name, WorldError.NOT_FOUND);
        }
        if (engine.isLoaded(name)) {
            return fail(who, name, WorldError.ALREADY_LOADED);
        }
        Result<Unit, WorldError> loaded = engine.load(known.get());
        if (loaded.isErr()) {
            return fail(who, name, loaded.errorOrThrow());
        }
        scheduler.async(() -> persistOffTick(who, name, known.get()));
        return Result.ok();
    }

    private void persistOffTick(PlayerRef who, WorldName name, ManagedWorld known) {
        engine.uidOf(name).ifPresent(uid -> repository.save(known.withKnownUid(uid)));
        events.publish(new WorldLoaded(name));
        scheduler.onEntity(who, () -> notifier.send(who, WorldsMessageKey.WORLD_LOADED, Map.of("world", name.value())));
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName name, WorldError error) {
        notifier.send(who, error.messageKey(), Map.of("world", name.value()));
        return Result.err(error);
    }
}
