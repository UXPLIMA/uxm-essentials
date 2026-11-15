package com.uxplima.uxmessentials.persistence.warps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * A whole-set reload takes in rows another server wrote while readers keep the old set, and never overwrites a warp
 * saved here while it was loading.
 */
class CachedWarpRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");

    private final SharedRows rows = new SharedRows();
    private final CachedWarpRepository cache = new CachedWarpRepository(rows);

    @Test
    void aReaderDuringTheReloadGetsTheOldWarpAndTheFreshOneAfter() {
        rows.put(warp("hub", 1));
        cache.all();
        rows.put(warp("hub", 9));
        AtomicReference<Optional<Warp>> seenDuringLoad = new AtomicReference<>(Optional.empty());
        rows.duringNextLoad(() -> seenDuringLoad.set(cache.find(WarpName.of("hub"))));

        cache.reloadAll();

        assertThat(seenDuringLoad.get()).contains(warp("hub", 1));
        assertThat(cache.find(WarpName.of("hub"))).contains(warp("hub", 9));
    }

    @Test
    void aWarpSavedHereWhileTheReloadLoadsSurvivesIt() {
        rows.put(warp("hub", 1));
        cache.all();
        rows.duringNextLoad(() -> cache.save(warp("arena", 2)));

        cache.reloadAll();

        assertThat(cache.find(WarpName.of("arena"))).contains(warp("arena", 2));
        assertThat(cache.find(WarpName.of("hub"))).contains(warp("hub", 1));
    }

    private static Warp warp(String name, double x) {
        return Warp.create(
                WarpName.of(name), new Position(WORLD, x, 64, 0, 0, 0), OWNER, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** The shared table; a hook runs inside the next {@code all()} after its rows are read, mid-load. */
    private static final class SharedRows implements WarpRepository {
        private final Map<String, Warp> rows = new LinkedHashMap<>();
        private final List<Runnable> duringNextLoad = new ArrayList<>();

        void put(Warp warp) {
            rows.put(warp.name().value(), warp);
        }

        void duringNextLoad(Runnable hook) {
            duringNextLoad.add(hook);
        }

        @Override
        public List<Warp> all() {
            List<Warp> read = List.copyOf(rows.values());
            List<Runnable> hooks = List.copyOf(duringNextLoad);
            duringNextLoad.clear();
            hooks.forEach(Runnable::run);
            return read;
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public boolean exists(WarpName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            put(warp);
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
}
