package com.uxplima.uxmessentials.homes.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A teleport to a home set on another backend, from the charge on the origin to the move on the target, and every way
 * the charge comes back when the player is not moved. Money is followed in the player's wallet, so a refund paid twice
 * or a charge kept for a move that never happened shows up as a wrong balance.
 */
class CrossServerHomeTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final HomeSlot SLOT = HomeSlot.of(0);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final HomeCost PRICE = HomeCost.of(new BigDecimal("25.50"), "gold");
    private static final BigDecimal START = new BigDecimal("100");

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final MemoryHopStore store = new MemoryHopStore();
    private final InMemoryHomes homes = new InMemoryHomes();
    private final Purse purse = new Purse();
    private final Moves moves = new Moves();
    private final Online online = new Online();
    private final Claims claims = new Claims();
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final ClockCooldowns cooldowns = new ClockCooldowns(clock);
    private final FakePermissions permissions = new FakePermissions();
    private final List<String> connects = new ArrayList<>();
    // Read when a backend's resolver is built, so it is declared before the backends below.
    private boolean guardUnsafe = true;
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final HopResolvers lobbyResolvers = resolversOn(LOBBY);
    private final HopWatchdog watchdog = new HopWatchdog(store, lobbyResolvers, charges, scheduler, log, TTL, clock);
    private final HopArrival survival = arrivalOn(SURVIVAL);
    private HopGate gate = HopGate.OPEN;
    private boolean proxyAvailable = true;
    private boolean tagged;
    private boolean unsafe;

    CrossServerHomeTest() {
        homes.save(baseOn(SURVIVAL));
        scheduler.online(ALEX);
        online.here.add(ALEX.uuid());
        purse.wallet = START;
    }

    private HopResolvers resolversOn(String server) {
        HopResolvers resolvers = new HopResolvers();
        resolvers.register(new HomeHopResolver(
                homes,
                moves,
                Optional.of(purse),
                position -> unsafe,
                claims,
                guardUnsafe,
                permissions,
                notifier.notifier(),
                scheduler,
                server));
        return resolvers;
    }

    private HopArrival arrivalOn(String server) {
        return new HopArrival(store, resolversOn(server), charges, scheduler, log, server, SETTLE, TTL, clock);
    }

    /** The lobby's homes, with the homes cross-server block on or off. */
    private HomeHops hops(boolean routed) {
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
        return routed
                ? new HomeHops(
                        Optional.of(new HopDepartures(
                                gate, proxy, watchdog, charges, lobbyResolvers, scheduler, LOBBY, clock)),
                        online,
                        LOBBY,
                        true)
                : HomeHops.local(online, LOBBY, true);
    }

    /** {@code /home} on the lobby: the menu's teleport button. */
    private TeleportHome lobby(boolean routed) {
        HomeChargeSettings costs = new HomeChargeSettings(HomeCost.free(), HomeCost.free(), PRICE);
        return new TeleportHome(
                homes,
                moves,
                notifier.notifier(),
                new HomeCharge(permissions, Optional.of(purse), costs),
                hops(routed),
                new PaidTeleports(scheduler, log));
    }

    private Result<Unit, HomeError> goHome() {
        return lobby(true).toSlot(ALEX, SLOT);
    }

    private void join(HopArrival arrival) {
        arrival.onArrival(ALEX);
        scheduler.advance(SETTLE);
    }

    private static Home baseOn(String server) {
        return Home.create(ALEX, SLOT, at(0, 64, 0), server, NOW).withLabel(Optional.of(HomeLabel.of("base")), NOW);
    }

    @Test
    void aHomeOnAnotherServerChargesOnceMovesThePlayerThereOnceAndKeepsTheCharge() {
        assertThat(goHome().isOk()).isTrue();
        assertThat(connects).containsExactly("Alex->survival");
        assertThat(store.find(ALEX.uuid()).map(PendingHop::kind)).contains(HopKind.HOME);
        assertThat(purse.wallet).isEqualByComparingTo("74.50");

        // Two joins that both read the row before either claims it, then the origin's timer.
        store.afterNextFind(() -> survival.onArrival(ALEX));
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->base");
        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(purse.refunds).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys()).containsExactly("home.cross-server.sending", "home.cross-server.arrived");
    }

    @Test
    void aTeleportTheProxyNeverCarriedOutIsRefundedAtTheTtl() {
        goHome();

        scheduler.advance(TTL);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly(
                        "home.cross-server.sending", "home.cross-server.timed-out", "home.cross-server.refunded");
    }

    @Test
    void aTeleportThatCannotLeaveIsRefundedAndNeverRecorded() {
        proxyAvailable = false;

        goHome();

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("home.cross-server.unavailable", "home.cross-server.refunded");
    }

    @Test
    void aTeleportThatCannotBeRecordedIsRefundedAndNotSent() {
        store.failRecords(true);

        goHome();

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(connects).isEmpty();
    }

    @Test
    void aPlayerTheProxyPutOnTheWrongServerIsRefundedOnce() {
        goHome();

        arrivalOn("creative").onArrival(ALEX);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("home.cross-server.misrouted");
    }

    @Test
    void aHomeDeletedBeforeThePlayerArrivesIsRefundedOnce() {
        goHome();
        homes.deleteSlot(ALEX, SLOT);

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("home.cross-server.failed", "home.cross-server.refunded");
    }

    @Test
    void aHomeRelocatedToAnotherServerBeforeThePlayerArrivesIsRefundedOnce() {
        goHome();
        homes.save(baseOn("creative"));

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aLandingThatMissesIsRefundedOnce() {
        moves.refuses = true;
        goHome();

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aPlayerWhoLeavesDuringTheSettlingDelayIsRefundedOnce() {
        goHome();
        survival.onArrival(ALEX);
        scheduler.offline(ALEX);

        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aLandingInAWorldThatIsNotLoadedHereIsRefundedOnce() {
        goHome();
        scheduler.unload(WORLD);

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly("home.cross-server.sending", "home.cross-server.failed", "home.cross-server.refunded");
    }

    @Test
    void anUnsafeHomeIsRefusedOnLandingAndRefundedOnce() {
        goHome();
        unsafe = true;

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys())
                .containsExactly(
                        "home.cross-server.sending",
                        "home.cross-server.unsafe",
                        "home.cross-server.failed",
                        "home.cross-server.refunded");
    }

    @Test
    void aPlayerWhoMayBypassTheUnsafeCheckLandsOnAnUnsafeHome() {
        permissions.grant(ALEX, HomeHopResolver.BYPASS_UNSAFE);
        unsafe = true;
        goHome();

        join(survival);

        assertThat(moves.made).containsExactly("Alex->base");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void withTheUnsafeConfirmOffAnUnsafeHomeIsLandedOnAsALocalTeleportWouldBe() {
        guardUnsafe = false;
        HopArrival lenient = arrivalOn(SURVIVAL);
        unsafe = true;
        goHome();

        join(lenient);

        assertThat(moves.made).containsExactly("Alex->base");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aClaimThePlayerMayNoLongerEnterIsRefusedOnLandingAndRefundedOnce() {
        goHome();
        claims.access = ClaimDecision.DENIED_ACCESS;

        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("home.claim.access-denied");
    }

    @Test
    void aCombatTaggedPlayerIsRefusedBeforeAnythingIsCharged() {
        tagged = true;
        gate = new TeleportHopGate(engine());

        Result<Unit, HomeError> used = goHome();

        assertThat(used.asError()).contains(HomeError.TELEPORT_REFUSED);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.charges).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged");
    }

    @Test
    void aLandedHomeStartsTheHomeCooldownAndTheNextTeleportIsRefusedBeforeTheCharge() {
        TeleportEngine engine = engine();
        gate = new TeleportHopGate(engine);
        moves.engine = engine;
        goHome();
        join(survival);
        assertThat(moves.made).containsExactly("Alex->base");

        goHome();

        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(purse.charges).isOne();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys()).contains("teleport.cooldown.active");
    }

    @Test
    void aPlayerWhoLeftBeforeTheTeleportRanIsNeitherChargedNorSent() {
        online.here.remove(ALEX.uuid());

        goHome();

        assertThat(purse.charges).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void withTheCrossServerBlockOffAHomeOfAnotherServerIsUsedHereAsBefore() {
        Result<Unit, HomeError> used = lobby(false).toSlot(ALEX, SLOT);

        assertThat(used.isOk()).isTrue();
        assertThat(moves.teleported).containsExactly("Alex->base");
        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void aLocalTeleportThatLandsKeepsItsCharge() {
        homes.save(baseOn(LOBBY));
        moves.holds = true;

        goHome();
        moves.finish(TeleportOutcome.LANDED);

        assertThat(purse.wallet).isEqualByComparingTo("74.50");
        assertThat(purse.refunds).isZero();
        assertThat(notifier.keys()).containsExactly("home.teleporting");
    }

    @ParameterizedTest
    @EnumSource(value = TeleportOutcome.class, names = "LANDED", mode = EnumSource.Mode.EXCLUDE)
    void aLocalTeleportThatNeverLandsIsRefundedOnceAndTheyAreToldOnce(TeleportOutcome ended) {
        homes.save(baseOn(LOBBY));
        moves.holds = true;

        goHome();
        assertThat(purse.wallet).as("charged before the warmup").isEqualByComparingTo("74.50");
        moves.finish(ended);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.sent())
                .extracting(RecordingNotifier.Sent::key)
                .containsExactly("home.teleporting", "home.cross-server.refunded");
        assertThat(notifier.sent().get(1).placeholders()).containsEntry("home", "base");
    }

    @Test
    void aLocalTeleportForAPlayerWhoMayTeleportFreeMovesNoMoneyWhenItNeverLands() {
        homes.save(baseOn(LOBBY));
        permissions.grant(ALEX, DestinationPermissions.HOME_BYPASS_COST);
        moves.holds = true;

        goHome();
        moves.finish(TeleportOutcome.CANCELLED);

        assertThat(purse.charges).isZero();
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aCrossServerHomeThatMissesIsRefundedOnceByTheHopAlone() {
        moves.holds = true;
        moves.refuses = true;

        goHome();
        join(survival);
        scheduler.advance(TTL);

        assertThat(moves.held)
                .as("a cross-server hop never takes the local outcome route")
                .isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aHomeOfThisServerIsUsedHereWithTheBlockOn() {
        homes.save(baseOn(LOBBY));

        goHome();

        assertThat(moves.teleported).containsExactly("Alex->base");
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void aHomeOfUnknownServerIsUsedHereWithTheBlockOn() {
        homes.save(Home.create(ALEX, SLOT, at(0, 64, 0), NOW).withLabel(Optional.of(HomeLabel.of("base")), NOW));

        goHome();

        assertThat(moves.teleported).containsExactly("Alex->base");
        assertThat(connects).isEmpty();
    }

    /** Sam, a guest, goes to Alex's home through the hop and lands on the survival server. */
    private void samLandsOnSurvival() {
        new HopArrival(store, resolversOn(SURVIVAL), charges, scheduler, log, SURVIVAL, SETTLE, TTL, clock)
                .onArrival(SAM);
        scheduler.advance(SETTLE);
    }

    private void samIsOnline() {
        scheduler.online(SAM);
        online.here.add(SAM.uuid());
    }

    private VisitHome visits() {
        return new VisitHome(homes, homes, moves, notifier.notifier(), hops(true), lobby(true));
    }

    @Test
    void aVisitToAPublicHomeOnAnotherServerSkipsTheOwnersLandingChecksAndIsFree() {
        homes.save(baseOn(SURVIVAL).withVisibility(true, NOW));
        samIsOnline();
        claims.ownerOnly = true;
        unsafe = true;

        Result<Unit, HomeError> visited = visits().visit(SAM, ALEX, SLOT);
        samLandsOnSurvival();

        assertThat(visited.isOk()).isTrue();
        assertThat(connects).containsExactly("Sam->survival");
        assertThat(moves.made).containsExactly("Sam->base");
        assertThat(purse.charges).isZero();
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aStaffTeleportToAHomeOnAnotherServerGoesThroughTheHopWithoutTheOwnersChecks() {
        samIsOnline();
        claims.ownerOnly = true;
        unsafe = true;
        HomeAdmin admin =
                new HomeAdmin(homes, homes, moves, notifier.notifier(), new RecordingEvents(), clock, hops(true));

        Result<Unit, HomeError> sent = admin.teleport(SAM, ALEX, SLOT);
        samLandsOnSurvival();

        assertThat(sent.isOk()).isTrue();
        assertThat(connects).containsExactly("Sam->survival");
        assertThat(moves.made).containsExactly("Sam->base");
        assertThat(purse.charges).isZero();
    }

    @Test
    void theOwnerVisitingTheirOwnHomePaysTheTeleportCost() {
        Result<Unit, HomeError> visited = visits().visit(ALEX, ALEX, SLOT);
        join(survival);

        assertThat(visited.isOk()).isTrue();
        assertThat(moves.made).containsExactly("Alex->base");
        assertThat(purse.wallet).isEqualByComparingTo("74.50");
    }

    /** The teleport engine both backends run, sharing one cooldown store as a player's cooldowns follow them. */
    private TeleportEngine engine() {
        return new TeleportEngine(
                cooldowns,
                (who, kind, onComplete, onCancel) -> {
                    onComplete.run();
                    return DONE;
                },
                (who, destination, kind) -> {},
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(new Defaults()),
                who -> false,
                who -> tagged);
    }

    /** A warmup that has already counted down. */
    private static final Warmups.WarmupHandle DONE = new Warmups.WarmupHandle() {
        @Override
        public void cancel() {}

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /** The player's wallet, with every debit and credit counted. */
    private static final class Purse implements HomeEconomy {

        BigDecimal wallet = BigDecimal.ZERO;
        int charges;
        int refunds;

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
     * Records every move: local teleports apart from cross-server landings. Without an engine every landing lands
     * unless told to refuse; given one, it lands through that engine, as the teleport adapter does.
     */
    private static final class Moves implements HomeTeleporter {

        final List<String> teleported = new ArrayList<>();
        final List<String> made = new ArrayList<>();
        boolean refuses;
        /** Outcomes the test finishes itself, oldest first, while {@code holds} is set. */
        final List<CompletableFuture<TeleportOutcome>> held = new ArrayList<>();

        /** Leave each local teleport's outcome to the test instead of landing it. */
        boolean holds;

        @Nullable TeleportEngine engine;

        @Override
        public CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, Home home) {
            CompletableFuture<TeleportOutcome> outcome = new CompletableFuture<>();
            if (holds) {
                held.add(outcome);
                return outcome;
            }
            teleported.add(who.name() + "->" + HomeHops.labelOf(home));
            outcome.complete(TeleportOutcome.LANDED);
            return outcome;
        }

        /** End the oldest held teleport with {@code outcome}. */
        void finish(TeleportOutcome outcome) {
            held.remove(0).complete(outcome);
        }

        @Override
        public void arrive(PlayerRef who, Home home, Runnable onLanded, Runnable onMissed) {
            if (refuses) {
                onMissed.run();
                return;
            }
            Runnable landed = () -> {
                made.add(who.name() + "->" + HomeHops.labelOf(home));
                onLanded.run();
            };
            TeleportEngine through = engine;
            if (through == null) {
                landed.run();
                return;
            }
            if (through.arriveImmediately(who, Destination.at(home.location()), TeleportKind.HOME, landed, onMissed)
                    .isErr()) {
                onMissed.run();
            }
        }
    }

    /** Which players are connected to this backend. */
    private static final class Online implements PlayerLookup {

        final Set<UUID> here = new HashSet<>();

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

    /** The claim plugin on the target, answering one decision for every spot. */
    private static final class Claims implements ClaimService {

        ClaimDecision access = ClaimDecision.ALLOWED;
        // A claim of Alex's that only Alex may enter.
        boolean ownerOnly;

        @Override
        public ClaimDecision canPlace(PlayerRef who, Position at) {
            return ClaimDecision.ALLOWED;
        }

        @Override
        public ClaimDecision canAccess(PlayerRef who, Position at) {
            if (ownerOnly) {
                return who.equals(ALEX) ? ClaimDecision.ALLOWED : ClaimDecision.DENIED_ACCESS;
            }
            return access;
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
