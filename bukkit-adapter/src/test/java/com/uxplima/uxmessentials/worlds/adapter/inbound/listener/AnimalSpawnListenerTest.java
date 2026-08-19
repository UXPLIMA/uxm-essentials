package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The per-world {@code spawn-animals} setting, which has no server-side toggle left in Paper 26.2: a natural
 * animal spawn in a world that switched animals off is cancelled, a monster is left to its own setting, and a
 * spawn from an egg or a spawner is never touched.
 */
class AnimalSpawnListenerTest {

    private ServerMock server;
    private World world;
    private FakeWorldRepository repository;
    private AnimalSpawnListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("w");
        repository = new FakeWorldRepository();
        listener = new AnimalSpawnListener(repository);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cancelsANaturalAnimalSpawnWhenTheWorldSaysNo() {
        repository.put(managed(false));

        CreatureSpawnEvent event = spawn(EntityType.COW, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void leavesTheSpawnAloneWhenTheWorldAllowsAnimals() {
        repository.put(managed(true));

        CreatureSpawnEvent event = spawn(EntityType.COW, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void leavesMonstersToTheirOwnSetting() {
        repository.put(managed(false));

        CreatureSpawnEvent event = spawn(EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void leavesADeliberateSpawnAlone() {
        repository.put(managed(false));

        CreatureSpawnEvent event = spawn(EntityType.COW, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
        listener.onSpawn(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void leavesAWorldTheModuleDoesNotManageAlone() {
        CreatureSpawnEvent event = spawn(EntityType.COW, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(event);

        assertThat(event.isCancelled()).isFalse();
    }

    private CreatureSpawnEvent spawn(EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        Entity entity = world.spawnEntity(new Location(world, 0, 64, 0), type);
        return new CreatureSpawnEvent((org.bukkit.entity.LivingEntity) entity, reason);
    }

    private ManagedWorld managed(boolean allowAnimals) {
        WorldSettings settings = WorldSettings.defaults().with(WorldProperties.SPAWN_ANIMALS, allowAnimals);
        return new ManagedWorld(
                WorldName.of("w"),
                WorldSpec.normal(),
                Optional.empty(),
                true,
                true,
                Optional.of(world.getUID()),
                Instant.EPOCH,
                Optional.empty(),
                settings);
    }

    /** An in-memory {@link WorldRepository} returning only the worlds a test seeds. */
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
}
