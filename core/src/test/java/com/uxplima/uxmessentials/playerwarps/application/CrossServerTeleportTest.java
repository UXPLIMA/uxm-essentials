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

import com.uxplima.uxmessentials.playerwarps.application.port.CrossServerTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A use of a player warp on another backend, from the charge on the origin to the hop on the target, and every way
 * the charge comes back when the player is not moved. Money is followed in the visitor's wallet and the owner's
 * vault, so a refund that leaves the owner paid shows up as money created.
 */
class CrossServerTeleportTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final PlayerWarpId MARKET = PlayerWarpId.of(7);
    private static final PlayerWarpName MARKET_NAME = PlayerWarpName.of("market");
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final WarpCost PRICE = WarpCost.of(new BigDecimal("25.50"), "gold");
    private static final BigDecimal START = new BigDecimal("100");

    private final MutableClock clock = new MutableClock(NOW);
    private final Steps scheduler = new Steps(clock);
    private final Pending store = new Pending();
    private final Warps warps = new Warps();
    private final Purse purse = new Purse();
    private final Moves moves = new Moves();
    private final Log log = new Log();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final CrossServerCharges charges = new CrossServerCharges(Optional.of(purse), notifier.notifier(), log);
    private final CrossServerWatchdog lobby = new CrossServerWatchdog(store, warps, charges, scheduler, log, TTL);
    private final CrossServerArrival survival = arrivalOn(SURVIVAL);

    CrossServerTeleportTest() {
        warps.put(marketOn(SURVIVAL));
        scheduler.online.add(ALEX.uuid());
        purse.wallets.put(ALEX.uuid(), START);
    }

    private CrossServerArrival arrivalOn(String server) {
        return new CrossServerArrival(
                store, warps, moves, charges, notifier.notifier(), scheduler, log, server, SETTLE, TTL, clock);
    }

    /** The lobby's gate with the cross-server route on, or off when {@code route} is empty. */
    private UsePlayerWarp lobbyGate(Optional<CrossServerTeleport> route) {
        return new UsePlayerWarp(
                warps,
                moves,
                notifier.notifier(),
                position -> true,
                new FakePermissions(),
                new NoBans(),
                new NoMembers(),
                new NoWhitelist(),
                new NoPasswords(),
                new ClockCooldowns(clock),
                Optional.of(purse),
                LOBBY,
                route,
                charges,
                clock);
    }

    /** What the origin's send seam does once the gate charged: record the request, then hand the player on. */
    private UsePlayerWarp lobbyGate() {
        CrossServerTeleport route = (who, warp, charged) -> lobby.track(
                who,
                new PendingTeleport(
                        who.uuid(),
                        warp.id().orElseThrow(),
                        warp.serverId().orElseThrow(),
                        LOBBY,
                        clock.instant(),
                        charged,
                        true),
                warp.name().value());
        return lobbyGate(Optional.of(route));
    }

    private boolean track(Optional<WarpCost> charged) {
        return lobby.track(ALEX, request(charged, clock.instant()), "market");
    }

    private PendingTeleport request(Optional<WarpCost> charged, Instant at) {
        return new PendingTeleport(ALEX.uuid(), MARKET, SURVIVAL, LOBBY, at, charged, true);
    }

    private BigDecimal wallet() {
        return purse.wallets.getOrDefault(ALEX.uuid(), BigDecimal.ZERO);
    }

    private BigDecimal vault() {
        return purse.vaults.getOrDefault(MARKET, BigDecimal.ZERO);
    }

    private static PlayerWarp marketOn(String server) {
        PlayerWarp base = PlayerWarp.create(SAM, "Sam", MARKET_NAME, at(0, 64, 0), NOW)
                .withId(MARKET)
                .withAccess(WarpAccess.PUBLIC, NOW)
                .withPrice(PRICE, NOW);
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
    void aUseOfAWarpOnAnotherBackendMovesThePlayerThereAndPaysTheOwnerOnce() {
        assertThat(lobbyGate().useFor(ALEX, MARKET_NAME).isOk()).isTrue();
        assertThat(vault()).isEqualByComparingTo("0");

        // Two joins that both read the row before either claims it.
        store.afterNextFind = () -> survival.onArrival(ALEX);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(vault()).isEqualByComparingTo("25.50");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.arrived");
    }

    @Test
    void aUseTheProxyNeverCarriedOutIsRefundedAndLeavesTheOwnerUnpaid() {
        lobbyGate().useFor(ALEX, MARKET_NAME);

        scheduler.advance(TTL.minusSeconds(1));
        assertThat(wallet()).isEqualByComparingTo("74.50");

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out", "pwarp.cross-server.refunded");
        assertThat(store.rows).isEmpty();
    }

    @Test
    void aUseWithTheCrossServerRouteOffIsRefundedAndLeavesTheOwnerUnpaid() {
        lobbyGate(Optional.empty()).useFor(ALEX, MARKET_NAME);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.unavailable", "pwarp.cross-server.refunded");
    }

    @Test
    void aLapsedFreeRequestCreditsNothing() {
        track(Optional.empty());

        scheduler.advance(TTL);

        assertThat(purse.refunds).isZero();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out");
    }

    @Test
    void aJoinAfterTheOriginRefundedNeitherMovesNorRefundsAgain() {
        track(Optional.of(PRICE));
        scheduler.advance(TTL);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aJoinThatRacesTheLapsingRequestLetsOnlyOneSideAct() {
        track(Optional.of(PRICE));
        clock.advance(TTL.plusSeconds(1));
        store.afterNextFind = () -> scheduler.advance(Duration.ZERO);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(vault()).isEqualByComparingTo("0");
    }

    @Test
    void aTargetThatStopsDuringTheSettlingDelayLeavesTheRequestToBeRefundedLater() {
        track(Optional.of(PRICE));
        survival.onArrival(ALEX);

        scheduler.dropWaiting();
        assertThat(store.rows).containsKey(ALEX.uuid());

        clock.advance(TTL.multipliedBy(2).plusSeconds(1));
        survival.onArrival(ALEX);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(store.rows).isEmpty();
    }

    @Test
    void aRowStampedByAnOriginClockThatRunsBehindIsStillHonoured() {
        store.record(request(Optional.of(PRICE), NOW.minus(TTL).minusSeconds(10)));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aRequestPastTwiceTheTtlIsRefundedOnTheNextJoin() {
        store.record(request(Optional.of(PRICE), NOW));
        clock.advance(TTL.multipliedBy(2).plusSeconds(1));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out", "pwarp.cross-server.refunded");
    }

    @Test
    void aPlayerTheProxyPutOnTheWrongServerIsRefundedThere() {
        track(Optional.of(PRICE));

        arrivalOn("creative").onArrival(ALEX);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.misrouted", "pwarp.cross-server.refunded");
    }

    @Test
    void aWarpThatIsGoneWhenThePlayerArrivesIsRefunded() {
        track(Optional.of(PRICE));
        warps.deleteById(MARKET);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(notifier.sent().getFirst().placeholders()).containsEntry("warp", "#7");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.failed", "pwarp.cross-server.refunded");
    }

    @Test
    void aWarpThatMovedToAnotherBackendBeforeTheArrivalIsRefunded() {
        track(Optional.of(PRICE));
        warps.put(marketOn("creative"));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aPlayerWhoLeavesDuringTheSettlingDelayIsRefundedAndTheOwnerStaysUnpaid() {
        lobbyGate().useFor(ALEX, MARKET_NAME);

        survival.onArrival(ALEX);
        scheduler.online.remove(ALEX.uuid());
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
    }

    @Test
    void anOwnerShareThatCannotBeBankedGoesBackToTheVisitor() {
        lobbyGate().useFor(ALEX, MARKET_NAME);
        purse.accrualFails = true;

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
    }

    @Test
    void aRefundTheEconomyRejectsIsLoggedWithThePlayerAndAmount() {
        track(Optional.of(PRICE));
        purse.refundFails = true;

        scheduler.advance(TTL);

        assertThat(log.errors)
                .singleElement()
                .satisfies(line ->
                        assertThat(line).contains(ALEX.uuid().toString()).contains("25.50"));
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out");
    }

    @Test
    void aSecondRequestRefundsTheUnclaimedOneItReplacesAndBothAreRefundedOnce() {
        WarpCost second = WarpCost.of(new BigDecimal("10"), "gold");
        track(Optional.of(PRICE));
        scheduler.advance(Duration.ofSeconds(5));

        track(Optional.of(second));
        assertThat(wallet()).isEqualByComparingTo("125.50");

        scheduler.advance(TTL);
        assertThat(wallet()).isEqualByComparingTo("135.50");
        assertThat(purse.refunds).isEqualTo(2);
    }

    @Test
    void anArrivalTheTargetRefusesIsRefundedAndLeavesTheOwnerUnpaid() {
        lobbyGate().useFor(ALEX, MARKET_NAME);
        moves.refuses = true;

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.failed", "pwarp.cross-server.refunded");
    }

    @Test
    void aRowAnEarlierOriginWroteMovesThePlayerWithoutPayingTheOwnerAgain() {
        store.record(new PendingTeleport(ALEX.uuid(), MARKET, SURVIVAL, LOBBY, NOW, Optional.of(PRICE), false));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void anOwnerPaymentTheAsyncLaneRefusesIsMadeInPlace() {
        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.rejectsAsync = true;

        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(vault()).isEqualByComparingTo("25.50");
    }

    @Test
    void aRefundTheAsyncLaneRefusesIsMadeInPlace() {
        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.online.remove(ALEX.uuid());
        scheduler.rejectsAsync = true;

        scheduler.advance(SETTLE);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
    }

    @Test
    void aRequestWhoseTimerCannotStartIsTakenBackAndRefundedOnce() {
        scheduler.rejectsDelayed = true;

        assertThat(track(Optional.of(PRICE))).isFalse();
        assertThat(store.rows).isEmpty();
        assertThat(purse.refunds).isOne();

        scheduler.rejectsDelayed = false;
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        assertThat(purse.refunds).isOne();
        assertThat(moves.made).isEmpty();
    }

    @Test
    void aRequestThatCannotBeRecordedIsRefundedAndNotSent() {
        store.recordFails = true;

        assertThat(track(Optional.of(PRICE))).isFalse();

        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.unavailable", "pwarp.cross-server.refunded");
    }

    /** The network-shared table: one row per player, deleted only while it is still the request the caller read. */
    private static final class Pending implements PendingTeleportStore {

        private final Map<UUID, PendingTeleport> rows = new HashMap<>();

        /** Runs once right after the next read, so a second caller can act between a read and its delete. */
        private @Nullable Runnable afterNextFind;

        private boolean recordFails;

        @Override
        public void record(PendingTeleport pending) {
            if (recordFails) {
                throw new IllegalStateException("the database refused the write");
            }
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

        /** What a disabling server's schedulers do: refuse new work. */
        private boolean rejectsAsync;

        private boolean rejectsDelayed;

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

        /** What a stopping server does to the delayed tasks it still holds. */
        void dropWaiting() {
            waiting.clear();
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
            if (rejectsAsync) {
                throw new IllegalStateException("the async lane is closed");
            }
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            if (rejectsDelayed) {
                throw new IllegalStateException("the delayed lane is closed");
            }
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

    /** Wallets per player and each warp's vault, with no cut, so every amount can be followed exactly. */
    private static final class Purse implements PlayerWarpEconomy {

        private final Map<UUID, BigDecimal> wallets = new HashMap<>();
        private final Map<PlayerWarpId, BigDecimal> vaults = new HashMap<>();
        private int refunds;
        private boolean accrualFails;
        private boolean refundFails;

        @Override
        public Result<Unit, ChargeError> charge(PlayerRef payer, BigDecimal price, String currencyId) {
            BigDecimal balance = wallets.getOrDefault(payer.uuid(), BigDecimal.ZERO);
            if (balance.compareTo(price) < 0) {
                return Result.err(ChargeError.INSUFFICIENT_FUNDS);
            }
            wallets.put(payer.uuid(), balance.subtract(price));
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            Result<Unit, ChargeError> charged = charge(payer, price, currencyId);
            if (charged.isOk()) {
                vaults.merge(warp, price, BigDecimal::add);
            }
            return charged;
        }

        @Override
        public Result<Unit, ChargeError> accrue(PlayerWarpId warp, BigDecimal price, String currencyId) {
            if (accrualFails) {
                return Result.err(ChargeError.ACCRUAL_FAILED);
            }
            vaults.merge(warp, price, BigDecimal::add);
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            if (refundFails) {
                return Result.err(ChargeError.PROVIDER_ERROR);
            }
            wallets.merge(to.uuid(), amount, BigDecimal::add);
            refunds++;
            return Result.ok();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return wallets.getOrDefault(who.uuid(), BigDecimal.ZERO).compareTo(amount) >= 0;
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

    /** Lands every hop unless told to refuse it, as the teleport engine refuses a jailed player. */
    private static final class Moves implements PlayerWarpTeleporter {

        private final List<String> made = new ArrayList<>();
        private boolean refuses;

        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {
            made.add(who.name() + "->" + warp.name().value());
        }

        @Override
        public void arrive(PlayerRef who, PlayerWarp warp, Runnable onLanded, Runnable onMissed) {
            if (refuses) {
                onMissed.run();
                return;
            }
            made.add(who.name() + "->" + warp.name().value());
            onLanded.run();
        }
    }

    private static final class Log implements Logger {

        private final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class NoBans implements WarpBanStore {

        @Override
        public void ban(PlayerWarpId warp, BanRecord record) {}

        @Override
        public void unban(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<BanRecord> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private static final class NoMembers implements WarpMemberStore {

        @Override
        public void put(PlayerWarpId warp, WarpMember member) {}

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
            return Optional.empty();
        }

        @Override
        public List<WarpMember> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private static final class NoWhitelist implements WarpWhitelistStore {

        @Override
        public void add(PlayerWarpId warp, UUID player) {}

        @Override
        public void remove(PlayerWarpId warp, UUID player) {}

        @Override
        public boolean contains(PlayerWarpId warp, UUID player) {
            return false;
        }

        @Override
        public List<UUID> list(PlayerWarpId warp) {
            return List.of();
        }
    }

    private static final class NoPasswords implements PlayerWarpPasswordStore {

        @Override
        public void set(PlayerWarpId warp, String plaintext) {}

        @Override
        public void clear(PlayerWarpId warp) {}

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return false;
        }
    }
}
