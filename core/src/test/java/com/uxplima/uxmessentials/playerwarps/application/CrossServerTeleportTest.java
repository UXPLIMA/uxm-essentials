package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A cross-server player-warp use from the charge on the origin to the hop on the target, and every way the charge
 * comes back when the player is not moved.
 */
class CrossServerTeleportTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final PlayerWarpId MARKET = PlayerWarpId.of(7);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final WarpCost PRICE = WarpCost.of(new BigDecimal("25.50"), "gold");

    private final MutableClock clock = new MutableClock(NOW);
    private final Steps scheduler = new Steps(clock);
    private final Pending store = new Pending();
    private final Warps warps = new Warps();
    private final Purse purse = new Purse();
    private final Moves moves = new Moves();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private final CrossServerWatchdog lobby =
            new CrossServerWatchdog(store, warps, Optional.of(purse), notifier.notifier(), scheduler, TTL);
    private final CrossServerArrival survival = arrivalOn(SURVIVAL);

    CrossServerTeleportTest() {
        warps.put(marketOn(SURVIVAL));
        scheduler.online.add(ALEX.uuid());
    }

    private CrossServerArrival arrivalOn(String server) {
        return new CrossServerArrival(
                store, warps, moves, Optional.of(purse), notifier.notifier(), scheduler, server, SETTLE, TTL, clock);
    }

    /** What the origin does once the gate charged: record the request and let the proxy carry the player. */
    private void sendAlexToMarket(Optional<WarpCost> charged) {
        lobby.track(ALEX, request(charged), "market");
    }

    private PendingTeleport request(Optional<WarpCost> charged) {
        return new PendingTeleport(ALEX.uuid(), MARKET, SURVIVAL, LOBBY, clock.instant(), charged);
    }

    private static PlayerWarp marketOn(String server) {
        PlayerWarp base = PlayerWarp.create(SAM, "Sam", PlayerWarpName.of("market"), at(0, 64, 0), NOW)
                .withId(MARKET);
        return new PlayerWarp(
                base.id(),
                base.owner(),
                base.ownerName(),
                base.name(),
                base.displayName(),
                base.location(),
                Optional.of(server),
                base.categoryId(),
                base.description(),
                base.icon(),
                base.access(),
                base.passwordSet(),
                base.status(),
                base.price(),
                base.earnings(),
                base.ratings(),
                base.visits(),
                base.favouriteCount(),
                base.sponsorship(),
                base.rent(),
                base.effects(),
                base.timing(),
                base.createdAt(),
                base.updatedAt());
    }

    @Test
    void aPlayerWhoReachesTheTargetIsMovedToTheWarpAndKeepsTheCharge() {
        sendAlexToMarket(Optional.of(PRICE));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.refunds).isEmpty();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.arrived");
    }

    @Test
    void twoJoinsThatBothReadTheRowMoveThePlayerOnce() {
        sendAlexToMarket(Optional.of(PRICE));
        store.afterNextFind = () -> survival.onArrival(ALEX);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.refunds).isEmpty();
    }

    @Test
    void aPlayerTheProxyNeverMovedIsRefundedWhenTheRequestLapses() {
        sendAlexToMarket(Optional.of(PRICE));

        scheduler.advance(TTL.minusSeconds(1));
        assertThat(purse.refunds).isEmpty();

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out", "pwarp.cross-server.refunded");
        assertThat(store.rows).isEmpty();
    }

    @Test
    void aLapsedFreeRequestCreditsNothing() {
        sendAlexToMarket(Optional.empty());

        scheduler.advance(TTL);

        assertThat(purse.refunds).isEmpty();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out");
    }

    @Test
    void aJoinAfterTheOriginRefundedNeitherMovesNorRefundsAgain() {
        sendAlexToMarket(Optional.of(PRICE));
        scheduler.advance(TTL);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
    }

    @Test
    void aJoinThatRacesTheLapsingRequestLetsOnlyOneSideRefund() {
        sendAlexToMarket(Optional.of(PRICE));
        clock.advance(TTL.plusSeconds(1));
        store.afterNextFind = () -> scheduler.advance(Duration.ZERO);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
    }

    @Test
    void aLapsedRequestTheOriginStoppedWatchingIsRefundedOnTheNextJoin() {
        store.record(request(Optional.of(PRICE)));
        clock.advance(TTL.plusSeconds(1));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out", "pwarp.cross-server.refunded");
    }

    @Test
    void aPlayerTheProxyPutOnTheWrongServerIsRefundedThere() {
        sendAlexToMarket(Optional.of(PRICE));

        arrivalOn("creative").onArrival(ALEX);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.misrouted", "pwarp.cross-server.refunded");
    }

    @Test
    void aWarpThatIsGoneWhenThePlayerArrivesIsRefunded() {
        sendAlexToMarket(Optional.of(PRICE));
        warps.deleteById(MARKET);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
        assertThat(notifier.sent().getFirst().placeholders()).containsEntry("warp", "#7");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.failed", "pwarp.cross-server.refunded");
    }

    @Test
    void aWarpThatMovedToAnotherBackendBeforeTheArrivalIsRefunded() {
        sendAlexToMarket(Optional.of(PRICE));
        warps.put(marketOn("creative"));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
    }

    @Test
    void aPlayerWhoLeavesDuringTheSettlingDelayIsRefunded() {
        sendAlexToMarket(Optional.of(PRICE));

        survival.onArrival(ALEX);
        scheduler.online.remove(ALEX.uuid());
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));
    }

    @Test
    void aSecondRequestRefundsTheUnclaimedOneItReplacesAndBothAreRefundedOnce() {
        WarpCost second = WarpCost.of(new BigDecimal("10"), "gold");
        sendAlexToMarket(Optional.of(PRICE));
        scheduler.advance(Duration.ofSeconds(5));

        sendAlexToMarket(Optional.of(second));
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE));

        scheduler.advance(TTL);
        assertThat(purse.refunds).containsExactly(new Refund(ALEX, PRICE), new Refund(ALEX, second));
    }

    private record Refund(PlayerRef to, WarpCost cost) {}

    /** The network-shared table: one row per player, deleted only while it is still the request the caller read. */
    private static final class Pending implements PendingTeleportStore {

        private final Map<UUID, PendingTeleport> rows = new HashMap<>();

        /** Runs once right after the next read, so a second caller can act between a read and its delete. */
        private @Nullable Runnable afterNextFind;

        @Override
        public void record(PendingTeleport pending) {
            rows.put(pending.player(), pending);
        }

        @Override
        public Optional<PendingTeleport> find(UUID player) {
            Optional<PendingTeleport> found = Optional.ofNullable(rows.get(player));
            Runnable racer = afterNextFind;
            afterNextFind = null;
            if (racer != null) {
                racer.run();
            }
            return found;
        }

        @Override
        public int clear(PendingTeleport pending) {
            PendingTeleport stored = rows.get(pending.player());
            if (stored == null || !sameRequest(stored, pending)) {
                return 0;
            }
            rows.remove(pending.player());
            return 1;
        }

        private static boolean sameRequest(PendingTeleport a, PendingTeleport b) {
            return a.warp().equals(b.warp())
                    && a.targetServer().equals(b.targetServer())
                    && a.requestedAt().toEpochMilli() == b.requestedAt().toEpochMilli();
        }
    }

    /** Runs async work at once and delayed work when the test moves the clock past it. */
    private static final class Steps implements Scheduler {

        private record Timed(Instant at, long order, Runnable task) {}

        private final MutableClock clock;
        private final List<Timed> waiting = new ArrayList<>();
        private final Set<UUID> online = new HashSet<>();
        private long scheduled;

        Steps(MutableClock clock) {
            this.clock = clock;
        }

        void advance(Duration by) {
            Instant until = clock.instant().plus(by);
            while (true) {
                Optional<Timed> next = waiting.stream()
                        .filter(timed -> !timed.at().isAfter(until))
                        .min(Comparator.comparing(Timed::at).thenComparingLong(Timed::order));
                if (next.isEmpty()) {
                    break;
                }
                waiting.remove(next.get());
                clock.advance(Duration.between(clock.instant(), next.get().at()));
                next.get().task().run();
            }
            clock.advance(Duration.between(clock.instant(), until));
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
            if (online.contains(player.uuid())) {
                task.run();
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (online.contains(player.uuid())) {
                task.run();
            } else {
                retired.run();
            }
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            waiting.add(new Timed(clock.instant().plus(delay), scheduled++, task));
        }
    }

    private static final class Warps implements PlayerWarpRepository {

        private final Map<PlayerWarpId, PlayerWarp> byId = new HashMap<>();

        void put(PlayerWarp warp) {
            byId.put(warp.id().orElseThrow(), warp);
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return byId.values().stream().filter(w -> w.name().equals(name)).findFirst();
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return byId.values().stream().filter(w -> w.owner().equals(owner)).toList();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return ownedBy(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return ownedBy(owner).size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return findByName(name).isPresent();
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            put(warp);
            return warp.id().orElseThrow();
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            byId.remove(id);
        }

        @Override
        public void recordVisit(PlayerWarpId id) {}

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {}

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {}
    }

    /** Keeps every refund; the other money paths are the gate's and never run here. */
    private static final class Purse implements PlayerWarpEconomy {

        private final List<Refund> refunds = new ArrayList<>();

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            refunds.add(new Refund(to, WarpCost.of(amount, currencyId)));
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            throw new UnsupportedOperationException("a cross-server hop never charges");
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("a cross-server hop never probes a balance");
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            throw new UnsupportedOperationException("a cross-server hop never withdraws");
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("a cross-server hop never collects rent");
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("a cross-server hop never charges an owner");
        }
    }

    private static final class Moves implements PlayerWarpTeleporter {

        private final List<String> made = new ArrayList<>();

        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {
            made.add(who.name() + "->" + warp.name().value());
        }
    }
}
