package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;

/**
 * {@code /pwarp <name>}: teleport a player to a player-warp resolved by its server-wide-unique name. This is a
 * deliberately <em>fail-closed minimal</em> access gate: the actor always reaches a warp they own, and reaches
 * another owner's warp only when its access is {@link WarpAccess#PUBLIC}. Password, whitelist, and private warps
 * are all owner-only here — the rich gate (password verification, whitelist, bans, roles, the economy charge)
 * lands in a later task and can only ever <em>widen</em> this, never narrow it, so this stance is the safe one.
 *
 * <p>Execution is then <em>delegated</em> to the teleport context through {@link PlayerWarpTeleporter}; this use
 * case never moves the player itself, so the shared cooldown, the move-cancellable warmup, and the region-aware
 * async hop are all the teleport context's concern. The {@link Permissions} port survives only for the
 * {@code bypass.safety} node; the safety check itself is delegated to {@link WarpSafetyChecker}.
 */
public final class UsePlayerWarp {

    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final PlayerWarpNotifier notifier;
    private final WarpSafetyChecker safetyChecker;
    private final Permissions permissions;

    public UsePlayerWarp(
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            PlayerWarpNotifier notifier,
            WarpSafetyChecker safetyChecker,
            Permissions permissions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.safetyChecker = Objects.requireNonNull(safetyChecker, "safetyChecker");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    /**
     * Teleport {@code actor} to the warp {@code name}. A missing warp is rejected with
     * {@link PlayerWarpError#NOT_FOUND}; a non-public warp owned by someone else is refused with
     * {@link PlayerWarpError#NOT_PUBLIC}. The actor always reaches their own warp.
     */
    public Result<Unit, PlayerWarpError> useFor(PlayerRef actor, PlayerWarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> warp = repository.findByName(name);
        if (warp.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        return admitAndGo(actor, warp.get());
    }

    /**
     * The overload the command surface calls when it still resolves an explicit owner argument. Warp names are
     * globally unique now, so the warp is found by name alone and {@code owner} is only a caller-side hint; the
     * owner used for the access gate is always the resolved warp's own owner.
     */
    public Result<Unit, PlayerWarpError> useFor(PlayerRef actor, PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        return useFor(actor, name);
    }

    private Result<Unit, PlayerWarpError> admitAndGo(PlayerRef actor, PlayerWarp warp) {
        PlayerRef owner = warp.owner();
        if (!actor.uuid().equals(owner.uuid()) && warp.access() != WarpAccess.PUBLIC) {
            notifier.send(
                    actor,
                    PlayerWarpError.NOT_PUBLIC.messageKey(),
                    Map.of("warp", warp.name().value()));
            return Result.err(PlayerWarpError.NOT_PUBLIC);
        }
        if (!safetyChecker.isSafe(warp.location())
                && !permissions.has(actor, "uxmessentials.playerwarp.bypass.safety")) {
            notifier.send(
                    actor,
                    PlayerWarpError.UNSAFE_LOCATION.messageKey(),
                    Map.of("warp", warp.name().value()));
            return Result.err(PlayerWarpError.UNSAFE_LOCATION);
        }

        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_TELEPORTING,
                Map.of("warp", warp.name().value()));

        // Record the visit atomically in storage rather than reading, bumping, and saving the whole row here —
        // that loses concurrent visits to a last-writer-wins race and would needlessly invalidate the owner's
        // cross-server cache on every teleport. The warp we hand the teleporter shows the pre-visit count, which
        // is fine for this request.
        repository.recordVisit(warp.id().orElseThrow());

        teleporter.teleportTo(actor, warp);
        return Result.ok();
    }
}
