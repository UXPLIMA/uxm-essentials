package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp del <name>}: remove one of the owner's player-warps, freeing its name for reuse. A name the
 * owner has no warp at is rejected with {@link PlayerWarpError#NOT_FOUND}; a successful delete removes the row
 * and publishes {@code PlayerWarpDeleted}. The base {@code uxmessentials.pwarp.delete} node guards the
 * command at the adapter; a player only ever deletes their own warps.
 */
public final class DelPlayerWarp {

    private final PlayerWarpRepository repository;
    private final PlayerWarpNotifier notifier;
    private final DomainEventPublisher events;

    public DelPlayerWarp(PlayerWarpRepository repository, PlayerWarpNotifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Delete {@code owner}'s warp {@code name}, or reject when no such warp exists. */
    public Result<Unit, PlayerWarpError> delete(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(owner, name)) {
            notifier.send(owner, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        repository.delete(owner, name);
        events.publish(new PlayerWarpDeleted(owner, name));
        notifier.send(owner, PlayerwarpsMessageKey.PWARP_DELETED, Map.of("warp", name.value()));
        return Result.ok();
    }
}
