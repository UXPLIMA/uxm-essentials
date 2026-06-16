package com.uxplima.uxmessentials.persistence.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the in-memory-authoritative behaviour of {@link CachedNpcRepository} against a counting fake delegate.
 * The {@code /npc} use cases call {@code exists}/{@code find} on the command (tick/region) thread to validate a
 * name before a mutation; those reads must be served from the warm-loaded in-memory set, never a synchronous
 * SQLite query on that thread. So after a single warm load (which the renderer's spawn-on-enable triggers), any
 * number of command-style {@code exists}/{@code find}/{@code all} reads hit the delegate zero further times,
 * while a {@code save} makes the new NPC immediately findable and a {@code delete} immediately absent — both
 * write-through at the delegate and reflected in memory with no extra delegate read.
 */
class CachedNpcRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final NpcName GUIDE = NpcName.of("guide");
    private static final NpcName SHOP = NpcName.of("shop");

    private static Npc npc(NpcName name) {
        return Npc.create(name, Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000));
    }

    @Test
    void repeatedExistsAndFindReadsHitTheDelegateOnceThenServeFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(npc(GUIDE));
        CachedNpcRepository cached = new CachedNpcRepository(delegate);

        cached.all(); // the renderer's spawn-on-enable warm load

        for (int i = 0; i < 100; i++) {
            assertThat(cached.exists(GUIDE)).isTrue();
            assertThat(cached.exists(SHOP)).isFalse();
            assertThat(cached.find(GUIDE)).isPresent();
            assertThat(cached.find(SHOP)).isEmpty();
            assertThat(cached.all()).hasSize(1);
        }

        // Only the single warm load read the delegate; every command-style lookup served from memory.
        assertThat(delegate.allReads.get()).isEqualTo(1);
    }

    @Test
    void aSavedNpcIsImmediatelyFindableWithoutAFurtherDelegateRead() {
        CountingRepository delegate = new CountingRepository();
        CachedNpcRepository cached = new CachedNpcRepository(delegate);

        cached.all(); // warm load over an empty set
        assertThat(cached.exists(SHOP)).isFalse();

        cached.save(npc(SHOP)); // write-through at the delegate, reflected in the in-memory set

        assertThat(cached.exists(SHOP)).isTrue();
        assertThat(cached.find(SHOP)).isPresent();
        assertThat(cached.all()).hasSize(1);
        assertThat(delegate.allReads.get()).isEqualTo(1); // still just the one warm load
        assertThat(delegate.saves.get()).isEqualTo(1);
    }

    @Test
    void aDeletedNpcIsImmediatelyAbsentWithoutAFurtherDelegateRead() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(npc(GUIDE));
        CachedNpcRepository cached = new CachedNpcRepository(delegate);

        cached.all(); // warm load
        assertThat(cached.exists(GUIDE)).isTrue();

        cached.delete(GUIDE); // write-through at the delegate, removed from the in-memory set

        assertThat(cached.exists(GUIDE)).isFalse();
        assertThat(cached.find(GUIDE)).isEmpty();
        assertThat(cached.all()).isEmpty();
        assertThat(delegate.allReads.get()).isEqualTo(1); // still just the one warm load
        assertThat(delegate.deletes.get()).isEqualTo(1);
    }

    @Test
    void firstReadBeforeAnyWarmLoadLoadsOnceThenServesFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(npc(GUIDE));
        CachedNpcRepository cached = new CachedNpcRepository(delegate);

        // Even without an explicit warm load, the first lookup loads the set, then every later read is served
        // from memory — a later miss never re-queries the delegate.
        assertThat(cached.exists(GUIDE)).isTrue();
        for (int i = 0; i < 50; i++) {
            assertThat(cached.exists(SHOP)).isFalse();
            assertThat(cached.find(GUIDE)).isPresent();
        }

        assertThat(delegate.allReads.get()).isEqualTo(1);
    }

    /** A fake delegate that counts {@code all}/{@code save}/{@code delete} calls and holds the NPCs in memory. */
    private static final class CountingRepository implements NpcRepository {

        private final Map<String, Npc> stored = new LinkedHashMap<>();
        private final AtomicInteger allReads = new AtomicInteger();
        private final AtomicInteger findReads = new AtomicInteger();
        private final AtomicInteger existsReads = new AtomicInteger();
        private final AtomicInteger saves = new AtomicInteger();
        private final AtomicInteger deletes = new AtomicInteger();

        void store(Npc npc) {
            stored.put(npc.name().value(), npc);
        }

        @Override
        public Optional<Npc> find(NpcName name) {
            findReads.incrementAndGet();
            return Optional.ofNullable(stored.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            allReads.incrementAndGet();
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean exists(NpcName name) {
            existsReads.incrementAndGet();
            return stored.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            saves.incrementAndGet();
            store(Objects.requireNonNull(npc, "npc"));
        }

        @Override
        public void delete(NpcName name) {
            deletes.incrementAndGet();
            stored.remove(name.value());
        }
    }
}
