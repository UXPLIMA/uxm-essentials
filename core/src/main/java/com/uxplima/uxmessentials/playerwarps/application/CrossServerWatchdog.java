package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The origin's guard over a cross-server request it just charged for. The proxy connect is fire and forget: with
 * no proxy in front, or a target name the proxy does not know, the message is dropped and the player stays where
 * they are, and nothing on this backend can see that happen. So once the request TTL has passed the origin tries
 * to claim the row itself. The target's {@link CrossServerArrival} claims it when it moves the player, so a claim
 * that still removes the row here means no backend took the player, and the exact charge goes back.
 *
 * <p>Whether the player is still on this backend or has left the network makes no difference to the money:
 * either way no backend completed the hop. They are told only while they are still here, since a line for an
 * offline player is dropped. The timer lives in memory; if this backend stops before it fires, the row waits for
 * the player's next join, where the arrival refunds it as expired.
 *
 * <p>A new request overwrites the player's single row, so an earlier one nobody claimed is refunded as it is
 * replaced; otherwise its timer would find the newer row, claim nothing, and keep that charge.
 *
 * <p>The claim and the refund are IO, so {@link #track} is called off the tick thread and the TTL check runs on the
 * {@link Scheduler}'s async lane.
 */
public final class CrossServerWatchdog {

    private final PendingTeleportStore store;
    private final PlayerWarpRepository repository;
    private final CrossServerCharges charges;
    private final Scheduler scheduler;
    private final Duration requestTtl;

    public CrossServerWatchdog(
            PendingTeleportStore store,
            PlayerWarpRepository repository,
            CrossServerCharges charges,
            Scheduler scheduler,
            Duration requestTtl) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.requestTtl = Objects.requireNonNull(requestTtl, "requestTtl");
    }

    /**
     * Record {@code pending} as {@code who}'s in-flight request, refunding an earlier unclaimed one it replaces,
     * and start its TTL timer. Called by the origin immediately before it connects the player. A throw means the
     * new request was not recorded, so the caller still owns its charge and must give it back.
     */
    public void track(PlayerRef who, PendingTeleport pending, String warpLabel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(warpLabel, "warpLabel");
        Optional<PendingTeleport> earlier = store.find(who.uuid());
        if (earlier.isPresent()) {
            String earlierLabel = labelOf(earlier.get());
            if (store.clear(earlier.get()) == 1) {
                charges.refund(who, earlier.get().paid(), earlierLabel);
            }
        }
        store.record(pending);
        scheduler.asyncAfter(requestTtl, () -> expire(who, pending, warpLabel));
    }

    private void expire(PlayerRef who, PendingTeleport pending, String warpLabel) {
        if (store.clear(pending) != 1) {
            return;
        }
        charges.fail(who, pending.paid(), PlayerwarpsMessageKey.PWARP_CROSS_SERVER_TIMED_OUT, Map.of(), warpLabel);
    }

    /** The earlier request's warp name, or its surrogate id when the warp is gone or cannot be read. */
    private String labelOf(PendingTeleport earlier) {
        String fallback = "#" + earlier.warp().value();
        try {
            return repository
                    .findById(earlier.warp())
                    .map(warp -> warp.name().value())
                    .orElse(fallback);
        } catch (RuntimeException unreadable) {
            // Only the label is lost; refusing the new request over it would strand both charges.
            return fallback;
        }
    }
}
