package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpCreated;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /setpwarp <name>}: create a player-owned warp at the player's current position, or re-anchor an
 * existing one of the same name in place. A name the owner already has re-anchors the row (keeping its
 * visibility and creation time) and saves with the {@code moved} feedback; a brand-new name is gated against
 * the owner's resolved {@link PlayerWarpLimit} — hitting the cap returns {@link PlayerWarpError#LIMIT_REACHED}
 * and renders the limit message — otherwise it is stored as a new private warp and publishes
 * {@code PlayerWarpCreated}.
 *
 * <p>The owner's limit is resolved through {@link PlayerWarpQuota} scoped to the warp's world, so a
 * world-scoped {@code uxmessentials.pwarp.limit.<world>.<n>} node folds in. A new warp is always private
 * until the owner makes it public.
 */
public final class SetPlayerWarp {

    private final PlayerWarpRepository repository;
    private final PlayerWarpQuota quota;
    private final PlayerWarpNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final java.util.List<String> worldBlacklist;

    public SetPlayerWarp(
            PlayerWarpRepository repository,
            PlayerWarpQuota quota,
            PlayerWarpNotifier notifier,
            DomainEventPublisher events,
            Clock clock,
            java.util.List<String> worldBlacklist) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.worldBlacklist = java.util.List.copyOf(worldBlacklist);
    }

    /** Create or re-anchor {@code owner}'s warp {@code name} at {@code at}. */
    public Result<Unit, PlayerWarpError> set(PlayerRef owner, PlayerWarpName name, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");

        if (worldBlacklist.contains(at.world().name())) {
            notifier.send(
                    owner,
                    PlayerWarpError.WORLD_BLACKLISTED.messageKey(),
                    Map.of("world", at.world().name()));
            return Result.err(PlayerWarpError.WORLD_BLACKLISTED);
        }

        Optional<PlayerWarp> existing = repository.find(owner, name);
        return existing.isPresent() ? reanchor(owner, existing.get(), at) : create(owner, name, at);
    }

    private Result<Unit, PlayerWarpError> create(PlayerRef owner, PlayerWarpName name, Position at) {
        PlayerWarpLimit limit = quota.resolve(owner, at.world());
        if (limit.isReachedAt(repository.count(owner))) {
            notifier.send(
                    owner, PlayerWarpError.LIMIT_REACHED.messageKey(), Map.of("limit", Integer.toString(limit.cap())));
            return Result.err(PlayerWarpError.LIMIT_REACHED);
        }
        PlayerWarp warp = PlayerWarp.create(owner, name, at, clock.instant());
        repository.save(warp);
        events.publish(new PlayerWarpCreated(owner, name, at));
        notifier.send(owner, PlayerwarpsMessageKey.PWARP_SET, Map.of("warp", name.value()));
        return Result.ok();
    }

    private Result<Unit, PlayerWarpError> reanchor(PlayerRef owner, PlayerWarp existing, Position at) {
        repository.save(existing.movedTo(at));
        notifier.send(
                owner,
                PlayerwarpsMessageKey.PWARP_MOVED,
                Map.of("warp", existing.name().value()));
        return Result.ok();
    }
}
