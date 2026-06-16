package com.uxplima.uxmessentials.persistence.holograms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import org.junit.jupiter.api.Test;

/**
 * Pins the read-cache behaviour of {@link CachedHologramRepository} against a counting fake delegate, with the
 * MANUAL viewer set as the focus: the renderer reads {@code manualViewers} on every render (a refreshing MANUAL
 * hologram on every refresh tick) and on every join, so the cache must serve those reads from memory rather than
 * hit the delegate (a synchronous SQLite query on the tick thread) each time, while a show/hide/delete still
 * invalidates so the next read reflects the durable state.
 */
class CachedHologramRepositoryTest {

    private static final HologramName SPAWN = HologramName.of("spawn");
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void repeatedManualViewerReadsHitTheDelegateOnceThenServeFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.showTo(SPAWN, ALICE);
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        // Simulate many render/refresh/join reads of the same hologram's viewer set.
        for (int i = 0; i < 100; i++) {
            assertThat(cached.manualViewers(SPAWN)).containsExactly(ALICE);
        }

        assertThat(delegate.manualViewerReads.get()).isEqualTo(1);
    }

    @Test
    void aShowInvalidatesSoTheNextReadReflectsTheNewSet() {
        CountingRepository delegate = new CountingRepository();
        delegate.showTo(SPAWN, ALICE);
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        assertThat(cached.manualViewers(SPAWN)).containsExactly(ALICE); // loads and caches
        cached.showTo(SPAWN, BOB); // write-through at delegate, invalidate the cached set

        assertThat(cached.manualViewers(SPAWN)).containsExactlyInAnyOrder(ALICE, BOB); // reloaded, not stale
        assertThat(delegate.manualViewerReads.get()).isEqualTo(2); // one initial load, one reload after the show
    }

    @Test
    void aHideInvalidatesSoTheNextReadReflectsTheNewSet() {
        CountingRepository delegate = new CountingRepository();
        delegate.showTo(SPAWN, ALICE);
        delegate.showTo(SPAWN, BOB);
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        assertThat(cached.manualViewers(SPAWN)).containsExactlyInAnyOrder(ALICE, BOB);
        cached.hideFrom(SPAWN, ALICE);

        assertThat(cached.manualViewers(SPAWN)).containsExactly(BOB);
    }

    @Test
    void aDeleteInvalidatesTheViewerSet() {
        CountingRepository delegate = new CountingRepository();
        delegate.showTo(SPAWN, ALICE);
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        assertThat(cached.manualViewers(SPAWN)).containsExactly(ALICE);
        cached.delete(SPAWN);

        assertThat(cached.manualViewers(SPAWN)).isEmpty();
    }

    /** A fake delegate that counts {@code manualViewers} reads and holds the viewer set in memory. */
    private static final class CountingRepository implements HologramRepository {

        private final Map<String, Set<UUID>> sets = new ConcurrentHashMap<>();
        private final AtomicInteger manualViewerReads = new AtomicInteger();

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            manualViewerReads.incrementAndGet();
            return new LinkedHashSet<>(sets.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            sets.computeIfAbsent(name.value(), key -> new LinkedHashSet<>()).add(viewer);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            Set<UUID> set = sets.get(name.value());
            if (set != null) {
                set.remove(viewer);
            }
        }

        @Override
        public void delete(HologramName name) {
            sets.remove(name.value());
        }

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.empty();
        }

        @Override
        public List<Hologram> all() {
            return new ArrayList<>();
        }

        @Override
        public boolean exists(HologramName name) {
            return sets.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {}
    }
}
