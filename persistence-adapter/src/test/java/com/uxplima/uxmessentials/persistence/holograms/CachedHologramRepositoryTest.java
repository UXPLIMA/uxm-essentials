package com.uxplima.uxmessentials.persistence.holograms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the cache behaviour of {@link CachedHologramRepository} against a counting fake delegate. Two read paths
 * are hot on the tick thread and must be served from memory after a single warm load, never a synchronous SQLite
 * query: the whole-set reads ({@code all}/{@code find}/{@code exists}) the refresh tick runs once a second and
 * the {@code /hologram} commands run for a name check, and the MANUAL viewer-set reads the renderer runs on every
 * render and join. A {@code save}/{@code delete} (whole set) and a {@code show}/{@code hide}/{@code delete}
 * (viewer set) keep the in-memory view in step so the next read reflects the durable state with no extra read.
 */
class CachedHologramRepositoryTest {

    private static final HologramName SPAWN = HologramName.of("spawn");
    private static final HologramName SHOP = HologramName.of("shop");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static Hologram hologram(HologramName name) {
        return Hologram.create(
                name, Position.of(WORLD, 1, 64, 1), List.of(new HologramLine("line")), Instant.ofEpochMilli(1_000));
    }

    @Test
    void repeatedAllExistsAndFindReadsHitTheDelegateOnceThenServeFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(hologram(SPAWN));
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        cached.all(); // the renderer's spawn-on-enable warm load

        // The refresh tick reads all() once a second on the global tick thread; commands read exists/find.
        for (int i = 0; i < 100; i++) {
            assertThat(cached.all()).hasSize(1);
            assertThat(cached.exists(SPAWN)).isTrue();
            assertThat(cached.exists(SHOP)).isFalse();
            assertThat(cached.find(SPAWN)).isPresent();
            assertThat(cached.find(SHOP)).isEmpty();
        }

        assertThat(delegate.allReads.get()).isEqualTo(1);
    }

    @Test
    void aSavedHologramIsImmediatelyFindableWithoutAFurtherDelegateRead() {
        CountingRepository delegate = new CountingRepository();
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        cached.all(); // warm load over an empty set
        cached.save(hologram(SHOP)); // write-through at the delegate, reflected in the in-memory set

        assertThat(cached.exists(SHOP)).isTrue();
        assertThat(cached.find(SHOP)).isPresent();
        assertThat(cached.all()).hasSize(1);
        assertThat(delegate.allReads.get()).isEqualTo(1); // still just the one warm load
    }

    @Test
    void aDeletedHologramIsImmediatelyAbsentWithoutAFurtherDelegateRead() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(hologram(SPAWN));
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        cached.all(); // warm load
        cached.delete(SPAWN); // write-through at the delegate, removed from the in-memory set

        assertThat(cached.exists(SPAWN)).isFalse();
        assertThat(cached.find(SPAWN)).isEmpty();
        assertThat(cached.all()).isEmpty();
        assertThat(delegate.allReads.get()).isEqualTo(1); // still just the one warm load
    }

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

    @Test
    void repeatedBlacklistReadsHitTheDelegateOnceThenServeFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.addToBlacklist(SPAWN, ALICE);
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        // The renderer reads the blacklist on every spawn and join, so it must be served from memory.
        for (int i = 0; i < 100; i++) {
            assertThat(cached.blacklisted(SPAWN)).containsExactly(ALICE);
        }

        assertThat(delegate.blacklistReads.get()).isEqualTo(1);
    }

    @Test
    void aBlacklistChangeInvalidatesSoTheNextReadReflectsIt() {
        CountingRepository delegate = new CountingRepository();
        delegate.addToBlacklist(SPAWN, ALICE);
        CachedHologramRepository cached = new CachedHologramRepository(delegate);

        assertThat(cached.blacklisted(SPAWN)).containsExactly(ALICE);
        cached.addToBlacklist(SPAWN, BOB);
        assertThat(cached.blacklisted(SPAWN)).containsExactlyInAnyOrder(ALICE, BOB);

        cached.removeFromBlacklist(SPAWN, ALICE);
        assertThat(cached.blacklisted(SPAWN)).containsExactly(BOB);
    }

    /** A fake delegate that counts {@code all}/{@code manualViewers} reads and holds both sets in memory. */
    private static final class CountingRepository implements HologramRepository {

        private final Map<String, Set<UUID>> sets = new ConcurrentHashMap<>();
        private final Map<String, Set<UUID>> blacklistSets = new ConcurrentHashMap<>();
        private final Map<String, Hologram> stored = new LinkedHashMap<>();
        private final AtomicInteger manualViewerReads = new AtomicInteger();
        private final AtomicInteger blacklistReads = new AtomicInteger();
        private final AtomicInteger allReads = new AtomicInteger();

        void store(Hologram hologram) {
            stored.put(hologram.name().value(), hologram);
        }

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
        public Set<UUID> blacklisted(HologramName name) {
            blacklistReads.incrementAndGet();
            return new LinkedHashSet<>(blacklistSets.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            blacklistSets
                    .computeIfAbsent(name.value(), key -> new LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            Set<UUID> set = blacklistSets.get(name.value());
            if (set != null) {
                set.remove(viewer);
            }
        }

        @Override
        public void delete(HologramName name) {
            sets.remove(name.value());
            blacklistSets.remove(name.value());
            stored.remove(name.value());
        }

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(stored.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            allReads.incrementAndGet();
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return stored.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            store(Objects.requireNonNull(hologram, "hologram"));
        }
    }
}
