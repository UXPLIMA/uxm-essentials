package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * A peer's warp change is taken in on the async lane: a reader before the reload still gets the warps from memory,
 * never a load of its own, and the warp the peer added is there once the reload ran.
 */
class WarpSyncTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");

    private final SharedRows rows = new SharedRows();
    private final CachedWarpRepository cache = new CachedWarpRepository(rows);
    private final ManualScheduler scheduler = new ManualScheduler();
    private final RemoteSyncListener listener = WarpSync.listener(cache, scheduler);

    @Test
    void aPeersChangeIsLoadedOffTheReadersThreadAndReadersKeepTheOldSetUntilThen() {
        rows.save(warp("hub"));
        cache.all();
        rows.save(warp("arena"));

        listener.onRemoteChange(new WarpChanged("survival", "arena"));
        int loadsBefore = rows.loads;

        assertThat(cache.all()).extracting(warp -> warp.name().value()).containsExactly("hub");
        assertThat(rows.loads).isEqualTo(loadsBefore);

        scheduler.runAsync();

        assertThat(cache.find(WarpName.of("arena"))).isPresent();
        assertThat(rows.loads).isEqualTo(loadsBefore + 1);
    }

    private static Warp warp(String name) {
        return Warp.create(
                WarpName.of(name), new Position(WORLD, 0, 64, 0, 0, 0), OWNER, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** The shared table; counts every whole-set read. */
    private static final class SharedRows implements WarpRepository {
        private final Map<String, Warp> rows = new LinkedHashMap<>();
        int loads;

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            loads++;
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            rows.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            rows.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0;
        }
    }

    /** Keeps async tasks until the test runs them, as the async lane would run them later. */
    private static final class ManualScheduler implements Scheduler {
        private final List<Runnable> async = new ArrayList<>();

        void runAsync() {
            List<Runnable> due = List.copyOf(async);
            async.clear();
            due.forEach(Runnable::run);
        }

        @Override
        public void async(Runnable task) {
            async.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            async.add(task);
        }

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
    }
}
