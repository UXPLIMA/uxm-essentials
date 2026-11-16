package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
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
import com.uxplima.uxmessentials.testing.HeldHops;
import com.uxplima.uxmessentials.testing.ManualWarmups;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    private static final HopCharge CHARGE = new HopCharge(new BigDecimal("25.50"), "gold");
    private static final Optional<HopSettlement> TO_MARKET = Optional.of(new HopSettlement("7"));
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
    private final List<String> connects = new ArrayList<>();
    private final FakePermissions permissions = new FakePermissions();
    /** The target's thread hops, which a test can hold to run them one task at a time, as Folia does. */
    private final HeldHops targetLanes = new HeldHops(scheduler);
    /** Backends whose blocks read unsafe at the warp, as an origin without its world does. */
    private final Set<String> unsafeOn = new HashSet<>();
    /** Backends whose safety check throws instead of answering. */
    private final Set<String> unreadableOn = new HashSet<>();

    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final HopResolvers lobbyResolvers = resolversOn(LOBBY);
    private final HopWatchdog lobby = new HopWatchdog(store, lobbyResolvers, charges, scheduler, log, TTL, clock);
    private final HopArrival survival = arrivalOn(SURVIVAL);

    private HopGate gate = HopGate.OPEN;
    private boolean tagged;

    CrossServerTeleportTest() {
        warps.put(marketOn(SURVIVAL));
        scheduler.online(ALEX);
        purse.wallets.put(ALEX.uuid(), START);
    }

    private HopResolvers resolversOn(String server) {
        HopResolvers resolvers = new HopResolvers();
        resolvers.register(resolverOn(server));
        return resolvers;
    }

    private PlayerWarpHopResolver resolverOn(String server) {
        return new PlayerWarpHopResolver(
                warps,
                moves,
                Optional.of(purse),
                safetyOn(server),
                permissions,
                notifier.notifier(),
                targetLanes,
                server);
    }

    /** The safety check as {@code server} answers it, from its own blocks. */
    private WarpSafetyChecker safetyOn(String server) {
        return position -> {
            if (unreadableOn.contains(server)) {
                throw new IllegalStateException("the chunk could not be read");
            }
            return !unsafeOn.contains(server);
        };
    }

    private HopArrival arrivalOn(String server) {
        return new HopArrival(store, resolversOn(server), charges, scheduler, log, server, SETTLE, TTL, clock);
    }

    /** The lobby's gate with the cross-server route on, or off when {@code routed} is false. */
    private UsePlayerWarp lobbyGate(boolean routed) {
        HopConnector proxy = new HopConnector() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void connect(PlayerRef player, String server) {
                connects.add(player.name() + "->" + server);
            }
        };
        Optional<HopDepartures> departures = routed
                ? Optional.of(new HopDepartures(gate, proxy, lobby, charges, lobbyResolvers, scheduler, LOBBY, clock))
                : Optional.empty();
        return new UsePlayerWarp(
                warps,
                moves,
                notifier.notifier(),
                safetyOn(LOBBY),
                permissions,
                new NoBans(),
                new NoMembers(),
                new NoWhitelist(),
                new NoPasswords(),
                cooldowns,
                Optional.of(purse),
                LOBBY,
                new PlayerWarpHops(departures, charges, resolverOn(LOBBY)),
                clock,
                new PaidTeleports(scheduler, log),
                log);
    }

    private UsePlayerWarp lobbyGate() {
        return lobbyGate(true);
    }

    /**
     * The teleport engine both backends run, sharing one cooldown store as a player's cooldowns follow them. Its
     * hops always land; nothing here warms up.
     */
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

    private boolean track(Optional<HopCharge> charged) {
        return lobby.track(ALEX, request(charged, clock.instant()), "market");
    }

    private PendingHop request(Optional<HopCharge> charged, Instant at) {
        return new PendingHop(ALEX.uuid(), HopKind.PLAYER_WARP, "7", SURVIVAL, LOBBY, at, charged, TO_MARKET);
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
        assertThat(connects).containsExactly("Alex->survival");

        // Two joins that both read the row before either claims it.
        store.afterNextFind(() -> survival.onArrival(ALEX));
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(vault()).isEqualByComparingTo("25.50");
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.sending", "pwarp.cross-server.arrived");
    }

    @Test
    void aUseTheProxyNeverCarriedOutIsRefundedAndLeavesTheOwnerUnpaid() {
        lobbyGate().useFor(ALEX, MARKET_NAME);

        scheduler.advance(TTL.minusSeconds(1));
        assertThat(wallet()).isEqualByComparingTo("74.50");

        scheduler.advance(Duration.ofSeconds(1));
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(notifier.keys())
                .containsExactly(
                        "pwarp.cross-server.sending", "pwarp.cross-server.timed-out", "pwarp.cross-server.refunded");
        assertThat(store.rows()).isEmpty();
    }

    @Test
    void aUseWithTheCrossServerRouteOffIsRefundedAndLeavesTheOwnerUnpaid() {
        lobbyGate(false).useFor(ALEX, MARKET_NAME);

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
        track(Optional.of(CHARGE));
        scheduler.advance(TTL);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aJoinThatRacesTheLapsingRequestLetsOnlyOneSideAct() {
        track(Optional.of(CHARGE));
        clock.advance(TTL.plusSeconds(1));
        store.afterNextFind(() -> scheduler.advance(Duration.ZERO));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(vault()).isEqualByComparingTo("0");
    }

    @Test
    void aTargetThatStopsDuringTheSettlingDelayLeavesTheRequestToBeRefundedLater() {
        track(Optional.of(CHARGE));
        survival.onArrival(ALEX);

        scheduler.dropWaiting();
        assertThat(store.rows()).containsKey(ALEX.uuid());

        clock.advance(TTL.multipliedBy(2).plusSeconds(1));
        survival.onArrival(ALEX);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
    }

    @Test
    void aRowStampedByAnOriginClockThatRunsBehindIsStillHonoured() {
        store.record(request(Optional.of(CHARGE), NOW.minus(TTL).minusSeconds(10)));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aRequestPastTwiceTheTtlIsRefundedOnTheNextJoin() {
        store.record(request(Optional.of(CHARGE), NOW));
        clock.advance(TTL.multipliedBy(2).plusSeconds(1));

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out", "pwarp.cross-server.refunded");
    }

    @Test
    void aPlayerTheProxyPutOnTheWrongServerIsRefundedThere() {
        track(Optional.of(CHARGE));

        arrivalOn("creative").onArrival(ALEX);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.misrouted", "pwarp.cross-server.refunded");
    }

    @Test
    void aWarpThatIsGoneWhenThePlayerArrivesIsRefunded() {
        track(Optional.of(CHARGE));
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
        track(Optional.of(CHARGE));
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
        scheduler.offline(ALEX);
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
        track(Optional.of(CHARGE));
        purse.refundFails = true;

        scheduler.advance(TTL);

        assertThat(log.errors())
                .singleElement()
                .satisfies(line ->
                        assertThat(line).contains(ALEX.uuid().toString()).contains("25.50"));
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.timed-out");
    }

    @Test
    void aSecondRequestRefundsTheUnclaimedOneItReplacesAndBothAreRefundedOnce() {
        HopCharge second = new HopCharge(new BigDecimal("10"), "gold");
        track(Optional.of(CHARGE));
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
        assertThat(notifier.keys())
                .containsExactly(
                        "pwarp.cross-server.sending", "pwarp.cross-server.failed", "pwarp.cross-server.refunded");
    }

    @Test
    void aRowAnEarlierOriginWroteMovesThePlayerWithoutPayingTheOwnerAgain() {
        store.record(new PendingHop(
                ALEX.uuid(), HopKind.PLAYER_WARP, "7", SURVIVAL, LOBBY, NOW, Optional.of(CHARGE), Optional.empty()));

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
        scheduler.rejectAsync(true);

        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(vault()).isEqualByComparingTo("25.50");
    }

    @Test
    void aRefundTheAsyncLaneRefusesIsMadeInPlace() {
        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.offline(ALEX);
        scheduler.rejectAsync(true);

        scheduler.advance(SETTLE);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
    }

    @Test
    void aRequestWhoseTimerCannotStartIsTakenBackAndRefundedOnce() {
        scheduler.rejectDelayed(true);

        assertThat(track(Optional.of(CHARGE))).isFalse();
        assertThat(store.rows()).isEmpty();
        assertThat(purse.refunds).isOne();

        scheduler.rejectDelayed(false);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        assertThat(purse.refunds).isOne();
        assertThat(moves.made).isEmpty();
    }

    @Test
    void aRequestThatCannotBeRecordedIsRefundedAndNotSent() {
        store.failRecords(true);

        assertThat(track(Optional.of(CHARGE))).isFalse();

        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.cross-server.unavailable", "pwarp.cross-server.refunded");
    }

    @Test
    void aRequestThatRacesAnotherIsRefundedOnceAndToldTheOtherMoveIsUnderWay() {
        // The second request starts while the first has read no earlier row and not yet written its own.
        store.afterNextFind(() -> assertThat(track(Optional.of(CHARGE))).isFalse());

        assertThat(track(Optional.of(CHARGE))).isTrue();

        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("common.hop-busy", "pwarp.cross-server.refunded");

        // The first request is the one in the store, refunded by its own timer and by nothing else.
        scheduler.advance(TTL);
        assertThat(purse.refunds).isEqualTo(2);
        assertThat(store.rows()).isEmpty();
    }

    @Test
    void aCrossServerUseIsNotJudgedByTheOriginWhichHasNoneOfTheWarpsBlocks() {
        unsafeOn.add(LOBBY);

        assertThat(lobbyGate().useFor(ALEX, MARKET_NAME).isOk()).isTrue();
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(vault()).isEqualByComparingTo("25.50");
    }

    @Test
    void aCrossServerUseOntoAnUnsafeDestinationIsRefusedOnLandingAndRefundedOnce() {
        unsafeOn.add(SURVIVAL);

        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys())
                .containsExactly(
                        "pwarp.cross-server.sending",
                        "pwarp.unsafe",
                        "pwarp.cross-server.failed",
                        "pwarp.cross-server.refunded");
    }

    @Test
    void aCrossServerDestinationThatCannotBeJudgedIsRefundedRatherThanLandedOn() {
        unreadableOn.add(SURVIVAL);

        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aLandingInAWorldThatIsNotLoadedOnTheTargetIsRefundedOnce() {
        lobbyGate().useFor(ALEX, MARKET_NAME);
        scheduler.unload(WORLD);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly(
                        "pwarp.cross-server.sending", "pwarp.cross-server.failed", "pwarp.cross-server.refunded");
    }

    @Test
    void aVisitorWhoLeavesBetweenTheSafetyCheckAndTheMoveIsRefundedOnce() {
        targetLanes.hold();
        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        targetLanes.runNext();
        scheduler.offline(ALEX);
        targetLanes.runAll();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aMoveThatThrowsOnTheVisitorsOwnThreadIsRefundedOnceAndStillReported() {
        targetLanes.hold();
        moves.throwsOnArrive = true;
        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        targetLanes.runAll();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
        assertThat(targetLanes.thrown())
                .as("the failure still reaches the scheduler, which logs it")
                .singleElement()
                .satisfies(failure -> assertThat(failure).hasMessage("the jail lookup failed"));
    }

    @Test
    void aPlayerWhoMayBypassTheSafetyCheckLandsOnAnUnsafeCrossServerDestination() {
        permissions.grant(ALEX, DestinationPermissions.PWARP_BYPASS_SAFETY);
        unsafeOn.add(SURVIVAL);

        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);

        assertThat(moves.made).containsExactly("Alex->market");
        assertThat(vault()).isEqualByComparingTo("25.50");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aLocalUseOntoAnUnsafeDestinationIsRefusedBeforeAnythingIsCharged() {
        warps.put(marketOn(LOBBY));
        unsafeOn.add(LOBBY);

        Result<Unit, PlayerWarpError> used = lobbyGate().useFor(ALEX, MARKET_NAME);

        assertThat(used.asError()).contains(PlayerWarpError.UNSAFE_LOCATION);
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
    }

    @Test
    void aCombatTaggedPlayerIsRefusedACrossServerWarpBeforeAnythingIsCharged() {
        gate = new TeleportHopGate(engine());
        tagged = true;

        Result<Unit, PlayerWarpError> used = lobbyGate().useFor(ALEX, MARKET_NAME);

        assertThat(used.asError()).contains(PlayerWarpError.TELEPORT_REFUSED);
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged");
    }

    @Test
    void aLocalUseTheTeleportRulesRefuseChargesTheVisitorNothingAndPaysTheOwnerNothing() {
        warps.put(marketOn(LOBBY));
        moves.engine = engine();
        tagged = true;

        Result<Unit, PlayerWarpError> used = lobbyGate().useFor(ALEX, MARKET_NAME);

        assertThat(used.asError()).contains(PlayerWarpError.TELEPORT_REFUSED);
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(moves.made).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged");
    }

    @Test
    void aLocalUseThatLandsPaysTheOwnerOnlyOnceTheVisitorHasArrived() {
        warps.put(marketOn(LOBBY));
        moves.holds = true;

        lobbyGate().useFor(ALEX, MARKET_NAME);
        assertThat(wallet()).as("the visitor pays before the warmup").isEqualByComparingTo("74.50");
        assertThat(vault())
                .as("the owner is not paid for a teleport that may not happen")
                .isEqualByComparingTo("0");

        moves.finish(TeleportOutcome.LANDED);

        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(vault()).isEqualByComparingTo("25.50");
        assertThat(purse.refunds).isZero();
        assertThat(notifier.keys()).containsExactly("pwarp.teleporting");
    }

    @ParameterizedTest
    @EnumSource(value = TeleportOutcome.class, names = "LANDED", mode = EnumSource.Mode.EXCLUDE)
    void aLocalUseThatNeverLandsRefundsTheVisitorInFullAndNeverPaysTheOwner(TeleportOutcome ended) {
        warps.put(marketOn(LOBBY));
        moves.holds = true;

        lobbyGate().useFor(ALEX, MARKET_NAME);
        moves.finish(ended);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("pwarp.teleporting", "pwarp.cross-server.refunded");
    }

    @Test
    void aLocalUseMovedOutOfItsWarmupRefundsTheVisitorAndLeavesTheOwnerUnpaid() {
        warps.put(marketOn(LOBBY));
        ManualWarmups warmups = new ManualWarmups();
        moves.engine = warmingEngine(warmups);

        lobbyGate().useFor(ALEX, MARKET_NAME);
        warmups.move(ALEX);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(notifier.keys())
                .containsExactly("pwarp.teleporting", "teleport.warmup.cancelled", "pwarp.cross-server.refunded");
    }

    @Test
    void aLocalUseWhoseWarmupEndsPaysTheOwnerOnce() {
        warps.put(marketOn(LOBBY));
        ManualWarmups warmups = new ManualWarmups();
        moves.engine = warmingEngine(warmups);

        lobbyGate().useFor(ALEX, MARKET_NAME);
        warmups.finish(ALEX);

        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(vault()).isEqualByComparingTo("25.50");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aLocalLandingWhoseOwnerShareCannotBeBankedGivesTheVisitorTheirMoneyBack() {
        warps.put(marketOn(LOBBY));
        moves.holds = true;
        purse.accrualFails = true;

        lobbyGate().useFor(ALEX, MARKET_NAME);
        moves.finish(TeleportOutcome.LANDED);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(log.warnings())
                .singleElement()
                .satisfies(line ->
                        assertThat(line).contains(ALEX.uuid().toString()).contains("player_refunded"));
    }

    @Test
    void aVisitCountThatFailsCostsTheCountNotTheUse() {
        warps.put(marketOn(LOBBY));
        warps.visitFails = true;
        moves.holds = true;

        Result<Unit, PlayerWarpError> used = lobbyGate().useFor(ALEX, MARKET_NAME);
        moves.finish(TeleportOutcome.LANDED);

        assertThat(used.isOk()).isTrue();
        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(vault()).isEqualByComparingTo("25.50");
        assertThat(log.warnings())
                .singleElement()
                .satisfies(line -> assertThat(line).contains("pwarp_visit_not_counted"));
    }

    @Test
    void aLocalLaunchThatThrowsAfterTheChargeRefundsTheVisitorAndNeverPaysTheOwner() {
        warps.put(marketOn(LOBBY));
        moves.throwsOnTeleport = true;

        assertThatThrownBy(() -> lobbyGate().useFor(ALEX, MARKET_NAME)).isInstanceOf(IllegalStateException.class);

        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(vault()).isEqualByComparingTo("0");
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aCrossServerUseThatMissesIsRefundedOnceByTheHopAlone() {
        moves.holds = true;
        moves.refuses = true;
        lobbyGate().useFor(ALEX, MARKET_NAME);

        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.held)
                .as("a cross-server use never takes the local outcome route")
                .isEmpty();
        assertThat(wallet()).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    /** The engine with a warmup the test ends itself; its hops always land. */
    private TeleportEngine warmingEngine(ManualWarmups warmups) {
        return new TeleportEngine(
                cooldowns,
                warmups,
                (who, destination, kind) -> {},
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(new Defaults()),
                who -> false,
                who -> tagged);
    }

    @Test
    void aLandedCrossServerWarpStartsItsCooldownAndTheNextUseIsRefusedBeforeTheCharge() {
        warps.put(marketOn(SURVIVAL).withTiming(new WarpTimingOverrides(Optional.empty(), Optional.of(60.0)), NOW));
        TeleportEngine engine = engine();
        gate = new TeleportHopGate(engine);
        moves.engine = engine;
        lobbyGate().useFor(ALEX, MARKET_NAME);
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        assertThat(moves.made).containsExactly("Alex->market");

        Result<Unit, PlayerWarpError> again = lobbyGate().useFor(ALEX, MARKET_NAME);

        assertThat(again.asError()).contains(PlayerWarpError.TELEPORT_REFUSED);
        assertThat(wallet()).isEqualByComparingTo("74.50");
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys()).last().isEqualTo("teleport.cooldown.active");

        scheduler.advance(Duration.ofSeconds(60));
        assertThat(lobbyGate().useFor(ALEX, MARKET_NAME).isOk())
                .as("the warp's own cooldown is the key both the landing and the gate use")
                .isTrue();
        assertThat(wallet()).isEqualByComparingTo("49.00");
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

        /** Make the visit counter's write fail, as a database fault would. */
        private boolean visitFails;

        @Override
        public void recordVisit(PlayerWarpId id) {
            if (visitFails) {
                throw new IllegalStateException("the visit write failed");
            }
        }

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

    /**
     * Lands every hop unless told to refuse it, as the teleport engine refuses a jailed player. Given an engine, it
     * arrives through that engine instead, as the teleport adapter does.
     */
    private static final class Moves implements PlayerWarpTeleporter {

        private final List<String> made = new ArrayList<>();
        private boolean refuses;
        private @Nullable TeleportEngine engine;
        /** Outcomes the test finishes itself, oldest first, while {@code holds} is set. */
        private final List<CompletableFuture<TeleportOutcome>> held = new ArrayList<>();

        /** Leave each local teleport's outcome to the test instead of landing it. */
        private boolean holds;

        /** Make every local teleport throw where it starts, as a launch that fails on the caller's thread does. */
        private boolean throwsOnTeleport;

        /** Make every arrival throw, as a jail or combat lookup that fails on the player's thread does. */
        private boolean throwsOnArrive;

        @Override
        public boolean admits(PlayerRef who, PlayerWarp warp) {
            TeleportEngine through = engine;
            return through == null
                    || through.gateHop(
                                    who,
                                    Destination.at(
                                            warp.location(),
                                            warp.timing().warmup(),
                                            warp.timing().cooldown()),
                                    TeleportKind.WARP)
                            .isOk();
        }

        @Override
        public CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, PlayerWarp warp) {
            if (throwsOnTeleport) {
                throw new IllegalStateException("the launch failed");
            }
            CompletableFuture<TeleportOutcome> outcome = new CompletableFuture<>();
            if (holds) {
                held.add(outcome);
                return outcome;
            }
            TeleportEngine through = engine;
            if (through == null) {
                made.add(who.name() + "->" + warp.name().value());
                outcome.complete(TeleportOutcome.LANDED);
                return outcome;
            }
            Destination destination = Destination.at(
                    warp.location(), warp.timing().warmup(), warp.timing().cooldown());
            if (through.launch(who, destination, TeleportKind.WARP, outcome::complete)
                    .isOk()) {
                made.add(who.name() + "->" + warp.name().value());
            }
            return outcome;
        }

        /** End the oldest held teleport with {@code outcome}. */
        private void finish(TeleportOutcome outcome) {
            held.remove(0).complete(outcome);
        }

        @Override
        public void arrive(PlayerRef who, PlayerWarp warp, Runnable onLanded, Runnable onMissed) {
            if (throwsOnArrive) {
                throw new IllegalStateException("the jail lookup failed");
            }
            if (refuses) {
                onMissed.run();
                return;
            }
            TeleportEngine through = engine;
            if (through == null) {
                made.add(who.name() + "->" + warp.name().value());
                onLanded.run();
                return;
            }
            Destination destination = Destination.at(
                    warp.location(), warp.timing().warmup(), warp.timing().cooldown());
            Runnable landed = () -> {
                made.add(who.name() + "->" + warp.name().value());
                onLanded.run();
            };
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
