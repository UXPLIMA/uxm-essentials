package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The arrival half of a cross-server teleport. When a player joins this backend the inbound listener hands them
 * here; if the shared store holds a pending teleport that targets this backend and is still fresh, the warp is
 * resolved and the player is hopped locally after a short settling delay, exactly as if they had run
 * {@code /pwarp} here. A row older than the request TTL, or one aimed at another backend, is refunded instead, and
 * so is a warp that is gone, suspended, or has since moved off this backend.
 *
 * <p>Every path starts by claiming the row: the store's conditional delete must remove it, or this join does
 * nothing. Of two quick joins, or a join racing the origin's {@link CrossServerWatchdog}, only one caller can win
 * that delete, so the player is moved or refunded once.
 *
 * <p>Every store and repository read runs off the tick thread through the {@link Scheduler} port; only the final
 * local hop is marshalled back onto the player's entity region, so the live player is moved on their own region
 * thread (Folia-safe). The refund goes through the same {@link PlayerWarpEconomy} seam the origin charged on, so
 * the debit and its reversal share one currency-aware, DB-backed path.
 */
public final class CrossServerArrival {

    private final PendingTeleportStore store;
    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final CrossServerRefund refund;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final String localServerId;
    private final Duration arrivalDelay;
    private final Duration requestTtl;
    private final Clock clock;

    public CrossServerArrival(
            PendingTeleportStore store,
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            Optional<PlayerWarpEconomy> economy,
            Notifier notifier,
            Scheduler scheduler,
            String localServerId,
            Duration arrivalDelay,
            Duration requestTtl,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.refund = new CrossServerRefund(economy, notifier);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.arrivalDelay = Objects.requireNonNull(arrivalDelay, "arrivalDelay");
        this.requestTtl = Objects.requireNonNull(requestTtl, "requestTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Read the joining player's pending teleport off-tick and either schedule the hop or refund the row. */
    public void onArrival(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> triage(who));
    }

    /** Off-tick: claim the row, then refund it when it is stale or misrouted, else defer to the delayed hop. */
    private void triage(PlayerRef who) {
        Optional<PendingTeleport> found = store.find(who.uuid());
        if (found.isEmpty()) {
            return;
        }
        PendingTeleport pending = found.get();
        if (store.clear(pending) != 1) {
            return;
        }
        if (isExpired(pending)) {
            fail(who, pending, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_TIMED_OUT, repository.findById(pending.warp()));
            return;
        }
        if (!pending.targetServer().equals(localServerId)) {
            // The proxy put the player somewhere other than the warp's backend, so the hop they paid for never
            // happened and no other backend will see this row again.
            fail(who, pending, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_MISROUTED, repository.findById(pending.warp()));
            return;
        }
        scheduler.asyncAfter(arrivalDelay, () -> complete(who, pending));
    }

    /** Off-tick after the settling delay: resolve the warp and either hop the player or refund and notify failure. */
    private void complete(PlayerRef who, PendingTeleport pending) {
        Optional<PlayerWarp> resolved = repository.findById(pending.warp());
        if (!isArrivable(resolved)) {
            fail(who, pending, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_FAILED, resolved);
            return;
        }
        PlayerWarp warp = resolved.orElseThrow();
        String name = warp.name().value();
        // A player who leaves during the settling delay was never moved, so the retired branch refunds them; the
        // refund is IO, so it goes back to the async lane.
        scheduler.onEntity(
                who,
                () -> {
                    notifier.send(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_ARRIVED, Map.of("warp", name));
                    teleporter.teleportTo(who, warp);
                },
                () -> scheduler.async(() -> refund.give(who, pending, name)));
    }

    /** Tell the player why the hop did not happen and give the charge back. */
    private void fail(PlayerRef who, PendingTeleport pending, PlayerwarpsMessageKey reason, Optional<PlayerWarp> warp) {
        String label = label(warp, pending);
        notifier.send(who, reason, Map.of("warp", label));
        refund.give(who, pending, label);
    }

    /** True once the request has aged past the configured TTL, so a long-abandoned row is cleared not honoured. */
    private boolean isExpired(PendingTeleport pending) {
        return pending.requestedAt().plus(requestTtl).isBefore(clock.instant());
    }

    /** A warp is arrivable only if it still exists, is ACTIVE, and lives on this backend as of the read. */
    private boolean isArrivable(Optional<PlayerWarp> warp) {
        return warp.filter(w -> w.status() == WarpStatus.ACTIVE)
                .flatMap(PlayerWarp::serverId)
                .map(localServerId::equals)
                .orElse(false);
    }

    /** The warp label for the failure notice: the resolved name when known, else the surrogate id. */
    private static String label(Optional<PlayerWarp> warp, PendingTeleport pending) {
        return warp.map(w -> w.name().value()).orElse("#" + pending.warp().value());
    }
}
