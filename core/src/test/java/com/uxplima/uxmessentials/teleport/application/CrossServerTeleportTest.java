package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.Warmups.CancelCause;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.CrossServerStaffTeleport.Named;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.application.port.TeleportNetwork;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleporting;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Teleport requests and staff teleports between players on different backends. Three fake backends share one fake
 * bus that delivers every frame twice, one hop table, one cooldown store and one proxy, so a line read twice, a hop
 * landed twice or a request shown twice all show up in what the players read and where they end up.
 */
class CrossServerTeleportTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final String CREATIVE = "creative";
    private static final PlayerRef MOD = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000d"), "Mod");
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration HOP_TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final Position SAM_AT_ACCEPT = at(10, 64, 10);
    private static final Position SAM_LATER = at(250, 70, -40);
    private static final Position ALEX_AT = at(-5, 64, 3);
    private static final Position MOD_AT = at(0, 100, 0);

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final MemoryHopStore hopTable = new MemoryHopStore();
    private final ClockCooldowns cooldowns = new ClockCooldowns(clock);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingLogger log = new RecordingLogger();
    private final Map<UUID, OnlinePlayer> roster = new LinkedHashMap<>();
    private final Map<UUID, Position> positions = new HashMap<>();
    private final List<String> moves = new ArrayList<>();
    private final List<TeleportKind> vetoAsked = new ArrayList<>();
    private final Bus bus = new Bus();
    private final Set<UUID> jailed = new HashSet<>();
    /** The vanish rows every backend shares in the database, by player, at their use level. */
    private final Map<UUID, Integer> vanishRows = new HashMap<>();

    private boolean proxyCarries = true;
    private boolean holdWarmups;
    private final List<Runnable[]> heldWarmups = new ArrayList<>();

    private final Backend lobby = new Backend(LOBBY);
    private final Backend survival = new Backend(SURVIVAL);
    private final Backend creative = new Backend(CREATIVE);

    CrossServerTeleportTest() {
        join(lobby, ALEX, ALEX_AT);
        join(survival, SAM, SAM_AT_ACCEPT);
        join(creative, MOD, MOD_AT);
    }

    // The scenarios.

    @Test
    void anAcceptedTpaToAPlayerOnAnotherServerLandsTheRequesterOnceWhereTheTargetStandsAtLanding() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent");
        assertThat(keys(SAM)).containsExactly("teleport.tpa.received");
        assertThat(survival.registry.pendingFor(SAM)).hasSize(1);

        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        positions.put(SAM.uuid(), SAM_LATER);
        bus.deliver();
        scheduler.advance(SETTLE);
        scheduler.advance(HOP_TTL);

        assertThat(moves).containsExactly("Alex@survival->250,70,-40 REQUEST");
        assertThat(keys(ALEX))
                .containsExactly(
                        "teleport.tpa.sent",
                        "teleport.tpa.accepted",
                        "teleport.cross-server.sending",
                        "teleport.cross-server.arrived");
        assertThat(keys(SAM)).containsExactly("teleport.tpa.received");
        assertThat(hopTable.rows()).isEmpty();
        assertThat(vetoAsked).containsExactly(TeleportKind.REQUEST);
    }

    @Test
    void aRequestTheOtherWayRoundIsAcceptedOnTheServerTheTargetIsOn() {
        survival.requests.request(SAM, "Alex", RequestDirection.TO_TARGET);
        bus.deliver();
        lobby.accept.accept(ALEX, ALEX_AT);
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Sam@lobby->-5,64,3 REQUEST");
        assertThat(keys(SAM))
                .containsExactly(
                        "teleport.tpa.sent",
                        "teleport.tpa.accepted",
                        "teleport.cross-server.sending",
                        "teleport.cross-server.arrived");
    }

    @Test
    void anAcceptedTpahereMovesTheTargetToTheRequester() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_REQUESTER);
        bus.deliver();
        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        positions.put(ALEX.uuid(), at(1, 2, 3));
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(keys(SAM)).first().isEqualTo("teleport.tpa.here-received");
        assertThat(moves).containsExactly("Sam@lobby->1,2,3 REQUEST");
        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.accepted");
        assertThat(keys(SAM))
                .containsExactly(
                        "teleport.tpa.here-received", "teleport.cross-server.sending", "teleport.cross-server.arrived");
    }

    @Test
    void aTargetWithAutoAcceptOnAcceptsARemoteRequestAtOnce() {
        survival.flags.auto.add(SAM.uuid());

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Alex@survival->10,64,10 REQUEST");
        assertThat(keys(ALEX)).contains("teleport.tpa.accepted").doesNotHaveDuplicates();
    }

    @Test
    void aDeniedRequestTellsTheRequesterOnceAndMovesNobody() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        survival.accept.deny(SAM);
        bus.deliver();
        scheduler.advance(TTL.plus(TTL));

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.denied-remote");
        assertThat(keys(SAM)).containsExactly("teleport.tpa.received", "teleport.tpa.denied");
        assertThat(moves).isEmpty();
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void aRequestNobodyAnswersExpiresOnceOnBothSides() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        scheduler.advance(TTL);
        survival.sweep();
        bus.deliver();
        scheduler.advance(TTL);
        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.expired");
        assertThat(keys(SAM))
                .containsExactly("teleport.tpa.received", "teleport.tpa.expired", "teleport.tpa.none-pending");
        assertThat(moves).isEmpty();
    }

    @Test
    void aTargetThatRefusesRequestsIsReportedOnceAndShownNothing() {
        survival.flags.off.add(SAM.uuid());

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.toggled-off");
        assertThat(keys(SAM)).isEmpty();
        assertThat(survival.registry.pendingFor(SAM)).isEmpty();
    }

    @Test
    void aTargetWhoBlockedTheRequesterIsReportedOnceAndShownNothing() {
        survival.flags.block(SAM, ALEX);

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.blocked");
        assertThat(keys(SAM)).isEmpty();
    }

    @Test
    void aVanishedTargetLooksOfflineToARequesterWhoMayNotSeeThem() {
        survival.vanished.put(SAM.uuid(), 1);

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.target-offline");
        assertThat(keys(SAM)).isEmpty();
        assertThat(survival.registry.pendingFor(SAM)).isEmpty();
    }

    @Test
    void aRequesterWhoMaySeeTheVanishedTargetReachesThem() {
        survival.vanished.put(SAM.uuid(), 1);
        lobby.seeLevels.put(ALEX.uuid(), 1);

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent");
        assertThat(keys(SAM)).containsExactly("teleport.tpa.received");
    }

    @Test
    void aNameNoServerHasIsOfflineAndSendsNothing() {
        lobby.requests.request(ALEX, "Nobody", RequestDirection.TO_TARGET);

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.target-offline");
        assertThat(bus.sent).isEmpty();
    }

    @Test
    void aRequestNoServerAnswersIsGivenUpAsOfflineAndALateDeliveryIsWithdrawn() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);

        scheduler.advance(CrossServerRequests.ANSWER_GRACE);
        bus.deliver();
        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.target-offline");
        assertThat(keys(SAM)).containsExactly("teleport.tpa.received", "teleport.tpa.none-pending");
        assertThat(moves).isEmpty();
    }

    @Test
    void theRequesterCancelsARemoteRequestAndItLeavesTheTargetsList() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        lobby.accept.cancel(ALEX);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.cancelled");
        assertThat(survival.registry.pendingFor(SAM)).isEmpty();
    }

    @Test
    void aJailedRequesterIsRefusedBeforeAnythingIsSent() {
        jailed.add(ALEX.uuid());

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);

        assertThat(keys(ALEX)).containsExactly("teleport.jailed");
        assertThat(bus.sent).isEmpty();
    }

    // Every way an accepted request can fail to land ends it once.

    @Test
    void aTargetWhoLeftBeforeTheLandingEndsTheHopOnceAsFailed() {
        acceptAlexToSam();
        leave(survival, SAM);

        scheduler.advance(SETTLE);
        scheduler.advance(HOP_TTL);

        assertThat(moves).isEmpty();
        assertThat(keys(ALEX)).endsWith("teleport.cross-server.sending", "teleport.cross-server.failed");
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void aHopTheProxyNeverCarriesTimesOutOnce() {
        proxyCarries = false;
        acceptAlexToSam();

        scheduler.advance(HOP_TTL);
        scheduler.advance(HOP_TTL);

        assertThat(moves).isEmpty();
        assertThat(keys(ALEX)).endsWith("teleport.cross-server.sending", "teleport.cross-server.timed-out");
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void aMoverJailedBeforeTheLandingIsRefusedThereAndTheHopEndsOnce() {
        acceptAlexToSam();
        jailed.add(ALEX.uuid());

        scheduler.advance(SETTLE);
        scheduler.advance(HOP_TTL);

        assertThat(moves).isEmpty();
        assertThat(keys(ALEX))
                .endsWith("teleport.cross-server.sending", "teleport.jailed", "teleport.cross-server.failed");
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void aMoverWhoLeftDuringTheWarmupIsNeverSent() {
        holdWarmups = true;
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();
        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();
        leave(lobby, ALEX);

        heldWarmups.get(0)[0].run();

        assertThat(hopTable.rows()).isEmpty();
        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.accepted");
    }

    @Test
    void aWarmupCancelledByMovingSendsNobody() {
        holdWarmups = true;
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();
        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();

        heldWarmups.get(0)[1].run();

        assertThat(hopTable.rows()).isEmpty();
        assertThat(keys(ALEX)).endsWith("teleport.warmup.cancelled");
    }

    @Test
    void aMoverOnCooldownIsStillMovedSinceAnAcceptedRequestNeverChecksTheCooldown() {
        lobby.engine.arriveImmediately(ALEX, Destination.at(ALEX_AT), TeleportKind.REQUEST, () -> {}, () -> {});
        moves.clear();
        acceptAlexToSam();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Alex@survival->10,64,10 REQUEST");
        assertThat(keys(ALEX)).doesNotContain("teleport.cooldown.active");
    }

    // Either player may switch servers between the request and the move.

    @Test
    void aTpahereRequesterWhoSwitchedServersBeforeTheAcceptIsJoinedWhereTheyAreNow() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_REQUESTER);
        bus.deliver();
        join(creative, ALEX, at(4, 5, 6));

        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Sam@creative->4,5,6 REQUEST");
        assertThat(keys(SAM)).endsWith("teleport.cross-server.sending", "teleport.cross-server.arrived");
    }

    @Test
    void aTpaTargetWhoSwitchedServersDuringTheWarmupIsFollowedThere() {
        holdWarmups = true;
        acceptAlexToSam();
        join(creative, SAM, at(8, 9, 10));

        heldWarmups.get(0)[0].run();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Alex@creative->8,9,10 REQUEST");
    }

    @Test
    void aTpaTargetWhoCameToTheMoversServerIsReachedWithoutAHop() {
        holdWarmups = true;
        acceptAlexToSam();
        join(lobby, SAM, at(1, 1, 1));

        heldWarmups.get(0)[0].run();

        assertThat(moves).containsExactly("Alex@lobby->1,1,1 REQUEST");
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void aTpaTargetWhoLeftDuringTheWarmupLeavesTheMoverWhereTheyAreWithALine() {
        holdWarmups = true;
        acceptAlexToSam();
        leave(survival, SAM);

        heldWarmups.get(0)[0].run();

        assertThat(moves).isEmpty();
        assertThat(hopTable.rows()).isEmpty();
        assertThat(keys(ALEX)).endsWith("teleport.tpa.accepted", "teleport.cross-server.failed");
    }

    @Test
    void aTpahereCancelledWhileTheAcceptWasOnItsWayMovesNobody() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_REQUESTER);
        bus.deliver();

        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        lobby.accept.cancel(ALEX);
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(moves).isEmpty();
        assertThat(hopTable.rows()).isEmpty();
        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.cancelled");
        assertThat(keys(SAM)).containsExactly("teleport.tpa.here-received", "teleport.cross-server.failed");
    }

    @Test
    void aWithdrawalThatRacesTheAcceptNeverRunsTheLocalAcceptForARemoteRequest() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();
        lobby.accept.cancel(ALEX);
        // The withdrawal lands between the accept's read of the request and its outcome.
        survival.registry.afterNextPending = bus::deliver;

        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();

        assertThat(keys(ALEX)).containsExactly("teleport.tpa.sent", "teleport.tpa.cancelled");
        assertThat(moves).isEmpty();
        assertThat(heldWarmups).isEmpty();
    }

    @Test
    void aNewRequestReplacingOneToAPlayerWhoSwitchedServersWithdrawsTheOldOne() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();
        join(creative, SAM, at(2, 2, 2));

        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();

        assertThat(survival.registry.pendingFor(SAM)).isEmpty();
        assertThat(creative.registry.pendingFor(SAM)).hasSize(1);
    }

    // Staff teleports.

    @Test
    void staffTeleportToAPlayerOnAnotherServerLandsAtTheirLivePositionAsAStaffTeleport() {
        creative.staff.goTo(MOD, "Sam");
        positions.put(SAM.uuid(), SAM_LATER);
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Mod@survival->250,70,-40 ADMIN");
        // A local staff /tp asks no veto and stamps no cooldown; neither does this one.
        assertThat(vetoAsked).isEmpty();
        assertThat(cooldowns.checkLabel(MOD, "tp").isOk()).isTrue();
        assertThat(keys(MOD)).containsExactly("teleport.cross-server.sending", "teleport.cross-server.arrived");
    }

    @Test
    void staffMoveAJailedPlayerAcrossServersAsALocalStaffTeleportWould() {
        jailed.add(SAM.uuid());

        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.here("Mod"));
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Sam@creative->0,100,0 ADMIN");
        assertThat(keys(SAM)).doesNotContain("teleport.jailed");
        assertThat(keys(MOD)).containsExactly("teleport.tp.cross-server.sent");
    }

    @Test
    void aNameTheSelectorFoundNobodyForHereIsNotTakenForAHiddenPlayerHere() {
        PlayerRef console = PlayerRef.system("Console");

        lobby.staff.move(console, Named.elsewhere("Alex"), Named.elsewhere("Sam"));

        assertThat(keys(console)).containsExactly("teleport.tpa.target-offline");
        assertThat(bus.sent).isEmpty();
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void staffTeleportOfAPlayerOnAnotherServerToAThirdServerSendsThemAndReportsOnce() {
        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.elsewhere("Alex"));
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Sam@lobby->-5,64,3 ADMIN");
        assertThat(keys(MOD)).containsExactly("teleport.tp.cross-server.sent");
        assertThat(notifier.sent().stream()
                        .filter(line -> line.viewer().equals(MOD))
                        .findFirst()
                        .orElseThrow()
                        .placeholders())
                .containsEntry("server", LOBBY)
                .containsEntry("player", "Sam")
                .containsEntry("target", "Alex");
        assertThat(keys(SAM)).containsExactly("teleport.cross-server.sending", "teleport.cross-server.arrived");
    }

    @Test
    void staffTeleportOfAPlayerToSomeoneOnTheirOwnServerMovesThemThereAndReportsDone() {
        join(survival, ALEX, at(7, 7, 7));

        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.elsewhere("Alex"));
        bus.deliver();

        assertThat(moves).containsExactly("Sam@survival->7,7,7 ADMIN");
        assertThat(keys(MOD)).containsExactly("teleport.tp.done");
        assertThat(hopTable.rows()).isEmpty();
    }

    @Test
    void staffTeleportOfASubjectWhoseServerAnswersLateReadsNoAnswerThenWhatHappened() {
        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.elsewhere("Alex"));
        scheduler.advance(CrossServerRequests.ANSWER_GRACE);

        assertThat(keys(MOD)).containsExactly("teleport.tp.cross-server.no-answer");

        bus.deliver();

        assertThat(keys(MOD)).containsExactly("teleport.tp.cross-server.no-answer", "teleport.tp.cross-server.sent");
    }

    // Vanish holds for staff teleports across servers.

    @Test
    void staffGoingToAVanishedPlayerElsewhereReadsOfflineAndNobodyIsSent() {
        vanish(survival, SAM);

        creative.staff.goTo(MOD, "Sam");

        assertThat(keys(MOD)).containsExactly("teleport.tpa.target-offline");
        assertThat(hopTable.rows()).isEmpty();
        assertThat(moves).isEmpty();
    }

    @Test
    void staffWhoMaySeeTheVanishedPlayerReachThem() {
        vanish(survival, SAM);
        creative.seeLevels.put(MOD.uuid(), 1);

        creative.staff.goTo(MOD, "Sam");
        scheduler.advance(SETTLE);

        assertThat(moves).containsExactly("Mod@survival->10,64,10 ADMIN");
    }

    @Test
    void aVanishedSubjectOnAnotherServerReadsOfflineAndIsNotMoved() {
        vanish(survival, SAM);

        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.elsewhere("Alex"));
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(keys(MOD)).containsExactly("teleport.tpa.target-offline");
        assertThat(notifier.sent().get(0).placeholders()).containsEntry("player", "Sam");
        assertThat(moves).isEmpty();
    }

    @Test
    void aVanishedDestinationOnTheSubjectsServerReadsOfflineAndNobodyIsMoved() {
        join(survival, ALEX, at(7, 7, 7));
        vanish(survival, ALEX);

        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.elsewhere("Alex"));
        bus.deliver();

        assertThat(keys(MOD)).containsExactly("teleport.tpa.target-offline");
        assertThat(notifier.sent().get(0).placeholders()).containsEntry("player", "Alex");
        assertThat(moves).isEmpty();
    }

    @Test
    void aVanishedDestinationOnAThirdServerReadsOfflineAndTheSubjectIsNotSent() {
        vanish(lobby, ALEX);

        creative.staff.move(MOD, Named.elsewhere("Sam"), Named.elsewhere("Alex"));
        bus.deliver();
        scheduler.advance(SETTLE);

        assertThat(keys(MOD)).containsExactly("teleport.tpa.target-offline");
        assertThat(keys(SAM)).isEmpty();
        assertThat(hopTable.rows()).isEmpty();
        assertThat(moves).isEmpty();
    }

    @Test
    void aDestinationWhoVanishedBeforeTheirRowSaidSoIsRefusedAtTheLanding() {
        // The destination's own backend already hides them; the shared row has not caught up.
        survival.vanished.put(SAM.uuid(), 1);

        creative.staff.goTo(MOD, "Sam");
        scheduler.advance(SETTLE);

        assertThat(moves).isEmpty();
        assertThat(keys(MOD)).containsExactly("teleport.cross-server.sending", "teleport.cross-server.failed");
        assertThat(hopTable.rows()).isEmpty();
    }

    // The scaffolding.

    /** {@code player} vanishes at level 1 on {@code backend}, which writes the shared row too. */
    private void vanish(Backend backend, PlayerRef player) {
        backend.vanished.put(player.uuid(), 1);
        vanishRows.put(player.uuid(), 1);
    }

    private void acceptAlexToSam() {
        lobby.requests.request(ALEX, "Sam", RequestDirection.TO_TARGET);
        bus.deliver();
        survival.accept.accept(SAM, SAM_AT_ACCEPT);
        bus.deliver();
    }

    private List<String> keys(PlayerRef viewer) {
        return notifier.sent().stream()
                .filter(line -> line.viewer().equals(viewer))
                .map(RecordingNotifier.Sent::key)
                .toList();
    }

    private void join(Backend backend, PlayerRef player, Position where) {
        for (Backend each : List.of(lobby, survival, creative)) {
            each.here.remove(player.uuid());
        }
        backend.here.add(player.uuid());
        roster.put(player.uuid(), new OnlinePlayer(player, backend.name));
        positions.put(player.uuid(), where);
        scheduler.online(player);
    }

    private void leave(Backend backend, PlayerRef player) {
        backend.here.remove(player.uuid());
        roster.remove(player.uuid());
        scheduler.offline(player);
    }

    private Backend backend(String name) {
        return List.of(lobby, survival, creative).stream()
                .filter(each -> each.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** One backend: its players, its teleport use cases and its half of the shared hop. */
    private final class Backend {

        final String name;
        final Set<UUID> here = new HashSet<>();
        final Map<UUID, Integer> vanished = new HashMap<>();
        final Map<UUID, Integer> seeLevels = new HashMap<>();
        final Flags flags = new Flags();
        final Registry registry = new Registry();
        final TeleportEngine engine;
        final AcceptTeleport accept;
        final CrossServerRequests requests;
        final CrossServerStaffTeleport staff;
        final HopArrival arrival;

        Backend(String name) {
            this.name = name;
            TeleportSettings settings = new TeleportSettings(new Config());
            PlayerLookup players = new Lookup(here);
            TeleportExecutor executor = new Executor(name, here);
            engine = new TeleportEngine(
                    cooldowns,
                    new HeldWarmups(),
                    executor,
                    notifier.notifier(),
                    new RecordingEvents(),
                    settings,
                    who -> jailed.contains(who.uuid()),
                    who -> false,
                    TeleportFee.FREE,
                    com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace.NONE,
                    proposal -> {
                        if (proposal instanceof PlayerTeleporting teleporting) {
                            vetoAsked.add(teleporting.kind());
                        }
                        return true;
                    },
                    log);
            Visibility visibility = new Visibility();
            HopResolvers resolvers = new HopResolvers();
            for (HopKind kind : List.of(HopKind.PLAYER_REQUEST, HopKind.PLAYER_STAFF)) {
                resolvers.register(
                        new PlayerHopResolver(kind, engine, executor, visibility, players, this::locate, scheduler));
            }
            HopCharges charges = new HopCharges(notifier.notifier(), log);
            HopWatchdog watchdog = new HopWatchdog(hopTable, resolvers, charges, scheduler, log, HOP_TTL, clock);
            arrival = new HopArrival(hopTable, resolvers, charges, scheduler, log, name, SETTLE, HOP_TTL, clock);
            PlayerHops hops = new PlayerHops(Optional.of(new HopDepartures(
                    new TeleportHopGate(engine), new Proxy(), watchdog, charges, resolvers, scheduler, name, clock)));
            RequestTeleport requestTeleport = new RequestTeleport(
                    registry,
                    flags,
                    notifier.notifier(),
                    new RecordingEvents(),
                    settings,
                    who -> jailed.contains(who.uuid()),
                    who -> false,
                    clock);
            Net network = new Net(name);
            AcceptTeleport[] holder = new AcceptTeleport[1];
            requests = new CrossServerRequests(
                    network,
                    new Presence(name),
                    players,
                    this::locate,
                    registry,
                    requestTeleport,
                    visibility,
                    hops,
                    engine,
                    notifier.notifier(),
                    new RecordingEvents(),
                    settings,
                    scheduler,
                    target -> {
                        if (flags.autoAccepts(target)) {
                            locate(target).ifPresent(standing -> holder[0].accept(target, standing));
                        }
                    },
                    log);
            accept = new AcceptTeleport(registry, engine, notifier.notifier(), new RecordingEvents(), clock, requests);
            holder[0] = accept;
            staff = new CrossServerStaffTeleport(
                    network,
                    new Presence(name),
                    players,
                    this::locate,
                    visibility,
                    hops,
                    executor,
                    notifier.notifier(),
                    scheduler,
                    log);
            network.subscribe(message -> {
                requests.onMessage(message);
                staff.onMessage(message);
            });
        }

        Optional<Position> locate(PlayerRef who) {
            return here.contains(who.uuid()) ? Optional.ofNullable(positions.get(who.uuid())) : Optional.empty();
        }

        /** What the adapter's expiry sweep does each second. */
        void sweep() {
            registry.all().forEach(accept::expireIfDue);
        }

        final class Visibility implements RequestVisibility {

            @Override
            public int seeLevel(PlayerRef viewer) {
                return viewer.isSystem() ? SEES_EVERYONE : seeLevels.getOrDefault(viewer.uuid(), 0);
            }

            @Override
            public boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel) {
                Integer level = vanished.get(target.uuid());
                return level != null && viewerSeeLevel < level;
            }

            @Override
            public boolean hiddenElsewhere(PlayerRef target, int viewerSeeLevel) {
                Integer level = vanishRows.get(target.uuid());
                return level != null && viewerSeeLevel < level;
            }
        }
    }

    /** The proxy: carries a player to the server named and lets that backend's join handler see them arrive. */
    private final class Proxy implements HopConnector {

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void connect(PlayerRef player, String server) {
            if (!proxyCarries) {
                return;
            }
            Backend target = backend(server);
            join(target, player, positions.getOrDefault(player.uuid(), at(0, 0, 0)));
            target.arrival.onArrival(player);
        }
    }

    /** The bus: every frame reaches every other backend twice, in the order sent, when the test delivers. */
    private static final class Bus {

        private record Frame(String from, RemoteTeleport message) {}

        final List<RemoteTeleport> sent = new ArrayList<>();
        private final List<Frame> queue = new ArrayList<>();
        private final Map<String, Consumer<RemoteTeleport>> handlers = new HashMap<>();

        void publish(String from, RemoteTeleport message) {
            sent.add(message);
            queue.add(new Frame(from, message));
        }

        void deliver() {
            while (!queue.isEmpty()) {
                Frame next = queue.remove(0);
                handlers.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .filter(entry -> !entry.getKey().equals(next.from()))
                        .forEach(entry -> {
                            entry.getValue().accept(next.message());
                            entry.getValue().accept(next.message());
                        });
            }
        }
    }

    private final class Net implements TeleportNetwork {

        private final String server;

        Net(String server) {
            this.server = server;
        }

        @Override
        public String localServer() {
            return server;
        }

        @Override
        public void send(RemoteTeleport message) {
            bus.publish(server, message);
        }

        @Override
        public void subscribe(Consumer<RemoteTeleport> handler) {
            bus.handlers.put(server, handler);
        }
    }

    private final class Presence implements NetworkPresence {

        private final String server;

        Presence(String server) {
            this.server = server;
        }

        @Override
        public String localServer() {
            return server;
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.ofNullable(roster.get(player));
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return roster.values().stream()
                    .filter(online -> online.player().name().equalsIgnoreCase(name))
                    .min(Comparator.comparing(online -> !online.server().equals(server)));
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return roster.values().stream()
                    .filter(online -> online.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.copyOf(roster.values());
        }

        @Override
        public boolean isLive(String server) {
            return true;
        }
    }

    private static final class Lookup implements PlayerLookup {

        private final Set<UUID> here;

        Lookup(Set<UUID> here) {
            this.here = here;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return here.contains(uuid);
        }
    }

    /** Moves only a player who is on its backend, and records where each landed and as what. */
    private final class Executor implements TeleportExecutor {

        private final String server;
        private final Set<UUID> here;

        Executor(String server, Set<UUID> here) {
            this.server = server;
            this.here = here;
        }

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            teleport(who, destination, kind, () -> {}, () -> {});
        }

        @Override
        public void teleport(
                PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
            if (!here.contains(who.uuid())) {
                onFailed.run();
                return;
            }
            Position to = destination.position();
            moves.add(who.name() + "@" + server + "->" + (int) to.x() + "," + (int) to.y() + "," + (int) to.z() + " "
                    + kind);
            positions.put(who.uuid(), to);
            onLanded.run();
        }
    }

    /** Warmups that count down at once, or wait for the test while it holds them. */
    private final class HeldWarmups implements Warmups {

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            return begin(who, kind, onComplete, (CancelCause cause) -> onCancel.run(), onCancel);
        }

        @Override
        public WarmupHandle begin(
                PlayerRef who,
                WarmupKind kind,
                Runnable onComplete,
                Consumer<CancelCause> onCancel,
                Runnable onSuperseded) {
            if (holdWarmups) {
                // The held cancel is a step off the origin block.
                heldWarmups.add(new Runnable[] {onComplete, () -> onCancel.accept(CancelCause.MOVED)});
                return new RefusedWarmup();
            }
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class Flags implements TeleportFlags {

        final Set<UUID> off = new HashSet<>();
        final Set<UUID> auto = new HashSet<>();
        private final Map<UUID, Set<UUID>> blocks = new HashMap<>();

        @Override
        public boolean acceptsRequests(PlayerRef who) {
            return !off.contains(who.uuid());
        }

        @Override
        public boolean toggleRequests(PlayerRef who) {
            return off.remove(who.uuid()) || !off.add(who.uuid());
        }

        @Override
        public void setAcceptsRequests(PlayerRef who, boolean accepting) {
            if (accepting) {
                off.remove(who.uuid());
            } else {
                off.add(who.uuid());
            }
        }

        @Override
        public boolean autoAccepts(PlayerRef who) {
            return auto.contains(who.uuid());
        }

        @Override
        public boolean toggleAutoAccepts(PlayerRef who) {
            return auto.remove(who.uuid()) ? false : auto.add(who.uuid());
        }

        @Override
        public boolean hasBlocked(PlayerRef target, PlayerRef requester) {
            return blocks.getOrDefault(target.uuid(), Set.of()).contains(requester.uuid());
        }

        @Override
        public void block(PlayerRef blocker, PlayerRef requester) {
            blocks.computeIfAbsent(blocker.uuid(), id -> new HashSet<>()).add(requester.uuid());
        }

        @Override
        public void unblock(PlayerRef blocker, PlayerRef requester) {
            blocks.getOrDefault(blocker.uuid(), new HashSet<>()).remove(requester.uuid());
        }
    }

    /** The registry in single-request-displace mode, the shipped default. */
    private static final class Registry implements RequestRegistry {

        private final Map<RequestId, TeleportRequest> byId = new LinkedHashMap<>();

        @Nullable Runnable afterNextPending;

        @Override
        public void store(TeleportRequest request) {
            byId.values().removeIf(earlier -> earlier.target().equals(request.target()));
            byId.put(request.id(), request);
        }

        @Override
        public Optional<TeleportRequest> byId(RequestId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<TeleportRequest> pendingFor(PlayerRef target) {
            List<TeleportRequest> pending = byId.values().stream()
                    .filter(request -> request.target().equals(target))
                    .sorted(Comparator.comparing(TeleportRequest::expiresAt))
                    .toList();
            Runnable racer = afterNextPending;
            afterNextPending = null;
            if (racer != null) {
                racer.run();
            }
            return pending;
        }

        @Override
        public Optional<TeleportRequest> outgoing(PlayerRef requester) {
            return byId.values().stream()
                    .filter(request -> request.requester().equals(requester))
                    .findFirst();
        }

        @Override
        public void remove(RequestId id) {
            byId.remove(id);
        }

        List<TeleportRequest> all() {
            return List.copyOf(byId.values());
        }
    }

    /** The shipped teleport settings: a 60 second request, a 5 second cooldown stamped on landing. */
    private static final class Config implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
