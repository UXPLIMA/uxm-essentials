package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp <name> [owner]}: teleport a player to a player-warp. With no owner the actor warps to their
 * own warp; with an owner they warp to that player's warp, which is permitted only when the warp is public.
 * Access is by ownership and the public flag — the actor always reaches their own warp, public or private,
 * and reaches another owner's warp only when it is public. Execution is then <em>delegated</em> to the
 * teleport context through {@link PlayerWarpTeleporter}; this use case never moves the player itself, so the
 * shared cooldown, the move-cancellable warmup, and the region-aware async hop are all the teleport context's
 * concern.
 */
public final class UsePlayerWarp {

    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final PlayerWarpNotifier notifier;

    public UsePlayerWarp(
            PlayerWarpRepository repository, PlayerWarpTeleporter teleporter, PlayerWarpNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Teleport {@code who} to their own warp {@code name}. */
    public Result<Unit, PlayerWarpError> use(PlayerRef who, PlayerWarpName name) {
        return useFor(who, who, name);
    }

    /**
     * Teleport {@code actor} to the warp {@code name} owned by {@code owner}. A missing warp is rejected with
     * {@link PlayerWarpError#NOT_FOUND}; a private warp owned by someone else is refused with
     * {@link PlayerWarpError#NOT_PUBLIC}. The actor always reaches their own warp.
     */
    public Result<Unit, PlayerWarpError> useFor(PlayerRef actor, PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> warp = repository.find(owner, name);
        if (warp.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        return admitAndGo(actor, owner, warp.get());
    }

    private Result<Unit, PlayerWarpError> admitAndGo(PlayerRef actor, PlayerRef owner, PlayerWarp warp) {
        if (!actor.uuid().equals(owner.uuid()) && !warp.isPublic()) {
            notifier.send(
                    actor,
                    PlayerWarpError.NOT_PUBLIC.messageKey(),
                    Map.of("warp", warp.name().value()));
            return Result.err(PlayerWarpError.NOT_PUBLIC);
        }
        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_TELEPORTING,
                Map.of("warp", warp.name().value()));
        teleporter.teleportTo(actor, warp);
        return Result.ok();
    }
}
