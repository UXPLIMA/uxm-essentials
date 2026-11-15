package com.uxplima.uxmessentials.shared.adapter.outbound.hop;

import java.time.Clock;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.hop.HopArrivalListener;
import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * The cross-server hop every teleport kind shares on this backend: one store, one resolver registry, one watchdog
 * and one arrival, built by the bootstrap before the modules wire. A context whose own {@code cross-server} switch
 * is on {@link #register registers} its resolver and takes {@link #departures} over its teleport gate. One whose
 * switch is off registers nothing, so a row of its kind that lands here is left for the origin's watchdog, which
 * can refund it, and a new hop that would overwrite such a row is refused until the row is abandoned.
 */
public final class CrossServerHops {

    private final HopResolvers resolvers;
    private final HopCharges charges;
    private final HopWatchdog watchdog;
    private final HopArrival arrival;
    private final HopConnector connector;
    private final Scheduler scheduler;
    private final String server;
    private final Clock clock;

    /** {@code server} is this backend's network name, stamped as the origin of every hop it sends. */
    public CrossServerHops(
            HopResolvers resolvers,
            HopCharges charges,
            HopWatchdog watchdog,
            HopArrival arrival,
            HopConnector connector,
            Scheduler scheduler,
            String server,
            Clock clock) {
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog");
        this.arrival = Objects.requireNonNull(arrival, "arrival");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Register {@code resolver} so its kind's hops can be landed and refunded on this backend. */
    public void register(HopResolver resolver) {
        resolvers.register(resolver);
    }

    /** Where every kind's hop money ends up, and how its notices are sent. */
    public HopCharges charges() {
        return charges;
    }

    /** This backend's network name. */
    public String server() {
        return server;
    }

    /** The send half for a context whose hops pass {@code gate} before they are charged. */
    public HopDepartures departures(HopGate gate) {
        Objects.requireNonNull(gate, "gate");
        return new HopDepartures(gate, connector, watchdog, charges, resolvers, scheduler, server, clock);
    }

    /** The join listener that ends a hop on this backend. */
    public Listener arrivalListener() {
        return new HopArrivalListener(arrival, resolvers);
    }
}
