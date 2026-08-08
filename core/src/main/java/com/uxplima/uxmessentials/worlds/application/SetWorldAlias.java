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
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;

/** Sets (or clears) a world's operator-facing alias (the {@code world.alias} column; DB write off-tick). */
public final class SetWorldAlias {

    private static final String ALIAS_KEY = "alias";

    private final WorldRepository repository;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;

    public SetWorldAlias(
            WorldRepository repository, Notifier notifier, DomainEventPublisher events, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> set(PlayerRef who, WorldName name, Optional<String> alias) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(alias, "alias");
        Optional<ManagedWorld> found = repository.find(name);
        if (found.isEmpty()) {
            notifier.send(who, WorldError.NOT_FOUND.messageKey(), Map.of("world", name.value()));
            return Result.err(WorldError.NOT_FOUND);
        }
        ManagedWorld next = found.get().withAlias(alias);
        scheduler.async(() -> {
            repository.save(next);
            events.publish(new WorldSettingChanged(name, ALIAS_KEY, alias));
            scheduler.onEntity(
                    who,
                    () -> notifier.send(
                            who,
                            WorldsMessageKey.WORLD_ALIAS_SET,
                            Map.of("world", name.value(), "alias", alias.orElse(""))));
        });
        return Result.ok();
    }
}
