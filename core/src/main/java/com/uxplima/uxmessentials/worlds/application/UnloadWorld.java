package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnloaded;

/** Unloads a loaded world; refuses the protected default world and worlds with players inside. */
public final class UnloadWorld {

    private final WorldEngine engine;
    private final WorldNotifier notifier;
    private final DomainEventPublisher events;
    private final BooleanSupplier protectDefault;

    public UnloadWorld(
            WorldEngine engine, WorldNotifier notifier, DomainEventPublisher events, BooleanSupplier protectDefault) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.protectDefault = Objects.requireNonNull(protectDefault, "protectDefault");
    }

    public Result<Unit, WorldError> unload(PlayerRef who, WorldName name, boolean save) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        if (!engine.isLoaded(name)) {
            return fail(who, name, WorldError.NOT_LOADED);
        }
        if (protectDefault.getAsBoolean() && engine.defaultWorldName().equals(name)) {
            return fail(who, name, WorldError.IS_PROTECTED);
        }
        if (engine.playerCount(name) > 0) {
            return fail(who, name, WorldError.PLAYERS_PRESENT);
        }
        Result<Unit, WorldError> unloaded = engine.unload(name, save);
        if (unloaded.isErr()) {
            return fail(who, name, unloaded.errorOrThrow());
        }
        events.publish(new WorldUnloaded(name));
        notifier.send(who, WorldsMessageKey.WORLD_UNLOADED, Map.of("world", name.value()));
        return Result.ok();
    }

    private Result<Unit, WorldError> fail(PlayerRef who, WorldName name, WorldError error) {
        notifier.send(who, error.messageKey(), Map.of("world", name.value()));
        return Result.err(error);
    }
}
