package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportHopGate;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.HeldHops;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * A use of a server warp set on another backend, from the charge on the origin to the move on the target, and every
 * way the charge comes back when the player is not moved. Money is followed in the player's wallet, so a refund
 * paid twice or a charge kept for a move that never happened shows up as a wrong balance.
 */
class CrossServerWarpTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final WarpName MARKET = WarpName.of("market");
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final WarpCost PRICE = WarpCost.of(new BigDecimal("25.50"), "gold");
    private static final BigDecimal START = new BigDecimal("100");

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final MemoryHopStore store = new MemoryHopStore();
    private final List<String> storeCallsOnTheCallerThread = new ArrayList<>();
    private final Lanes lanes = new Lanes(scheduler);
    /** The target's thread hops, which a test can hold to run them one task at a time, as Folia does. */
    private final HeldHops targetLanes = new HeldHops(lanes);

    private final PendingHopStore shared = new Guarded(store);
    private final WarpFakes.Warps warps = new WarpFakes.Warps();
    private final WarpFakes.Purse purse = new WarpFakes.Purse();
    private final WarpFakes.Moves moves = new WarpFakes.Moves();
    private final WarpFakes.Online online = new WarpFakes.Online();
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final ClockCooldowns cooldowns = new ClockCooldowns(clock);
    private final FakePermissions permissions = new FakePermissions().grant(ALEX, WarpAccess.useNode(MARKET));
    private final List<String> connects = new ArrayList<>();
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final HopResolvers lobbyResolvers = resolversOn(LOBBY);
    private final HopWatchdog watchdog = new HopWatchdog(shared, lobbyResolvers, charges, lanes, log, TTL, clock);
    private final HopArrival survival = arrivalOn(SURVIVAL);
    private HopGate gate = HopGate.OPEN;
    private boolean proxyAvailable = true;
    private boolean tagged;
    private boolean unsafe;

    CrossServerWarpTest() {
        warps.put(marketOn(SURVIVAL));
        scheduler.online(ALEX);
        online.here.add(ALEX.uuid());
        purse.wallet = START;
    }

    private HopResolvers resolversOn(String server) {
        HopResolvers resolvers = new HopResolvers();
        resolvers.register(new WarpHopResolver(
                warps,
                moves,
                Optional.of(purse),
                position -> !unsafe,
                permissions,
                notifier.notifier(),
                targetLanes,
                server));
        return resolvers;
    }

    private HopArrival arrivalOn(String server) {
        return new HopArrival(shared, resolversOn(server), charges, lanes, log, server, SETTLE, TTL, clock);
    }

    /** {@code /warp} on the lobby, with the warps cross-server block on or off. */
    private UseWarp lobby(boolean routed) {
        HopConnector proxy = new HopConnector() {
            @Override
            public boolean isAvailable() {
                return proxyAvailable;
            }

            @Override
            public void connect(PlayerRef player, String server) {
                connects.add(player.name() + "->" + server);
            }
        };
        WarpHops hops = routed
                ? new WarpHops(
                        Optional.of(
                                new HopDepartures(gate, proxy, watchdog, charges, lobbyResolvers, lanes, LOBBY, clock)),
                        LOBBY)
                : WarpHops.local(LOBBY);
        return new UseWarp(
                warps,
                new WarpAccess(permissions, Optional.of(purse)),
                moves,
                notifier.notifier(),
                position -> !unsafe,
                permissions,
                lanes,
                online,
                hops,
                new PaidTeleports(lanes, log),
                log);
    }

    private Result<Unit, WarpError> useMarket() {
        return lobby(true).use(ALEX, MARKET);
    }

    private void join(HopArrival arrival) {
        arrival.onArrival(ALEX);
        scheduler.advance(SETTLE);
    }

    /** The teleport engine both backends run, sharing one cooldown store as a player's cooldowns follow them. */
    private TeleportEngine engine() {
        return WarpFakes.engine(cooldowns, notifier, () -> tagged);
    }

    private static Warp marketOn(String server) {
        return Warp.create(MARKET, at(0, 64, 0), SAM, NOW, PRICE, Optional.empty())
                .onServer(server);
    }

    @Test
    void aUseOfAWarpOnAnotherServerChargesOnceMovesThePlayerThereOnceAndKeepsTheCharge() {
        assertThat(useMarket().isOk()).isTrue();
        assertThat(connects).containsExactly("Alex->survival");
        assertThat(store.find(ALEX.uuid()).map(PendingHop::kind)).contains(HopKind.WARP);
        assertThat(purse.wallet).isEqualByComparingTo("74.50");

        // Two joins that both read the row before either claims it, then the origin's timer.
        store.afterNextFind(() -> survival.onArrival(ALEX));
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(purse.refunds).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys()).containsExactly("warp.cross-server.sending", "warp.cross-server.arrived");
    }

    @Test
    void aUseTheProxyNeverCarriedOutIsRefundedAtTheTtl() {
        useMarket();

        scheduler.advance(TTL);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly(
                        "warp.cross-server.sending", "warp.cross-server.timed-out", "warp.cross-server.refunded");
    }

    @Test
    void aUseThatCannotLeaveIsRefundedAndNeverRecorded() {
        proxyAvailable = false;

        useMarket();

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("warp.cross-server.unavailable", "warp.cross-server.refunded");
    }

    @Test
    void aUseThatCannotBeRecordedIsRefundedAndNotSent() {
        store.failRecords(true);

        useMarket();

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(connects).isEmpty();
    }

    @Test
    void aPlayerTheProxyPutOnTheWrongServerIsRefundedThereAndOnlyThere() {
        useMarket();

        arrivalOn("creative").onArrival(ALEX);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("warp.cross-server.misrouted");
    }

    @Test
    void aWarpDeletedBeforeThePlayerArrivesIsRefunded() {
        useMarket();
        warps.delete(MARKET);

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("warp.cross-server.failed", "warp.cross-server.refunded");
    }

    @Test
    void aWarpMovedToAnotherServerBeforeThePlayerArrivesIsRefunded() {
        useMarket();
        warps.put(marketOn("creative"));

        join(survival);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aLandingThatMissesIsRefundedOnce() {
        moves.refuses = true;
        useMarket();

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aPlayerWhoLeavesDuringTheSettlingDelayIsRefunded() {
        useMarket();
        survival.onArrival(ALEX);
        scheduler.offline(ALEX);

        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aCombatTaggedPlayerIsRefusedBeforeAnythingIsCharged() {
        tagged = true;
        gate = new TeleportHopGate(engine());

        useMarket();

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.charges).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged");
    }

    @Test
    void aLandedWarpStartsItsCooldownAndTheNextUseIsRefusedBeforeTheCharge() {
        warps.put(marketOn(SURVIVAL).withCooldownOverride(Optional.of(60.0)));
        TeleportEngine engine = engine();
        gate = new TeleportHopGate(engine);
        moves.engine = engine;
        useMarket();
        join(survival);
        assertThat(moves.made).containsExactly("Alex->market");

        useMarket();

        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(purse.charges).isOne();
        assertThat(store.rows()).isEmpty();
    }

    @Test
    void aLockedRemoteWarpIsRefusedBeforeTheTeleportRulesAndCostsNothing() {
        warps.put(marketOn(SURVIVAL).withLocked(true));
        gate = (who, departure) -> {
            throw new AssertionError("the teleport rules are asked only once the warp's own checks pass");
        };

        Result<Unit, WarpError> used = useMarket();

        assertThat(used.asError()).contains(WarpError.LOCKED);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(connects).isEmpty();
    }

    @Test
    void withTheCrossServerBlockOffAWarpOfAnotherServerIsUsedHereAsBefore() {
        Result<Unit, WarpError> used = lobby(false).use(ALEX, MARKET);

        assertThat(used.isOk()).isTrue();
        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void aWarpOfThisServerIsUsedHereWithTheBlockOn() {
        warps.put(marketOn(LOBBY));

        useMarket();

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void theRemoteUseTouchesTheSharedStoreOnlyFromTheAsyncLane() {
        useMarket();
        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(storeCallsOnTheCallerThread).isEmpty();
    }

    @Test
    void aRemoteUseTheAsyncLaneRefusesChargesNothingAndSaysTheWarpIsUnreachable() {
        scheduler.rejectAsync(true);

        Result<Unit, WarpError> used = useMarket();

        assertThat(used.asError()).contains(WarpError.TELEPORT_REFUSED);
        assertThat(purse.charges).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("warp.cross-server.unavailable");
    }

    @Test
    void anUnsafeDestinationIsRefusedOnLandingAndRefundedOnce() {
        useMarket();
        unsafe = true;

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys())
                .containsExactly(
                        "warp.cross-server.sending",
                        "warp.unsafe",
                        "warp.cross-server.failed",
                        "warp.cross-server.refunded");
    }

    @Test
    void aPlayerWhoMayBypassTheSafetyCheckLandsOnAnUnsafeDestination() {
        permissions.grant(ALEX, UseWarp.BYPASS_SAFETY);
        unsafe = true;
        useMarket();

        join(survival);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aLandingCountsAsAVisit() {
        useMarket();

        join(survival);

        assertThat(warps.visitors(MARKET)).isOne();
    }

    @Test
    void aStaffSendTheTeleportRulesRefuseTellsTheStaffMember() {
        tagged = true;
        gate = new TeleportHopGate(engine());

        lobby(true).useFor(SAM, ALEX, MARKET);

        assertThat(purse.charges).isZero();
        assertThat(notifier.sent()).anySatisfy(line -> {
            assertThat(line.viewer()).isEqualTo(SAM);
            assertThat(line.key()).isEqualTo("warp.sent-refused");
        });
    }

    @Test
    void aMoveThatThrowsOnThePlayersOwnThreadIsRefundedOnceAndStillReported() {
        targetLanes.hold();
        moves.throwsOnArrive = true;
        useMarket();

        join(survival);
        targetLanes.runAll();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(targetLanes.thrown())
                .singleElement()
                .satisfies(failure -> assertThat(failure).hasMessage("the jail lookup failed"));
    }

    @Test
    void aLandingInAWorldThatIsNotLoadedHereIsRefundedOnce() {
        useMarket();
        scheduler.unload(WORLD);

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly("warp.cross-server.sending", "warp.cross-server.failed", "warp.cross-server.refunded");
    }

    @Test
    void aPlayerWhoLeftBeforeTheHopTaskRanIsNeitherChargedNorSent() {
        online.here.remove(ALEX.uuid());

        useMarket();

        assertThat(purse.charges).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    /** Runs every task like the scheduler it wraps, and knows whether the task running now is on the async lane. */
    private static final class Lanes implements Scheduler {

        private final SteppedScheduler delegate;
        private int asyncDepth;

        Lanes(SteppedScheduler delegate) {
            this.delegate = delegate;
        }

        boolean offTick() {
            return asyncDepth > 0;
        }

        @Override
        public void onGlobal(Runnable task) {
            delegate.onGlobal(task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            delegate.onRegion(position, task);
        }

        @Override
        public void onRegion(Position position, Runnable task, Runnable retired) {
            delegate.onRegion(position, task, retired);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            delegate.onEntity(player, task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            delegate.onEntity(player, task, retired);
        }

        @Override
        public void async(Runnable task) {
            delegate.async(() -> offTick(task));
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delegate.asyncAfter(delay, () -> offTick(task));
        }

        private void offTick(Runnable task) {
            asyncDepth++;
            try {
                task.run();
            } finally {
                asyncDepth--;
            }
        }
    }

    /** The shared store, noting every call made from outside the async lane. */
    private final class Guarded implements PendingHopStore {

        private final PendingHopStore delegate;

        Guarded(PendingHopStore delegate) {
            this.delegate = delegate;
        }

        private void check(String call) {
            if (!lanes.offTick()) {
                storeCallsOnTheCallerThread.add(call);
            }
        }

        @Override
        public void record(PendingHop pending) {
            check("record");
            delegate.record(pending);
        }

        @Override
        public Optional<PendingHop> find(UUID player) {
            check("find");
            return delegate.find(player);
        }

        @Override
        public int clear(PendingHop pending) {
            check("clear");
            return delegate.clear(pending);
        }
    }
}
