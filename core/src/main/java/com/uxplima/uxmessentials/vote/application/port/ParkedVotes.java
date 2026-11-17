package com.uxplima.uxmessentials.vote.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.vote.domain.ParkedVote;

/**
 * The votes for names no account answers to yet, kept in the shared database by the lower-cased name until a player
 * with that name first joins any server of it.
 */
public interface ParkedVotes {

    /** Keep {@code vote} until a player it names joins. */
    void park(ParkedVote vote);

    /**
     * Take the votes a player answering to {@code names} may claim, oldest first, removing them: every vote whose name
     * is one of {@code names} ignoring case, except a vote that needs its exact spelling, which only an exact match
     * claims. A vote is handed to one taker only, so two servers taking at once never credit it twice.
     */
    List<ParkedVote> take(Set<String> names);

    /** Forget the votes parked before {@code cutoff}, which nobody claimed, and return how many there were. */
    int forgetOlderThan(Instant cutoff);
}
