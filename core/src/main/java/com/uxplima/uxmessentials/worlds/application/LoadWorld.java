package com.uxplima.uxmessentials.worlds.application;

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
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;

/** Loads a registered world that is not currently loaded, refreshing its known uid afterwards. */
public final class LoadWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;

    public LoadWorld(
            WorldRepository repository, WorldEngine engine, WorldNotifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
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
        Result<Unit, WorldError> loaded = engine.load(name);
        if (loaded.isErr()) {
            return fail(who, name, loaded.errorOrThrow());
        }
        engine.uidOf(name).ifPresent(uid -> repository.save(known.get().withKnownUid(uid)));
        events.publish(new WorldLoaded(name));
        notifier.send(who, WorldsMessageKey.WORLD_LOADED, Map.of("world", name.value()));
        return Result.ok();
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName name, WorldError error) {
        notifier.send(who, error.messageKey(), Map.of("world", name.value()));
        return Result.err(error);
    }
}
