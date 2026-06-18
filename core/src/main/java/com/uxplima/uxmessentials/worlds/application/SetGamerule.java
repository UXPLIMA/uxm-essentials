package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog.GameRuleType;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;

/** Sets a per-world gamerule, validating the rule name + value against the {@link GameRuleCatalog}. */
public final class SetGamerule {

    private final WorldRepository repository;
    private final GameRuleCatalog catalog;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;

    public SetGamerule(
            WorldRepository repository,
            GameRuleCatalog catalog,
            WorldNotifier notifier,
            DomainEventPublisher events,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> set(PlayerRef who, WorldName name, String rule, String rawValue) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Optional<ManagedWorld> found = repository.find(name);
        if (found.isEmpty()) {
            return fail(who, name, WorldError.NOT_FOUND);
        }
        Optional<GameRuleType> type = catalog.typeOf(rule);
        if (type.isEmpty()) {
            return fail(who, name, WorldError.GAMERULE_UNKNOWN);
        }
        if (!valid(type.get(), rawValue)) {
            return fail(who, name, WorldError.GAMERULE_INVALID_VALUE);
        }
        String key = WorldSettings.gameruleKey(rule);
        ManagedWorld next = found.get().withSettings(found.get().settings().withRaw(key, rawValue));
        scheduler.async(() -> {
            repository.save(next);
            events.publish(new WorldSettingChanged(name, key, Optional.of(rawValue)));
            scheduler.onEntity(
                    who,
                    () -> notifier.send(
                            who,
                            WorldsMessageKey.WORLD_GAMERULE_SET,
                            Map.of("world", name.value(), "rule", rule, "value", rawValue)));
        });
        return Result.ok();
    }

    private static boolean valid(GameRuleType type, String raw) {
        if (raw == null) {
            return false;
        }
        return switch (type) {
            case BOOLEAN -> raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false");
            case INTEGER -> {
                try {
                    Integer.parseInt(raw.strip());
                    yield true;
                } catch (NumberFormatException notAnInt) {
                    yield false;
                }
            }
        };
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName name, WorldError error) {
        notifier.send(who, error.messageKey(), Map.of("world", name.value()));
        return Result.err(error);
    }
}
