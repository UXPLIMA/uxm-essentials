package com.uxplima.uxmessentials.vote.application;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VotePartyCounter;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;

/**
 * The core vote use case. On a received {@link Vote}: an online voter is rewarded at once (the
 * configured per-vote reward commands run, every online player is thanked, and {@link VoteReceived} is
 * published); an offline voter's reward is enqueued for their next join and nothing is dispatched. Then
 * the global party counter advances — when it reaches the configured threshold a party fires (the party
 * reward commands run for every online player, {@link VotePartyTriggered} is published, and the counter
 * resets to zero), otherwise the incremented count is persisted.
 *
 * <p>The {@link Outcome} it returns names what happened (rewarded vs queued, and whether a party fired)
 * so the test paths and the {@code /vote testreward} confirmation can assert on it without reading
 * adapter side effects.
 */
public final class HandleVote {

    private final VoteRepository repository;
    private final RewardDispatcher dispatcher;
    private final VoteAudience audience;
    private final VoteNotifier notifier;
    private final DomainEventPublisher events;
    private final VoteRewards rewards;
    private final PlayerLookup players;
    private final Clock clock;
    private final ZoneId zone;

    public HandleVote(
            VoteRepository repository,
            RewardDispatcher dispatcher,
            VoteAudience audience,
            VoteNotifier notifier,
            DomainEventPublisher events,
            VoteRewards rewards,
            PlayerLookup players,
            Clock clock,
            ZoneId zone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.players = Objects.requireNonNull(players, "players");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Apply {@code vote}: record the tally, reward or queue the voter, advance the counter, fire a party when due. */
    public Outcome handle(Vote vote) {
        Objects.requireNonNull(vote, "vote");
        recordTotals(vote.voter(), vote.at());
        boolean rewarded = creditVoter(vote.voter());
        boolean party = advanceCounter();
        return new Outcome(rewarded, party);
    }

    private void recordTotals(PlayerRef voter, java.time.Instant at) {
        VoteTally current = repository.totalsOf(voter);
        repository.saveTotals(voter, current.recordVote(at, zone));
    }

    private boolean creditVoter(PlayerRef voter) {
        if (players.isOnline(voter.uuid())) {
            dispatcher.dispatch(rewards.perVote(), voter.name());
            broadcastThanks(voter.name());
            events.publish(new VoteReceived(voter, "vote"));
            return true;
        }
        repository.enqueue(new QueuedReward(voter, rewards.perVote(), clock.instant()));
        return false;
    }

    private boolean advanceCounter() {
        // The increment is atomic in the repository, so exactly one concurrent vote observes the count that
        // reaches the threshold — that single thread fires the party and resets the counter, the rest do not.
        VotePartyCounter counter =
                new VotePartyCounter(repository.incrementAndGetPartyCount(), rewards.partyThreshold());
        if (!counter.isReached()) {
            return false;
        }
        fireParty(counter.threshold());
        repository.setPartyCount(counter.reset().count());
        return true;
    }

    private void fireParty(int threshold) {
        for (PlayerRef online : audience.online()) {
            dispatcher.dispatch(rewards.party(), online.name());
            notifier.send(online, VoteMessageKey.VOTEPARTY_REACHED, Map.of("threshold", Integer.toString(threshold)));
        }
        events.publish(new VotePartyTriggered(threshold));
    }

    private void broadcastThanks(String voterName) {
        Map<String, String> placeholders = Map.of("player", voterName);
        for (PlayerRef online : audience.online()) {
            notifier.send(online, VoteMessageKey.VOTE_THANKS, placeholders);
        }
    }

    /** The configured reward content: the per-vote commands, the party commands, and the threshold. */
    public record VoteRewards(List<String> perVote, List<String> party, int partyThreshold) {

        public VoteRewards {
            perVote = List.copyOf(Objects.requireNonNull(perVote, "perVote"));
            party = List.copyOf(Objects.requireNonNull(party, "party"));
            if (partyThreshold < 1) {
                throw new IllegalArgumentException("partyThreshold must be at least one: " + partyThreshold);
            }
        }
    }

    /** What {@link #handle} did: whether the voter was rewarded now (vs queued) and whether a party fired. */
    public record Outcome(boolean rewardedNow, boolean partyTriggered) {}
}
