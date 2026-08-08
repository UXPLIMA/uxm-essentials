package com.uxplima.uxmessentials.vote.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.Vote;

/**
 * Admin use case: inject {@code amount} synthetic votes for a target player, running the full
 * {@link HandleVote} flow for each one. This re-uses every gate (rewards, party counter, party fire)
 * so the behaviour is identical to real Votifier votes arriving from a service named {@code "admin"}.
 */
public final class GiveVote {

    private final HandleVote handleVote;
    private final Notifier notifier;

    public GiveVote(HandleVote handleVote, Notifier notifier) {
        this.handleVote = Objects.requireNonNull(handleVote, "handleVote");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Run {@link HandleVote#handle} {@code amount} times for {@code target} (all at the current
     * instant, service = {@code "admin"}) and notify {@code actor} on completion.
     *
     * @param actor  the admin giving the votes (receives confirmation)
     * @param target the player who receives the votes
     * @param amount the number of votes to grant (must be at least 1)
     */
    public void give(PlayerRef actor, PlayerRef target, int amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least one: " + amount);
        }
        Instant now = Instant.now();
        for (int i = 0; i < amount; i++) {
            handleVote.handle(new Vote(target, "admin", now));
        }
        notifier.send(
                actor,
                VoteMessageKey.VOTE_ADMIN_GAVE,
                Map.of("player", target.name(), "amount", Integer.toString(amount)));
    }
}
