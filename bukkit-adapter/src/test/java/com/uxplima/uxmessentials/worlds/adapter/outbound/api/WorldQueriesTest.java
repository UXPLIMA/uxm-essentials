package com.uxplima.uxmessentials.worlds.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmWorld;
import com.uxplima.uxmessentials.api.view.UxmWorldAccess;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published worlds query: it lists only the worlds the plugin manages, it reports loaded state from the
 * server rather than from the register, and its entry decision is the one the command would make.
 */
class WorldQueriesTest {

    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Instant CREATED = Instant.parse("2026-08-09T12:00:00Z");

    private FakeWorldRepository repository;
    private FakeWorldEngine engine;
    private NodePermissions permissions;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeWorldRepository();
        engine = new FakeWorldEngine();
        permissions = new NodePermissions();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyRegisterReadRunsOffTheCallingThread() {
        queries().list().join();
        queries().get("world").join();
        queries().access(PLAYER.uuid(), "world").join();

        assertThat(scheduler.asyncCalls()).isEqualTo(3);
    }

    @Test
    void whetherAWorldIsLoadedIsAnsweredWithoutWaiting() {
        engine.load("world");

        assertThat(queries().isLoaded("world")).isTrue();
        assertThat(scheduler.asyncCalls())
                .as("the server already knows which worlds are loaded, so there is nothing to wait for")
                .isZero();
    }

    @Test
    void theViewCarriesWhatTheWorldWasCreatedWith() {
        repository.put(world("mines"));
        engine.load("mines");
        engine.players("mines", 3);

        UxmWorld view = queries().get("mines").join().orElseThrow();

        assertThat(view.name()).isEqualTo("mines");
        assertThat(view.alias()).isEmpty();
        assertThat(view.displayName()).isEqualTo("mines");
        assertThat(view.environment()).isEqualTo("NORMAL");
        assertThat(view.generation()).isEqualTo("NORMAL");
        assertThat(view.autoLoad()).isTrue();
        assertThat(view.loaded()).isTrue();
        assertThat(view.playerCount()).isEqualTo(3);
    }

    @Test
    void anAliasIsWhatTheWorldDisplaysAs() {
        repository.put(world("mines").withAlias(Optional.of("The Mines")));

        assertThat(queries().get("mines").join().orElseThrow().displayName()).isEqualTo("The Mines");
    }

    @Test
    void anUnloadedWorldStillDescribesItselfAndHoldsNobody() {
        repository.put(world("archive"));

        UxmWorld view = queries().get("archive").join().orElseThrow();

        assertThat(view.loaded()).isFalse();
        assertThat(view.playerCount()).isZero();
    }

    @Test
    void aWorldTheServerHasButThePluginDoesNotManageIsNotInTheRegister() {
        engine.load("nether");

        assertThat(queries().list().join()).isEmpty();
        assertThat(queries().get("nether").join())
                .as("this is the plugin's register, not the server's world list")
                .isEmpty();
    }

    @Test
    void aNameNoWorldCouldHaveIsSimplyAbsent() {
        assertThat(queries().get("../escape").join()).isEmpty();
        assertThat(queries().isLoaded("../escape")).isFalse();
        assertThat(scheduler.asyncCalls())
                .as("an impossible name is answered before anything is scheduled")
                .isZero();
    }

    @Test
    void aRestrictedWorldRefusesAPlayerWithoutItsNode() {
        repository.put(restricted("staff"));

        assertThat(queries().access(PLAYER.uuid(), "staff").join()).isEqualTo(UxmWorldAccess.DENIED_PERMISSION);

        permissions.grant(PLAYER, WorldAccessPolicy.enterNode(WorldName.of("staff")));
        assertThat(queries().access(PLAYER.uuid(), "staff").join()).isEqualTo(UxmWorldAccess.ALLOWED);
    }

    @Test
    void aFullWorldRefusesEvenAPlayerWhoIsAllowedIn() {
        ManagedWorld arena = world("arena");
        repository.put(arena.withSettings(arena.settings().with(WorldProperties.PLAYER_LIMIT, 2)));
        engine.load("arena");
        engine.players("arena", 2);

        assertThat(queries().access(PLAYER.uuid(), "arena").join()).isEqualTo(UxmWorldAccess.DENIED_FULL);
    }

    @Test
    void aWorldThePluginDoesNotManageCarriesNoEntryRules() {
        assertThat(queries().access(PLAYER.uuid(), "nether").join())
                .as("the plugin has no rules for a world it was never given, so it refuses nobody")
                .isEqualTo(UxmWorldAccess.ALLOWED);
    }

    private WorldQueries queries() {
        return new WorldQueries(
                repository,
                engine,
                new WorldAccessPolicy(permissions, engine),
                new QueryDoubles.MapLookup().with(PLAYER),
                scheduler);
    }

    private static ManagedWorld world(String name) {
        return ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), CREATED);
    }

    private static ManagedWorld restricted(String name) {
        ManagedWorld base = world(name);
        return base.withSettings(base.settings().with(WorldProperties.ACCESS_RESTRICTED, true));
    }

    private static final class FakeWorldRepository implements WorldRepository {

        private final Map<WorldName, ManagedWorld> worlds = new LinkedHashMap<>();

        void put(ManagedWorld world) {
            worlds.put(world.name(), world);
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(worlds.get(name));
        }

        @Override
        public java.util.List<ManagedWorld> all() {
            return java.util.List.copyOf(worlds.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return worlds.containsKey(name);
        }

        @Override
        public void save(ManagedWorld world) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void delete(WorldName name) {
            throw new AssertionError("a query must never write");
        }
    }

    /** Knows which worlds are loaded and how many players they hold; every world operation is a trap. */
    private static final class FakeWorldEngine implements WorldEngine {

        private final Set<String> loaded = new HashSet<>();
        private final Map<String, Integer> players = new HashMap<>();

        void load(String name) {
            loaded.add(name);
        }

        void players(String name, int count) {
            players.put(name, count);
        }

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            throw new AssertionError("a query must never touch a world");
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            throw new AssertionError("a query must never touch a world");
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            throw new AssertionError("a query must never touch a world");
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            throw new AssertionError("a query must never touch a world");
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            throw new AssertionError("a query must never read the world folder");
        }

        @Override
        public boolean exists(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name.value());
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return loaded.stream().map(WorldName::of).collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return players.getOrDefault(name.value(), 0);
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    /** Grants exactly the nodes it was told about, so the entry gate has something to refuse. */
    private static final class NodePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

        void grant(PlayerRef who, String node) {
            granted.add(who.uuid() + "|" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + "|" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
