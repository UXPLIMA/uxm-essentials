package com.uxplima.uxmessentials.shared.application.hop;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import org.junit.jupiter.api.Test;

/**
 * The shared cross-server hop with a kind other than player warps: a paid hop to a home whose fee is paid on to the
 * home's keeper once the player lands. Money is followed in the player's wallet and the keeper's account, so a
 * refund that leaves the keeper paid shows up as money created.
 */
class CrossServerHopTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final String BASE = "base";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final HopCharge FEE = new HopCharge(new BigDecimal("10"), "coins");
    private static final BigDecimal START = new BigDecimal("100");
    private static final Duration DRAIN = Duration.ofSeconds(5);

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final MemoryHopStore store = new MemoryHopStore();
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Ledger ledger = new Ledger();
    private final List<String> moved = new ArrayList<>();
    private final List<String> connects = new ArrayList<>();
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final HopResolvers lobbyResolvers = homesOn(LOBBY);
    private final HopWatchdog watchdog = new HopWatchdog(store, lobbyResolvers, charges, scheduler, log, TTL, clock);
    private final HopArrival survival = arrivalOn(SURVIVAL, homesOn(SURVIVAL));
    private Landing landing = Landing.LAND;
    private Runnable heldLanding = () -> {};
    private Runnable heldMiss = () -> {};
    private boolean gateRefuses;
    private boolean proxyAvailable = true;

    private final HopDepartures lobby = new HopDepartures(
            (who, departure) -> !gateRefuses,
            new HopConnector() {
                @Override
                public boolean isAvailable() {
                    return proxyAvailable;
                }

                @Override
                public void connect(PlayerRef player, String server) {
                    connects.add(player.name() + "->" + server);
                }
            },
            watchdog,
            charges,
            lobbyResolvers,
            scheduler,
            LOBBY,
            clock);

    CrossServerHopTest() {
        scheduler.online(ALEX);
        ledger.wallet = START;
    }

    private HopResolvers homesOn(String server) {
        HopResolvers resolvers = new HopResolvers();
        resolvers.register(new Homes(server));
        return resolvers;
    }

    private HopArrival arrivalOn(String server, HopResolvers resolvers) {
        return new HopArrival(store, resolvers, charges, scheduler, log, server, SETTLE, TTL, clock);
    }

    private static HopDeparture toBase() {
        return new HopDeparture(HopKind.HOME, BASE, SURVIVAL, BASE, at(0, 64, 0), Optional.empty());
    }

    /** What a homes use case does with a home on another backend: ask the gate, charge, then send. */
    private boolean goHome() {
        HopDeparture departure = toBase();
        if (!lobby.admit(ALEX, departure)) {
            return false;
        }
        ledger.wallet = ledger.wallet.subtract(FEE.amount());
        lobby.send(ALEX, departure, Optional.of(FEE), Optional.of(new HopSettlement("keeper")));
        return true;
    }

    private void join(HopArrival arrival) {
        arrival.onArrival(ALEX);
        scheduler.advance(SETTLE);
    }

    @Test
    void aHopThatLandsMovesThePlayerOnceAndSettlesTheFeeOnce() {
        assertThat(goHome()).isTrue();
        assertThat(connects).containsExactly("Alex->survival");
        assertThat(ledger.keeper).isEqualByComparingTo("0");

        // Two joins that both read the row before either claims it.
        store.afterNextFind(() -> survival.onArrival(ALEX));
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moved).containsExactly("Alex->base");
        assertThat(ledger.wallet).isEqualByComparingTo("90");
        assertThat(ledger.keeper).isEqualByComparingTo("10");
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.arrived");
        assertThat(store.rows()).isEmpty();
    }

    @Test
    void aHopThatMissesIsRefundedOnceAndSettlesNothing() {
        landing = Landing.MISS_THEN_THROW;
        goHome();

        join(survival);
        scheduler.advance(TTL);

        assertThat(moved).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.keeper).isEqualByComparingTo("0");
        assertThat(ledger.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.failed", "home.hop.refunded");
    }

    @Test
    void aHopClaimedButNotMovedWhenTheServerStopsIsRefundedOnce() {
        landing = Landing.DROPPED;
        goHome();
        join(survival);
        assertThat(store.rows())
                .as("claimed, so no other backend can refund it")
                .isEmpty();

        survival.stop(DRAIN);
        survival.stop(DRAIN);
        scheduler.advance(TTL);

        assertThat(moved).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.keeper).isEqualByComparingTo("0");
        assertThat(ledger.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.refunded");
    }

    @Test
    void aHopThatLandedBeforeTheStopKeepsItsSettlement() {
        goHome();
        join(survival);

        survival.stop(DRAIN);

        assertThat(moved).containsExactly("Alex->base");
        assertThat(ledger.refunds).isZero();
        assertThat(ledger.keeper).isEqualByComparingTo("10");
    }

    @Test
    void aStoppedBackendClaimsNothingAndLeavesTheRowForTheWatchdog() {
        goHome();
        survival.onArrival(ALEX);

        survival.stop(DRAIN);
        scheduler.advance(SETTLE);
        assertThat(store.rows()).containsKey(ALEX.uuid());
        scheduler.advance(TTL);

        assertThat(moved).isEmpty();
        assertThat(ledger.refunds).isOne();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
    }

    @Test
    void aStopThatBeginsRightAfterTheClaimIsRefundedOnceAndNeverMoves() {
        goHome();
        // The stop lands between the row's delete and the hop being held: it must not wait on the claiming join,
        // which runs on this thread here, so it is given no time to wait at all.
        store.afterNextClear(() -> survival.stop(Duration.ZERO));

        join(survival);
        survival.stop(DRAIN);
        scheduler.advance(TTL);

        assertThat(moved).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.keeper).isEqualByComparingTo("0");
        assertThat(ledger.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.refunded");
    }

    @Test
    void aMoveThatFinishesAfterTheStopRefundedItPaysNothingMore() {
        landing = Landing.HELD;
        goHome();
        join(survival);

        survival.stop(DRAIN);
        assertThat(log.warnings()).anyMatch(line -> line.contains("refunded_after_move_started"));
        // The server finishes the move it had already started, then a late miss arrives as well.
        heldLanding.run();
        heldMiss.run();

        assertThat(moved).containsExactly("Alex->base");
        assertThat(ledger.refunds).isOne();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.keeper).isEqualByComparingTo("0");
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.refunded");
    }

    @Test
    void aMoveTaskThatRunsAfterTheStopRefundedItDoesNotMoveThePlayer() {
        goHome();
        scheduler.holdEntityWork();
        join(survival);

        survival.stop(DRAIN);
        scheduler.releaseEntityWork();

        assertThat(moved).isEmpty();
        assertThat(ledger.refunds).isOne();
        assertThat(ledger.keeper).isEqualByComparingTo("0");
        assertThat(log.warnings()).noneMatch(line -> line.contains("refunded_after_move_started"));
    }

    @Test
    void aStopWaitsForARefundAlreadyOnTheAsyncLane() {
        goHome();
        survival.onArrival(ALEX);
        // The player left before the move, so the refund is handed to an async lane that is slow to run it.
        scheduler.offline(ALEX);
        scheduler.asyncOn(task -> new Thread(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    task.run();
                })
                .start());
        scheduler.advance(SETTLE);
        assertThat(ledger.refunds).as("still on the slow lane").isZero();

        survival.stop(DRAIN);

        assertThat(ledger.refunds).isOne();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
    }

    @Test
    void aHopTheGateRefusesChargesNothingRecordsNothingAndConnectsNobody() {
        gateRefuses = true;

        assertThat(goHome()).isFalse();

        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void aHopNoBackendTookIsRefundedByTheWatchdogAtTheTtl() {
        goHome();

        scheduler.advance(TTL.minusSeconds(1));
        assertThat(ledger.wallet).isEqualByComparingTo("90");

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.keeper).isEqualByComparingTo("0");
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.timed-out", "home.hop.refunded");
    }

    @Test
    void aHopThatCannotLeaveIsRefundedAndNeverRecorded() {
        proxyAvailable = false;

        goHome();

        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.sent().getFirst().placeholders()).containsEntry("server", SURVIVAL);
        assertThat(notifier.keys()).containsExactly("home.hop.unavailable", "home.hop.refunded");
    }

    @Test
    void aPlayerTheProxyPutOnTheWrongBackendIsRefundedThereAndTheWatchdogFindsNothing() {
        goHome();

        arrivalOn("creative", homesOn("creative")).onArrival(ALEX);
        scheduler.advance(TTL);

        assertThat(moved).isEmpty();
        assertThat(ledger.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.misrouted", "home.hop.refunded");
    }

    @Test
    void aHomeThatIsGoneWhenThePlayerArrivesIsRefunded() {
        goHome();
        ledger.homes.remove(BASE);

        join(survival);

        assertThat(moved).isEmpty();
        assertThat(ledger.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("home.hop.sending", "home.hop.failed", "home.hop.refunded");
    }

    @Test
    void aBackendThatDoesNotKnowTheKindLeavesTheRowForTheOriginToRefund() {
        goHome();

        join(arrivalOn(SURVIVAL, new HopResolvers()));
        assertThat(store.rows()).containsKey(ALEX.uuid());

        scheduler.advance(TTL);
        assertThat(moved).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.refunds).isOne();
    }

    @Test
    void aSettlementTheAsyncLaneRefusesIsMadeInPlace() {
        goHome();
        survival.onArrival(ALEX);
        scheduler.rejectAsync(true);

        scheduler.advance(SETTLE);

        assertThat(moved).containsExactly("Alex->base");
        assertThat(ledger.keeper).isEqualByComparingTo("10");
    }

    @Test
    void aRefundTheAsyncLaneRefusesWhileThePlayerLeavesIsMadeInPlace() {
        goHome();
        survival.onArrival(ALEX);
        scheduler.offline(ALEX);
        scheduler.rejectAsync(true);

        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moved).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.refunds).isOne();
    }

    @Test
    void aSettlementThatBanksNothingGivesThePlayerTheFeeBack() {
        ledger.settles = false;
        goHome();

        join(survival);

        assertThat(moved).containsExactly("Alex->base");
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.keeper).isEqualByComparingTo("0");
    }

    @Test
    void aNewHopRefundsTheUnclaimedOneItReplaces() {
        goHome();
        scheduler.advance(Duration.ofSeconds(5));

        goHome();
        assertThat(ledger.wallet).isEqualByComparingTo("90");

        scheduler.advance(TTL);
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.refunds).isEqualTo(2);
    }

    @Test
    void twoHopsTrackedAtOnceRefundEachChargeExactlyOnce() {
        // The second hop starts while the first has read no earlier row and not yet written its own.
        store.afterNextFind(this::goHome);
        goHome();

        assertThat(connects).containsExactly("Alex->survival");
        assertThat(ledger.wallet).isEqualByComparingTo("90");
        // The first hop is still on its way, so the second is told so rather than that the server is down.
        assertThat(notifier.keys()).containsExactly("home.hop.busy", "home.hop.refunded", "home.hop.sending");
        // The refund landed before the player read any line, and the kind heard of it after both.
        assertThat(ledger.linesBeforeRefund).containsExactly(0);
        assertThat(ledger.failures).containsExactly("busy:refunded");

        scheduler.advance(TTL);
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.refunds).isEqualTo(2);
        assertThat(store.rows()).isEmpty();
    }

    @Test
    void aFreeHopRefusedForRacingIsToldWhy() {
        store.afterNextFind(() -> lobby.send(ALEX, toBase(), Optional.empty(), Optional.empty()));
        goHome();

        assertThat(connects).containsExactly("Alex->survival");
        assertThat(notifier.keys()).containsExactly("home.hop.busy", "home.hop.sending");
        assertThat(ledger.refunds).isZero();
        assertThat(ledger.failures).containsExactly("busy:unpaid");
    }

    @Test
    void aHopAfterTheRaceIsSentAndReplacesTheEarlierRowWithARefund() {
        store.afterNextFind(this::goHome);
        goHome();
        scheduler.advance(Duration.ofSeconds(5));

        goHome();

        assertThat(connects).containsExactly("Alex->survival", "Alex->survival");
        assertThat(ledger.wallet).isEqualByComparingTo("90");
        scheduler.advance(TTL);
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.refunds).isEqualTo(3);
    }

    @Test
    void aHopAfterATrackThatCouldNotBeWrittenIsSent() {
        store.failRecords(true);
        goHome();
        assertThat(connects).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);

        store.failRecords(false);
        goHome();

        assertThat(connects).containsExactly("Alex->survival");
        assertThat(store.rows()).containsKey(ALEX.uuid());
    }

    @Test
    void aStaleRowOfAKindNoResolverHereServesIsClaimedOnJoinAndLoggedWithItsCharge() {
        store.record(jailHop(clock.instant()));
        clock.advance(TTL.multipliedBy(2).plusSeconds(1));

        join(arrivalOn(SURVIVAL, new HopResolvers()));

        assertThat(store.rows()).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(ledger.refunds).isZero();
        assertThat(log.warnings())
                .anySatisfy(line -> assertThat(line)
                        .contains("cross_server_hop_abandoned")
                        .contains("JAIL")
                        .contains("10")
                        .contains("coins"));
    }

    @Test
    void aFreshRowOfAnUnknownKindRefusesANewHopAndRefundsIt() {
        store.record(jailHop(clock.instant()));

        goHome();

        assertThat(store.find(ALEX.uuid()).map(PendingHop::kind)).contains(HopKind.JAIL);
        assertThat(connects).isEmpty();
        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(notifier.keys()).containsExactly("home.hop.busy", "home.hop.refunded");
        assertThat(ledger.failures).containsExactly("busy:refunded");
    }

    @Test
    void aStaleRowOfAnUnknownKindNoLongerBlocksANewHop() {
        store.record(jailHop(clock.instant()));
        clock.advance(TTL.multipliedBy(2).plusSeconds(1));

        goHome();

        assertThat(store.find(ALEX.uuid()).map(PendingHop::kind)).contains(HopKind.HOME);
        assertThat(connects).containsExactly("Alex->survival");
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("cross_server_hop_abandoned"));
    }

    @Test
    void aKindWithNoResolverIsRefusedAtAdmissionBeforeAnythingIsCharged() {
        HopDeparture toNether =
                new HopDeparture(HopKind.WORLD, "nether", SURVIVAL, "nether", at(0, 64, 0), Optional.empty());

        assertThatThrownBy(() -> lobby.admit(ALEX, toNether)).isInstanceOf(IllegalStateException.class);

        assertThat(ledger.wallet).isEqualByComparingTo(START);
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    /** A charged hop of a kind neither backend in this test registers. */
    private static PendingHop jailHop(java.time.Instant at) {
        return new PendingHop(
                ALEX.uuid(), HopKind.JAIL, "cell-1", SURVIVAL, "prison", at, Optional.of(FEE), Optional.empty());
    }

    /** How the fake move ends. */
    private enum Landing {
        LAND,
        // Reports the miss and then throws as well, the worst case for refunding exactly once.
        MISS_THEN_THROW,
        // Its region task is dropped by a stopping server, so it never reports anything.
        DROPPED,
        // Started, and finished only when the test says so, as a move the server completes after the stop.
        HELD
    }

    /** The player's wallet, the keeper's account and the homes that exist, with where each lives. */
    private static final class Ledger {

        private final Map<String, String> homes = new HashMap<>(Map.of(BASE, SURVIVAL));
        private BigDecimal wallet = BigDecimal.ZERO;
        private BigDecimal keeper = BigDecimal.ZERO;
        private int refunds;
        private boolean settles = true;
        /** Each failure the kind heard of, with whether the refund had already landed when it was told. */
        private final List<String> failures = new ArrayList<>();
        /** How many lines the player had read when each refund landed. */
        private final List<Integer> linesBeforeRefund = new ArrayList<>();
    }

    /** A test kind: a home resolved by name, moved to by the fake landing, paid through the ledger. */
    private final class Homes implements HopResolver {

        private final String server;

        Homes(String server) {
            this.server = server;
        }

        @Override
        public HopKind kind() {
            return HopKind.HOME;
        }

        @Override
        public HopNotices notices() {
            Map<HopNotice, MessageKey> keys = new HashMap<>();
            for (HopNotice notice : HopNotice.values()) {
                String key = "home.hop."
                        + notice.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                keys.put(notice, () -> key);
            }
            return new HopNotices("home", keys);
        }

        @Override
        public String label(PendingHop hop) {
            return hop.target();
        }

        @Override
        public HopResolution resolve(PendingHop hop) {
            if (!server.equals(ledger.homes.get(hop.target()))) {
                return HopResolution.unreachable(hop.target());
            }
            return HopResolution.landsAt(hop.target(), (who, onLanded, onMissed) -> {
                if (landing == Landing.DROPPED) {
                    return;
                }
                if (landing == Landing.HELD) {
                    heldLanding = () -> {
                        moved.add(who.name() + "->" + hop.target());
                        onLanded.run();
                    };
                    heldMiss = onMissed;
                    return;
                }
                if (landing == Landing.MISS_THEN_THROW) {
                    onMissed.run();
                    throw new IllegalStateException("the move broke after reporting its miss");
                }
                moved.add(who.name() + "->" + hop.target());
                onLanded.run();
            });
        }

        @Override
        public boolean refund(PlayerRef who, HopCharge charge) {
            ledger.wallet = ledger.wallet.add(charge.amount());
            ledger.refunds++;
            ledger.linesBeforeRefund.add(notifier.keys().size());
            return true;
        }

        @Override
        public void failed(PlayerRef who, String label, HopNotice reason) {
            String paid = ledger.refunds > 0 ? "refunded" : "unpaid";
            ledger.failures.add(reason.name().toLowerCase(java.util.Locale.ROOT) + ":" + paid);
        }

        @Override
        public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
            if (!ledger.settles) {
                return false;
            }
            ledger.keeper = ledger.keeper.add(charge.amount());
            return true;
        }
    }
}
