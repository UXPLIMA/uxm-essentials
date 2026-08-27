package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.DamageEvents;
import com.uxplima.uxmessentials.worlds.application.ResolveVoidRescue;
import com.uxplima.uxmessentials.worlds.application.port.RescueTargets;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the two void-rescue triggers: the void damage every armed world catches, and the
 * earlier height trigger a world arms by also setting {@code void-rescue-y}. Spectators and holders of the
 * exempt node fall through both, and an unarmed world leaves the damage exactly as vanilla dealt it.
 */
class VoidRescueListenerTest {

    private ServerMock server;
    private World world;
    private FakeRepository repository;
    private RecordingTeleporter teleporter;
    private FakePermissions permissions;
    private VoidRescueListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("lobby");
        repository = new FakeRepository();
        teleporter = new RecordingTeleporter();
        permissions = new FakePermissions();
        WorldRef ref = new WorldRef(world.getUID(), world.getName());
        RescueTargets targets = new RescueTargets() {
            @Override
            public Optional<Position> spawn(WorldRef which) {
                return Optional.of(Position.of(ref, 0, 80, 0));
            }

            @Override
            public Optional<Position> warp(String name) {
                return Optional.empty();
            }
        };
        WorldLookup lookup = new WorldLookup() {
            @Override
            public Optional<WorldRef> findByName(String name) {
                return name.equals(ref.name()) ? Optional.of(ref) : Optional.empty();
            }

            @Override
            public Optional<WorldRef> findByUid(UUID uid) {
                return uid.equals(ref.uid()) ? Optional.of(ref) : Optional.empty();
            }
        };
        ResolveVoidRescue rescue = new ResolveVoidRescue(
                repository, targets, lookup, new SilentLogger(), Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC));
        listener = new VoidRescueListener(rescue, teleporter, permissions);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void voidDamageInAnArmedWorldIsCancelledAndThePlayerIsMoved() {
        arm("spawn", null);
        PlayerMock alice = server.addPlayer("Alice");

        EntityDamageEvent damage = voidDamage(alice);
        listener.onVoidDamage(damage);

        assertThat(damage.isCancelled()).isTrue();
        assertThat(teleporter.causes).containsExactly(WorldTeleportCause.VOID_RESCUE);
        assertThat(teleporter.destinations.get(0).y()).isEqualTo(80);
    }

    @Test
    void voidDamageInAnUnarmedWorldIsLeftAlone() {
        PlayerMock alice = server.addPlayer("Alice");

        EntityDamageEvent damage = voidDamage(alice);
        listener.onVoidDamage(damage);

        assertThat(damage.isCancelled()).isFalse();
        assertThat(teleporter.causes).isEmpty();
    }

    @Test
    void aSpectatorKeepsFalling() {
        arm("spawn", null);
        PlayerMock ghost = server.addPlayer("Ghost");
        ghost.setGameMode(GameMode.SPECTATOR);

        EntityDamageEvent damage = voidDamage(ghost);
        listener.onVoidDamage(damage);

        assertThat(damage.isCancelled()).isFalse();
        assertThat(teleporter.causes).isEmpty();
    }

    @Test
    void anExemptPlayerKeepsFalling() {
        arm("spawn", null);
        PlayerMock staff = server.addPlayer("Staff");
        permissions.grant(staff.getUniqueId(), VoidRescueListener.EXEMPT_NODE);

        EntityDamageEvent damage = voidDamage(staff);
        listener.onVoidDamage(damage);

        assertThat(damage.isCancelled()).isFalse();
        assertThat(teleporter.causes).isEmpty();
    }

    @Test
    void fallingPastTheTriggerHeightRescuesBeforeTheVoid() {
        arm("spawn", "-10");
        PlayerMock alice = server.addPlayer("Alice");

        listener.onMove(move(alice, 5, -11));

        assertThat(teleporter.causes).containsExactly(WorldTeleportCause.VOID_RESCUE);
    }

    @Test
    void aMoveAboveTheTriggerHeightOrUpwardsIsIgnored() {
        arm("spawn", "-10");
        PlayerMock alice = server.addPlayer("Alice");

        listener.onMove(move(alice, 5, 4));
        listener.onMove(move(alice, -11, -9));

        assertThat(teleporter.causes).isEmpty();
    }

    @Test
    void aWorldWithoutATriggerHeightIgnoresMovesEntirely() {
        arm("spawn", null);
        PlayerMock alice = server.addPlayer("Alice");

        listener.onMove(move(alice, 5, -300));

        assertThat(teleporter.causes).isEmpty();
    }

    private void arm(String chain, @Nullable String triggerY) {
        WorldSettings settings = WorldSettings.defaults().withRaw("void-rescue", chain);
        if (triggerY != null) {
            settings = settings.withRaw("void-rescue-y", triggerY);
        }
        repository.put(new ManagedWorld(
                WorldName.of(world.getName()),
                WorldSpec.normal(),
                Optional.empty(),
                true,
                true,
                Optional.of(world.getUID()),
                Instant.EPOCH,
                Optional.empty(),
                settings));
    }

    private EntityDamageEvent voidDamage(PlayerMock player) {
        return DamageEvents.of(
                player,
                DamageCause.VOID,
                DamageSource.builder(DamageType.OUT_OF_WORLD).build(),
                4.0);
    }

    private PlayerMoveEvent move(PlayerMock player, int fromY, int toY) {
        return new PlayerMoveEvent(player, new Location(world, 0.5, fromY, 0.5), new Location(world, 0.5, toY, 0.5));
    }

    private static final class RecordingTeleporter implements WorldTeleporter {
        private final List<WorldTeleportCause> causes = new ArrayList<>();
        private final List<Position> destinations = new ArrayList<>();

        @Override
        public boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
            causes.add(cause);
            destinations.add(to);
            return true;
        }
    }

    private static final class FakePermissions implements Permissions {
        private final Map<UUID, Set<String>> granted = new HashMap<>();

        void grant(UUID who, String node) {
            granted.computeIfAbsent(who, uuid -> new HashSet<>()).add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.getOrDefault(who.uuid(), Set.of()).contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class FakeRepository implements WorldRepository {
        private final Map<WorldName, ManagedWorld> byName = new HashMap<>();

        void put(ManagedWorld managed) {
            byName.put(managed.name(), managed);
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
        public void save(ManagedWorld managed) {
            byName.put(managed.name(), managed);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name);
        }
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
