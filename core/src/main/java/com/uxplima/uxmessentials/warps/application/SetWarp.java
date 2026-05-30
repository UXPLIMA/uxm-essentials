package com.uxplima.uxmessentials.warps.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.event.WarpCreated;

/**
 * {@code /setwarp <name>}: create a server-wide warp at the staff member's current position, or re-anchor
 * an existing one of the same name in place. A brand-new name is stored as a new {@link Warp} and publishes
 * {@code WarpCreated}; an existing name overwrites the row, keeping the warp's owner, creation time, and
 * gates, and saves silently. The operator-only permission to run this command is enforced at the adapter
 * gate; this use case owns the create-vs-move decision and the persistence.
 *
 * <p>The optional per-warp cost and required permission are passed through from the command so an operator
 * can set a gated or priced warp; with neither, a plain free warp is created.
 */
public final class SetWarp {

    private final WarpRepository repository;
    private final WarpNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetWarp(WarpRepository repository, WarpNotifier notifier, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Create or move the warp {@code name} to {@code at}, free and ungated. */
    public Result<Unit, WarpError> set(PlayerRef owner, WarpName name, Position at) {
        return set(owner, name, at, WarpCost.free(), Optional.empty());
    }

    /** Create or move the warp {@code name} to {@code at} with an optional cost and required permission. */
    public Result<Unit, WarpError> set(
            PlayerRef owner, WarpName name, Position at, WarpCost cost, Optional<String> requiredPermission) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(requiredPermission, "requiredPermission");
        Optional<Warp> existing = repository.find(name);
        return existing.isPresent()
                ? reanchor(owner, existing.get(), at)
                : create(owner, name, at, cost, requiredPermission);
    }

    private Result<Unit, WarpError> create(
            PlayerRef owner, WarpName name, Position at, WarpCost cost, Optional<String> requiredPermission) {
        Warp warp = Warp.create(name, at, owner, clock.instant(), cost, requiredPermission);
        repository.save(warp);
        events.publish(new WarpCreated(name, owner, at));
        notifier.send(owner, WarpsMessageKey.WARP_SET, Map.of("warp", name.value()));
        return Result.ok();
    }

    private Result<Unit, WarpError> reanchor(PlayerRef owner, Warp existing, Position at) {
        repository.save(existing.movedTo(at));
        notifier.send(
                owner,
                WarpsMessageKey.WARP_MOVED,
                Map.of("warp", existing.name().value()));
        return Result.ok();
    }
}
