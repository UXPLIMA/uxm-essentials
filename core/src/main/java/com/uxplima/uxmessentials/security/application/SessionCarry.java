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
 *       A new login is a new connection, so it always proves the factor. A proof made over a proxy connection carries
 *       this way only;
 *   <li>on a network that has never heard from the proxy plugin, a proof made without a proxy connection carries when
 *       the player arrives from the address the proof was made from, and the session was released within the grace,
 *       or the player is still on the backend that holds it as far as this server knows (the switch ordering: the new
 *       backend sees the player before the old one sees them leave).
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

    /** {@code player} proved their factor on {@code server} at {@code now}; see {@link VerifiedSessions#record}. */
    public void proved(UUID player, String server, Optional<UUID> connection, Optional<String> ipHash, Instant now) {
        if (enabled()) {
            sessions.record(player, server, connection, ipHash, now);
        }
    }

    /**
     * Whether {@code player}, arriving at {@code server} as {@code arrival} from the address hashed to {@code ipHash},
     * carries a session; when they do, {@code server} holds it from now on.
     *
     * @param stillOn whether {@code player} is on the named backend as far as this server knows
     */
    public boolean carries(
            UUID player,
            String server,
            Arrival arrival,
            Optional<String> ipHash,
            Instant now,
            Predicate<String> stillOn) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(ipHash, "ipHash");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(stillOn, "stillOn");
        if (!enabled()) {
            return false;
        }
        Optional<Session> found = sessions.find(player);
        if (found.isEmpty() || !matches(found.get(), server, arrival, ipHash, now, stillOn)) {
            return false;
        }
        // Taken only as it was read, so one recorded or taken anew meanwhile is judged again on the next arrival.
        return sessions.take(player, server, found.get(), now);
    }

    /** {@code player} left {@code server}: their session may be taken over for the grace. */
    public void left(UUID player, String server, Instant now) {
        if (enabled()) {
            sessions.release(player, server, now.plus(grace));
        }
    }

    /** {@code server} started at {@code now}: every session it held from before then was left when it stopped. */
    public void started(String server, Instant now) {
        sessions.releaseAll(server, now);
    }

    /** Whether any backend has ever heard from the proxy plugin, so only the proxy may confirm a switch. */
    public boolean proxySeen() {
        return sessions.proxySeen();
    }

    /** This backend heard from the proxy plugin at {@code now}. */
    public void proxyHeard(Instant now) {
        sessions.markProxySeen(now);
    }

    /** Forget {@code player}'s session, so their next arrival anywhere proves the factor again. */
    public void revoke(UUID player) {
        sessions.revoke(player);
    }

    private static boolean matches(
            Session session,
            String server,
            Arrival arrival,
            Optional<String> ipHash,
            Instant now,
            Predicate<String> stillOn) {
        if (session.expiresAt().isPresent() && !now.isBefore(session.expiresAt().get())) {
            return false;
        }
        return switch (arrival) {
            case Arrival.ProxySwitch confirmed ->
                session.connection().isPresent() && session.connection().get().equals(confirmed.connection());
            case Arrival.ProxyLogin login -> false;
            case Arrival.Unconfirmed unconfirmed -> false;
            case Arrival.NoProxy none ->
                // A proof made over a proxy connection carries only on the proxy's word, never on the address.
                session.connection().isEmpty()
                        && ipHash.isPresent()
                        && session.ipHash().equals(ipHash)
                        && (session.expiresAt().isPresent() || stillElsewhere(session.heldBy(), server, stillOn));
        };
    }

    /**
     * Whether the player is still on {@code heldBy}, another backend than the {@code server} they arrive at: the switch
     * ordering. A session this very backend still holds was left without its release landing, so it does not count.
     */
    private static boolean stillElsewhere(String heldBy, String server, Predicate<String> stillOn) {
        return !heldBy.equals(server) && stillOn.test(heldBy);
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

        /** The network has never heard from the proxy plugin: only the address and the grace speak for the arrival. */
        record NoProxy() implements Arrival {}
    }
}
