package com.uxplima.uxmessentials.vote.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import com.uxplima.uxmessentials.vote.application.port.ForwardingVoteRepository;
import com.uxplima.uxmessentials.vote.application.port.RemoteVoter;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VoteMessageKey;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.Test;

/**
 * A vote received on one server for a player on another is paid there at once: the rewards are queued in the shared
 * store, the server the player is on is told, and its drain pays them once, however often it runs.
 */
class RemoteVoteTest {

    private static final Instant AT = Instant.parse("2030-04-01T12:00:00Z");
    private static final RewardSpec DIAMOND = new RewardSpec(
            100, Optional.empty(), List.of("give {player} diamond"), List.of(), List.of(), List.of(), Set.of());

    private final SharedQueue queue = new SharedQueue();
    private final QueueingApplier applier = new QueueingApplier(queue);
    private final List<String> told = new ArrayList<>();
    private final List<MessageKey> broadcasts = new ArrayList<>();
    private final RecordingEvents lobbyEvents = new RecordingEvents();
    private final RecordingEvents survivalEvents = new RecordingEvents();
    private final List<String> paid = new ArrayList<>();

    @Test
    void aVoteForAPlayerOnAnotherServerQueuesTheRewardsTellsThatServerAndThanksThem() {
        HandleVote lobby = lobby(new Where(false, true));

        lobby.handle(new Vote(ALEX, "PlanetMinecraft", AT));

        assertThat(told).containsExactly(ALEX.name() + "@PlanetMinecraft");
        assertThat(queue.rows.get(ALEX)).containsExactly("give {player} diamond");
        assertThat(broadcasts).contains(VoteMessageKey.VOTE_THANKS);
        assertThat(lobbyEvents.published()).noneMatch(VoteReceived.class::isInstance);
    }

    @Test
    void aVoteForAPlayerWhoIsOfflineEverywhereTellsNoServer() {
        HandleVote lobby = lobby(new Where(false, false));

        lobby.handle(new Vote(ALEX, "PlanetMinecraft", AT));

        assertThat(told).isEmpty();
        assertThat(queue.rows.get(ALEX)).containsExactly("give {player} diamond");
        assertThat(broadcasts).doesNotContain(VoteMessageKey.VOTE_THANKS);
    }

    @Test
    void theOtherServerIsToldOnlyOnceEveryRewardOfTheVoteIsQueued() {
        HeldApplier held = new HeldApplier(queue);
        HandleVote lobby = lobby(new Where(false, true), held, DIAMOND, DIAMOND);

        lobby.handle(new Vote(ALEX, "PlanetMinecraft", AT));

        assertThat(told).as("nothing is stored yet").isEmpty();
        held.store();
        assertThat(queue.rows.get(ALEX)).containsExactly("give {player} diamond", "give {player} diamond");
        assertThat(told).containsExactly(ALEX.name() + "@PlanetMinecraft");
    }

    @Test
    void theServerThePlayerIsOnPaysTheQueuedRewardsOnceAndPublishesTheVote() {
        lobby(new Where(false, true)).handle(new Vote(ALEX, "PlanetMinecraft", AT));
        ApplyQueuedRewards drain =
                new ApplyQueuedRewards(queue, (commands, name) -> commands.forEach(c -> paid.add(name + ":" + c)));
        PayRemoteVote survival = new PayRemoteVote(drain, survivalEvents);

        assertThat(survival.pay(ALEX, "PlanetMinecraft")).isEqualTo(1);
        assertThat(drain.applyFor(ALEX)).as("the next join pays nothing").isZero();

        assertThat(paid).containsExactly("Alex:give {player} diamond");
        assertThat(survivalEvents.published()).containsExactly(new VoteReceived(ALEX, "PlanetMinecraft"));
    }

    @Test
    void aVoteWhoseRewardsRunNoCommandIsStillPublishedOnTheServerThePlayerIsOn() {
        RewardSpec thanksOnly = new RewardSpec(
                100, Optional.empty(), List.of(), List.of("<green>thanks"), List.of(), List.of(), Set.of());
        lobby(new Where(false, true), applier, thanksOnly).handle(new Vote(ALEX, "PlanetMinecraft", AT));
        PayRemoteVote survival = new PayRemoteVote(
                new ApplyQueuedRewards(queue, (commands, name) -> commands.forEach(c -> paid.add(name + ":" + c))),
                survivalEvents);

        assertThat(told).containsExactly(ALEX.name() + "@PlanetMinecraft");
        assertThat(survival.pay(ALEX, "PlanetMinecraft")).isZero();
        assertThat(paid).isEmpty();
        assertThat(survivalEvents.published()).containsExactly(new VoteReceived(ALEX, "PlanetMinecraft"));
    }

