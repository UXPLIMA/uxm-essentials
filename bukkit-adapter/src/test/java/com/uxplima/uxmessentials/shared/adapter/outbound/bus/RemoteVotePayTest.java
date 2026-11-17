package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VoteRewardsQueued;
import org.junit.jupiter.api.Test;

/**
 * A vote received on another server is paid on the server the voter is on, once per vote however many copies of the
 * announcement arrive, even when the proxy's copy arrives after this server restarted, and only while the voter is
 * still there when the pay runs.
 */
class RemoteVotePayTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");

    /** When this server started listening, after a restart. */
    private static final Instant STARTED = Instant.parse("2026-11-17T12:00:00Z");

    private final List<String> paid = new ArrayList<>();
    private final Roster roster = new Roster();
    private final Network network = new Network();
    private final HeldLane lane = new HeldLane();
    private final RemoteSyncListener listener = listenerSince(Instant.MIN);

    private RemoteSyncListener listenerSince(Instant started) {
        return VoteSync.queuedListener(
                (voter, service) -> paid.add(voter.name() + "@" + service), roster, network, lane, started);
    }

    /** A vote sent on both carriers at {@code sentAt}. */
    private static VoteRewardsQueued bothCarriersAt(Instant sentAt) {
        return new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft", sentAt, true);
    }

    @Test
    void theProxyCopyOfAVoteSentBeforeThisServerRestartedIsNotAnnouncedAgain() {
        roster.here.add(ALEX.uuid());
        RemoteSyncListener restarted = listenerSince(STARTED);

        restarted.onRemoteChange(bothCarriersAt(STARTED.minus(Duration.ofHours(2))));
        lane.runAll();

        assertThat(paid).isEmpty();
    }

    @Test
    void aVoteSentWhileThisServerWasStillSubscribingIsPaid() {
        roster.here.add(ALEX.uuid());
        RemoteSyncListener restarted = listenerSince(STARTED);

        restarted.onRemoteChange(bothCarriersAt(STARTED.minus(Duration.ofSeconds(20))));
        lane.runAll();

        assertThat(paid).containsExactly("Alex@PlanetMinecraft");
    }

    @Test
    void aVoteFromAServerThatSendsNoTimeIsPaid() {
        roster.here.add(ALEX.uuid());
        RemoteSyncListener restarted = listenerSince(STARTED);

        restarted.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft"));
        lane.runAll();

        assertThat(paid).containsExactly("Alex@PlanetMinecraft");
    }

    @Test
    void aVoterStillConnectedToTheServerTheyAreLeavingIsPaidOnlyWhereTheNetworkPlacesThem() {
        roster.here.add(ALEX.uuid());
        network.place(ALEX, "survival");

        listener.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft"));
        lane.runAll();

        assertThat(paid).isEmpty();
    }

    @Test
    void aVoterTheNetworkPlacesHereIsPaid() {
        roster.here.add(ALEX.uuid());
        network.place(ALEX, Network.HERE);

        listener.onRemoteChange(new VoteRewardsQueued("lobby", UUID.randomUUID(), ALEX.uuid(), "PlanetMinecraft"));
        lane.runAll();

        assertThat(paid).containsExactly("Alex@PlanetMinecraft");
    }

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

        Clock clock = Clock.fixed(STARTED, ZoneOffset.UTC);
        VoteSync.remoteVoter(bus, new Quiet(), clock, true).rewardsQueued(ALEX, "PlanetMinecraft");
        VoteSync.remoteVoter(bus, new Quiet(), clock, true).rewardsQueued(ALEX, "PlanetMinecraft");

        assertThat(sent)
                .extracting(message -> ((VoteRewardsQueued) message).voteId())
                .doesNotHaveDuplicates()
                .hasSize(2);
        assertThat(sent)
                .extracting(message -> ((VoteRewardsQueued) message).sentAt())
                .containsOnly(STARTED);
    }

    /** Where the network places each player; this server is {@link #HERE}, and a player it never heard of is nowhere. */
    private static final class Network implements NetworkPresence {

        static final String HERE = "lobby-2";

        private final Map<UUID, OnlinePlayer> placed = new HashMap<>();

        void place(PlayerRef player, String server) {
            placed.put(player.uuid(), new OnlinePlayer(player, server));
        }

        @Override
        public String localServer() {
            return HERE;
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.ofNullable(placed.get(player));
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return placed.values().stream()
                    .filter(online -> online.player().name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return placed.values().stream()
                    .filter(online -> online.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.copyOf(placed.values());
        }
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
