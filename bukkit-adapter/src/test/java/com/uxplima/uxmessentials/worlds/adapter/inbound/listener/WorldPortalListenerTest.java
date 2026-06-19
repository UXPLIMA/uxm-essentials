package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import com.uxplima.uxmessentials.worlds.application.ResolvePortalDestination;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the portal-redirect adapter. A real {@link PlayerPortalEvent} is constructed with each
 * teleport cause and {@link WorldPortalListener#onPortal} invoked directly. MockBukkit fires this event with a
 * settable {@link TeleportCause} and exposes a readable, settable {@code getTo()}, so the full live path is
 * asserted: a nether portal in a linked source world is rewritten to the scaled exit in the loaded target world,
 * while an unlinked source, an unloaded target (warn-once), and a non-portal cause leave the destination at its
 * vanilla value. The {@link ResolvePortalDestination} runs over an in-memory repository so no DB is touched.
 */
class WorldPortalListenerTest {

    private static final WorldName OVERWORLD = WorldName.of("overworld");
    private static final WorldName NETHER = WorldName.of("thenether");

    private ServerMock server;
    private World overworld;
    private FakeWorldRepository repository;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("overworld");
        repository = new FakeWorldRepository();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rewritesNetherPortalToScaledExitInLoadedLinkedTarget() {
        repository.save(linked(OVERWORLD, WorldSpec.normal(), WorldProperties.PORTAL_NETHER_LINK, "thenether"));
        repository.save(world(NETHER, netherSpec()));
        World target = server.addSimpleWorld("thenether");
        PlayerMock alice = server.addPlayer("Alice");

        Location from = new Location(overworld, 80, 64, -16, 90f, 10f);
        PlayerPortalEvent event = portal(alice, from, TeleportCause.NETHER_PORTAL);
        listener().onPortal(event);

        Location to = event.getTo();
        assertThat(to.getWorld()).isEqualTo(target);
        assertThat(to.getX()).isEqualTo(10.0);
        assertThat(to.getY()).isEqualTo(64.0);
        assertThat(to.getZ()).isEqualTo(-2.0);
        assertThat(to.getYaw()).isEqualTo(90f);
        assertThat(to.getPitch()).isEqualTo(10f);
        assertThat(event.getCanCreatePortal()).isTrue();
    }

    @Test
    void leavesPortalUntouchedWhenSourceWorldHasNoLink() {
        repository.save(world(OVERWORLD, WorldSpec.normal()));
        PlayerMock bob = server.addPlayer("Bob");

        Location from = new Location(overworld, 80, 64, -16);
        Location originalTo = from.clone();
        PlayerPortalEvent event = portal(bob, from, originalTo, TeleportCause.NETHER_PORTAL);
        listener().onPortal(event);

        assertThat(event.getTo()).isEqualTo(originalTo);
    }

    @Test
    void leavesPortalUntouchedAndWarnsOnceWhenLinkedTargetIsNotLoaded() {
        repository.save(linked(OVERWORLD, WorldSpec.normal(), WorldProperties.PORTAL_NETHER_LINK, "thenether"));
        repository.save(world(NETHER, netherSpec()));
        // The target world is registered in the repository but never added to the live server.
        PlayerMock carol = server.addPlayer("Carol");
        WorldPortalListener listener = listener();

        Location from = new Location(overworld, 80, 64, -16);
        Location originalTo = from.clone();
        PlayerPortalEvent event = portal(carol, from, originalTo, TeleportCause.NETHER_PORTAL);
        listener.onPortal(event);

        assertThat(event.getTo()).isEqualTo(originalTo);

        // Warn-once: the seam returns empty a second time without re-warning (the set already holds the name).
        assertThat(listener.destinationFor(TeleportCause.NETHER_PORTAL, from)).isEmpty();
        assertThat(recordedWarnings).hasSize(1);
        assertThat(recordedWarnings.get(0)).contains("thenether");
    }

    @Test
    void ignoresNonPortalTeleportCause() {
        repository.save(linked(OVERWORLD, WorldSpec.normal(), WorldProperties.PORTAL_NETHER_LINK, "thenether"));
        repository.save(world(NETHER, netherSpec()));
        server.addSimpleWorld("thenether");
        PlayerMock dave = server.addPlayer("Dave");

        Location from = new Location(overworld, 80, 64, -16);
        Location originalTo = from.clone();
        PlayerPortalEvent event = portal(dave, from, originalTo, TeleportCause.ENDER_PEARL);
        listener().onPortal(event);

        assertThat(event.getTo()).isEqualTo(originalTo);
    }

    private final List<String> recordedWarnings = new ArrayList<>();

    private WorldPortalListener listener() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                recordedWarnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return new WorldPortalListener(new ResolvePortalDestination(repository), server, log);
    }

    private PlayerPortalEvent portal(PlayerMock player, Location from, TeleportCause cause) {
        return portal(player, from, from.clone(), cause);
    }

    private PlayerPortalEvent portal(PlayerMock player, Location from, Location to, TeleportCause cause) {
        return new PlayerPortalEvent(player, from, to, cause);
    }

    private static WorldSpec netherSpec() {
        return new WorldSpec(
                WorldEnvironment.NETHER,
                WorldGenType.NORMAL,
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
    }

    private static ManagedWorld world(WorldName name, WorldSpec spec) {
        return ManagedWorld.created(name, spec, true, Optional.empty(), Instant.EPOCH);
    }

    private static ManagedWorld linked(WorldName name, WorldSpec spec, WorldProperty<String> link, String target) {
        ManagedWorld base = world(name, spec);
        return base.withSettings(base.settings().with(link, target));
    }

    /** An in-memory {@link WorldRepository} returning only the worlds explicitly seeded by a test. */
    private static final class FakeWorldRepository implements WorldRepository {
        private final Map<String, ManagedWorld> store = new HashMap<>();

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(store.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return store.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            store.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            store.remove(name.value());
        }
    }
}
