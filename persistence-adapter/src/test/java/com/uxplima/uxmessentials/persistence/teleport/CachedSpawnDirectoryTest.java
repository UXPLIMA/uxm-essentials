package com.uxplima.uxmessentials.persistence.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The spawn cache reloads while players read it, to take in rows another server wrote: a reader never sees a gap, a
 * spawn deleted elsewhere drops, and a reload never overwrites a spawn set here while it was loading.
 */
class CachedSpawnDirectoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private final Deque<Supplier<Optional<SpawnDirectorySnapshot>>> loads = new ArrayDeque<>();
    private final CachedSpawnDirectory spawns = new CachedSpawnDirectory(
            new IgnoringStore(), () -> loads.removeFirst().get());

    @Test
    void aSpawnDeletedOnAnotherServerDropsOnTheNextReload() {
        loads.add(() -> snapshot(Map.of("hub", at(1), "arena", at(2))));
        loads.add(() -> snapshot(Map.of("hub", at(1))));

        spawns.warmAll();
        spawns.warmAll();

        assertThat(spawns.namedSpawn("arena")).isEmpty();
        assertThat(spawns.namedSpawn("hub")).contains(at(1));
    }

    @Test
    void aReaderKeepsTheOldSpawnWhileTheReloadRuns() {
        AtomicReference<Optional<Position>> seenDuringLoad = new AtomicReference<>(Optional.empty());
        loads.add(() -> snapshot(Map.of("hub", at(1))));
        loads.add(() -> {
            seenDuringLoad.set(spawns.namedSpawn("hub"));
            return snapshot(Map.of("hub", at(9)));
        });

        spawns.warmAll();
        spawns.warmAll();

        assertThat(seenDuringLoad.get()).contains(at(1));
        assertThat(spawns.namedSpawn("hub")).contains(at(9));
    }

    @Test
    void aSpawnSetHereWhileTheReloadLoadsIsNotOverwritten() {
        loads.add(() -> snapshot(Map.of("hub", at(1))));
        loads.add(() -> {
            spawns.setNamedSpawn("hub", at(5));
            return snapshot(Map.of("hub", at(1)));
        });

        spawns.warmAll();
        spawns.warmAll();

        assertThat(spawns.namedSpawn("hub")).contains(at(5));
    }

    private static Optional<SpawnDirectorySnapshot> snapshot(Map<String, Position> named) {
        return Optional.of(new SpawnDirectorySnapshot(Map.of(), named, Map.of(), Map.of()));
    }

    private static Position at(double x) {
        return new Position(WORLD, x, 64, 0, 0, 0);
    }

    /** The durable store; these tests drive the cache through the snapshot loader only. */
    private static final class IgnoringStore implements SpawnDirectory {
        @Override
        public Optional<Position> defaultSpawn(WorldRef world) {
            return Optional.empty();
        }

        @Override
        public Optional<Position> operatorSpawn(WorldRef world) {
            return Optional.empty();
        }

        @Override
        public Optional<Position> mainSpawn(@Nullable ServerScope scope) {
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
        public void setDefaultSpawn(WorldRef world, Position position) {}

        @Override
        public void setNamedSpawn(String name, Position position) {}

        @Override
        public void setMainSpawn(@Nullable ServerScope scope, Position position) {}

        @Override
        public boolean removeDefaultSpawn(WorldRef world) {
            return false;
        }

        @Override
        public void setMirror(SpawnMirror mirror) {}
    }
}
