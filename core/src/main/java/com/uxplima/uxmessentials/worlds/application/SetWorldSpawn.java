package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.SpawnCodec;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;

/** Stores a world's spawn (from the operator's location) under the {@code spawn} setting key (DB write off-tick). */
public final class SetWorldSpawn {

    private final WorldRepository repository;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;

    public SetWorldSpawn(
            WorldRepository repository, WorldNotifier notifier, DomainEventPublisher events, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> set(PlayerRef who, WorldName name, Position spawn) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(spawn, "spawn");
        Optional<ManagedWorld> found = repository.find(name);
        if (found.isEmpty()) {
            notifier.send(who, WorldError.NOT_FOUND.messageKey(), Map.of("world", name.value()));
            return Result.err(WorldError.NOT_FOUND);
        }
        String encoded = SpawnCodec.encode(spawn);
        ManagedWorld next = found.get().withSettings(found.get().settings().withRaw(WorldSettings.spawnKey(), encoded));
        scheduler.async(() -> {
            repository.save(next);
            events.publish(new WorldSettingChanged(name, WorldSettings.spawnKey(), Optional.of(encoded)));
            scheduler.onEntity(
                    who, () -> notifier.send(who, WorldsMessageKey.WORLD_SPAWN_SET, Map.of("world", name.value())));
        });
        return Result.ok();
    }
}
