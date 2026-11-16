package com.uxplima.uxmessentials.shared.application.hop;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.application.hop.port.LocalWarmups;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The send half of every cross-server hop. A use case that finds its destination on another backend asks
 * {@link #admit} first, so the jail, combat-tag and cooldown checks of a local teleport refuse the hop before any
 * money moves; then it takes its charge and calls {@link #send}, which records the hop through the
 * {@link HopWatchdog} and asks the proxy to move the player. The target's {@link HopArrival} then claims the row
 * and finishes the hop.
 *
 * <p>The connect cannot report whether the proxy acted on it, so the watchdog refunds any hop no backend claimed
 * within the request TTL, and {@link HopConnector#isAvailable()} only catches a channel this plugin failed to
 * register. {@link #send} is called off the tick thread, since the watchdog writes to the store; the connect rides
 * the player's own connection and is marshalled onto their entity region (Folia-safe), skipped if they left in
 * between, which leaves the hop to the watchdog.
 *
 * <p>A hop that is sent replaces the player's local warmup, as a new local teleport does, on the same region task
 * that connects them: a slow or failed transfer must not let the earlier teleport land after the player chose another.
 * A refused hop leaves it running, as a refused local teleport does.
 */
public final class HopDepartures {

    private final HopGate gate;
    private final HopConnector connector;
    private final HopWatchdog watchdog;
    private final HopCharges charges;
    private final HopResolvers resolvers;
    private final Scheduler scheduler;
    private final String localServerId;
    private final Clock clock;
    private final LocalWarmups localWarmups;

    /** A departure on a backend with no local warmups to replace. */
    public HopDepartures(
            HopGate gate,
            HopConnector connector,
            HopWatchdog watchdog,
            HopCharges charges,
            HopResolvers resolvers,
            Scheduler scheduler,
            String localServerId,
            Clock clock) {
        this(gate, connector, watchdog, charges, resolvers, scheduler, localServerId, clock, LocalWarmups.NONE);
    }

    public HopDepartures(
            HopGate gate,
            HopConnector connector,
            HopWatchdog watchdog,
            HopCharges charges,
            HopResolvers resolvers,
            Scheduler scheduler,
            String localServerId,
            Clock clock,
            LocalWarmups localWarmups) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.localWarmups = Objects.requireNonNull(localWarmups, "localWarmups");
    }

    /**
     * Whether {@code who} may start {@code departure} under the rules a local teleport obeys. Called before the
     * caller charges, so a refused hop costs nothing; on false the player has already been told why.
     *
     * @throws IllegalStateException when no resolver serves the departure's kind on this backend, a wiring fault
     *     raised here so it surfaces before any money moves rather than at {@link #send}, after the charge
     */
    public boolean admit(PlayerRef who, HopDeparture departure) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(departure, "departure");
        resolverFor(departure.kind());
        return gate.admits(who, departure);
    }

    /**
     * Send {@code who} on {@code departure}. {@code charge} is exactly what the caller debited (empty when nothing
     * was), carried with the hop so a hop that never lands is refunded precisely, and {@code settlement} is where
     * the charge goes once the player lands.
     */
    public void send(
            PlayerRef who, HopDeparture departure, Optional<HopCharge> charge, Optional<HopSettlement> settlement) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(settlement, "settlement");
        HopResolver resolver = resolverFor(departure.kind());
        String target = departure.targetServer();
        if (!connector.isAvailable()) {
            charges.fail(resolver, who, charge, HopNotice.UNAVAILABLE, Map.of("server", target), departure.label());
            return;
        }
        PendingHop pending = new PendingHop(
                who.uuid(),
                departure.kind(),
                departure.target(),
                target,
                localServerId,
                clock.instant(),
                charge,
                settlement);
        // An untracked hop has already been refunded by the watchdog, so the player must not be sent.
        if (!watchdog.track(who, pending, departure.label())) {
            return;
        }
        charges.tell(resolver, who, HopNotice.SENDING, Map.of("server", target), departure.label());
        scheduler.onEntity(who, () -> {
            // The player is already charged and tracked, so a fault ending the old warmup must not keep them here.
            try {
                localWarmups.supersede(who);
            } finally {
                connector.connect(who, target);
            }
        });
    }

    private HopResolver resolverFor(HopKind kind) {
        return resolvers.find(kind).orElseThrow(() -> new IllegalStateException("no hop resolver for " + kind));
    }
}
