package com.uxplima.uxmessentials.shared.application.hop;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The origin's guard over a hop it just charged for. The proxy connect is fire and forget: with no proxy in front,
 * or a target name the proxy does not know, the message is dropped and the player stays where they are, and
 * nothing on this backend can see that happen. So once the request TTL has passed the origin tries to claim the
 * row itself. The target's {@link HopArrival} claims it when it moves the player, so a claim that still removes
 * the row here means no backend took the player, and the exact charge goes back.
 *
 * <p>Whether the player is still on this backend or has left the network makes no difference to the money:
 * either way no backend completed the hop. They are told only while they are still here, since a line for an
 * offline player is dropped. The timer lives in memory; if this backend stops before it fires, the row waits for
 * the player's next join, where the arrival refunds it as expired.
 *
 * <p>A new hop overwrites the player's single row, so an earlier one nobody claimed is refunded as it is replaced;
 * otherwise its timer would find the newer row, claim nothing, and keep that charge. An earlier row of a kind this
 * backend has no resolver for cannot be refunded here, so the new hop is refused instead of overwriting it, until
 * that row is past twice the TTL: then it is claimed and logged (see {@link AbandonedHops}) and the new hop goes
 * ahead.
 *
 * <p>Two tracks for the same player at once would both read no earlier row, the second write would replace the
 * first unseen, and the first hop's timer would then find nothing to claim and keep its charge. So while one track
 * is between its read and its write, a second one for that player on this backend is refused and refunded there;
 * the first goes ahead. Once a row is written, a later track reads it and replaces it as above.
 *
 * <p>The claim and the refund are IO, so {@link #track} is called off the tick thread and the TTL check runs on the
 * {@link Scheduler}'s async lane.
 */
public final class HopWatchdog {

    private final PendingHopStore store;
    private final HopResolvers resolvers;
    private final HopCharges charges;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration requestTtl;
    private final AbandonedHops abandoned;
    /** The players whose track is between reading the earlier row and writing the new one. */
    private final Set<UUID> recording = ConcurrentHashMap.newKeySet();

    public HopWatchdog(
            PendingHopStore store,
            HopResolvers resolvers,
            HopCharges charges,
            Scheduler scheduler,
            Logger log,
            Duration requestTtl,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.requestTtl = Objects.requireNonNull(requestTtl, "requestTtl");
        this.abandoned = new AbandonedHops(store, log, requestTtl, clock);
    }

    /**
     * Record {@code pending} as {@code who}'s in-flight hop, refunding an earlier unclaimed one it replaces, and
     * start its TTL timer. Called by the origin immediately before it connects the player. Returns false when the
     * hop could not be tracked; its charge has then been dealt with here and the player must not be sent.
     */
    public boolean track(PlayerRef who, PendingHop pending, String label) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(label, "label");
        HopResolver resolver = resolvers
                .find(pending.kind())
                .orElseThrow(() -> new IllegalStateException("no hop resolver for " + pending.kind()));
        if (!recording.add(who.uuid())) {
            // Nothing of this hop was written, so this refund is the only one its charge can get. The kind's failure
            // hook is not told: the hop never started, and the first one is still on its way.
            log.debug("event=cross_server_hop_refused player={} reason=concurrent_hop", who.uuid());
            charges.tell(resolver, who, HopNotice.UNAVAILABLE, Map.of("server", pending.targetServer()), label);
            charges.refund(resolver, who, pending.charge(), label);
            return false;
        }
        try {
            if (!replaceEarlier(who)) {
                unreachable(resolver, who, pending, label);
                return false;
            }
            store.record(pending);
        } catch (RuntimeException unrecorded) {
            log.error("event=cross_server_hop_record_failed player=" + who.uuid(), unrecorded);
            // The player has not been sent yet, so no backend can have claimed the row. A write that landed despite
            // the error is deleted before the refund where the database still answers, so a later join does not
            // refund it a second time.
            clearQuietly(pending);
            unreachable(resolver, who, pending, label);
            return false;
        } finally {
            recording.remove(who.uuid());
        }
        try {
            scheduler.asyncAfter(requestTtl, () -> expire(resolver, who, pending, label));
            return true;
        } catch (RuntimeException unscheduled) {
            log.error("event=cross_server_hop_timer_failed player=" + who.uuid(), unscheduled);
            // The row is stored but has no timer. Only a claim that removes it may refund; if the delete fails the
            // row stays for the player's next join to refund as expired.
            if (clearQuietly(pending) == 1) {
                unreachable(resolver, who, pending, label);
            }
            return false;
        }
    }

    /** Refund an earlier unclaimed hop before it is overwritten; false when it cannot be refunded here. */
    private boolean replaceEarlier(PlayerRef who) {
        Optional<PendingHop> earlier = store.find(who.uuid());
        if (earlier.isEmpty()) {
            return true;
        }
        Optional<HopResolver> resolver = resolvers.find(earlier.get().kind());
        if (resolver.isEmpty()) {
            if (abandoned.claimUnresolvable(earlier.get())) {
                return true;
            }
            log.warn(
                    "event=cross_server_hop_replace_refused player={} earlier_kind={} reason=no_resolver",
                    who.uuid(),
                    earlier.get().kind());
            return false;
        }
        String earlierLabel = labelOf(resolver.get(), earlier.get());
        if (store.clear(earlier.get()) == 1) {
            charges.refund(resolver.get(), who, earlier.get().charge(), earlierLabel);
        }
        return true;
    }

    private void expire(HopResolver resolver, PlayerRef who, PendingHop pending, String label) {
        if (store.clear(pending) != 1) {
            return;
        }
        charges.fail(resolver, who, pending.charge(), HopNotice.TIMED_OUT, Map.of(), label);
    }

    private void unreachable(HopResolver resolver, PlayerRef who, PendingHop pending, String label) {
        charges.fail(
                resolver,
                who,
                pending.charge(),
                HopNotice.UNAVAILABLE,
                Map.of("server", pending.targetServer()),
                label);
    }

    /** The removed-row count of a best-effort delete, or -1 when the delete itself failed. */
    private int clearQuietly(PendingHop pending) {
        try {
            return store.clear(pending);
        } catch (RuntimeException undeleted) {
            log.error("event=cross_server_hop_clear_failed player=" + pending.player(), undeleted);
            return -1;
        }
    }

    /** The label a resolver gives, or the bare target reference when it cannot read one. */
    static String labelOf(HopResolver resolver, PendingHop hop) {
        try {
            return resolver.label(hop);
        } catch (RuntimeException unreadable) {
            // Only the label is lost; refusing or delaying the refund over it would strand the charge.
            return "#" + hop.target();
        }
    }
}
