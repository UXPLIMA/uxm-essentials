package com.uxplima.uxmessentials.vote.application;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VotePartyCounter;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;

/**
 * The core vote use case. On a received {@link Vote} it records the voter's tally first, then runs the
 * configured reward sets through the {@link RewardEngine}: the per-vote, per-site, first-vote, and
 * milestone specs whose chance/permission/world gates pass become {@link RewardGrant}s, each applied via
 * the {@link RewardApplier}. An online voter has the grants applied at once (commands run, messages and
 * items delivered) and is thanked, with {@link VoteReceived} published; an offline voter has the grants
 * queued for their next join (the applier queues the commands) and is not thanked. Then the global party
 * counter advances — when it reaches the configured threshold a party fires (the party reward runs for
 * every online player, {@link VotePartyTriggered} is published, and the counter resets to zero),
 * otherwise the incremented count is persisted.
 *
 * <p>The {@link Outcome} it returns names what happened (rewarded vs queued, and whether a party fired)
 * so the test paths and the {@code /vote testreward} confirmation can assert on it without reading
 * adapter side effects.
 */
public final class HandleVote {

    private final VoteRepository repository;
    private final RewardEngine engine;
    private final RewardApplier applier;
    private final VoteContext context;
    private final VoteAudience audience;
    private final VoteNotifier notifier;
    private final DomainEventPublisher events;
    private final PartyReward party;
    private final ZoneId zone;

    public HandleVote(
            VoteRepository repository,
            RewardEngine engine,
            RewardApplier applier,
            VoteContext context,
            VoteAudience audience,
            VoteNotifier notifier,
            DomainEventPublisher events,
            PartyReward party,
            ZoneId zone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.context = Objects.requireNonNull(context, "context");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.party = Objects.requireNonNull(party, "party");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Apply {@code vote}: record the tally, resolve and apply the reward sets, advance the counter, fire a party when due. */
    public Outcome handle(Vote vote) {
        Objects.requireNonNull(vote, "vote");
        VoteTally after = repository.totalsOf(vote.voter()).recordVote(vote.at(), zone);
        repository.saveTotals(vote.voter(), after);
        boolean rewarded = creditVoter(vote, after);
        boolean partyFired = advanceCounter();
        return new Outcome(rewarded, partyFired);
    }

    private boolean creditVoter(Vote vote, VoteTally after) {
        PlayerRef voter = vote.voter();
        boolean online = context.isOnline(voter);
        RewardContext ctx = new RewardContext(
                voter,
                online,
                online ? context.worldOf(voter) : "",
                vote.serviceName(),
                after,
                node -> context.hasPermission(voter, node),
                context::roll);
        for (RewardGrant grant : engine.resolve(ctx)) {
            applier.apply(voter, online, grant);
        }
        if (online) {
            broadcastThanks(voter.name());
            events.publish(new VoteReceived(voter, "vote"));
        }
        return online;
    }

    private boolean advanceCounter() {
        // The increment is atomic in the repository, so exactly one concurrent vote observes the count that
        // reaches the threshold — that single thread fires the party and resets the counter, the rest do not.
        VotePartyCounter counter = new VotePartyCounter(repository.incrementAndGetPartyCount(), party.threshold());
        if (!counter.isReached()) {
            return false;
        }
        fireParty(counter.threshold());
        repository.setPartyCount(counter.reset().count());
        return true;
    }

    private void fireParty(int threshold) {
        RewardGrant partyGrant = RewardGrant.of(party.spec());
        for (PlayerRef online : audience.online()) {
            applier.apply(online, true, partyGrant);
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

    /**
     * The vote-party reward content: the commands every online player runs when a party fires, and the
     * threshold that triggers it. The commands are wrapped into a one-spec {@link RewardSpec} (always-grant,
     * no permission/world gate) so the same {@link RewardApplier} pays both per-vote and party rewards. The
     * richer per-vote engine catalog is constructed and injected separately.
     *
     * @param commands the party console commands, in order, carrying {@code {player}}
     * @param threshold the accumulated vote count at which a party fires
     */
    public record PartyReward(List<String> commands, int threshold) {

        public PartyReward {
            commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            if (threshold < 1) {
                throw new IllegalArgumentException("threshold must be at least one: " + threshold);
            }
        }

        /** The party commands as an always-grant {@link RewardSpec}. */
        RewardSpec spec() {
            return new RewardSpec(100, Optional.empty(), commands, List.of(), List.of(), List.of(), Set.of());
        }
    }

    /** What {@link #handle} did: whether the voter was rewarded now (vs queued) and whether a party fired. */
    public record Outcome(boolean rewardedNow, boolean partyTriggered) {}
}
