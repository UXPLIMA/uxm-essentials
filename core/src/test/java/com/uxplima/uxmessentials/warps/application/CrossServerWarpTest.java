package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportHopGate;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;
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
    private final Warps warps = new Warps();
    private final Purse purse = new Purse();
    private final Moves moves = new Moves();
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final ClockCooldowns cooldowns = new ClockCooldowns(clock);
    private final FakePermissions permissions = new FakePermissions().grant(ALEX, MARKET.useNode());
    private final List<String> connects = new ArrayList<>();
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final HopResolvers lobbyResolvers = resolversOn(LOBBY);
    private final HopWatchdog watchdog = new HopWatchdog(store, lobbyResolvers, charges, scheduler, log, TTL, clock);
    private final HopArrival survival = arrivalOn(SURVIVAL);
    private HopGate gate = HopGate.OPEN;
    private boolean proxyAvailable = true;
    private boolean tagged;

    CrossServerWarpTest() {
        warps.put(marketOn(SURVIVAL));
        scheduler.online(ALEX);
        purse.wallet = START;
    }

    private HopResolvers resolversOn(String server) {
        HopResolvers resolvers = new HopResolvers();
        resolvers.register(new WarpHopResolver(warps, moves, Optional.of(purse), server));
        return resolvers;
    }

    private HopArrival arrivalOn(String server) {
        return new HopArrival(store, resolversOn(server), charges, scheduler, log, server, SETTLE, TTL, clock);
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
                        Optional.of(new HopDepartures(
                                gate, proxy, watchdog, charges, lobbyResolvers, scheduler, LOBBY, clock)),
                        LOBBY)
                : WarpHops.local(LOBBY);
        return new UseWarp(
                warps,
                new WarpAccess(permissions, Optional.of(purse)),
                moves,
                notifier.notifier(),
                position -> true,
                permissions,
                scheduler,
                hops);
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
        return new TeleportEngine(
                cooldowns,
                (who, kind, onComplete, onCancel) -> {
                    throw new UnsupportedOperationException("a cross-server hop never warms up");
                },
                (who, destination, kind) -> {},
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(new Defaults()),
                who -> false,
                who -> tagged);
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

        Result<Unit, WarpError> used = useMarket();

        assertThat(used.asError()).contains(WarpError.TELEPORT_REFUSED);
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

        Result<Unit, WarpError> again = useMarket();

        assertThat(again.asError()).contains(WarpError.TELEPORT_REFUSED);
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

    /** The warps table both backends share. */
    private static final class Warps implements WarpRepository {

        private final Map<String, Warp> byName = new HashMap<>();

        void put(Warp warp) {
            byName.put(warp.name().value(), warp);
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            put(warp);
        }

        @Override
        public void delete(WarpName name) {
            byName.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0;
        }
    }

    /** The player's wallet, with every debit and credit counted. */
    private static final class Purse implements WarpEconomy {

        private BigDecimal wallet = BigDecimal.ZERO;
        private int charges;
        private int refunds;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return wallet.compareTo(amount) >= 0;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (!canAfford(who, amount, currencyId)) {
                return false;
            }
            wallet = wallet.subtract(amount);
            charges++;
            return true;
        }

        @Override
        public boolean refund(PlayerRef who, BigDecimal amount, String currencyId) {
            wallet = wallet.add(amount);
            refunds++;
            return true;
        }
    }

    /**
     * Records every move, and lands every arrival unless told to refuse it. Given an engine, it arrives through that
     * engine instead, as the teleport adapter does.
     */
    private static final class Moves implements WarpTeleporter {

        private final List<String> made = new ArrayList<>();
        private boolean refuses;
        private @Nullable TeleportEngine engine;

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            made.add(who.name() + "->" + warp.name().value());
        }

        @Override
        public void arrive(PlayerRef who, Warp warp, Runnable onLanded, Runnable onMissed) {
            if (refuses) {
                onMissed.run();
                return;
            }
            Runnable landed = () -> {
                made.add(who.name() + "->" + warp.name().value());
                onLanded.run();
            };
            TeleportEngine through = engine;
            if (through == null) {
                landed.run();
                return;
            }
            Optional<Duration> cooldown =
                    warp.cooldownOverrideSeconds().map(seconds -> Duration.ofMillis((long) (seconds * 1000)));
            Destination destination = Destination.at(warp.location(), Optional.empty(), cooldown);
            if (through.arriveImmediately(who, destination, TeleportKind.WARP, landed, onMissed)
                    .isErr()) {
                onMissed.run();
            }
        }
    }

    /** Teleport settings at their defaults. */
    private static final class Defaults implements ConfigStore {

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
