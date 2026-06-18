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
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldImported;

/**
 * Imports an existing on-disk world folder into management: rejects an already-registered name, a
 * missing folder, or a folder without a readable {@code level.dat}; otherwise builds the spec from
 * the detected environment/seed (plus an optional generator), loads the world, persists it, and
 * publishes {@link WorldImported}.
 */
public final class ImportWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ImportWorld(
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

    public Result<Unit, WorldError> importWorld(
            PlayerRef importer, WorldName name, WorldEnvironment environment, Optional<GeneratorRef> generator) {
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(generator, "generator");
        if (repository.exists(name)) {
            return fail(importer, name, WorldError.ALREADY_EXISTS);
        }
        if (!engine.exists(name)) {
            return fail(importer, name, WorldError.FOLDER_MISSING);
        }
        Optional<WorldEngine.DetectedWorld> detected = engine.scanFolder(name);
        if (detected.isEmpty()) {
            return fail(importer, name, WorldError.NOT_A_WORLD_FOLDER);
        }
        WorldSpec spec = new WorldSpec(
                environment, WorldGenType.NORMAL, detected.get().seed(), generator, true, Optional.empty());
        ManagedWorld world = ManagedWorld.created(name, spec, true, Optional.of(importer.uuid()), clock.instant());
        Result<Unit, WorldError> loaded = engine.load(name);
        if (loaded.isErr()) {
            return fail(importer, name, loaded.errorOrThrow());
        }
        repository.save(engine.uidOf(name).map(world::withKnownUid).orElse(world));
        events.publish(new WorldImported(name));
        notifier.send(importer, WorldsMessageKey.WORLD_IMPORTED, Map.of("world", name.value()));
        return Result.ok();
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName name, WorldError error) {
        notifier.send(who, error.messageKey(), Map.of("world", name.value()));
        return Result.err(error);
    }
}
