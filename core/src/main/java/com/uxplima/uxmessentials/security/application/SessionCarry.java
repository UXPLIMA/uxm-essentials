package com.uxplima.uxmessentials.security.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions.Session;

/**
 * Lets a player who proved their second factor on one backend move to another backend of the network without
 * proving it again, while every new login still proves it.
 *
 * <p>A proof is recorded in the shared {@link VerifiedSessions} with the backend it was made on, and, with the proxy
 * plugin, the proxy connection it was made over. When the player leaves a backend the session may be taken over for
 * the switch grace; while they stay it does not expire. A backend a player arrives at carries the session only when:
 *
 * <ul>
 *   <li>with the proxy plugin, the proxy confirms the arrival is a switch on the connection the proof was made over.
 *       A new login is a new connection, so it always proves the factor;
 *   <li>without the proxy plugin, the player arrives from the address the proof was made from, and the session was
 *       released within the grace, or is still held by a backend that is running (the switch ordering: the new backend
 *       sees the player before the old one sees them leave).
 * </ul>
 *
 * <p>A grace of zero turns carrying off. Every method does I/O and runs off the tick.
 */
public final class SessionCarry {

    private final VerifiedSessions sessions;
    private final Duration grace;

    public SessionCarry(VerifiedSessions sessions, Duration grace) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.grace = Objects.requireNonNull(grace, "grace");
        if (grace.isNegative()) {
            throw new IllegalArgumentException("grace must not be negative: " + grace);
        }
    }

    /** Whether carrying is on. */
    public boolean enabled() {
        return !grace.isZero();
    }

    /** Whether carrying is on and {@code player} holds a session that might carry. */
    public boolean holds(UUID player) {
        return enabled()
                && sessions.find(Objects.requireNonNull(player, "player")).isPresent();
    }

    /** {@code player} proved their factor on {@code server}; see {@link VerifiedSessions#record}. */
    public void proved(UUID player, String server, Optional<UUID> connection, Optional<String> ipHash) {
        if (enabled()) {
            sessions.record(player, server, connection, ipHash);
        }
    }

    /**
     * Whether {@code player}, arriving at {@code server} as {@code arrival} from the address hashed to {@code ipHash},
     * carries a session; when they do, {@code server} holds it from now on.
     *
     * @param runningServer whether a backend is running as far as this server knows
     */
    public boolean carries(
            UUID player,
            String server,
            Arrival arrival,
            Optional<String> ipHash,
            Instant now,
            Predicate<String> runningServer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(ipHash, "ipHash");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(runningServer, "runningServer");
        if (!enabled()) {
            return false;
        }
        Optional<Session> found = sessions.find(player);
        if (found.isEmpty() || !matches(found.get(), arrival, ipHash, now, runningServer)) {
            return false;
        }
        return sessions.take(player, server, now);
    }

    /** {@code player} left {@code server}: their session may be taken over for the grace. */
    public void left(UUID player, String server, Instant now) {
        if (enabled()) {
            sessions.release(player, server, now.plus(grace));
        }
    }

    /** {@code server} started: every session it held was left when it stopped. */
    public void started(String server, Instant now) {
        sessions.releaseAll(server, now);
    }

    /** Forget {@code player}'s session, so their next arrival anywhere proves the factor again. */
    public void revoke(UUID player) {
        sessions.revoke(player);
    }

    private static boolean matches(
            Session session, Arrival arrival, Optional<String> ipHash, Instant now, Predicate<String> runningServer) {
        if (session.expiresAt().isPresent() && !now.isBefore(session.expiresAt().get())) {
            return false;
        }
        return switch (arrival) {
            case Arrival.ProxySwitch confirmed ->
                session.connection().isPresent() && session.connection().get().equals(confirmed.connection());
            case Arrival.ProxyLogin login -> false;
            case Arrival.Unconfirmed unconfirmed -> false;
            case Arrival.NoProxy none ->
                ipHash.isPresent()
                        && session.ipHash().equals(ipHash)
                        && (session.expiresAt().isPresent() || runningServer.test(session.heldBy()));
        };
    }

    /** What is known about how a player arrived at a backend. */
    public sealed interface Arrival {

        /** The proxy plugin confirmed a switch from another backend on {@code connection}. */
        record ProxySwitch(UUID connection) implements Arrival {

            public ProxySwitch {
                Objects.requireNonNull(connection, "connection");
            }
        }

        /** The proxy plugin confirmed a new login. */
        record ProxyLogin() implements Arrival {}

        /** The proxy plugin is there but said nothing about this arrival in time. */
        record Unconfirmed() implements Arrival {}

        /** No proxy plugin runs: only the address and the grace speak for the arrival. */
        record NoProxy() implements Arrival {}
    }
}
