package com.uxplima.uxmessentials.economy.application.port;

import java.util.UUID;

/**
 * When each player last took the daily login reward, in the database every server on a network shares. It exists
 * because the PDC stamp lives in one server's world files, so each backend of a network would pay the reward again.
 *
 * <p>Every method does I/O and runs off the tick. {@link #claimIfDue} is a compare-and-set in the database, so of
 * two servers that log the same player in at once, exactly one is told to pay.
 */
public interface DailyRewardLedger {

    /**
     * Stamp {@code player}'s reward as taken at {@code now} if their last one was taken at least {@code windowMillis}
     * ago, or never. True only for the call whose stamp landed.
     */
    boolean claimIfDue(UUID player, long now, long windowMillis);

    /** Store {@code claimedAt} as {@code player}'s last reward unless a later one is stored already. */
    void keepLatest(UUID player, long claimedAt);
}
