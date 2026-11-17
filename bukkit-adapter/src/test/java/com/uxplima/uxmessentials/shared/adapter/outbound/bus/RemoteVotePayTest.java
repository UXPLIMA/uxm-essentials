package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VoteRewardsQueued;
import org.junit.jupiter.api.Test;

/**
 * A vote received on another server is paid on the server the voter is on, once per vote however many copies of the
 * announcement arrive, and only while the voter is still there when the pay runs.
 */
class RemoteVotePayTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");

    private final List<String> paid = new ArrayList<>();
    private final Roster roster = new Roster();
    private final HeldLane lane = new HeldLane();
    private final RemoteSyncListener listener =
            VoteSync.queuedListener((voter, service) -> paid.add(voter.name() + "@" + service), roster, lane);

    @Test
    void anAnnouncedVoteIsPaidOnceThoughBothCarriersDeliverIt() {
        roster.here.add(ALEX.uuid());
        VoteRewardsQueued vote = new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft");

        listener.onRemoteChange(vote);
        listener.onRemoteChange(vote);
        lane.runAll();

        assertThat(paid).containsExactly("Alex@PlanetMinecraft");
    }

    @Test
    void twoVotesAreEachPaid() {
        roster.here.add(ALEX.uuid());

        listener.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft"));
        listener.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "MinecraftServers"));
        lane.runAll();

        assertThat(paid).containsExactly("Alex@PlanetMinecraft", "Alex@MinecraftServers");
    }

    @Test
    void aVoterWhoLeftBeforeThePayRunsKeepsTheQueueForTheirNextJoin() {
        roster.here.add(ALEX.uuid());
        listener.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft"));

        roster.here.remove(ALEX.uuid());
        lane.runAll();

        assertThat(paid).isEmpty();
    }

    @Test
    void aVoterWhoIsNotHereIsNotPaidHere() {
        listener.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft"));
        lane.runAll();

        assertThat(paid).isEmpty();
    }

    @Test
    void theAnnouncementCarriesANewVoteIdEachTime() {
        List<NetworkMessage> sent = new ArrayList<>();
        BusPublisher bus = new BusPublisher() {
            @Override
            public void publish(NetworkMessage message) {
                sent.add(message);
            }

            @Override
            public String serverId() {
                return "lobby";
            }
        };

        VoteSync.remoteVoter(bus, new Quiet()).rewardsQueued(ALEX, "PlanetMinecraft");
        VoteSync.remoteVoter(bus, new Quiet()).rewardsQueued(ALEX, "PlanetMinecraft");

        assertThat(sent)
                .extracting(message -> ((VoteRewardsQueued) message).voteId())
                .doesNotHaveDuplicates()
                .hasSize(2);
    }

    /** The players connected to this server, changed by the test between the frame and the pay. */
    private static final class Roster implements PlayerLookup {

        private final Set<UUID> here = new HashSet<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return here.contains(ALEX.uuid()) && name.equals(ALEX.name()) ? Optional.of(ALEX) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return uuid.equals(ALEX.uuid()) ? Optional.of(ALEX) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return here.contains(uuid);
        }
    }

    /** An async lane the test runs when it chooses, so a player can leave between the frame and the pay. */
    private static final class HeldLane implements Scheduler {

        private final List<Runnable> waiting = new ArrayList<>();

        void runAll() {
            List<Runnable> now = List.copyOf(waiting);
            waiting.clear();
            now.forEach(Runnable::run);
        }

        @Override
        public void async(Runnable task) {
            waiting.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("a remote vote is paid at once");
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }
    }

    private static final class Quiet implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
