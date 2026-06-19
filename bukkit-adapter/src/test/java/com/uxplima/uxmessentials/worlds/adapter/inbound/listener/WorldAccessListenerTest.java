package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the cross-world access gate. A cancellable {@link PlayerTeleportEvent} from an
 * open world into a restricted one is constructed and {@link WorldAccessListener#onTeleport} is invoked
 * directly: the event is cancelled and a {@link WorldEntryDenied} published when the player lacks the
 * world's enter node, and left untouched when they hold it, when the worlds match, when the destination is
 * unmanaged, or when the destination carries no restriction at all. The fast-exit case asserts the policy
 * and engine are never consulted, which a counting fake engine and a recording policy seam verify.
 */
class WorldAccessListenerTest {

    private static final String ENTER_NODE = "uxmessentials.world.locked.enter";

    private ServerMock server;
    private World open;
    private World locked;
    private FakeWorldRepository repository;
    private FakePermissions permissions;
    private CountingEngine engine;
    private RecordingPublisher events;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        open = server.addSimpleWorld("open");
        locked = server.addSimpleWorld("locked");
        repository = new FakeWorldRepository();
        permissions = new FakePermissions();
        engine = new CountingEngine();
        events = new RecordingPublisher();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cancelsTeleportIntoRestrictedWorldWithoutEnterNode() {
        repository.put(restricted("locked"));
        PlayerMock alice = server.addPlayer("Alice");

        PlayerTeleportEvent event = crossWorld(alice, open, locked);
        listener().onTeleport(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(events.published).hasSize(1).first().isInstanceOf(WorldEntryDenied.class);
    }

    @Test
    void allowsTeleportIntoRestrictedWorldWhenPlayerHoldsEnterNode() {
        repository.put(restricted("locked"));
        PlayerMock bob = server.addPlayer("Bob");
        permissions.grant(bob.getUniqueId(), ENTER_NODE);

        PlayerTeleportEvent event = crossWorld(bob, open, locked);
        listener().onTeleport(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void ignoresSameWorldTeleport() {
        repository.put(restricted("locked"));
        PlayerMock carol = server.addPlayer("Carol");

        PlayerTeleportEvent event = crossWorld(carol, locked, locked);
        listener().onTeleport(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void ignoresTeleportIntoUnmanagedWorld() {
        PlayerMock dave = server.addPlayer("Dave");

        PlayerTeleportEvent event = crossWorld(dave, open, locked);
        listener().onTeleport(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void fastExitsForUnrestrictedDestinationWithoutConsultingPolicy() {
        repository.put(unrestricted("locked"));
        PlayerMock erin = server.addPlayer("Erin");

        PlayerTeleportEvent event = crossWorld(erin, open, locked);
        listener().onTeleport(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(events.published).isEmpty();
        assertThat(engine.playerCountCalls.get()).isZero();
        assertThat(permissions.checks.get()).isZero();
    }

    @Test
    void redirectsRestrictedLoginToDefaultWorldSpawnWhenEnabled() {
        repository.put(restricted("locked"));
        PlayerMock frank = inWorld("Frank", locked);
        WorldTeleportService teleportService = mock(WorldTeleportService.class);

        joinListener(teleportService, true).onJoin(joinEvent(frank));

        PlayerRef who = new PlayerRef(frank.getUniqueId(), frank.getName());
        verify(teleportService, times(1)).forced(who, who, WorldName.of("open"));
    }

    @Test
    void doesNotRedirectRestrictedLoginWhenRedirectDisabled() {
        repository.put(restricted("locked"));
        PlayerMock grace = inWorld("Grace", locked);
        WorldTeleportService teleportService = mock(WorldTeleportService.class);

        joinListener(teleportService, false).onJoin(joinEvent(grace));

        verifyNoInteractions(teleportService);
    }

    @Test
    void doesNotRedirectLoginWhenPlayerHoldsEnterNode() {
        repository.put(restricted("locked"));
        PlayerMock heidi = inWorld("Heidi", locked);
        permissions.grant(heidi.getUniqueId(), ENTER_NODE);
        WorldTeleportService teleportService = mock(WorldTeleportService.class);

        joinListener(teleportService, true).onJoin(joinEvent(heidi));

        verifyNoInteractions(teleportService);
    }

    @Test
    void doesNotRedirectRestrictedLoginWhenNoDefaultWorldConfigured() {
        repository.put(restricted("locked"));
        engine.defaultWorld(null);
        PlayerMock ivan = inWorld("Ivan", locked);
        WorldTeleportService teleportService = mock(WorldTeleportService.class);

        joinListener(teleportService, true).onJoin(joinEvent(ivan));

        verifyNoInteractions(teleportService);
    }

    private WorldAccessListener listener() {
        WorldAccessPolicy policy = new WorldAccessPolicy(permissions, engine);
        WorldNotifier notifier = mock(WorldNotifier.class);
        WorldTeleportService teleportService = mock(WorldTeleportService.class);
        return new WorldAccessListener(
                repository, policy, teleportService, engine, events, new InlineScheduler(), notifier, true);
    }

    private PlayerTeleportEvent crossWorld(PlayerMock player, World from, World to) {
        return new PlayerTeleportEvent(player, new Location(from, 0, 64, 0), new Location(to, 0, 64, 0));
    }

    /** A listener wired with a caller-supplied teleport mock so the redirect hand-off can be verified. */
    private WorldAccessListener joinListener(WorldTeleportService teleportService, boolean redirectOnRestrictedJoin) {
        WorldAccessPolicy policy = new WorldAccessPolicy(permissions, engine);
        WorldNotifier notifier = mock(WorldNotifier.class);
        return new WorldAccessListener(
                repository,
                policy,
                teleportService,
                engine,
                events,
                new InlineScheduler(),
                notifier,
                redirectOnRestrictedJoin);
    }

    /** A player added to the server and placed into {@code world}, the world {@code onJoin} reads. */
    private PlayerMock inWorld(String name, World world) {
        PlayerMock player = server.addPlayer(name);
        player.teleport(new Location(world, 0, 64, 0));
        return player;
    }

    private PlayerJoinEvent joinEvent(PlayerMock player) {
        return new PlayerJoinEvent(player, Component.empty());
    }

    private ManagedWorld restricted(String name) {
        WorldSettings settings = WorldSettings.defaults().with(WorldProperties.ACCESS_RESTRICTED, true);
        return managed(name, settings);
    }

    private ManagedWorld unrestricted(String name) {
        return managed(name, WorldSettings.defaults());
    }

    private ManagedWorld managed(String name, WorldSettings settings) {
        return new ManagedWorld(
                WorldName.of(name),
                WorldSpec.normal(),
                Optional.empty(),
                true,
                true,
                Optional.empty(),
                Instant.EPOCH,
                Optional.empty(),
                settings);
    }

    /** An in-memory {@link WorldRepository} returning only the worlds explicitly seeded by a test. */
    private static final class FakeWorldRepository implements WorldRepository {
        private final Map<WorldName, ManagedWorld> byName = new HashMap<>();

        void put(ManagedWorld world) {
            byName.put(world.name(), world);
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return byName.containsKey(name);
        }

        @Override
        public void save(ManagedWorld world) {
            byName.put(world.name(), world);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name);
        }
    }

    /** A {@link Permissions} stub granting only explicitly seeded nodes and counting every {@code has} call. */
    private static final class FakePermissions implements Permissions {
        private final Map<UUID, Set<String>> nodes = new HashMap<>();
        private final AtomicInteger checks = new AtomicInteger();

        void grant(UUID player, String node) {
            nodes.computeIfAbsent(player, ignored -> new java.util.HashSet<>()).add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            checks.incrementAndGet();
            return nodes.getOrDefault(who.uuid(), Set.of()).contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** A {@link WorldEngine} that counts {@code playerCount} calls; only the access-gate hooks are exercised. */
    private static final class CountingEngine implements WorldEngine {
        private final AtomicInteger playerCountCalls = new AtomicInteger();
        private Optional<WorldName> defaultWorld = Optional.of(WorldName.of("open"));

        void defaultWorld(@Nullable WorldName name) {
            defaultWorld = Optional.ofNullable(name);
        }

        @Override
        public int playerCount(WorldName name) {
            playerCountCalls.incrementAndGet();
            return 0;
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return defaultWorld;
        }

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return false;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return false;
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.of();
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    /** A {@link com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher} recording every event. */
    private static final class RecordingPublisher
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        private final List<DomainEvent> published = new java.util.ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** A {@link Scheduler} that runs every task inline so the entity-thread hop is deterministic in tests. */
    private static final class InlineScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
