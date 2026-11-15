package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;

/**
 * {@code /warp <name>}: teleport a player to a server warp. The warp is resolved by name and the player is run
 * through every check in order: the warp's permissions, its lock, its password, the safety of the destination, and
 * only then the cost. Execution is then <em>delegated</em> to the teleport context through {@link WarpTeleporter}:
 * this use case never moves the player itself, so the shared cooldown, the move-cancellable warmup and the
 * region-aware async hop are the teleport context's concern.
 *
 * <p>A warp set on another backend, while the warps {@code cross-server} block is on, is handed to the shared
 * cross-server hop through {@link WarpHops} instead. That hop leaves the teleport engine's own launch gate behind, so
 * the jail, combat and cooldown checks run here before the charge; the destination's safety is left to the target,
 * the only backend that holds its world. The charge travels with the hop and comes back on every miss.
 *
 * <p>With no economy provider wired, a priced warp's cost is ignored and the warp is usable for free: the soft
 * coupling to the economy context.
 */
public final class UseWarp {

    private final WarpRepository repository;
    private final WarpAccess access;
    private final WarpTeleporter teleporter;
    private final Notifier notifier;
    private final WarpSafetyChecker safetyChecker;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final WarpHops hops;

    public UseWarp(
            WarpRepository repository,
            WarpAccess access,
            WarpTeleporter teleporter,
            Notifier notifier,
            WarpSafetyChecker safetyChecker,
            Permissions permissions,
            Scheduler scheduler,
            WarpHops hops) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.safetyChecker = Objects.requireNonNull(safetyChecker, "safetyChecker");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.hops = Objects.requireNonNull(hops, "hops");
    }

    /** Teleport {@code who} to the warp {@code name} themselves, gating access and cost first. */
    public Result<Unit, WarpError> use(PlayerRef who, WarpName name) {
        return useFor(who, who, name, Optional.empty());
    }

    public Result<Unit, WarpError> use(PlayerRef who, WarpName name, String password) {
        return useFor(who, who, name, Optional.of(password));
    }

    /**
     * Send {@code recipient} to the warp {@code name}, triggered by {@code actor} (a staff send when the two
     * differ). The access gate and the optional cost apply to the <em>recipient</em>, staff send another
     * player only to a warp that player may use, and the recipient is the one teleported. On a cross-player
     * success the actor is told the send went through; a self-send (actor == recipient) takes the usual
     * teleporting path with no extra notice.
     */
    public Result<Unit, WarpError> useFor(PlayerRef actor, PlayerRef recipient, WarpName name) {
        return useFor(actor, recipient, name, Optional.empty());
    }

    public Result<Unit, WarpError> useFor(
            PlayerRef actor, PlayerRef recipient, WarpName name, Optional<String> password) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(name, "name");
        Optional<Warp> warp = repository.find(name);
        if (warp.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        return admitAndGo(actor, recipient, warp.get(), password);
    }

    private Result<Unit, WarpError> admitAndGo(
            PlayerRef actor, PlayerRef recipient, Warp warp, Optional<String> enteredPassword) {
        Map<String, String> placeholders = Map.of("warp", warp.name().value());
        WarpError refusal = refusal(recipient, warp, enteredPassword);
        if (refusal != null) {
            notifier.send(actor, refusal.messageKey(), placeholders);
            return Result.err(refusal);
        }
        boolean routed = hops.routes(warp);
        if (routed && !hops.admit(recipient, warp)) {
            // The teleport rules have already told the player why.
            return Result.err(WarpError.TELEPORT_REFUSED);
        }
        Result<Optional<WarpCost>, WarpError> charged = access.charge(recipient, warp);
        if (charged.isErr()) {
            WarpError error = charged.errorOrThrow();
            notifier.send(actor, error.messageKey(), placeholders);
            return Result.err(error);
        }
        if (!actor.uuid().equals(recipient.uuid())) {
            notifier.send(
                    actor, WarpsMessageKey.WARP_SENT, Map.of("warp", warp.name().value(), "player", recipient.name()));
        }
        if (routed) {
            hops.send(recipient, warp, charged.orElseThrow());
            return Result.ok();
        }
        notifier.send(recipient, WarpsMessageKey.WARP_TELEPORTING, placeholders);

        // Increment the visitor counter. The write persists to the DB (a single-writer SQLite by default),
        // so it is handed to the async scheduler rather than run on the caller's region thread, the counter
        // is a display statistic the teleport does not depend on, and the in-memory {@code updated} warp
        // already carries the new count for the hop below.
        Warp updated = warp.incrementedVisitors();
        scheduler.async(() -> repository.save(updated));

        teleporter.teleportTo(recipient, updated);
        return Result.ok();
    }

    /** The first check {@code recipient} fails before any money moves, or null when they pass them all. */
    private @Nullable WarpError refusal(PlayerRef recipient, Warp warp, Optional<String> enteredPassword) {
        if (!access.permits(recipient, warp)) {
            return WarpError.NO_PERMISSION;
        }
        if (warp.isLocked() && !permissions.has(recipient, "uxmessentials.warp.bypass.lock")) {
            return WarpError.LOCKED;
        }
        if (warp.password().isPresent()
                && !permissions.has(recipient, "uxmessentials.warp.bypass.password")
                && !enteredPassword.equals(warp.password())) {
            return WarpError.WRONG_PASSWORD;
        }
        // A warp on another backend points into a world this one does not hold, so only the target can judge it.
        if (!hops.routes(warp)
                && !safetyChecker.isSafe(warp.location())
                && !permissions.has(recipient, "uxmessentials.warp.bypass.safety")) {
            return WarpError.UNSAFE_LOCATION;
        }
        return null;
    }
}
