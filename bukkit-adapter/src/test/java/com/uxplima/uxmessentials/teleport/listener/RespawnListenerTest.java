package com.uxplima.uxmessentials.teleport.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.RespawnListener;
import com.uxplima.uxmessentials.teleport.application.ResolveRespawn;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the death respawn chain. The listener walks the per-world chain on
 * {@link PlayerRespawnEvent} and redirects the event when a step resolves: a player with a cached home
 * respawns at the home, one without falls through to spawn, and an unconfigured world leaves the event's
 * vanilla respawn location untouched. The homes seam and the spawn directory are inline fakes so the chain
 * order is the only behaviour under test.
 */
class RespawnListenerTest {

    private ServerMock server;
    private World world;
    private WorldRef worldRef;
    private FakeHomeLocator homes;
    private FakeSpawnDirectory spawns;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        worldRef = BukkitRefs.toRef(world);
        homes = new FakeHomeLocator();
        spawns = new FakeSpawnDirectory();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void homeWinsWhenTheChainListsHomeFirstAndTheCacheHasOne() {
        Position home = new Position(worldRef, 10, 65, 20, 0f, 0f);
        homes.set(home);
        spawns.setSpawn(worldRef, new Position(worldRef, 0, 64, 0, 0f, 0f));
        RespawnListener listener = listener(chain("home", "spawn"));

        PlayerMock player = server.addPlayer("Alice");
        PlayerRespawnEvent event = respawnEvent(player);
        listener.onRespawn(event);

        assertThat(event.getRespawnLocation()).isEqualTo(BukkitRefs.toLocation(world, home));
    }

    @Test
    void fallsThroughToSpawnWhenThePlayerHasNoHome() {
        Position spawn = new Position(worldRef, 0, 64, 0, 0f, 0f);
        spawns.setSpawn(worldRef, spawn);
        RespawnListener listener = listener(chain("home", "spawn"));

        PlayerMock player = server.addPlayer("Bob");
        PlayerRespawnEvent event = respawnEvent(player);
        listener.onRespawn(event);

        assertThat(event.getRespawnLocation()).isEqualTo(BukkitRefs.toLocation(world, spawn));
    }

    @Test
    void leavesTheEventUntouchedWhenNoChainIsConfigured() {
        homes.set(new Position(worldRef, 10, 65, 20, 0f, 0f));
        spawns.setSpawn(worldRef, new Position(worldRef, 0, 64, 0, 0f, 0f));
        RespawnListener listener = listener(emptyConfig());

        PlayerMock player = server.addPlayer("Cara");
        Location vanilla = world.getSpawnLocation().clone().add(5, 0, 5);
        PlayerRespawnEvent event = respawnEventAt(player, vanilla);
        listener.onRespawn(event);

        assertThat(event.getRespawnLocation()).isEqualTo(vanilla);
    }

    private RespawnListener listener(ConfigStore config) {
        return new RespawnListener(new ResolveRespawn(new TeleportSettings(config)), spawns, homes, server);
    }

    private PlayerRespawnEvent respawnEvent(PlayerMock player) {
        return respawnEventAt(player, world.getSpawnLocation());
    }

    private static PlayerRespawnEvent respawnEventAt(PlayerMock player, Location at) {
        return new PlayerRespawnEvent(player, at, false, false, false, PlayerRespawnEvent.RespawnReason.DEATH);
    }

    private ConfigStore chain(String... steps) {
        MapConfigStore config = new MapConfigStore();
        config.lists.put("respawn.chain." + world.getName(), List.of(steps));
        return config;
    }

    private static ConfigStore emptyConfig() {
        return new MapConfigStore();
    }

    /** A {@link ConfigStore} backed by an in-memory map; only the string-list getter carries test data. */
    private static final class MapConfigStore implements ConfigStore {
        private final Map<String, List<String>> lists = new HashMap<>();

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

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return lists.getOrDefault(path, fallback);
        }
    }

    /** A home-respawn seam returning a single fixed position, or empty when none is set. */
    private static final class FakeHomeLocator
            implements com.uxplima.uxmessentials.homes.application.HomeRespawnLocator {
        private Optional<Position> home = Optional.empty();

        void set(Position position) {
            this.home = Optional.of(position);
        }

        @Override
        public Optional<Position> respawnHome(PlayerRef who) {
            return home;
        }
    }

    /** A spawn directory answering only the per-world default spawn; the rest are unused in these tests. */
    private static final class FakeSpawnDirectory implements SpawnDirectory {
        private final Map<WorldRef, Position> spawnsByWorld = new HashMap<>();

        void setSpawn(WorldRef world, Position position) {
            spawnsByWorld.put(world, position);
        }

        @Override
        public Optional<Position> defaultSpawn(WorldRef world) {
            return Optional.ofNullable(spawnsByWorld.get(world));
        }

        @Override
        public Optional<Position> operatorSpawn(WorldRef world) {
            return Optional.ofNullable(spawnsByWorld.get(world));
        }

        @Override
        public Optional<Position> mainSpawn() {
            return Optional.empty();
        }

        @Override
        public Optional<Position> namedSpawn(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<SpawnMirror> mirrorFor(WorldRef world) {
            return Optional.empty();
        }

        @Override
        public void setDefaultSpawn(WorldRef world, Position position) {
            spawnsByWorld.put(world, position);
        }

        @Override
        public void setNamedSpawn(String name, Position position) {}

        @Override
        public void setMainSpawn(Position position) {}

        @Override
        public boolean removeDefaultSpawn(WorldRef world) {
            return spawnsByWorld.remove(world) != null;
        }

        @Override
        public void setMirror(SpawnMirror mirror) {}
    }
}
