package com.uxplima.uxmessentials.vote.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmVoteActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiActors;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import org.jspecify.annotations.NullMarked;

/**
 * The published vote actions, over the same use cases {@code /vote give} and {@code /voteparty add} run.
 *
 * <p>Both run on a worker, which is where the commands run them too: crediting a vote touches the totals table,
 * and the reward and broadcast work each hop to the thread it needs on its own way out.
 *
 * <p>There is no online check. A vote is a fact about an account rather than about a session, so a vote credited
 * to somebody who is away is stored and paid out when they come back, exactly as a real one from a listing site
 * would be.
 */
@NullMarked
public final class VoteActions implements UxmVoteActions {

    private final GiveVote give;
    private final AddPartyCount party;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final String source;

    public VoteActions(VoteApiWrites writes, PlayerLookup players, Scheduler scheduler, String source) {
        Objects.requireNonNull(writes, "writes");
        this.give = writes.give();
        this.party = writes.party();
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public CompletableFuture<UxmOutcome> giveVote(UUID playerId) {
        return giveVote(playerId, 1);
    }

    @Override
    public CompletableFuture<UxmOutcome> giveVote(UUID playerId, int amount) {
        Objects.requireNonNull(playerId, "playerId");
        atLeastOne(amount, "amount");
        PlayerRef target = ApiValues.subject(players, playerId);
        PlayerRef actor = ApiActors.of(source);
        return AsyncActions.perform(scheduler, () -> {
            give.give(actor, target, amount);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> addPartyProgress(int votes) {
        atLeastOne(votes, "votes");
        PlayerRef actor = ApiActors.of(source);
        return AsyncActions.perform(scheduler, () -> {
            party.add(actor, votes);
            return UxmOutcome.ok();
        });
    }

    /** A count of zero or less is a bug in the caller rather than an answer, so it throws where they stand. */
    private static void atLeastOne(int amount, String what) {
        if (amount < 1) {
            throw new IllegalArgumentException(what + " must be at least one: " + amount);
        }
    }
}
