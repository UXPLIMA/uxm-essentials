package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import org.junit.jupiter.api.Test;

/**
 * A teleport to a world another backend runs, from the fee on the origin to the landing on the target, and every way
 * the fee comes back when the player is not moved. Money is followed in the player's wallet, so a refund paid twice
 * or a fee kept for a move that never happened shows up as a wrong balance.
 */
class CrossServerWorldTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final String CREATIVE = "creative";
    private static final WorldName ARENA = WorldName.of("arena");
    private static final WorldRef ARENA_REF =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "arena");
    private static final Position SPAWN = Position.of(ARENA_REF, 0, 64, 0);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration SETTLE = Duration.ofSeconds(1);
    private static final BigDecimal FEE = new BigDecimal("10");
    private static final BigDecimal START = new BigDecimal("100");

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final MemoryHopStore store = new MemoryHopStore();
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final InMemoryWorlds.Table table = new InMemoryWorlds.Table();
    private final FakeWorldEngine lobbyEngine = new FakeWorldEngine();
    private final FakeWorldEngine survivalEngine = new FakeWorldEngine();
    private final FakePermissions permissions = new FakePermissions();
    private final Purse purse = new Purse();
    private final Moves moves = new Moves();
    private final Online online = new Online();
    private final List<String> connects = new ArrayList<>();
    private final HopResolvers lobbyResolvers = resolversOn(LOBBY, lobbyEngine);
    private final HopWatchdog watchdog = new HopWatchdog(store, lobbyResolvers, charges, scheduler, log, TTL, clock);
    private final HopArrival survival = arrivalOn(SURVIVAL, survivalEngine);
    private boolean unsafe;

    CrossServerWorldTest() {
        table.put(SURVIVAL, arena(FEE));
        survivalEngine.onDisk(ARENA, true, SPAWN);
        scheduler.online(ALEX);
        scheduler.online(SAM);
        online.here.add(ALEX.uuid());
        purse.wallet = START;
    }

    private static ManagedWorld arena(BigDecimal fee) {
        ManagedWorld world = ManagedWorld.created(ARENA, WorldSpec.normal(), true, Optional.empty(), NOW);
        return world.withSettings(world.settings().with(WorldProperties.ENTRY_FEE, fee));
    }

    private HopResolvers resolversOn(String server, FakeWorldEngine engine) {
        HopResolvers resolvers = new HopResolvers();
        resolvers.register(new WorldHopResolver(
                new InMemoryWorlds(table, server),
                engine,
                new WorldAccessPolicy(permissions, engine),
                moves,
                purse,
                position -> !unsafe,
                permissions,
                events,
                notifier.notifier(),
                scheduler));
        return resolvers;
    }

    private HopArrival arrivalOn(String server, FakeWorldEngine engine) {
        return new HopArrival(store, resolversOn(server, engine), charges, scheduler, log, server, SETTLE, TTL, clock);
    }

    /** {@code /worlds spawn} and {@code /worlds tp} on the lobby, with the worlds cross-server block on or off. */
    private WorldTeleportService lobby(boolean routed) {
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
        HopDepartures departures =
                new HopDepartures(HopGate.OPEN, proxy, watchdog, charges, lobbyResolvers, scheduler, LOBBY, clock);
        WorldHops hops = routed
                ? new WorldHops(Optional.of(departures), Optional.of(departures), LOBBY)
                : WorldHops.local(LOBBY);
        return new WorldTeleportService(
                new InMemoryWorlds(table, LOBBY),
                lobbyEngine,
                new WorldAccessPolicy(permissions, lobbyEngine),
                moves,
                purse,
                permissions,
                events,
                notifier.notifier(),
                scheduler,
                online,
                hops,
                log);
    }

    private Result<Unit, WorldError> spawnInArena() {
        return lobby(true).spawn(ALEX, ARENA);
    }

    private void join() {
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
    }

    @Test
    void aSpawnInAWorldOfAnotherServerChargesOnceLandsOnceAtItsSpawnAndKeepsTheFee() {
        assertThat(spawnInArena().isOk()).isTrue();
        assertThat(connects).containsExactly("Alex->survival");
        assertThat(store.find(ALEX.uuid()).map(PendingHop::kind)).contains(HopKind.WORLD);
        assertThat(purse.wallet).isEqualByComparingTo("90");

        // Two joins that both read the row before either claims it, then the origin's timer.
        store.afterNextFind(() -> survival.onArrival(ALEX));
        survival.onArrival(ALEX);
        scheduler.advance(SETTLE);
        scheduler.advance(TTL);

        assertThat(moves.made).containsExactly("Alex->arena@0,64,0 as SPAWN");
        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(purse.refunds).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly("world.enter.fee-charged", "world.cross-server.sending", "world.cross-server.arrived");
    }

    @Test
    void aHopTheProxyNeverCarriedOutIsRefundedAtTheTtl() {
        spawnInArena();

        scheduler.advance(TTL);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("world.cross-server.timed-out", "world.cross-server.refunded");
    }

    @Test
    void anUnsafeSpawnIsRefusedOnLandingAndRefundedOnce() {
        spawnInArena();
        unsafe = true;

        join();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys())
                .containsSubsequence(
                        "world.cross-server.unsafe", "world.cross-server.failed", "world.cross-server.refunded");
    }

    @Test
    void aPlayerWhoMayBypassTheSafetyCheckLandsOnAnUnsafeSpawn() {
        permissions.grant(ALEX, WorldHopResolver.BYPASS_SAFETY);
        unsafe = true;
        spawnInArena();

        join();

        assertThat(moves.made).hasSize(1);
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aWorldRemovedFromTheTargetBeforeThePlayerArrivesIsRefundedOnce() {
        spawnInArena();
        new InMemoryWorlds(table, SURVIVAL).delete(ARENA);

        join();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void anUnloadedWorldIsLoadedOnTheTargetAndLandedIn() {
        survivalEngine.unload(ARENA);
        spawnInArena();

        join();

        assertThat(survivalEngine.loads()).containsExactly(ARENA);
        assertThat(moves.made).hasSize(1);
    }

    @Test
    void aWorldThatWillNotLoadOnTheTargetIsRefundedOnce() {
        survivalEngine.unload(ARENA);
        survivalEngine.refuseLoads(true);
        spawnInArena();

        join();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aWorldThatFilledUpBeforeThePlayerLandedIsRefundedOnce() {
        ManagedWorld full = arena(FEE);
        table.put(SURVIVAL, full.withSettings(full.settings().with(WorldProperties.PLAYER_LIMIT, 1)));
        survivalEngine.players(ARENA, 1);
        spawnInArena();

        join();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).contains("world.enter.denied-full");
    }

    @Test
    void aLandingTheTeleportRefusesIsRefundedOnce() {
        moves.refuses = true;
        spawnInArena();

        join();
        scheduler.advance(TTL);

        assertThat(moves.made).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aPlayerWhoLeftBeforeTheHopTaskRanIsNeitherChargedNorSent() {
        online.here.remove(ALEX.uuid());

        spawnInArena();

        assertThat(purse.charges).isZero();
        assertThat(store.rows()).isEmpty();
        assertThat(connects).isEmpty();
    }

    @Test
    void aRestrictedWorldIsRefusedOnTheOriginBeforeAnythingIsCharged() {
        ManagedWorld restricted = arena(FEE);
        table.put(
                SURVIVAL, restricted.withSettings(restricted.settings().with(WorldProperties.ACCESS_RESTRICTED, true)));

        Result<Unit, WorldError> used = spawnInArena();

        assertThat(used.asError()).contains(WorldError.ACCESS_DENIED);
        assertThat(purse.charges).isZero();
        assertThat(connects).isEmpty();
        assertThat(notifier.keys()).containsExactly("world.enter.denied-permission");
    }

    @Test
    void aNameSeveralServersRunIsRefusedUntilOneIsNamed() {
        table.put(CREATIVE, arena(BigDecimal.ZERO));

        Result<Unit, WorldError> unnamed = spawnInArena();
        Result<Unit, WorldError> named = lobby(true).spawn(ALEX, ARENA, Optional.of(CREATIVE));

        assertThat(unnamed.asError()).contains(WorldError.AMBIGUOUS_SERVER);
        assertThat(named.isOk()).isTrue();
        assertThat(connects).containsExactly("Alex->creative");
        assertThat(purse.charges).isZero();
    }

    @Test
    void aWorldOfThisServerWinsOverOneOfTheSameNameElsewhere() {
        table.put(LOBBY, arena(BigDecimal.ZERO));
        lobbyEngine.onDisk(ARENA, true, SPAWN);

        lobby(true).spawn(ALEX, ARENA);

        assertThat(connects).isEmpty();
        assertThat(store.rows()).isEmpty();
        assertThat(moves.teleported).containsExactly("Alex->arena as SPAWN");
    }

    @Test
    void aLocalEntryTakesTheFeeOffTheTickAndThenMovesThePlayer() {
        table.put(LOBBY, arena(FEE));
        lobbyEngine.onDisk(ARENA, true, SPAWN);
        List<Runnable> lane = new ArrayList<>();
        scheduler.asyncOn(lane::add);

        lobby(true).spawn(ALEX, ARENA);
        assertThat(purse.charges).isZero();
        assertThat(moves.teleported).isEmpty();

        List.copyOf(lane).forEach(Runnable::run);
        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(moves.teleported).containsExactly("Alex->arena as SPAWN");
        assertThat(notifier.keys()).contains("world.enter.fee-charged");
    }

    @Test
    void aPlayerWhoCannotPayIsToldSoAndNotMoved() {
        table.put(LOBBY, arena(FEE));
        lobbyEngine.onDisk(ARENA, true, SPAWN);
        purse.wallet = new BigDecimal("5");

        lobby(true).spawn(ALEX, ARENA);

        assertThat(moves.teleported).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo("5");
        assertThat(notifier.keys()).contains("world.enter.fee-insufficient").doesNotContain("world.enter.fee-charged");
    }

    @Test
    void aRefusedLaunchGivesTheFeeBack() {
        table.put(LOBBY, arena(FEE));
        lobbyEngine.onDisk(ARENA, true, SPAWN);
        moves.refusesLaunch = true;

        lobby(true).spawn(ALEX, ARENA);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).doesNotContain("world.enter.fee-charged");
    }

    @Test
    void aPlayerWhoLeftBeforeTheirLocalEntryReachedThemIsNotMovedAndGetsTheFeeBack() {
        table.put(LOBBY, arena(FEE));
        lobbyEngine.onDisk(ARENA, true, SPAWN);
        scheduler.offline(ALEX);

        lobby(true).spawn(ALEX, ARENA);

        assertThat(moves.teleported).isEmpty();
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(notifier.keys()).doesNotContain("world.enter.fee-charged");
    }

    @Test
    void aStaffSendReachesAndIsReportedOnlyForAPlayerStillOnline() {
        table.put(LOBBY, arena(BigDecimal.ZERO));
        lobbyEngine.onDisk(ARENA, true, SPAWN);
        scheduler.offline(SAM);

        lobby(true).forcedHere(ALEX, SAM, ARENA);
        assertThat(moves.teleported).isEmpty();
        assertThat(notifier.keys()).doesNotContain("world.tp.other");

        scheduler.online(SAM);
        lobby(true).forcedHere(ALEX, SAM, ARENA);
        assertThat(moves.teleported).containsExactly("Sam->arena as ADMIN");
        assertThat(notifier.keys()).contains("world.tp.other");
    }

    @Test
    void anUnclaimedWorldCannotBeReached() {
        table.put(NetworkWorld.UNSCOPED, arena(BigDecimal.ZERO));
        new InMemoryWorlds(table, SURVIVAL).delete(ARENA);

        Result<Unit, WorldError> used = spawnInArena();

        assertThat(used.asError()).contains(WorldError.NOT_FOUND);
        assertThat(connects).isEmpty();
    }

    @Test
    void withTheCrossServerBlockOffAWorldOfAnotherServerIsNotFound() {
        Result<Unit, WorldError> used = lobby(false).spawn(ALEX, ARENA);

        assertThat(used.asError()).contains(WorldError.NOT_FOUND);
        assertThat(connects).isEmpty();
        assertThat(purse.charges).isZero();
    }

    @Test
    void aStaffSendSkipsTheFeeAndTheAccessRulesAndLandsAsAStaffTeleport() {
        ManagedWorld restricted = arena(FEE);
        table.put(
                SURVIVAL, restricted.withSettings(restricted.settings().with(WorldProperties.ACCESS_RESTRICTED, true)));

        lobby(true).forced(SAM, ALEX, ARENA);
        join();

        assertThat(purse.charges).isZero();
        assertThat(moves.made).containsExactly("Alex->arena@0,64,0 as ADMIN");
        assertThat(notifier.sent()).anySatisfy(line -> {
            assertThat(line.viewer()).isEqualTo(SAM);
            assertThat(line.key()).isEqualTo("world.tp.other");
        });
    }

    /** The player's wallet, as the entry fee port sees it. */
    private static final class Purse implements WorldEntryFee {

        BigDecimal wallet = BigDecimal.ZERO;
        int charges;
        int refunds;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return wallet.compareTo(amount) >= 0;
        }

        @Override
        public boolean charge(PlayerRef who, BigDecimal amount) {
            if (wallet.compareTo(amount) < 0) {
                return false;
            }
            wallet = wallet.subtract(amount);
            charges++;
            return true;
        }

        @Override
        public String currencyId() {
            return "gold";
        }

        @Override
        public boolean refund(PlayerRef who, BigDecimal amount, String currencyId) {
            wallet = wallet.add(amount);
            refunds++;
            return true;
        }
    }

    /** The moves the teleport context was asked to make. */
    private static final class Moves implements WorldTeleporter {

        final List<String> made = new ArrayList<>();
        final List<String> teleported = new ArrayList<>();
        boolean refuses;
        boolean refusesLaunch;

        @Override
        public boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
            if (refusesLaunch) {
                return false;
            }
            teleported.add(who.name() + "->" + to.world().name() + " as " + cause);
            return true;
        }

        @Override
        public void arrive(PlayerRef who, Position to, WorldTeleportCause cause, Runnable onLanded, Runnable onMissed) {
            if (refuses) {
                onMissed.run();
                return;
            }
            made.add(who.name() + "->" + to.world().name() + "@" + (int) to.x() + "," + (int) to.y() + ","
                    + (int) to.z() + " as " + cause);
            onLanded.run();
        }
    }

    /** Who is online on the origin when its async task runs. */
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
}
