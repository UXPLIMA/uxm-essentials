package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp del <name>}: remove one of the actor's own player-warps, freeing its name for reuse. The warp is
 * resolved by its global name, then guarded by ownership: a name no warp exists under, or one owned by another
 * player, is rejected with {@link PlayerWarpError#NOT_FOUND} — the same answer for both cases so this use case
 * never reveals that another player holds the name. A successful delete removes the row by its surrogate id and
 * publishes {@code PlayerWarpDeleted}. Staff deletion of any warp is a command-surface concern handled in the
 * adapter, not here; this core use case only ever deletes the caller's own warp.
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

    /** Delete {@code actor}'s warp {@code name}, or reject when no such warp of theirs exists. */
    public Result<Unit, PlayerWarpError> delete(PlayerRef actor, PlayerWarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> existing = repository.findByName(name);
        if (existing.isEmpty() || !existing.get().owner().uuid().equals(actor.uuid())) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        repository.deleteById(existing.get().id().orElseThrow());
        events.publish(new PlayerWarpDeleted(actor, name));
        notifier.send(actor, PlayerwarpsMessageKey.PWARP_DELETED, Map.of("warp", name.value()));
        return Result.ok();
    }
}
