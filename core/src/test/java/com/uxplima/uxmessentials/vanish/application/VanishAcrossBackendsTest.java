package com.uxplima.uxmessentials.vanish.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus.PeerRun;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * A vanish survives a server switch, ends when the player leaves the network unless they may persist it, and the
 * network list only shows players some running backend is hiding.
 *
 * <p>Each backend is the real use cases over fakes; they share one database and one bus. A server switch follows the
 * proxy's order: the target reads the row at login, then the origin lets the player go and its departure reaches the
 * target, then the target's join runs. Heartbeats only arrive when a test delivers them, and a backend can be made deaf
 * to every frame, as an empty backend on the proxy transport is.
 */
class VanishAcrossBackendsTest {

    private static final Duration HOP_WINDOW = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(NOW);
    private final SharedRecords database = new SharedRecords();
    private final Network network = new Network();

    @Test
    void aSwitchInTheProxysOrderKeepsTheVanishedPlayerHidden() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        Backend creative = network.start("creative");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.switchServer(ALEX, survival, lobby);

        assertThat(lobby.isHidden(ALEX)).isTrue();
        assertThat(creative.listFor(SAM)).containsExactly(ALEX.uuid());
        assertThat(database.find(ALEX.uuid()).flatMap(StoredVanish::server)).contains("lobby");
    }

    @Test
    void aSwitchWhoseRowWasNeverWrittenIsCoveredByTheOriginsDeparture() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);
        database.lose(ALEX.uuid());

        network.switchServer(ALEX, survival, lobby);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aPlayerWhoLeftIsNoLongerListedAnywhere() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);
        assertThat(lobby.listFor(SAM)).containsExactly(ALEX.uuid());

        survival.quit(ALEX);

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void theOriginsDepartureArrivingAfterTheTargetsJoinKeepsThePlayerListed() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        Backend creative = network.start("creative");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        LoginRead read = lobby.preLogin(ALEX);
        lobby.join(ALEX, read);
        survival.quit(ALEX);

        assertThat(lobby.isHidden(ALEX)).isTrue();
        assertThat(creative.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aJoinSlowerThanTheHopWindowAfterTheLoginReadKeepsTheSwitchingPlayerHidden() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        // A forced resource pack holds the player between the login read and the join.
        LoginRead read = lobby.preLogin(ALEX);
        survival.quit(ALEX);
        clock.advance(HOP_WINDOW.plusSeconds(15));
        lobby.join(ALEX, read);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aPeerThatNamesNoRunOnItsFramesIsStillHeardAfterItRestarts() {
        network.namesNoRun("survival");
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.joinHoldingPersist(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        Backend restarted = network.start("survival");
        network.beat();
        restarted.joinHoldingPersist(ALEX);

        assertThat(lobby.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aPeerThatNamesNoRunOnItsFramesStillLosesItsOldEntriesWhenItRestarts() {
        network.namesNoRun("survival");
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        network.start("survival");
        network.beat();

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aSwitchToABackendThatHasHeardNoPeerYetKeepsThePlayerHidden() {
        Backend survival = network.start("survival");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);
        Backend fresh = network.start("fresh");

        network.switchServer(ALEX, survival, fresh);

        assertThat(fresh.isHidden(ALEX)).isTrue();
    }

    @Test
    void aSwitchToABackendTheTransportCannotReachKeepsThePlayerHidden() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        Backend creative = network.start("creative");
        creative.join(SAM);
        network.beat();
        network.deafen("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.switchServer(ALEX, survival, lobby);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aSwitchWithinTheHopWindowOfTheDepartureKeepsThePlayerHidden() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        survival.quit(ALEX);
        clock.advance(HOP_WINDOW.minusSeconds(5));
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aSwitchThatLandsAfterTheHopWindowShowsThePlayer() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        // When the origin let the player go before the target's login read and the transfer outlasted the window, it
        // cannot be told apart from leaving the network and coming back, so the player lands visible, as a returning
        // player without the persist node does.
        survival.quit(ALEX);
        clock.advance(HOP_WINDOW.plusSeconds(1));
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aPlayerWithoutPersistWhoRelogsAfterTheHopWindowIsVisible() {
        Backend survival = network.start("survival");
        survival.join(ALEX);
        survival.toggle(ALEX);

        survival.quit(ALEX);
        clock.advance(Duration.ofHours(1));
        survival.join(ALEX);

        assertThat(survival.isHidden(ALEX)).isFalse();
        assertThat(database.find(ALEX.uuid()).map(StoredVanish::vanished)).contains(false);
    }

    @Test
    void aPlayerWithoutPersistWhoseBackendCrashedReturnsVisibleOnceItsSightingIsStale() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        clock.advance(HOP_WINDOW.plusSeconds(1));
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
        assertThat(database.find(ALEX.uuid()).map(StoredVanish::vanished)).contains(false);
    }

    @Test
    void aPlayerWithoutPersistWhoseBackendCrashedAndCameBackReturnsVisibleDaysLater() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        network.start("survival");
        network.beat();
        clock.advance(Duration.ofDays(2));
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
    }

    @Test
    void aPlayerWithoutPersistReturnsVisibleOnTheOnlyBackendLeftAfterTheirsWentDownForGood() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        clock.advance(Duration.ofDays(2));
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
    }

    @Test
    void aPlayerWithoutPersistOnABackendThatStoppedReturnsVisibleAfterTheHopWindow() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.stop("survival");
        clock.advance(HOP_WINDOW.plusSeconds(1));
        lobby.join(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
    }

    @Test
    void aPlayerLongOnTheirBackendStillSwitchesHiddenBecauseItKeepsSeeingThem() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        survival.join(ALEX);
        survival.toggle(ALEX);
        for (int refresh = 0; refresh < 360; refresh++) {
            clock.advance(HOP_WINDOW.dividedBy(3));
            survival.refresh();
        }

        network.switchServer(ALEX, survival, lobby);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aPersistHolderComesBackHiddenAfterTheirBackendRestarts() {
        Backend survival = network.start("survival");
        survival.joinHoldingPersist(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        clock.advance(Duration.ofHours(1));
        Backend restarted = network.start("survival");
        restarted.joinHoldingPersist(ALEX);

        assertThat(restarted.isHidden(ALEX)).isTrue();
    }

    @Test
    void aPlayerWhoReappearedArrivesVisibleOnTheNextBackend() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);
        survival.toggle(ALEX);

        network.switchServer(ALEX, survival, lobby);

        assertThat(lobby.isHidden(ALEX)).isFalse();
        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void theEntriesOfABackendThatStopsRunningExpire() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        clock.advance(HOP_WINDOW.plusSeconds(1));

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aBackendThatStartsAgainTakesItsOldEntriesWithIt() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        clock.advance(Duration.ofSeconds(5));
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        network.start("survival");
        clock.advance(Duration.ofSeconds(1));
        network.beat();

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void whatABackendsNewRunReportsBeforeItsFirstHeartbeatIsKept() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.joinHoldingPersist(ALEX);
        survival.toggle(ALEX);

        network.crash("survival");
        clock.advance(Duration.ofSeconds(5));
        Backend restarted = network.start("survival");
        restarted.joinHoldingPersist(ALEX);
        clock.advance(Duration.ofSeconds(1));
        network.beat();

        assertThat(lobby.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aLateFrameFromARunThatEndedIsIgnored() {
        network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        long endedRun = network.runOf("survival");
        network.crash("survival");
        network.start("survival");
        network.beat();

        lobby.sync.changed("survival", endedRun, 1L, VanishSync.vanished(ALEX, VanishLevel.DEFAULT));

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aPlayerHiddenOnlyUntilTheReadIsNotLeftListedOnPeers() {
        Backend lobby = network.start("lobby");
        lobby.joinUnread(ALEX, true);
        Backend survival = network.start("survival");
        network.beat();
        assertThat(survival.listFor(SAM)).containsExactly(ALEX.uuid());

        lobby.lateRead(ALEX);

        assertThat(survival.listFor(SAM)).isEmpty();
    }

    @Test
    void aRestartedBackendHearsOfThePlayersItsPeersAreHiding() {
        Backend survival = network.start("survival");
        network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.crash("lobby");
        Backend restarted = network.start("lobby");
        network.beat();

        assertThat(restarted.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aJoinWhoseReadIsLateHidesAPlayerWhoMayVanishUntilTheReadKeepsThemHidden() {
        Backend lobby = network.start("lobby");
        database.save(ALEX.uuid(), StoredVanish.vanished(VanishLevel.DEFAULT, "survival", NOW));
        database.markDeparted(ALEX.uuid(), "survival", NOW);

        lobby.joinUnread(ALEX, true);
        assertThat(lobby.isHidden(ALEX)).isTrue();
        lobby.lateRead(ALEX);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aLateReadThatDoesNotCarryTheVanishOverRevealsThePlayer() {
        Backend lobby = network.start("lobby");

        lobby.joinUnread(ALEX, true);
        assertThat(lobby.isHidden(ALEX)).isTrue();
        lobby.lateRead(ALEX);

        assertThat(lobby.isHidden(ALEX)).isFalse();
    }

    @Test
    void aJoinWhoseReadIsLateLeavesAPlayerWhoCannotVanishVisible() {
        Backend lobby = network.start("lobby");

        lobby.joinUnread(ALEX, false);

        assertThat(lobby.isHidden(ALEX)).isFalse();
    }

    @Test
    void aLateReadDoesNotUndoAChangeMadeAfterTheJoin() {
        Backend lobby = network.start("lobby");
        Optional<StoredVanish> readBeforeTheChange = Optional.of(
                StoredVanish.vanished(VanishLevel.DEFAULT, "survival", NOW).departed(NOW));
        lobby.joinUnread(ALEX, true);
        lobby.toggle(ALEX);

        lobby.applyLate(ALEX, readBeforeTheChange);

        assertThat(lobby.isHidden(ALEX)).isFalse();
    }

    @Test
    void aReadThatKeepsFailingLeavesAPlayerWhoMayVanishHidden() {
        Backend lobby = network.start("lobby");
        lobby.joinUnread(ALEX, true);

        lobby.readFailed(ALEX);

        assertThat(lobby.isHidden(ALEX)).isTrue();
    }

    @Test
    void aRevealThatArrivesAfterANewerVanishDoesNotShowThePlayer() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        // The reveal is held back, so the vanish sent after it reaches the lobby first.
        network.withhold("lobby");
        survival.toggle(ALEX);
        network.resume();
        survival.toggle(ALEX);
        network.deliverWithheld();

        assertThat(survival.isHidden(ALEX)).isTrue();
        assertThat(lobby.listFor(SAM)).containsExactly(ALEX.uuid());
    }

    @Test
    void aVanishThatArrivesAfterThePlayersDepartureDoesNotListThem() {
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);

        network.withhold("lobby");
        survival.toggle(ALEX);
        network.resume();
        survival.quit(ALEX);
        network.deliverWithheld();

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    @Test
    void aPeerThatNumbersNoFramesIsAppliedInTheOrderItsFramesArrive() {
        network.namesNoRun("survival");
        Backend survival = network.start("survival");
        Backend lobby = network.start("lobby");
        network.beat();
        survival.join(ALEX);
        survival.toggle(ALEX);

        network.withhold("lobby");
        survival.toggle(ALEX);
        network.resume();
        survival.toggle(ALEX);
        network.deliverWithheld();

        assertThat(lobby.listFor(SAM)).isEmpty();
    }

    /** The bus between the backends, and what each one has heard of the others. */
    private final class Network {

        private final Map<String, Backend> running = new LinkedHashMap<>();
        private final Map<String, Integer> runs = new HashMap<>();
        private final Map<String, Map<String, Integer>> runSeen = new HashMap<>();
        private final Map<String, Set<String>> heard = new HashMap<>();
        private final Set<String> deaf = new HashSet<>();
        private final Set<String> namesNoRun = new HashSet<>();
        private final Map<String, Long> sent = new HashMap<>();
        private final Set<String> withholding = new HashSet<>();
        private final List<Runnable> withheld = new ArrayList<>();

        Backend start(String name) {
            runs.merge(name, 1, Integer::sum);
            sent.remove(name);
            heard.put(name, new HashSet<>());
            runSeen.remove(name);
            Backend started = new Backend(name, this);
            running.put(name, started);
            started.remember.releaseOrphans();
            return started;
        }

        /** The backend stops cleanly: it records a departure for everyone it holds, then goes quiet. */
        void stop(String name) {
            Backend stopping = running.get(name);
            if (stopping != null) {
                stopping.remember.releaseAllNow();
            }
            crash(name);
        }

        long runOf(String name) {
            return runs.getOrDefault(name, 0);
        }

        /** The backend stops at once: no departure, no more heartbeats. */
        void crash(String name) {
            running.remove(name);
            heard.values().forEach(origins -> origins.remove(name));
        }

        /** The backend receives nothing from now on, and has forgotten what it heard. */
        void deafen(String name) {
            deaf.add(name);
            heardBy(name).clear();
        }

        /** Every running backend's heartbeat reaches every peer that can hear. */
        void beat() {
            for (String origin : List.copyOf(running.keySet())) {
                for (Backend receiver : others(origin)) {
                    heardBy(receiver.name).add(origin);
                    Integer before = runSeen.computeIfAbsent(receiver.name, key -> new HashMap<>())
                            .put(origin, runs.get(origin));
                    PeerRun run;
                    if (before == null) {
                        run = PeerRun.FIRST_HEARD;
                    } else if (before.equals(runs.get(origin))) {
                        run = PeerRun.SAME_RUN;
                    } else {
                        run = PeerRun.NEW_RUN;
                    }
                    receiver.sync.pinged(origin, runOf(origin), run);
                }
            }
        }

        /** The proxy's order: the target's login read, then the origin's quit and departure, then the target's join. */
        void switchServer(PlayerRef who, Backend from, Backend to) {
            LoginRead read = to.preLogin(who);
            from.quit(who);
            to.join(who, read);
        }

        /** The backend's vanish frames carry no run, as a peer on an older release sends them. */
        void namesNoRun(String name) {
            namesNoRun.add(name);
        }

        /** Frames to {@code receiver} are held back from now on, until {@link #deliverWithheld} sends them late. */
        void withhold(String receiver) {
            withholding.add(receiver);
        }

        /** Frames flow to every backend again; the ones held back stay held. */
        void resume() {
            withholding.clear();
        }

        /** The frames held back arrive now, after everything sent since. */
        void deliverWithheld() {
            List<Runnable> late = List.copyOf(withheld);
            withheld.clear();
            late.forEach(Runnable::run);
        }

        void changed(String origin, VanishSync change) {
            long run = namesNoRun.contains(origin) ? NetworkVanishStore.UNKNOWN_RUN : runOf(origin);
            long sequence = nextSequence(origin);
            others(origin).forEach(peer -> send(peer, () -> peer.sync.changed(origin, run, sequence, change)));
        }

        void departed(String origin, UUID player) {
            long run = namesNoRun.contains(origin) ? NetworkVanishStore.UNKNOWN_RUN : runOf(origin);
            long sequence = nextSequence(origin);
            others(origin).forEach(peer -> send(peer, () -> peer.sync.departed(origin, run, sequence, player)));
        }

        /** The number a run gives its next vanish frame; a peer on an older release numbers none. */
        private long nextSequence(String origin) {
            if (namesNoRun.contains(origin)) {
                return NetworkVanishStore.UNKNOWN_SEQUENCE;
            }
            return sent.merge(origin, 1L, Long::sum);
        }

        private void send(Backend receiver, Runnable frame) {
            if (withholding.contains(receiver.name)) {
                withheld.add(frame);
            } else {
                frame.run();
            }
        }

        boolean isLive(String viewer, String server) {
            return server.equals(viewer)
                    || (running.containsKey(server) && heardBy(viewer).contains(server));
        }

        List<OnlinePlayer> seenBy(String viewer) {
            List<OnlinePlayer> seen = new ArrayList<>();
            running.forEach((name, backend) -> {
                if (isLive(viewer, name)) {
                    backend.players.values().forEach(player -> seen.add(new OnlinePlayer(player, name)));
                }
            });
            return seen;
        }

        private Set<String> heardBy(String receiver) {
            return heard.computeIfAbsent(receiver, key -> new HashSet<>());
        }

        private List<Backend> others(String origin) {
            return running.values().stream()
                    .filter(peer -> !peer.name.equals(origin) && !deaf.contains(peer.name))
                    .toList();
        }
    }

    /** One backend: the real vanish use cases over in-memory fakes, sharing the database and the bus. */
    private final class Backend {

        private final String name;
        private final Map<UUID, PlayerRef> players = new ConcurrentHashMap<>();
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
            Scheduler scheduler = new OnlineScheduler(players);
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
            NetworkPresence presence = new SeenPresence(name, network);
            NetworkVanishRoster roster = new NetworkVanishRoster(presence, clock, HOP_WINDOW);
            remember = new RememberVanish(database, name, scheduler, clock, new RecordingLogger());
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
                    new JoinVanishReconciler(roster, store),
                    toggleVanish,
                    setVanishLevel,
                    remember,
                    store,
                    view,
                    levels,
                    bus,
                    presence,
                    clock,
                    HOP_WINDOW);
            departVanished = new DepartVanished(store, bus, remember);
            listVanished = new ListVanished(store, levels, roster);
            sync = new VanishNetworkSync(roster, store, view, bus, new OnlinePlayers(players), scheduler);
        }

        LoginRead preLogin(PlayerRef who) {
            return new LoginRead(remember.load(who.uuid()), clock.instant());
        }

        void join(PlayerRef who) {
            join(who, preLogin(who));
        }

        void join(PlayerRef who, LoginRead read) {
            players.put(who.uuid(), who);
            applyOnJoin.join(who, read.stored(), read.at(), false, false);
        }

        void joinHoldingPersist(PlayerRef who) {
            LoginRead read = preLogin(who);
            players.put(who.uuid(), who);
            applyOnJoin.join(who, read.stored(), read.at(), true, false);
        }

        void joinUnread(PlayerRef who, boolean canVanish) {
            players.put(who.uuid(), who);
            applyOnJoin.joinUnread(who, false, canVanish);
        }

        void lateRead(PlayerRef who) {
            applyLate(who, remember.load(who.uuid()));
        }

        void applyLate(PlayerRef who, Optional<StoredVanish> stored) {
            applyOnJoin.lateLoad(who, stored, false);
        }

        void readFailed(PlayerRef who) {
            applyOnJoin.readFailed(who);
        }

        void toggle(PlayerRef who) {
            toggleVanish.toggle(who);
        }

        /** The sighting timer fires. */
        void refresh() {
            remember.refresh(store.vanished());
        }

        void quit(PlayerRef who) {
            departVanished.depart(who);
            players.remove(who.uuid());
            hidden.remove(who.uuid());
        }

        boolean isHidden(PlayerRef who) {
            return players.containsKey(who.uuid()) && store.isVanished(who.uuid()) && hidden.contains(who.uuid());
        }

        List<UUID> listFor(PlayerRef caller) {
            return listVanished.visibleTo(caller);
        }
    }

    /** What a backend's login read returned, and when. */
    private record LoginRead(Optional<StoredVanish> stored, Instant at) {}

    /** What one backend's network presence knows: the players of the backends it has heard from. */
    private record SeenPresence(String localServer, Network network) implements NetworkPresence {

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            List<OnlinePlayer> found = online().stream()
                    .filter(online -> online.player().uuid().equals(player))
                    .toList();
            return found.stream()
                    .filter(online -> online.server().equals(localServer))
                    .findFirst()
                    .or(() -> found.stream().findFirst());
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return online().stream()
                    .filter(online -> online.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return network.seenBy(localServer);
        }

        @Override
        public boolean isLive(String server) {
            return network.isLive(localServer, server);
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

    /** The database every backend shares, following the real table's rules for each write. */
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

        @Override
        public void markDeparted(UUID player, String server, Instant at) {
            rows.computeIfPresent(
                    player,
                    (id, kept) ->
                            kept.vanished() && kept.server().equals(Optional.of(server)) ? kept.departed(at) : kept);
        }

        @Override
        public void markArrived(UUID player, String server, Instant at) {
            rows.computeIfPresent(player, (id, kept) -> kept.vanished() ? kept.seenOn(server, at) : kept);
        }

        @Override
        public void touch(Collection<UUID> players, String server, Instant at) {
            for (UUID player : players) {
                rows.computeIfPresent(player, (id, kept) -> heldBy(kept, server) ? kept.seenOn(server, at) : kept);
            }
        }

        @Override
        public void releaseHeld(String server, Instant at) {
            rows.replaceAll((id, kept) -> heldBy(kept, server) ? kept.departed(at) : kept);
        }

        @Override
        public void releaseStale(String server, Instant startedAt) {
            rows.replaceAll((id, kept) -> heldBy(kept, server)
                            && kept.seenAt()
                                    .map(seen -> seen.isBefore(startedAt))
                                    .orElse(true)
                    ? kept.departed(startedAt)
                    : kept);
        }

        private static boolean heldBy(StoredVanish kept, String server) {
            return kept.vanished()
                    && kept.server().equals(Optional.of(server))
                    && kept.departedAt().isEmpty();
        }

        /** The row never reached the database, as when its write failed. */
        void lose(UUID player) {
            rows.remove(player);
        }
    }

    /** Runs everything at once, except entity work for a player who is not online on this backend, which it drops. */
    private record OnlineScheduler(Map<UUID, PlayerRef> online) implements Scheduler {

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
            if (online.containsKey(player.uuid())) {
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
    private record OnlinePlayers(Map<UUID, PlayerRef> online) implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(online.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.containsKey(uuid);
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
