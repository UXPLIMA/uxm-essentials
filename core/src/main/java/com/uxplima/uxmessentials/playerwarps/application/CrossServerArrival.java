package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The arrival half of a cross-server teleport. When a player joins this backend the inbound listener hands them
 * here; if the shared store holds a pending teleport that targets this backend and is still fresh, the warp is
 * resolved and the player is hopped locally after a short settling delay. The hop is immediate, with no warmup and
 * no cooldown gate, since the player paid and waited on the origin; a jailed player is still refused and refunded.
 * The warp's owner is paid only once the player has landed, and only on a row whose origin left that payment to
 * the arrival (an earlier release paid at charge time). A row past its expiry, or one aimed at another
 * backend, is refunded instead, and so is a warp that is gone, suspended, or has since moved off this backend.
 *
 * <p>Nothing is acted on before the row is claimed: the store's conditional delete must remove it, or this join
 * does nothing. Of two quick joins, or a join racing the origin's {@link CrossServerWatchdog}, only one caller can
 * win that delete, so the player is moved or refunded once. The hop claims only after the settling delay, right
 * before it moves the player: a backend that stops during the delay drops the delayed task, and the row it left
 * unclaimed is then refunded by the origin's watchdog or by the player's next join. Reads that can fail run before
 * the claim for the same reason.
 *
 * <p>Every store and repository read runs off the tick thread through the {@link Scheduler} port; only the local
 * hop is marshalled onto the player's entity region (Folia-safe), and the owner's accrual goes back to the async
 * lane. Money moves through {@link CrossServerCharges}, the same economy seam the origin charged on.
 */
public final class CrossServerArrival {

    private final PendingTeleportStore store;
    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final CrossServerCharges charges;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Logger log;
    private final String localServerId;
    private final Duration arrivalDelay;
    private final Duration expiry;
    private final Clock clock;

    public CrossServerArrival(
            PendingTeleportStore store,
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            CrossServerCharges charges,
            Notifier notifier,
            Scheduler scheduler,
            Logger log,
            String localServerId,
            Duration arrivalDelay,
            Duration requestTtl,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.arrivalDelay = Objects.requireNonNull(arrivalDelay, "arrivalDelay");
        // The request time is stamped by the origin's clock and read against this backend's, so a skew between
        // the two would expire a fresh hop. Twice the TTL absorbs that; the live case, a player the proxy never
        // moved, is already settled at one TTL by the origin's watchdog, which compares only its own clock.
        this.expiry = Objects.requireNonNull(requestTtl, "requestTtl").multipliedBy(2);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Read the joining player's pending teleport off-tick and either schedule the hop or refund the row. */
    public void onArrival(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> triage(who));
    }

    /** Off-tick: defer a fresh row aimed here to the delayed hop; claim and refund an expired or misrouted one. */
    private void triage(PlayerRef who) {
        Optional<PendingTeleport> found = store.find(who.uuid());
        if (found.isEmpty()) {
            return;
        }
        PendingTeleport pending = found.get();
        boolean expired = isExpired(pending);
        // A row aimed at another backend means the proxy put the player somewhere other than the warp's backend,
        // so the hop they paid for never happened and no other backend will see this row again.
        boolean misrouted = !pending.targetServer().equals(localServerId);
        if (!expired && !misrouted) {
            scheduler.asyncAfter(arrivalDelay, () -> complete(who, pending));
            return;
        }
        String label = labelOf(pending);
        if (store.clear(pending) != 1) {
            return;
        }
        PlayerwarpsMessageKey reason = expired
                ? PlayerwarpsMessageKey.PWARP_CROSS_SERVER_TIMED_OUT
                : PlayerwarpsMessageKey.PWARP_CROSS_SERVER_MISROUTED;
        charges.fail(who, pending.paid(), reason, Map.of(), label);
    }

