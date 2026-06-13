package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;

/**
 * Read seam the expansion queries for the {@code votes_*} and {@code voteparty_*} placeholders. It is
 * an adapter over the vote context's existing read ports ({@code VoteRepository.totalsOf} and
 * {@code VotePartyStatus} counter) wired during bootstrap; when the vote module is disabled the seam
 * is absent and the placeholders degrade to the empty/"-" default.
 *
 * <p>The tally reads hit the cached repository so a PAPI request for an online player should see
 * a warm cache (populated by the vote handler). For a cold read the repository falls back to a DB
 * query — acceptable for a placeholder; the value is still correct.
 */
public interface VotePlaceholders {

    /** How many votes {@code who} has accumulated for the given {@code period}. */
    long countFor(PlayerRef who, VotePeriod period);

    /** The current accumulated vote-party counter. */
    int partyCount();

    /** The configured party threshold (votes needed to fire the party). */
    int partyThreshold();
}
