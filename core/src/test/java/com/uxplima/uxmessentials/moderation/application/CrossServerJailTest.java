package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.network.JailChanged;
import com.uxplima.uxmessentials.shared.network.JailHopFailed;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import org.junit.jupiter.api.Test;

/**
 * A jail across three backends sharing one database, one pending-hop table and one bus: a staff member on the
 * lobby jails or releases a player wherever they are connected, and a jail set on another backend is reached
 * through the cross-server hop. Each backend only acts on the players connected to it, as the real adapters do.
 */
class CrossServerJailTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final String PRISON = "prison";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final String STAFF_FAILED = "moderation.jail.cross-server.staff-failed Alex cell";
    private static final String IN_CELL = "Alex@7";

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final JailNetworkFakes.Records records = new JailNetworkFakes.Records();
    private final JailNetworkFakes.Locations locations = new JailNetworkFakes.Locations();
    private final JailNetworkFakes.Network network = new JailNetworkFakes.Network();
    private final MemoryHopStore hops = new MemoryHopStore();
    private final List<String> connects = new ArrayList<>();
    private final List<Backend> backends = new ArrayList<>();
    private boolean deliveredTwice;
    private final HopConnector proxy = new HopConnector() {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void connect(PlayerRef player, String server) {
            connects.add(player.name() + "->" + server);
        }
    };
    private final Backend lobby = new Backend(LOBBY, true);
    private final Backend survival = new Backend(SURVIVAL, true);
    private final Backend prison = new Backend(PRISON, true);

    CrossServerJailTest() {
        network.live.addAll(List.of(LOBBY, SURVIVAL, PRISON));
        connect(SAM, lobby);
    }

    @Test
    void aJailOnTheLobbyHoldsAPlayerOnAnotherServerAtOnce() {
        jailSetOn(SURVIVAL);
        connect(ALEX, survival);

        assertThat(jailAlex().isOk()).isTrue();

        assertThat(survival.live.moved).containsExactly(IN_CELL);
        assertThat(lobby.live.moved).isEmpty();
        assertThat(prison.live.moved).isEmpty();
        assertThat(survival.read).containsExactly("Alex moderation.jail.notify-target");
        assertThat(records.loadJail(ALEX).isActiveAt(clock.instant())).isTrue();
    }

    @Test
    void aReleaseOnTheLobbyReachesThePlayerWhereverTheyAre() {
        jailSetOn(PRISON);
        connect(ALEX, prison);
        jailAlex();

        assertThat(lobby.unjail.unjail(SAM, ALEX).isOk()).isTrue();

        assertThat(prison.live.released).containsExactly("Alex");
        assertThat(survival.live.released).isEmpty();
        assertThat(records.loadJail(ALEX)).isEqualTo(JailState.none());
    }

    @Test
    void aJailOnAnotherServerSendsThePlayerThereFreeOfChargeAndPutsThemInTheCell() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);

        jailAlex();

        assertThat(connects).containsExactly("Alex->prison");
        PendingHop pending = hops.find(ALEX.uuid()).orElseThrow();
        assertThat(pending.kind()).isEqualTo(HopKind.JAIL);
        assertThat(pending.charge()).isEmpty();
        assertThat(pending.originServer()).isEqualTo(LOBBY);
        assertThat(lobby.read).contains("Alex moderation.jail.cross-server.sending");

        arriveAt(prison);
        scheduler.advance(TTL);

        // Once by the join re-apply, once by the hop landing: both put the player in the same cell.
        assertThat(prison.live.moved).containsExactly(IN_CELL, IN_CELL);
        assertThat(prison.read).contains("Alex moderation.jail.cross-server.arrived");
        assertThat(hops.rows()).isEmpty();
        assertThat(staffLines()).isEmpty();
    }

    @Test
    void thePlayersOwnBackendStartsTheHopWhenTheJailIsOnAThirdServer() {
        jailSetOn(PRISON);
        connect(ALEX, survival);

        jailAlex();

        assertThat(connects).containsExactly("Alex->prison");
        assertThat(hops.find(ALEX.uuid()).orElseThrow().originServer()).isEqualTo(SURVIVAL);
    }

    @Test
    void aHopTheProxyNeverCarriesOutKeepsTheJailAndTellsTheStaffOfEveryServerOnce() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        jailAlex();

        scheduler.advance(TTL);

        assertThat(records.loadJail(ALEX).isActiveAt(clock.instant())).isTrue();
        assertThat(lobby.read).contains("Alex moderation.jail.cross-server.failed");
        assertThat(lobby.staff.lines).containsExactly(STAFF_FAILED);
        assertThat(survival.staff.lines).containsExactly(STAFF_FAILED);
        assertThat(prison.staff.lines).containsExactly(STAFF_FAILED);

        // Without a relog, the transfer is tried again.
        scheduler.advance(JailTransfer.FIRST_RETRY);
        assertThat(connects).containsExactly("Alex->prison", "Alex->prison");
    }

    @Test
    void aJailHopThatRacesAnotherTransferIsTriedAgainWithoutAlertingStaff() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        // A second transfer starts while the first has read no earlier row and not yet written its own.
        hops.afterNextFind(() -> lobby.transfer.send(ALEX, "cell"));

        jailAlex();

        assertThat(connects).containsExactly("Alex->prison");
        assertThat(lobby.read).contains("Alex common.hop-busy-ordered").doesNotContain("Alex common.hop-busy");
        assertThat(staffLines()).isEmpty();

        // The turned-away transfer is tried again on its own, before the first hop's timer could report anything.
        scheduler.advance(JailTransfer.FIRST_RETRY);
        assertThat(connects).containsExactly("Alex->prison", "Alex->prison");
        assertThat(staffLines()).isEmpty();
    }

    @Test
    void aJailHopBlockedByAnotherMoveThisServerCannotSettleIsTriedAgainUntilThatMoveIsGone() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        // A fresh hop of a kind this backend has no resolver for, which its own origin is still watching.
        hops.record(new PendingHop(
                ALEX.uuid(),
                HopKind.HOME,
                "base",
                SURVIVAL,
                SURVIVAL,
                clock.instant(),
                Optional.empty(),
                Optional.empty()));

        jailAlex();

        assertThat(connects).isEmpty();
        assertThat(lobby.read).contains("Alex common.hop-busy-ordered");
        assertThat(staffLines()).isEmpty();

        // The other row blocks until it is past twice the TTL; the retries keep going and send the player then.
        scheduler.advance(JailTransfer.FIRST_RETRY);
        assertThat(connects).isEmpty();
        scheduler.advance(JailTransfer.FIRST_RETRY.multipliedBy(2));
        assertThat(connects).containsExactly("Alex->prison");
        assertThat(staffLines()).isEmpty();
    }

    @Test
    void aPrisonerDroppedWhileTheJailServerIsDownIsSentThereOnceItIsBackWithoutRelogging() {
        jailSetOn(PRISON);
        network.live.remove(PRISON);
        connect(ALEX, lobby);

        jailAlex();
        scheduler.advance(JailTransfer.FIRST_RETRY);

        assertThat(connects).isEmpty();
        assertThat(lobby.read).containsOnlyOnce("Alex moderation.jail.cross-server.failed");

        network.live.add(PRISON);
        scheduler.advance(JailTransfer.FIRST_RETRY.multipliedBy(2));

        assertThat(connects).containsExactly("Alex->prison");
        assertThat(lobby.staff.lines).containsExactly(STAFF_FAILED);
        assertThat(survival.staff.lines).containsExactly(STAFF_FAILED);
    }

    @Test
    void aPrisonerAStaffMemberFrozeIsNotSentAwayUntilTheFreezeIsLifted() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        lobby.live.frozen.add(ALEX.uuid());

        jailAlex();
        scheduler.advance(JailTransfer.FIRST_RETRY);

        assertThat(connects).isEmpty();
        assertThat(staffLines()).isEmpty();

        lobby.live.frozen.remove(ALEX.uuid());
        scheduler.advance(JailTransfer.FIRST_RETRY.multipliedBy(2));

        assertThat(connects).containsExactly("Alex->prison");
    }

    @Test
    void aPrisonerWhoKeepsReloggingWhileTheJailServerIsDownIsReportedOnce() {
        jailSetOn(PRISON);
        network.live.remove(PRISON);
        connect(ALEX, lobby);
        jailAlex();

        for (int join = 0; join < 5; join++) {
            arriveAt(lobby);
        }
        arriveAt(survival);

        assertThat(lobby.staff.lines).containsExactly(STAFF_FAILED);
        assertThat(survival.staff.lines).containsExactly(STAFF_FAILED);
        assertThat(prison.staff.lines).containsExactly(STAFF_FAILED);

        scheduler.advance(JailHopAlerts.QUIET);
        arriveAt(lobby);

        assertThat(lobby.staff.lines).containsExactly(STAFF_FAILED, STAFF_FAILED);
    }

    @Test
    void aMisroutedJailHopIsNotReportedBecauseTheBackendReachedSendsThePlayerOn() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        jailAlex();

        arriveAt(survival);

        assertThat(staffLines()).isEmpty();
        assertThat(connects).containsExactly("Alex->prison", "Alex->prison");
    }

    @Test
    void aJailNamingAServerThatNoLongerAnswersIsAppliedWhereItsWorldIsLoaded() {
        locations.save(StoredJail.of("cell", at(7, 64, 7), "old-prison"));
        lobby.live.hasCellWorld = true;
        connect(ALEX, lobby);

        jailAlex();

        assertThat(lobby.live.moved).containsExactly(IN_CELL);
        assertThat(connects).isEmpty();
        assertThat(staffLines()).isEmpty();
    }

    @Test
    void aPlayerReleasedWhileOnTheWayIsNotPutInTheCellAndNobodyIsTold() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        jailAlex();
        lobby.unjail.unjail(SAM, ALEX);

        arriveAt(prison);
        scheduler.advance(TTL);

        assertThat(prison.live.moved).isEmpty();
        assertThat(prison.read).isEmpty();
        assertThat(hops.rows()).isEmpty();
        assertThat(staffLines()).isEmpty();
    }

    @Test
    void aCellThatCannotBeReachedOnArrivalKeepsTheJailAndTellsStaff() {
        jailSetOn(PRISON);
        connect(ALEX, lobby);
        jailAlex();
        prison.live.cellWorldMissing = true;

        arriveAt(prison);

        assertThat(prison.live.moved).isEmpty();
        assertThat(prison.read).contains("Alex moderation.jail.cross-server.failed");
        assertThat(records.loadJail(ALEX).isActiveAt(clock.instant())).isTrue();
        assertThat(lobby.staff.lines).containsExactly(STAFF_FAILED);
        assertThat(prison.staff.lines).containsExactly(STAFF_FAILED);
    }

    @Test
    void framesDeliveredTwiceAreActedOnOnce() {
        deliveredTwice = true;
        jailSetOn(SURVIVAL);
        connect(ALEX, survival);

        jailAlex();
        lobby.unjail.unjail(SAM, ALEX);
        jailSetOn(PRISON);
        network.live.remove(PRISON);
        jailAlex();

        assertThat(survival.live.moved).containsExactly(IN_CELL);
        assertThat(survival.live.released).containsExactly("Alex");
        assertThat(survival.read)
                .containsExactly(
                        "Alex moderation.jail.notify-target",
                        "Alex moderation.jail.notify-target",
                        "Alex moderation.jail.cross-server.failed");
        assertThat(lobby.staff.lines).containsExactly(STAFF_FAILED);
    }

    @Test
    void aJailedPlayerWhoSwitchesServersIsSentBackToTheJail() {
        jailSetOn(SURVIVAL);
        connect(ALEX, survival);
        jailAlex();

        arriveAt(lobby);

        assertThat(connects).containsExactly("Alex->survival");

        arriveAt(survival);

        assertThat(survival.live.moved).containsExactly(IN_CELL, IN_CELL, IN_CELL);
    }

    @Test
    void withTheNetworkOffAJailIsAppliedHereWhateverServerItNames() {
        Backend single = new Backend("single", false);
        jailSetOn(PRISON);
        connect(ALEX, single);

        single.jail.jail(SAM, ALEX, "cell", "", Optional.empty());

        assertThat(single.live.moved).containsExactly(IN_CELL);
        assertThat(connects).isEmpty();
    }

    private void jailSetOn(String server) {
        locations.save(StoredJail.of("cell", at(7, 64, 7), server));
    }

    private Result<JailState.Active, ModerationError> jailAlex() {
        return lobby.jail.jail(SAM, ALEX, "cell", "", Optional.empty());
    }

    private void connect(PlayerRef player, Backend backend) {
        network.connect(player, backend.name);
        scheduler.online(player);
    }

    /** The player joins {@code backend}: the hop arrival and the join re-apply both run, as the listeners do. */
    private void arriveAt(Backend backend) {
        connect(ALEX, backend);
        backend.arrival.onArrival(ALEX);
        backend.countdown.onJoin(ALEX);
        scheduler.advance(SETTLE);
    }

    private List<String> staffLines() {
        return backends.stream()
                .flatMap(backend -> backend.staff.lines.stream())
                .toList();
    }

    /** Hand a frame to every backend but its origin, through the real codec, once or twice like transport=both. */
    private void broadcast(NetworkMessage frame) {
        byte[] wire = NetworkMessageCodec.encode(frame);
        for (Backend backend : List.copyOf(backends)) {
            if (backend.name.equals(frame.originServer())) {
                continue;
            }
            backend.receive(NetworkMessageCodec.decode(wire));
            if (deliveredTwice) {
                backend.receive(NetworkMessageCodec.decode(wire));
            }
        }
    }

    /** One backend of the network: its live side, its staff, the lines its players read and its use cases. */
    private final class Backend {

        final String name;
        final JailNetworkFakes.LiveSide live;
        final JailNetworkFakes.StaffLines staff = new JailNetworkFakes.StaffLines();
        final List<String> read = new ArrayList<>();
        final JailHopAlerts alerts;
        final HopArrival arrival;
        final JailTransfer transfer;
        final Jail jail;
        final Unjail unjail;
        final JailCountdown countdown;
        final ApplyRemoteJail apply;

        Backend(String name, boolean networked) {
            this.name = name;
            this.live = new JailNetworkFakes.LiveSide(name, network);
            Notifier notifier = new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {
                if (name.equals(network.where.get(viewer.uuid()))) {
                    read.add(viewer.name() + " " + text);
                }
            });
            JailSync sync = networked ? new BusSync(name) : JailSync.NONE;
            QuietLog log = new QuietLog();
            this.alerts = new JailHopAlerts(staff, sync, clock);
            HopResolvers resolvers = new HopResolvers();
            HopCharges charges = new HopCharges(notifier, log);
            Optional<HopDepartures> departures = Optional.empty();
            if (networked) {
                HopWatchdog watchdog = new HopWatchdog(hops, resolvers, charges, scheduler, log, TTL, clock);
                departures = Optional.of(
                        new HopDepartures(HopGate.OPEN, proxy, watchdog, charges, resolvers, scheduler, name, clock));
            }
            this.arrival = new HopArrival(hops, resolvers, charges, scheduler, log, name, SETTLE, TTL, clock);
            this.transfer = new JailTransfer(
                    records,
                    locations,
                    live,
                    live,
                    network.seenFrom(name),
                    departures,
                    alerts,
                    notifier,
                    scheduler,
                    log,
                    clock);
            if (networked) {
                resolvers.register(new JailHopResolver(records, locations, live, alerts, transfer, name, clock));
            }
            SilentAudit audit = new SilentAudit();
            RecordingEvents events = new RecordingEvents();
            this.jail = new Jail(
                    records,
                    locations,
                    transfer,
                    sync,
                    new ModerationGuard(new FakePermissions()),
                    notifier,
                    audit,
                    events,
                    clock);
            this.unjail = new Unjail(records, live, sync, notifier, audit, events, clock);
            this.countdown = new JailCountdown(records, live, transfer, audit, events, clock);
            this.apply = new ApplyRemoteJail(records, transfer, live, network.seenFrom(name), notifier, clock);
            backends.add(this);
        }

        void receive(NetworkMessage message) {
            if (message instanceof JailChanged changed) {
                apply.onJailChanged(changed.frameId(), changed.target(), changed.jailed());
            } else if (message instanceof JailHopFailed failed) {
                alerts.failedOnPeer(failed.frameId(), failed.target(), failed.targetName(), failed.jail());
            }
        }
    }

    /** The bus as one backend publishes on it: each announcement becomes a frame with a fresh id. */
    private final class BusSync implements JailSync {

        private final String origin;

        BusSync(String origin) {
            this.origin = origin;
        }

        @Override
        public void jailChanged(PlayerRef target, boolean jailed) {
            broadcast(new JailChanged(origin, UUID.randomUUID(), target.uuid(), jailed));
        }

        @Override
        public void hopFailed(PlayerRef target, String jail) {
            broadcast(new JailHopFailed(origin, UUID.randomUUID(), target.uuid(), target.name(), jail));
        }
    }
}
