package com.uxplima.uxmessentials.persistence.network;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the persistence-adapter's cross-server bus transports, so the consuming bukkit-adapter wires a
 * {@link BusTransport} from configuration without ever naming a Jedis type — Jedis is a {@code compileOnly}
 * dependency of this module, kept off the consumer's compile classpath, exactly as {@code WalletRepositories}
 * does for the jOOQ types. The wiring (task B4) selects this transport when {@code network.transport} names
 * Redis and hands the returned SPI to its {@code BusCore}.
 */
@NullMarked
public final class NetworkTransports {

    private NetworkTransports() {}

    /**
     * Build the Redis pub/sub transport that carries the full bus frame bytes over a Redis channel with no
     * proxy. {@code password} is empty to skip auth; {@code db} is the logical database index every borrowed
     * connection selects (0 leaves the default database untouched); {@code channel} is the Redis pub/sub channel
     * both sides publish to and subscribe on. The subscribe loop runs on {@code scheduler}'s async executor; if
     * Jedis is absent at runtime the transport disables itself with a single WARN and becomes a no-op.
     */
    public static BusTransport redis(
            String host, int port, String password, int db, String channel, Scheduler scheduler, Logger log) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        return new RedisBusTransport(host, port, password, db, channel, scheduler, log);
    }
}
