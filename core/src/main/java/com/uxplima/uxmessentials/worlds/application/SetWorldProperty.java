package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;

/** Sets a scalar per-world property from the {@link WorldProperties} catalog (DB write off-tick). */
public final class SetWorldProperty {

    private final WorldRepository repository;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;

    public SetWorldProperty(
            WorldRepository repository, WorldNotifier notifier, DomainEventPublisher events, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> set(PlayerRef who, WorldName name, String propKey, String rawValue) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Optional<ManagedWorld> found = repository.find(name);
        if (found.isEmpty()) {
            return fail(who, name, WorldError.NOT_FOUND);
        }
        Optional<WorldProperty<?>> property = WorldProperties.byKey(propKey);
        if (property.isEmpty()) {
            return fail(who, name, WorldError.SETTING_UNKNOWN);
        }
        Optional<WorldSettings> updated = applied(found.get().settings(), property.get(), rawValue);
        if (updated.isEmpty()) {
            return fail(who, name, WorldError.SETTING_INVALID_VALUE);
        }
        ManagedWorld next = found.get().withSettings(updated.get());
        scheduler.async(() -> {
            repository.save(next);
            events.publish(new WorldSettingChanged(name, property.get().key(), Optional.of(rawValue)));
            scheduler.onEntity(
                    who,
                    () -> notifier.send(
                            who,
                            WorldsMessageKey.WORLD_SETTING_UPDATED,
                            Map.of("world", name.value(), "key", propKey, "value", rawValue)));
        });
        return Result.ok();
    }

    private static <T> Optional<WorldSettings> applied(WorldSettings settings, WorldProperty<T> property, String raw) {
        return property.decode(raw).map(value -> settings.with(property, value));
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName name, WorldError error) {
        notifier.send(who, error.messageKey(), Map.of("world", name.value()));
        return Result.err(error);
    }
}