    /** Off-tick after the settling delay: claim the row, then hop the player or refund and notify failure. */
    private void complete(PlayerRef who, PendingTeleport pending) {
        Optional<PlayerWarp> resolved = repository.findById(pending.warp());
        if (store.clear(pending) != 1) {
            return;
        }
        if (!isArrivable(resolved)) {
            charges.fail(
                    who,
                    pending.paid(),
                    PlayerwarpsMessageKey.PWARP_CROSS_SERVER_FAILED,
                    Map.of(),
                    label(resolved, pending));
            return;
        }
        PlayerWarp warp = resolved.orElseThrow();
        String name = warp.name().value();
        try {
            // A player who left during the settling delay, or a backend that is shutting down, never moved them,
            // so the retired branch refunds.
            scheduler.onEntity(
                    who, () -> hop(who, pending, warp), () -> offTick(() -> charges.refund(who, pending.paid(), name)));
        } catch (RuntimeException scheduleFailure) {
            log.error("event=playerwarp_cross_server_hop_unscheduled player=" + who.uuid(), scheduleFailure);
            charges.fail(who, pending.paid(), PlayerwarpsMessageKey.PWARP_CROSS_SERVER_FAILED, Map.of(), name);
        }
    }

    /**
     * On the player's region thread: move them at once, then pay the owner only once they have landed, or refund
     * a hop that was refused or missed. The guard keeps that to one outcome even if a hop reports a miss and then
     * throws as well.
     */
    private void hop(PlayerRef who, PendingTeleport pending, PlayerWarp warp) {
        String name = warp.name().value();
        AtomicBoolean settled = new AtomicBoolean();
        Runnable landed = () -> {
            if (settled.compareAndSet(false, true)) {
                if (pending.accrueOnArrival()) {
                    offTick(() -> charges.settle(who, pending.warp(), pending.paid(), name));
                }
                notifier.send(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_ARRIVED, Map.of("warp", name));
            }
        };
        Runnable missed = () -> {
            if (settled.compareAndSet(false, true)) {
                offTick(() -> charges.fail(
                        who, pending.paid(), PlayerwarpsMessageKey.PWARP_CROSS_SERVER_FAILED, Map.of(), name));
            }
        };
        try {
            teleporter.arrive(who, warp, landed, missed);
        } catch (RuntimeException hopFailure) {
            log.error("event=playerwarp_cross_server_hop_failed player=" + who.uuid(), hopFailure);
            missed.run();
        }
    }

    /**
     * Hand money work to the async lane, or run it here when the lane refuses it (a backend that is disabling), so a
     * refund or an owner payment is never dropped with the task.
     */
    private void offTick(Runnable work) {
        try {
            scheduler.async(work);
        } catch (RuntimeException rejected) {
            log.warn("event=playerwarp_cross_server_async_rejected reason={}", rejected.toString());
            work.run();
        }
    }

    /** True once the request has aged past the expiry, so a long-abandoned row is refunded not honoured. */
    private boolean isExpired(PendingTeleport pending) {
        return pending.requestedAt().plus(expiry).isBefore(clock.instant());
    }

    /** A warp is arrivable only if it still exists, is ACTIVE, and lives on this backend as of the read. */
    private boolean isArrivable(Optional<PlayerWarp> warp) {
        return warp.filter(w -> w.status() == WarpStatus.ACTIVE)
                .flatMap(PlayerWarp::serverId)
                .map(localServerId::equals)
                .orElse(false);
    }

    /** The label for a refund notice, read before the claim; a failed read falls back to the surrogate id. */
    private String labelOf(PendingTeleport pending) {
        try {
            return label(repository.findById(pending.warp()), pending);
        } catch (RuntimeException unreadable) {
            // Only the label is lost; the refund itself must not wait on the warp table.
            return label(Optional.empty(), pending);
        }
    }

    /** The warp label for the failure notice: the resolved name when known, else the surrogate id. */
    private static String label(Optional<PlayerWarp> warp, PendingTeleport pending) {
        return warp.map(w -> w.name().value()).orElse("#" + pending.warp().value());
    }
}
