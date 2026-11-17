package com.uxplima.uxmessentials.security.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The shared record of wrong second-factor proofs and lockouts, one per account. Every backend on one database reads
 * and writes the same record, so the attempt limit holds across the network rather than per server.
 *
 * <p>Every method does I/O and runs off the tick.
 */
public interface AttemptStore {

    /** When {@code player}'s lockout ends, or empty when none is set (an expired one included). */
    Optional<Instant> lockedUntil(UUID player);

    /**
     * Add one wrong proof to {@code player}'s count and return the new count. Two backends adding at once each see the
     * other's failure: the counts they return are distinct.
     */
    int addFailure(UUID player);

    /** Lock {@code player} out until {@code until} and start their count again from zero. */
    void lock(UUID player, Instant until);

    /** Start {@code player}'s count again from zero, leaving any lockout as it is. */
    void clearFailures(UUID player);

    /**
     * Forget {@code player}'s lockout and count, only when their lockout ended at or before {@code now}: one another
     * backend set meanwhile stays.
     */
    void clearExpired(UUID player, Instant now);

    /**
     * Forget {@code player}'s count and lockout, whether the lockout is still running or not.
     *
     * @return whether a lockout still running at {@code now} was lifted
     */
    boolean clear(UUID player, Instant now);
}
