package com.uxplima.uxmessentials.vanish.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * A vanish follows its player across backends and restarts, and the network list only shows players some running
 * backend is hiding. Each backend is the real use cases over fakes; they share one database and one bus.
 */
class VanishAcrossBackendsTest {

    private static final Duration GRACE = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(NOW);
    private final SharedRecords database = new SharedRecords();
    private final Network network = new Network();

    @Test
    void aVanishedPlayerWhoMovesToAnotherBackendArrivesHidden() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        survival.quit(ALEX);
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isTrue();
        assertThat(lobby.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aVanishedPlayerWhoMovesToABackendStartedAfterTheirVanishArrivesHidden() {
        Backend survival = network.start("survival");
        survival.join(ALEX);
        survival.toggle(ALEX);
        Backend fresh = network.start("fresh");

        survival.quit(ALEX);
        fresh.join(ALEX);

        assertThat(fresh.isHidden(ALEX)).isTrue();
    }

    @Test
    void aPlayerWhoReappearedArrivesVisibleOnTheNextBackend() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);
        survival.toggle(ALEX);

        survival.quit(ALEX);
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aVanishedPlayerWhoQuitsLeavesEveryNetworkList() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);
        assertThat(lobby.listFor(SAM)).containsExactly(ALEX.uuid());

        survival.quit(ALEX);

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void theOriginsDepartureArrivingAfterTheDestinationReportedThePlayerKeepsThemListed() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        Backend creative = network.start("creative");
        survival.join(ALEX);
        survival.toggle(ALEX);

        // The proxy connects the player to the destination before it lets go of the origin.
        lobby.join(ALEX);
        survival.quit(ALEX);

        assertThat(creative.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void theEntriesOfABackendThatStopsSendingHeartbeatsExpire() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        clock.advance(GRACE.plusSeconds(1));

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aRestartedBackendHearsOfThePlayersItsPeersAreHiding() {
        Backend survival = network.start("survival");
        network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("lobby");
        Backend restarted = network.start("lobby");

        assertThat(restarted.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aBackendThatRestartsWithinTheHeartbeatWindowTakesItsOldEntriesWithIt() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        network.start("survival");

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aVanishedPlayerWhoRejoinsTheirRestartedBackendIsStillHidden() {
        Backend survival = network.start("survival");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        Backend restarted = network.start("survival");
        restarted.join(ALEX);

        assertThat(restarted.isHidden(ALEX)).isTrue();
    }

    @Test
    void aKeptVanishReadAfterTheJoinStillHidesThePlayer() {
        Backend survival = network.start("survival");
        database.save(ALEX.uuid(), StoredVanish.vanished(VanishLevel.DEFAULT, NOW));

        survival.joinBeforeTheReadFinished(ALEX);
        survival.lateRead(ALEX);

        assertThat(survival.isHidden(ALEX)).isTrue();
    }

    @Test
    void aKeptVanishReadLateDoesNotUndoAChangeMadeAfterTheJoin() {
        Backend survival = network.start("survival");
        Optional<StoredVanish> readBeforeTheChange = Optional.of(StoredVanish.vanished(VanishLevel.DEFAULT, NOW));
        survival.joinBeforeTheReadFinished(ALEX);
        survival.toggle(ALEX);
        survival.toggle(ALEX);

        survival.applyLate(ALEX, readBeforeTheChange);

        assertThat(survival.isHidden(ALEX)).isFalse();
    }

    /** The bus between the backends: delivers each frame to every other running backend, and their heartbeats. */
    private final class Network {

        private final Map<String, Backend> running = new LinkedHashMap<>();
        private final Set<String> everStarted = new HashSet<>();

        Backend start(String name) {
            Backend started = new Backend(name, this);
            List<Backend> peers = List.copyOf(running.values());
            running.put(name, started);
            boolean restart = !everStarted.add(name);
            for (Backend peer : peers) {
                if (restart) {
                    peer.sync.peerStarted(name);
                } else {
                    peer.sync.peerFound(name);
                }
                started.sync.peerFound(peer.name);
            }
            return started;
        }

        /** The backend stops at once: no departure, no more heartbeats. */
        void crash(String name) {
            running.remove(name);
        }

        boolean isLive(String name) {
            return running.containsKey(name);
        }

        void changed(String origin, VanishSync change) {
            others(origin).forEach(peer -> peer.sync.changed(origin, change));
        }

        void departed(String origin, UUID player) {
            others(origin).forEach(peer -> peer.sync.departed(origin, player));
        }

        private List<Backend> others(String origin) {
            return running.values().stream()
                    .filter(peer -> !peer.name.equals(origin))
                    .toList();
        }
    }

    /** One backend: the real vanish use cases over in-memory fakes, sharing the database and the bus. */
    private final class Backend {

        private final String name;
        private final Set<UUID> online = new HashSet<>();
        private final Set<UUID> hidden = new HashSet<>();
        private final MapStore store = new MapStore();
        private final RememberVanish remember;
        private final ToggleVanish toggleVanish;
        private final ApplyVanishOnJoin applyOnJoin;
        private final DepartVanished departVanished;
        private final ListVanished listVanished;
        private final VanishNetworkSync sync;

        Backend(String name, Network network) {
            this.name = name;
            Scheduler scheduler = new OnlineScheduler(online);
            VanishView view = new VanishView() {
                @Override
                public void hide(PlayerRef who, VanishLevel level) {
                    hidden.add(who.uuid());
                }

                @Override
                public void reveal(PlayerRef who) {
                    hidden.remove(who.uuid());
                }
            };
            VanishBus bus = new BackendBus(name, network);
            NetworkVanishRoster roster = new NetworkVanishRoster(network::isLive, clock, GRACE);
            remember = new RememberVanish(database, scheduler, clock, new RecordingLogger());
            VanishBuffs buffs = new VanishBuffs() {
                @Override
                public void apply(PlayerRef who) {}

                @Override
                public void clear(PlayerRef who) {}
            };
            VanishLevelResolver levels = new VanishLevelResolver() {
                @Override
                public VanishLevel useLevel(PlayerRef who) {
                    return VanishLevel.DEFAULT;
                }

                @Override
                public int seeLevel(PlayerRef who) {
                    return VanishLevel.MIN_LEVEL;
                }
            };
            toggleVanish = new ToggleVanish(
                    store,
                    view,
                    levels,
                    new RecordingNotifier().notifier(),
                    buffs,
                    bus,
                    new RecordingEvents(),
                    remember);
            SetVanishLevel setVanishLevel = new SetVanishLevel(store, view, levels, buffs, bus, remember);
            applyOnJoin = new ApplyVanishOnJoin(
                    new JoinVanishReconciler(roster, store), toggleVanish, setVanishLevel, remember, store);
            departVanished = new DepartVanished(store, bus, remember);
            listVanished = new ListVanished(store, levels, roster);
            sync = new VanishNetworkSync(roster, store, view, bus, new OnlinePlayers(online), scheduler);
        }

        void join(PlayerRef who) {
            online.add(who.uuid());
            applyOnJoin.join(who, remember.load(who.uuid()), false);
        }

        void joinBeforeTheReadFinished(PlayerRef who) {
            online.add(who.uuid());
            applyOnJoin.join(who, Optional.empty(), false);
        }

        void lateRead(PlayerRef who) {
            applyLate(who, remember.load(who.uuid()));
        }

        void applyLate(PlayerRef who, Optional<StoredVanish> stored) {
            applyOnJoin.lateLoad(who, stored);
        }

        void toggle(PlayerRef who) {
            toggleVanish.toggle(who);
        }

        void quit(PlayerRef who) {
            departVanished.depart(who);
            online.remove(who.uuid());
            hidden.remove(who.uuid());
        }

        boolean isHidden(PlayerRef who) {
            return online.contains(who.uuid()) && store.isVanished(who.uuid()) && hidden.contains(who.uuid());
        }

        List<UUID> listFor(PlayerRef caller) {
            return listVanished.visibleTo(caller);
        }
    }

    /** One backend's end of the bus. */
    private record BackendBus(String name, Network network) implements VanishBus {

        @Override
        public void publish(VanishSync change) {
            network.changed(name, change);
        }

        @Override
        public void publishDeparture(PlayerRef who, VanishLevel level) {
            network.departed(name, who.uuid());
        }

        @Override
        public void subscribe(Listener listener) {
            // The network calls each backend's sync directly.
        }

        @Override
        public boolean healthy() {
            return true;
        }
    }

    /** The database every backend shares; like the real table, an older write never replaces a newer one. */
    private static final class SharedRecords implements VanishRecords {

        private final Map<UUID, StoredVanish> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<StoredVanish> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void save(UUID player, StoredVanish state) {
            rows.merge(player, state, (kept, next) -> next.updatedAt().isBefore(kept.updatedAt()) ? kept : next);
        }
    }

    /** Runs everything at once, except entity work for a player who is not online on this backend, which it drops. */
    private record OnlineScheduler(Set<UUID> online) implements Scheduler {

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
            if (online.contains(player.uuid())) {
                task.run();
            }
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** The players online on one backend. */
    private record OnlinePlayers(Set<UUID> online) implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            if (!online.contains(uuid)) {
                return Optional.empty();
            }
            return Optional.of(uuid.equals(ALEX.uuid()) ? ALEX : SAM);
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.contains(uuid);
        }
    }

    /** The vanish store of one backend. */
    private static final class MapStore implements VanishStore {

        private final Map<UUID, VanishLevel> vanished = new ConcurrentHashMap<>();

        @Override
        public boolean isVanished(UUID who) {
            return vanished.containsKey(who);
        }

        @Override
        public void vanish(UUID who, VanishLevel level) {
            vanished.put(who, level);
        }

        @Override
        public void reveal(UUID who) {
            vanished.remove(who);
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.ofNullable(vanished.get(who));
        }

        @Override
        public Set<UUID> vanished() {
            return Set.copyOf(vanished.keySet());
        }

        @Override
        public VanishState snapshot() {
            return new VanishState(vanished);
        }
    }
}
