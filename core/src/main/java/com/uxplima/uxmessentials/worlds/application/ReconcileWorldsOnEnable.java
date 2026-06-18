package com.uxplima.uxmessentials.worlds.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldAdopted;
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;

/**
 * Enable-time reconciliation between the server's live worlds and our registry: adopts
 * already-loaded worlds we do not yet manage, then auto-loads registered worlds flagged for it that
 * are not currently loaded. Both phases are gated by config. Runs on the global thread (the adapter
 * schedules it through the {@code Scheduler} port).
 */
public final class ReconcileWorldsOnEnable {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final BooleanSupplier adoptLoaded;
    private final BooleanSupplier autoLoadRegistered;

    public ReconcileWorldsOnEnable(
            WorldRepository repository,
            WorldEngine engine,
            DomainEventPublisher events,
            Clock clock,
            BooleanSupplier adoptLoaded,
            BooleanSupplier autoLoadRegistered) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.adoptLoaded = Objects.requireNonNull(adoptLoaded, "adoptLoaded");
        this.autoLoadRegistered = Objects.requireNonNull(autoLoadRegistered, "autoLoadRegistered");
    }

    public void run() {
        if (adoptLoaded.getAsBoolean()) {
            adoptLoadedWorlds();
        }
        if (autoLoadRegistered.getAsBoolean()) {
            autoLoadRegisteredWorlds();
        }
    }

    private void adoptLoadedWorlds() {
        for (WorldName name : engine.loadedWorldNames()) {
            if (repository.exists(name)) {
                continue;
            }
            WorldEnvironment env = engine.scanFolder(name)
                    .map(WorldEngine.DetectedWorld::environment)
                    .orElse(WorldEnvironment.NORMAL);
            WorldSpec spec = new WorldSpec(
                    env, WorldSpec.normal().worldType(), Optional.empty(), Optional.empty(), true, Optional.empty());
            ManagedWorld adopted = engine.uidOf(name)
                    .map(uid -> ManagedWorld.adopted(name, spec, uid, clock.instant()))
                    .orElseGet(() -> ManagedWorld.created(name, spec, true, Optional.empty(), clock.instant()));
            repository.save(adopted);
            events.publish(new WorldAdopted(name));
        }
    }

    private void autoLoadRegisteredWorlds() {
        for (ManagedWorld world : repository.all()) {
            if (!world.autoLoad() || engine.isLoaded(world.name())) {
                continue;
            }
            if (engine.load(world).isOk()) {
                engine.uidOf(world.name()).ifPresent(uid -> repository.save(world.withKnownUid(uid)));
                events.publish(new WorldLoaded(world.name()));
            }
        }
    }
}
