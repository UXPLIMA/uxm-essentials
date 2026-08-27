package com.uxplima.uxmessentials.persistence.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CachedSpawnDirectoryTest {

    @Test
    void authoritativeWarmMakesUnknownWorldAndNameReadsMemoryOnly() {
        WorldRef known = new WorldRef(UUID.randomUUID(), "known");
        WorldRef unknown = new WorldRef(UUID.randomUUID(), "later-loaded");
        Position spawn = Position.of(known, 3, 70, 4);
        CountingDirectory durable = new CountingDirectory();
        SpawnDirectorySnapshot snapshot =
                new SpawnDirectorySnapshot(Map.of(known.uid(), spawn), Map.of(), Map.of(), Optional.empty());
        CachedSpawnDirectory cached = new CachedSpawnDirectory(durable, () -> Optional.of(snapshot));

        cached.warmAll();

        assertThat(cached.operatorSpawn(known)).contains(spawn);
        assertThat(cached.operatorSpawn(unknown)).isEmpty();
        assertThat(cached.namedSpawn("missing")).isEmpty();
        assertThat(durable.worldReads).isZero();
        assertThat(durable.namedReads).isZero();
    }

    @Test
    void warmMakesAutomaticWorldReadsMemoryOnly() {
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        CountingDirectory durable = new CountingDirectory();
        durable.worlds.put(world.uid(), Position.of(world, 3, 70, 4));
        CachedSpawnDirectory cached = new CachedSpawnDirectory(durable);

        cached.warm(java.util.List.of(world));
        cached.operatorSpawn(world);
        cached.operatorSpawn(world);
        cached.mainSpawn();
        cached.mirrorFor(world);

        assertThat(durable.worldReads).isEqualTo(1);
        assertThat(durable.mainReads).isEqualTo(1);
        assertThat(durable.mirrorReads).isEqualTo(1);
    }

    @Test
    void writesAreDurableAndImmediatelyVisibleInTheSnapshot() {
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        CountingDirectory durable = new CountingDirectory();
        CachedSpawnDirectory cached = new CachedSpawnDirectory(durable);
        cached.warm(java.util.List.of(world));
        Position set = Position.of(world, 10, 80, 11);

        cached.setDefaultSpawn(world, set);

        assertThat(durable.worlds.get(world.uid())).isEqualTo(set);
        assertThat(cached.operatorSpawn(world)).contains(set);
        assertThat(durable.worldReads).isEqualTo(1);
    }

    private static final class CountingDirectory implements SpawnDirectory {
        private final Map<UUID, Position> worlds = new HashMap<>();
        private final Map<UUID, SpawnMirror> mirrors = new HashMap<>();
        private int worldReads;
        private int mainReads;
        private int mirrorReads;
        private int namedReads;
        private @Nullable Position main;

        @Override
        public Optional<Position> defaultSpawn(WorldRef world) {
            return operatorSpawn(world);
        }

        @Override
        public Optional<Position> operatorSpawn(WorldRef world) {
            worldReads++;
            return Optional.ofNullable(worlds.get(world.uid()));
        }

        @Override
        public Optional<Position> mainSpawn() {
            mainReads++;
            return Optional.ofNullable(main);
        }

        @Override
        public Optional<Position> namedSpawn(String name) {
            namedReads++;
            return Optional.empty();
        }

        @Override
        public Optional<SpawnMirror> mirrorFor(WorldRef world) {
            mirrorReads++;
            return Optional.ofNullable(mirrors.get(world.uid()));
        }

        @Override
        public void setDefaultSpawn(WorldRef world, Position position) {
            worlds.put(world.uid(), position);
        }

        @Override
        public void setNamedSpawn(String name, Position position) {}

        @Override
        public void setMainSpawn(Position position) {
            main = position;
        }

        @Override
        public boolean removeDefaultSpawn(WorldRef world) {
            return worlds.remove(world.uid()) != null;
        }

        @Override
        public void setMirror(SpawnMirror mirror) {
            mirrors.put(mirror.sourceWorld(), mirror);
        }
    }
}