    private HandleVote lobby(VoteContext where) {
        return lobby(where, applier, DIAMOND);
    }

    private HandleVote lobby(VoteContext where, RewardApplier rewards, RewardSpec... perVote) {
        return new HandleVote(
                queue,
                new RewardEngine(
                        new RewardCatalog(List.of(perVote), Map.of(), List.of(), List.of(), List.of()), Set.of()),
                rewards,
                where,
                List::of,
                new BroadcastSettings(BroadcastType.ONLINE_ONLY, Duration.ZERO, Set.of(), Set.of()),
                new Broadcasts(),
                new NoThrottle(),
                lobbyEvents,
                new PartyConfig(DIAMOND, 1000, false, 0, PartyResetSchedule.NONE, List.of(), false),
                (RemoteVoter) (voter, service) -> told.add(voter.name() + "@" + service),
                0,
                ZoneOffset.UTC);
    }

    /** Where the voter is: online on this server, or online on another one. */
    private record Where(boolean here, boolean elsewhere) implements VoteContext {

        @Override
        public String worldOf(PlayerRef voter) {
            return "";
        }

        @Override
        public boolean hasPermission(PlayerRef voter, String node) {
            return false;
        }

        @Override
        public boolean roll(int chancePercent) {
            return true;
        }

        @Override
        public boolean isOnline(PlayerRef voter) {
            return here;
        }

        @Override
        public boolean isOnlineElsewhere(PlayerRef voter) {
            return elsewhere;
        }
    }

    /** The shared store: the party side in memory, plus the tallies and the reward queue every server reads. */
    private static final class SharedQueue extends ForwardingVoteRepository {

        private final Map<PlayerRef, List<String>> rows = new HashMap<>();
        private final Map<PlayerRef, VoteTally> tallies = new HashMap<>();

        SharedQueue() {
            super(new PartyVotes());
        }

        @Override
        public void enqueue(QueuedReward reward) {
            rows.computeIfAbsent(reward.player(), id -> new ArrayList<>()).addAll(reward.commands());
        }

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            List<String> taken = rows.remove(player);
            return taken == null ? List.of() : List.of(new QueuedReward(player, taken, AT));
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return rows.containsKey(player);
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return tallies.getOrDefault(player, VoteTally.empty());
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {
            tallies.put(player, tally);
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }

    /** Queues the commands of a grant for a voter who is not here, as the server's applier does. */
    private record QueueingApplier(SharedQueue queue) implements RewardApplier {

        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {
            if (!online && !grant.commands().isEmpty()) {
                queue.enqueue(new QueuedReward(voter, grant.commands(), AT));
            }
        }
    }

    /** Queues the grants of a voter who is not here on a later lane, as the server's applier does, until told to. */
    private static final class HeldApplier implements RewardApplier {

        private final SharedQueue queue;
        private final List<Runnable> waiting = new ArrayList<>();

        HeldApplier(SharedQueue queue) {
            this.queue = queue;
        }

        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {
            if (!online) {
                waiting.add(() -> queue.enqueue(new QueuedReward(voter, grant.commands(), AT)));
            }
        }

        @Override
        public void queueAll(PlayerRef voter, List<RewardGrant> grants, Runnable afterQueued) {
            waiting.add(() -> {
                grants.forEach(grant -> queue.enqueue(new QueuedReward(voter, grant.commands(), AT)));
                afterQueued.run();
            });
        }

        void store() {
            waiting.forEach(Runnable::run);
            waiting.clear();
        }
    }

    private final class Broadcasts implements VoteBroadcaster {

        @Override
        public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {
            broadcasts.add(key);
        }
    }

    private static final class NoThrottle implements BroadcastThrottle {

        @Override
        public Optional<Instant> lastBroadcastAt(PlayerRef voter) {
            return Optional.empty();
        }

        @Override
        public void recordBroadcast(PlayerRef voter, Instant at) {}
    }
}
