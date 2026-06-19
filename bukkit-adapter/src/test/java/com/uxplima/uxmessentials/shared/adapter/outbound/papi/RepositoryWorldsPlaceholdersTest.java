package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * {@link RepositoryWorldsPlaceholders} over fakes of the two worlds ports: the managed count reads the
 * registry size, the loaded count the live loaded-name set, and the default-world reads pull the engine's
 * primary world and its player count. An engine with no primary world degrades {@code defaultWorld} to empty
 * and {@code defaultWorldPlayers} to zero.
 */
class RepositoryWorldsPlaceholdersTest {

    @Test
    void managedCountReadsTheRegistrySize() {
        RepositoryWorldsPlaceholders seam =
                new RepositoryWorldsPlaceholders(new FakeWorldRepository(3), new FakeWorldEngine());

        assertThat(seam.managedCount()).isEqualTo(3);
    }

    @Test
    void loadedCountReadsTheLoadedNameSetSize() {
        FakeWorldEngine engine = new FakeWorldEngine().loaded("world", "world_nether");
        RepositoryWorldsPlaceholders seam = new RepositoryWorldsPlaceholders(new FakeWorldRepository(0), engine);

        assertThat(seam.loadedCount()).isEqualTo(2);
    }

    @Test
    void defaultWorldReadsTheEnginePrimaryWorldName() {
        FakeWorldEngine engine = new FakeWorldEngine().defaultWorld("world").playerCount("world", 7);
        RepositoryWorldsPlaceholders seam = new RepositoryWorldsPlaceholders(new FakeWorldRepository(1), engine);

        assertThat(seam.defaultWorld()).contains("world");
        assertThat(seam.defaultWorldPlayers()).isEqualTo(7);
    }

    @Test
    void defaultWorldIsEmptyAndPlayersZeroWhenNoPrimaryWorld() {
        RepositoryWorldsPlaceholders seam =
                new RepositoryWorldsPlaceholders(new FakeWorldRepository(0), new FakeWorldEngine());

        assertThat(seam.defaultWorld()).isEmpty();
        assertThat(seam.defaultWorldPlayers()).isZero();
    }

    /** A {@link WorldRepository} fake whose {@link #all()} returns {@code count} managed worlds. */
    private static final class FakeWorldRepository implements WorldRepository {

        private final List<ManagedWorld> worlds;

        FakeWorldRepository(int count) {
            this.worlds = IntStream.range(0, count)
                    .mapToObj(i -> ManagedWorld.created(
                            WorldName.of("world_" + i), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH))
                    .toList();
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return worlds.stream().filter(w -> w.name().equals(name)).findFirst();
        }

        @Override
        public List<ManagedWorld> all() {
            return worlds;
        }

        @Override
        public boolean exists(WorldName name) {
            return find(name).isPresent();
        }

        @Override
        public void save(ManagedWorld world) {
            throw new UnsupportedOperationException("read-only fake");
        }

        @Override
        public void delete(WorldName name) {
            throw new UnsupportedOperationException("read-only fake");
        }
    }

    /** A {@link WorldEngine} fake with a settable loaded set, default world, and per-world player count. */
    private static final class FakeWorldEngine implements WorldEngine {

        private final Set<WorldName> loaded = new LinkedHashSet<>();
        private final Map<WorldName, Integer> players = new HashMap<>();
        private Optional<WorldName> defaultWorld = Optional.empty();

        FakeWorldEngine loaded(String... names) {
            for (String name : names) {
                loaded.add(WorldName.of(name));
            }
            return this;
        }

        FakeWorldEngine defaultWorld(String name) {
            this.defaultWorld = Optional.of(WorldName.of(name));
            return this;
        }

        FakeWorldEngine playerCount(String name, int count) {
            players.put(WorldName.of(name), count);
            return this;
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.copyOf(loaded);
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return defaultWorld;
        }

        @Override
        public int playerCount(WorldName name) {
            return players.getOrDefault(name, 0);
        }

        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            throw new UnsupportedOperationException("read-only fake");
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            throw new UnsupportedOperationException("read-only fake");
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            throw new UnsupportedOperationException("read-only fake");
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            throw new UnsupportedOperationException("read-only fake");
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return loaded.contains(name);
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return loaded.contains(name);
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
}
