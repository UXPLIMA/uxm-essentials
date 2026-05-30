package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * {@code /warp <name>}: teleport a player to a server warp. The warp is resolved by name, the player is run
 * through the {@link WarpAccess} gate (per-warp permission, the warp's optional extra permission, and — only
 * when an economy provider is present — the per-warp cost), and only then is execution <em>delegated</em> to
 * the teleport context through {@link WarpTeleporter}. This use case never moves the player itself, so the
 * shared cooldown, the move-cancellable warmup, and the region-aware async hop are all the teleport
 * context's concern.
 *
 * <p>The charge (if any) is taken inside the access gate before the hop is queued, so a warp the player
 * cannot afford never teleports them. With no economy provider wired, a priced warp's cost is ignored and
 * the warp is usable for free — the soft coupling to the economy context.
 */
public final class UseWarp {

    private final WarpRepository repository;
    private final WarpAccess access;
    private final WarpTeleporter teleporter;
    private final WarpNotifier notifier;

    public UseWarp(WarpRepository repository, WarpAccess access, WarpTeleporter teleporter, WarpNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Teleport {@code who} to the warp {@code name}, gating access and cost first. */
    public Result<Unit, WarpError> use(PlayerRef who, WarpName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Optional<Warp> warp = repository.find(name);
        if (warp.isEmpty()) {
            notifier.send(who, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        return admitAndGo(who, warp.get());
    }

    private Result<Unit, WarpError> admitAndGo(PlayerRef who, Warp warp) {
        Result<Unit, WarpError> admitted = access.admit(who, warp);
        if (admitted.isErr()) {
            WarpError error = admitted.errorOrThrow();
            notifier.send(who, error.messageKey(), Map.of("warp", warp.name().value()));
            return admitted;
        }
        notifier.send(
                who,
                WarpsMessageKey.WARP_TELEPORTING,
                Map.of("warp", warp.name().value()));
        teleporter.teleportTo(who, warp);
        return Result.ok();
    }
}
