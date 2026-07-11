package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp transfer <name> <player>}: hand ownership of a warp to another player. Owner-only by the capability
 * matrix — {@link WarpCapability#TRANSFER} is denied to co-owners and managers — so a delegate can never spirit a
 * warp away from under its owner. The transfer resolves the warp by its global name
 * ({@link PlayerWarpError#NOT_FOUND} when absent), gates the actor ({@link PlayerWarpError#NO_PERMISSION} when they
 * are not the owner), then re-owns the warp in place: the members, whitelist, bans, accrued earnings, and history
 * stay with the warp, only the owner identity moves.
 */
public final class TransferPlayerWarp {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final PlayerWarpNotifier notifier;
    private final Clock clock;

    public TransferPlayerWarp(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            PlayerWarpNotifier notifier,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Transfer {@code actor}'s warp {@code name} to {@code newOwner}, or reject when the actor may not transfer it. */
    public Result<Unit, PlayerWarpError> transfer(PlayerRef actor, PlayerWarpName name, PlayerRef newOwner) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(newOwner, "newOwner");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.TRANSFER)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        // A sponsored warp cannot be transferred (spec §6). Sponsorship lands in P6, so the guard hook belongs here
        // when it does; today no warp is sponsored, so there is nothing to check yet.
        repository.save(warp.transferredTo(newOwner, newOwner.name(), clock.instant()));
        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_TRANSFERRED,
                Map.of("warp", name.value(), "player", newOwner.name()));
        return Result.ok();
    }

    /**
     * Operator reassignment of the warp with surrogate id {@code id} to {@code newOwner}, skipping the per-warp role
     * gate. This is the by-id admin path {@code /pwarp admin setowner} runs; the command's {@code admin} node is the
     * authorization, so ownership moves without re-checking a role — a missing id is
     * {@link PlayerWarpError#NOT_FOUND}. Only the owner identity and cached name change; the warp keeps its members,
     * whitelist, bans, earnings, and history. The command renders the operator-facing result itself.
     */
    public Result<Unit, PlayerWarpError> adminSetOwner(PlayerWarpId id, PlayerRef newOwner) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(newOwner, "newOwner");
        Optional<PlayerWarp> found = repository.findById(id);
        if (found.isEmpty()) {
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        repository.save(found.get().transferredTo(newOwner, newOwner.name(), clock.instant()));
        return Result.ok();
    }
}
