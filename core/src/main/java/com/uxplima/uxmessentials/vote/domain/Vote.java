package com.uxplima.uxmessentials.vote.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One received vote: the voter, the vote service it came from, and when it arrived. The adapter builds
 * this from the upstream Votifier event (its service name and username) and resolves the voter to a
 * {@link PlayerRef}; the {@code HandleVote} use case then decides whether to reward immediately (voter
 * online) or to queue the reward for the voter's next join (voter offline).
 *
 * @param voter the player credited with the vote (online or offline)
 * @param serviceName the vote list the vote came from, for the thank-you broadcast
 * @param at when the vote was received
 */
public record Vote(PlayerRef voter, String serviceName, Instant at) {

    public Vote {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(at, "at");
    }
}
