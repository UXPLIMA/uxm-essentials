package com.uxplima.uxmessentials.security.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The shared record of second-factor proofs a player may carry to another backend of the network, one per account.
 * Every backend on one database reads and writes the same record.
 *
 * <p>Every method does I/O and runs off the tick.
 */
public interface VerifiedSessions {

    /** {@code player}'s session, or empty when they hold none. */
    Optional<Session> find(UUID player);

    /**
     * {@code player} proved their factor on {@code server}, over the proxy connection {@code connection} (empty without
     * the proxy plugin) from the address hashed to {@code ipHash} (empty when it is unknown). Replaces any earlier
     * session; the new one is held by {@code server} and does not expire while they stay there.
     */
    void record(UUID player, String server, Optional<UUID> connection, Optional<String> ipHash);

    /**
     * {@code player} carried their session to {@code server}: it is held there now and does not expire while they stay.
     * Only a session that has not expired at {@code now} is taken.
     *
     * @return whether the session was taken
     */
    boolean take(UUID player, String server, Instant now);

    /**
     * {@code player} left {@code server}: their session may be taken over until {@code expiresAt}. Only a session
     * {@code server} still holds changes, so a backend that already took it over keeps it.
     */
    void release(UUID player, String server, Instant expiresAt);

    /** Every session {@code server} holds is released at {@code at}: nobody is on a backend that just started. */
    void releaseAll(String server, Instant at);

    /** Forget {@code player}'s session, so their next arrival anywhere proves the factor again. */
    void revoke(UUID player);

    /**
     * One player's session as stored.
     *
     * @param heldBy the backend the player is on, or left last
     * @param connection the proxy connection the proof was made on, empty without the proxy plugin
     * @param ipHash the hashed address the proof was made from, empty when it was unknown
     * @param expiresAt when the session may no longer be taken over, empty while the player is on {@code heldBy}
     */
    record Session(String heldBy, Optional<UUID> connection, Optional<String> ipHash, Optional<Instant> expiresAt) {

        public Session {
            Objects.requireNonNull(heldBy, "heldBy");
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(ipHash, "ipHash");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
