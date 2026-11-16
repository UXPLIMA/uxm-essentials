package com.uxplima.uxmessentials.shared.application.hop;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The arrival half of every cross-server hop. When a player joins this backend the inbound listener hands them
 * here; if the shared store holds a hop that targets this backend and is still fresh, the kind's
 * {@link HopResolver} finds the destination and the player is moved after a short settling delay. The charge is
 * settled only once the player has landed. A row past its expiry, or one aimed at another backend, is refunded
 * instead, and so is a destination that is gone or has moved off this backend, and a hop that is refused or
 * misses. A row of a kind with no resolver here is left alone while it is fresh, since the origin's
 * {@link HopWatchdog} refunds it; once it is past the expiry it is claimed and logged instead, so it neither stays
 * forever nor blocks the player's next hop.
 *
 * <p>Nothing is acted on before the row is claimed: the store's conditional delete must remove it, or this join
 * does nothing. Of two quick joins, or a join racing the origin's watchdog, only one caller can win that delete,
 * so the player is moved or refunded once. The hop claims only after the settling delay, right before it moves the
 * player: a backend that stops during the delay drops the delayed task, and the row it left unclaimed is then
 * refunded by the origin's watchdog or by the player's next join. Reads that can fail run before the claim for
 * the same reason.
 *
 * <p>Once claimed, a hop is held in memory until it has ended, since no other backend can see it any more. A backend
 * that stops drops the region and entity tasks it had queued without running their fallbacks, so {@link #stop} refunds
 * every claimed hop that has not ended yet, once: each hop ends through a single guard, whichever of the landing, the
 * miss, the retired task or the stop reaches it first. After a stop nothing new is claimed, and a row left unclaimed
 * waits for the origin's watchdog or the player's next join as before.
 *
 * <p>Every store read runs off the tick thread through the {@link Scheduler} port; only the move is marshalled onto
 * the player's entity region (Folia-safe), and the money work goes back to the async lane, or runs in place when a
 * disabling backend's lane refuses it.
 */
public final class HopArrival {

    private final PendingHopStore store;
    private final HopResolvers resolvers;
    private final HopCharges charges;
    private final Scheduler scheduler;
    private final Logger log;
    private final String localServerId;
    private final Duration arrivalDelay;
    private final AbandonedHops abandoned;
    private final Set<Claimed> claimed = ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;

    public HopArrival(
            PendingHopStore store,
            HopResolvers resolvers,
            HopCharges charges,
            Scheduler scheduler,
            Logger log,
            String localServerId,
            Duration arrivalDelay,
            Duration requestTtl,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.arrivalDelay = Objects.requireNonNull(arrivalDelay, "arrivalDelay");
        this.abandoned = new AbandonedHops(store, log, requestTtl, clock);
    }

    /** Read the joining player's pending hop off-tick and either schedule the move or refund the row. */
    public void onArrival(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> triage(who));
    }

    /** Off-tick: defer a fresh row aimed here to the delayed move; claim and refund an expired or misrouted one. */
    private void triage(PlayerRef who) {
        Optional<PendingHop> found = store.find(who.uuid());
        if (found.isEmpty()) {
            return;
        }
        PendingHop pending = found.get();
        Optional<HopResolver> known = resolvers.find(pending.kind());
        if (known.isEmpty()) {
            if (!abandoned.claimUnresolvable(pending)) {
                log.warn(
                        "event=cross_server_hop_unknown_kind player={} kind={} outcome=left_for_origin",
                        who.uuid(),
                        pending.kind());
            }
            return;
        }
        HopResolver resolver = known.get();
        boolean expired = abandoned.expired(pending);
        // A row aimed at another backend means the proxy put the player somewhere other than the destination's
        // backend, so the hop they paid for never happened and no other backend will see this row again.
        boolean misrouted = !pending.targetServer().equals(localServerId);
        if (!expired && !misrouted) {
            scheduler.asyncAfter(arrivalDelay, () -> complete(who, pending, resolver));
            return;
        }
        String label = HopWatchdog.labelOf(resolver, pending);
        if (store.clear(pending) != 1) {
            return;
        }
        HopNotice reason = expired ? HopNotice.TIMED_OUT : HopNotice.MISROUTED;
        charges.fail(resolver, who, pending.charge(), reason, Map.of(), label);
    }

    /** Off-tick after the settling delay: claim the row, then move the player or refund and notify failure. */
    private void complete(PlayerRef who, PendingHop pending, HopResolver resolver) {
        if (stopped) {
            return; // left unclaimed, for the origin's watchdog or the player's next join
        }
        HopResolution resolution = resolver.resolve(pending);
        if (store.clear(pending) != 1) {
            return;
        }
        Optional<HopLanding> landing = resolution.landing();
        String label = resolution.label();
        if (resolution.withdrawn()) {
            charges.refund(resolver, who, pending.charge(), label);
            return;
        }
        if (landing.isEmpty()) {
            charges.fail(resolver, who, pending.charge(), HopNotice.FAILED, Map.of(), label);
            return;
        }
        Claimed hop = new Claimed(who, pending, resolver, label);
        claimed.add(hop);
        if (stopped) {
            // The stop ran between the claim and the line above and did not see this hop.
            hop.refund();
            return;
        }
        try {
            // A player who left during the settling delay, or a backend that is shutting down, never moved them,
            // so the retired branch refunds.
            scheduler.onEntity(who, () -> move(hop, landing.get()), () -> {
                if (hop.end()) {
                    offTick(hop::refundCharge);
                }
            });
        } catch (RuntimeException scheduleFailure) {
            log.error("event=cross_server_hop_move_unscheduled player=" + who.uuid(), scheduleFailure);
            hop.fail();
        }
    }

    /**
     * Stop claiming hops and refund every hop this backend claimed that has not ended yet, in place: the tasks that
     * would end them are dropped with the plugin. Called while the plugin is disabling, before the economies stop.
     */
    public void stop() {
        stopped = true;
        for (Claimed hop : Set.copyOf(claimed)) {
            hop.refund();
        }
    }

    /**
     * On the player's region thread: move them at once, then settle only once they have landed, or refund a move
     * that was refused or missed. The guard keeps that to one outcome even if a move reports a miss and then
     * throws as well.
     */
    private void move(Claimed hop, HopLanding landing) {
        Runnable landed = () -> {
            if (hop.end()) {
                if (hop.pending.settlement().isPresent()) {
                    offTick(() -> charges.settle(hop.resolver, hop.who, hop.pending, hop.label));
                }
                charges.tell(hop.resolver, hop.who, HopNotice.ARRIVED, Map.of(), hop.label);
            }
        };
        Runnable missed = () -> {
            if (hop.end()) {
                offTick(hop::failCharge);
            }
        };
        try {
            landing.land(hop.who, landed, missed);
        } catch (RuntimeException moveFailure) {
            log.error("event=cross_server_hop_move_failed player=" + hop.who.uuid(), moveFailure);
            missed.run();
        }
    }

    /**
     * One hop this backend claimed, until it ends. {@link #end} lets exactly one ending through, so the money is
     * settled, given back or failed once, whichever ending comes first.
     */
    private final class Claimed {

        private final PlayerRef who;
        private final PendingHop pending;
        private final HopResolver resolver;
        private final String label;
        private final AtomicBoolean ended = new AtomicBoolean();

        Claimed(PlayerRef who, PendingHop pending, HopResolver resolver, String label) {
            this.who = who;
            this.pending = pending;
            this.resolver = resolver;
            this.label = label;
        }

        /** True for the first ending only, which is then the one that settles the money. */
        boolean end() {
            if (!ended.compareAndSet(false, true)) {
                return false;
            }
            claimed.remove(this);
            return true;
        }

        /** End here as never moved, giving the charge back in place. */
        void refund() {
            if (end()) {
                refundCharge();
            }
        }

        /** End here as refused or missed, giving the charge back in place with the failure line. */
        void fail() {
            if (end()) {
                failCharge();
            }
        }

        /** The money of a hop that ended unmoved. Run only by the caller whose {@link #end} won. */
        void refundCharge() {
            charges.refund(resolver, who, pending.charge(), label);
        }

        /** The money of a hop whose move was refused or missed. Run only by the caller whose {@link #end} won. */
        void failCharge() {
            charges.fail(resolver, who, pending.charge(), HopNotice.FAILED, Map.of(), label);
        }
    }

    /**
     * Hand money work to the async lane, or run it here when the lane refuses it (a backend that is disabling), so a
     * refund or a settlement is never dropped with the task.
     */
    private void offTick(Runnable work) {
        try {
            scheduler.async(work);
        } catch (RuntimeException rejected) {
            log.warn("event=cross_server_hop_async_rejected reason={}", rejected.toString());
            work.run();
        }
    }
}
